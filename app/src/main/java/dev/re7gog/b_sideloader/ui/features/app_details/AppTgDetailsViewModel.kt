package dev.re7gog.b_sideloader.ui.features.app_details

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.LongSparseArray
import androidx.core.util.size
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.re7gog.b_sideloader.R
import dev.re7gog.b_sideloader.data.installer.InstallEventManager
import dev.re7gog.b_sideloader.data.installer.InstallManager
import dev.re7gog.b_sideloader.data.local.entities.AppEntity
import dev.re7gog.b_sideloader.data.local.entities.TelegramDetailsEntity
import dev.re7gog.b_sideloader.data.telegram.TelegramManager
import dev.re7gog.b_sideloader.domain.model.AppType
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import dev.re7gog.b_sideloader.ui.navigation.AppTgDetailsFromDbRoute
import dev.re7gog.b_sideloader.ui.navigation.AppTgDetailsFromSearchRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.io.File
import javax.inject.Inject

data class ApkTgMessageTemp(
    val foundFile: Boolean,
    val foundMsgText: Boolean,
    val file: TdApi.Document,
    val msgText: String,
    val id: Long
)

data class ApkTgMessage(
    val file: TdApi.Document,
    val msgText: String,
    val id: Long
)

@HiltViewModel
class AppTgDetailsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val repository: AppsRepository,
    private val telegramManager: TelegramManager,
    private val installManager: InstallManager
) : ViewModel() {
    private val _uiState = MutableStateFlow<AppTgDetailsUiState?>(null)
    val uiState: StateFlow<AppTgDetailsUiState?> = _uiState.asStateFlow()

    private val _apkMessages = MutableStateFlow<List<TdApi.Message>>(emptyList())
    private val _includeFilter = MutableStateFlow("")
    private val _excludeFilter = MutableStateFlow("")

    val includeFilter = _includeFilter.asStateFlow()
    val excludeFilter = _excludeFilter.asStateFlow()

    private val _msgIncludeFilter = MutableStateFlow("")
    private val _msgExcludeFilter = MutableStateFlow("")

    val msgIncludeFilter = _msgIncludeFilter.asStateFlow()
    val msgExcludeFilter = _msgExcludeFilter.asStateFlow()

    private val _shouldUpdate = MutableStateFlow(false)
    val shouldUpdate = _shouldUpdate.asStateFlow()

    private val _shouldSave = MutableStateFlow(false)
    val shouldSave = _shouldSave.asStateFlow()

    private val _isInstalling = MutableStateFlow(false)
    val isInstalling = _isInstalling.asStateFlow()

    private val _installProgress = MutableStateFlow(0f)
    val installProgress = _installProgress.asStateFlow()

    private val installEvents = InstallEventManager.installEvents
    private val uninstallEvents = InstallEventManager.uninstallEvents

    // Install events are a global bus shared by every detail screen, so we only react
    // to one after THIS screen actually started an install (avoids saving an app to the
    // DB just because an unrelated install finished while this page was open).
    private var installRequested = false

    private var newVersion = ""

    private val _icon = MutableStateFlow<Drawable?>(null)
    val icon = _icon.asStateFlow()

    // Becomes true once this screen's own install completes successfully; drives
    // where the back button goes (apps list on success vs. search on failure)
    private val _installSucceeded = MutableStateFlow(false)
    val installSucceeded = _installSucceeded.asStateFlow()

    // Small photo file id of the source channel, used for the header avatar
    private val _channelPhotoFileId = MutableStateFlow<Int?>(null)
    val channelPhotoFileId = _channelPhotoFileId.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>()
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    val filteredApkMessages = combine(
        _apkMessages,
        _includeFilter,
        _excludeFilter,
        _msgIncludeFilter,
        _msgExcludeFilter
    ) { messages, inc, exc, msgInc, msgExc ->
        val incWords = inc.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        val excWords = exc.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        val msgIncWords = msgInc.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        val msgExcWords = msgExc.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }

        // albumId: foundFile, foundMsgText, file, msgText
        val res = LongSparseArray<ApkTgMessageTemp>()
        var latestId = 0L  // For inserting single messages in right order

        for (msg in messages) {
            val content = (msg.content as? TdApi.MessageDocument) ?: continue
            val document = content.document
            val msgText = content.caption.text

            // Check if matches filename, skip unneeded checks
            val matchesFile by lazy {
                var fileName = document.fileName.lowercase()
                var matchesFile = fileName.endsWith(".apk")
                if (matchesFile) {
                    fileName = fileName.dropLast(4)
                    matchesFile = incWords.all { fileName.contains(it.lowercase()) } &&
                            excWords.none { fileName.contains(it.lowercase()) }
                }
                matchesFile
            }

            // Same, but for message text
            val matchesText by lazy {
                msgIncWords.all { msgText.contains(it.lowercase()) } &&
                        msgExcWords.none { msgText.contains(it.lowercase()) }
            }

            val albumId = msg.mediaAlbumId
            if (albumId == 0L) {  // Single message, insert after latest. Only if everything matches
                if (matchesFile && matchesText) {
                    latestId += 1
                    res.put(latestId, ApkTgMessageTemp(
                        foundFile = true, foundMsgText = true,
                        file = document, msgText = msgText, id = msg.id
                    ))
                }
            } else {
                var album = res.get(albumId)
                if (album == null) {
                    res.put(albumId, ApkTgMessageTemp(
                        foundFile = matchesFile, foundMsgText = matchesText,
                        file = document, msgText = msgText, id = msg.id
                    ))
                    if (albumId > latestId) latestId = albumId  // Remember latest groupId
                } else {
                    if (!album.foundFile && matchesFile) {  // Message with file is the main one
                        res.put(albumId, album.copy(foundFile = true, file = document, id = msg.id))
                        album = res.get(albumId)
                    }
                    if (album.msgText == "" && msgText != "") {
                        res.put(albumId, album.copy(foundMsgText = matchesText, msgText = msgText))
                    }
                }
            }
        }

        // We need to filter albums with no matching filenames and convert it to simple list
        val finalRes = mutableListOf<ApkTgMessage>()
        for (i in res.size - 1 downTo 0) {
            val value = res.valueAt(i)
            if (value.foundFile && value.foundMsgText) {
                finalRes.add(ApkTgMessage(
                    file = value.file, msgText = value.msgText, id = value.id
                ))
            }
        }
        finalRes
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val targetApkMessage = filteredApkMessages
        .map { it.firstOrNull() }
        .also { msg -> // Switch state to show that update is available
            viewModelScope.launch {
                val currMessageId = msg.firstOrNull()?.id?.toString() ?: return@launch
                val currVer = _uiState.value?.version
                if (currMessageId != currVer) {
                    newVersion = currMessageId
                    if (!currVer.isNullOrEmpty()) _shouldUpdate.value = true
                } else if (_shouldUpdate.value) {
                    _shouldUpdate.value = false
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null
        )

    init {
        val fromDb = runCatching { savedStateHandle.toRoute<AppTgDetailsFromDbRoute>() }.getOrNull()
        val fromSearch = runCatching { savedStateHandle.toRoute<AppTgDetailsFromSearchRoute>() }.getOrNull()

        viewModelScope.launch {
            if (fromDb != null) {
                val app = repository.getAppStream(fromDb.appId).firstOrNull()
                if (app != null && app.telegramDetails != null) {
                    _uiState.value = app.toTgUiState(fromDb.installed)
                    _includeFilter.value = app.app.filterInclude
                    _excludeFilter.value = app.app.filterExclude
                    _msgIncludeFilter.value = app.telegramDetails.messageInclude
                    _msgExcludeFilter.value = app.telegramDetails.messageExclude
                }
            } else if (fromSearch != null) {
                // If this searched channel/topic is already saved, open it as the stored app
                // so the page behaves like it was launched from the apps list (Open/Update and
                // a delete option, not "Save & install") instead of adding a duplicate.
                val existing = repository.findTelegramApp(fromSearch.chatId, fromSearch.topicId ?: 0)
                if (existing?.telegramDetails != null) {
                    _uiState.value = existing.toTgUiState(
                        installed = installManager.isPackageInstalled(existing.app.packageName)
                    )
                    _includeFilter.value = existing.app.filterInclude
                    _excludeFilter.value = existing.app.filterExclude
                    _msgIncludeFilter.value = existing.telegramDetails.messageInclude
                    _msgExcludeFilter.value = existing.telegramDetails.messageExclude
                } else {
                    _uiState.value = fromSearch.toTgUiState()
                }
            }
            loadApkMessages()
            _uiState.value?.chatId?.let { chatId ->
                _channelPhotoFileId.value = telegramManager.getChat(chatId)?.photo?.small?.id
            }
        }
        viewModelScope.launch {
            installEvents.collect { installRes ->
                if (!installRequested) return@collect  // Not our install, ignore
                installRequested = false
                if (installRes.succeeded) {
                    _uiState.update {
                        it?.copy(
                            version = newVersion,
                            installed = true,
                            packageName = installRes.packageName ?: it.packageName
                        )
                    }
                    _shouldUpdate.value = false
                    _shouldSave.value = false
                    if (_uiState.value?.isFromDb == true) {
                        repository.updateApp(genTgApp())
                    } else {
                        // First install of a searched app: persist it and turn this
                        // page into a saved, installed one so the button shows "Open"
                        val newId = repository.addApp(genTgApp())
                        _uiState.update { it?.copy(id = newId, isFromDb = true) }
                    }
                    _installSucceeded.value = true
                } else {
                    _snackbarEvents.emit(installRes.errorMessage ?: context.getString(R.string.installation_error))
                }
            }
        }
        viewModelScope.launch {
            uninstallEvents.collect { uninstallRes ->
                if (uninstallRes.succeeded) {
                    _uiState.update { it?.copy(installed = false) }
                } else {
                    _snackbarEvents.emit(uninstallRes.errorMessage ?: context.getString(R.string.uninstall_failed))
                }
            }
        }
    }

    private fun genTgApp(): AppType.TelegramApp {
        val app = AppEntity(
            id = _uiState.value?.id ?: 0L,
            sourceType = 2,
            packageName = _uiState.value?.packageName ?: "",
            name = _uiState.value?.name ?: "",
            version = _uiState.value?.version ?: "",
            autoupdate = _uiState.value?.autoupdate ?: true,
            filterInclude = _includeFilter.value,
            filterExclude = _excludeFilter.value,
        )
        val details = TelegramDetailsEntity(
            id = _uiState.value?.id ?: 0L,
            chatId = _uiState.value?.chatId ?: 0L,
            topicId = _uiState.value?.topicId ?: 0,
            messageInclude = _msgIncludeFilter.value,
            messageExclude = _msgExcludeFilter.value
        )
        return AppType.TelegramApp(app, details)
    }

    suspend fun loadApkMessages() {
        val result = telegramManager.searchApkMessages(
            _uiState.value?.chatId ?: 0L, _uiState.value?.topicId ?: 0)
        if (result != null) _apkMessages.value = result.messages.toList()
    }

    fun installAppTg() {
        viewModelScope.launch(Dispatchers.IO) {
            val doc = targetApkMessage.firstOrNull()?.file?.document ?: return@launch
            var localFile: File? = null
            try {
                _isInstalling.value = true
                installRequested = true

                val localPath = telegramManager.downloadFile(doc.id)
                localFile = File(localPath)

                installManager.installFromFile(localFile, doc.size).collect {
                    _installProgress.value = it
                }
            } catch (e: Exception) {
                installRequested = false  // No install event will arrive on download failure
                _snackbarEvents.emit(e.message ?: context.getString(R.string.installation_error))
            } finally {
                _installProgress.value = 0f
                _isInstalling.value = false
                cleanupFile(doc.id, localFile)
            }
        }
    }

    private fun cleanupFile(fileId: Int, file: File?) {
        telegramManager.deleteFile(fileId)
        if (file != null && file.exists()) {
            val deleted = file.delete()
            Log.d("Cleanup", "Physical file removed: $deleted")
        }
    }

    fun uninstallApp() {
        viewModelScope.launch(Dispatchers.IO) {
            installManager.uninstallPackage(_uiState.value?.packageName ?: return@launch)
        }
    }

    /** Opens the installed app. */
    fun openApp() {
        val pkg = _uiState.value?.packageName
        if (pkg.isNullOrEmpty() || !installManager.openApp(pkg)) {
            viewModelScope.launch { _snackbarEvents.emit(context.getString(R.string.unable_to_open_app)) }
        }
    }

    /** Removes the app from the database (does not uninstall it). */
    fun deleteApp(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteApp(genTgApp().app)
            onDeleted()
        }
    }

    fun saveToDb() {
        viewModelScope.launch {
            repository.updateApp(genTgApp())
            _shouldSave.value = false
        }
    }

    fun getAppIcon(packageName: String): Drawable? {
        return installManager.getAppIcon(packageName)
    }

    /** Downloads the channel photo/avatar and returns its local path (null if none). */
    suspend fun downloadPhoto(fileId: Int): String? = telegramManager.downloadPhoto(fileId)

    fun onAutoUpdateChange(enabled: Boolean) {
        _uiState.update { it?.copy(autoupdate = enabled) }
        if (!_shouldSave.value) _shouldSave.value = true
    }

    fun onIncludeFilterChange(text: String) {
        _includeFilter.value = text
        if (!_shouldSave.value) _shouldSave.value = true
    }
    fun onExcludeFilterChange(text: String) {
        _excludeFilter.value = text
        if (!_shouldSave.value) _shouldSave.value = true
    }

    fun onMsgIncludeFilterChange(text: String) {
        _msgIncludeFilter.value = text
        if (!_shouldSave.value) _shouldSave.value = true
    }
    fun onMsgExcludeFilterChange(text: String) {
        _msgExcludeFilter.value = text
        if (!_shouldSave.value) _shouldSave.value = true
    }
}