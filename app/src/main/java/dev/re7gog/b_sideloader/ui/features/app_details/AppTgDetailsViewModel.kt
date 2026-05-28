package dev.re7gog.b_sideloader.ui.features.app_details

import android.graphics.drawable.Drawable
import android.util.Log
import android.util.LongSparseArray
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.re7gog.b_sideloader.data.local.entities.AppEntity
import dev.re7gog.b_sideloader.data.local.entities.TelegramDetailsEntity
import dev.re7gog.b_sideloader.data.telegram.TelegramManager
import dev.re7gog.b_sideloader.domain.logic.IInstallManager
import dev.re7gog.b_sideloader.domain.model.AppType
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import dev.re7gog.b_sideloader.ui.navigation.AppTgDetailsFromDbRoute
import dev.re7gog.b_sideloader.ui.navigation.AppTgDetailsFromSearchRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
import androidx.core.util.size
import dev.re7gog.b_sideloader.data.installer.InstallEventManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@HiltViewModel
class AppTgDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AppsRepository,
    private val telegramManager: TelegramManager,
    private val installManager: IInstallManager
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

    private val _isInstalling = MutableStateFlow(false)
    val isInstalling = _isInstalling.asStateFlow()

    private val _installProgress = MutableStateFlow(0f)
    val installProgress = _installProgress.asStateFlow()

    private val installEvents = InstallEventManager.installEvents

    private var newVersion = ""

    private val _icon = MutableStateFlow<Drawable?>(null)
    val icon = _icon.asStateFlow()

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

        // Most optimized data structure for this case
        // albumId: matchesFilename, message
        val res = LongSparseArray<Pair<Boolean, TdApi.Message>>()
        var latestId = 0L  // For inserting single messages in right order

        for (msg in messages) {
            val content = (msg.content as? TdApi.MessageDocument) ?: continue

            // Check if matches filename, skip unneeded checks
            val matchesFile by lazy {
                var fileName = content.document?.fileName?.lowercase() ?: ""
                var matchesFile = fileName.endsWith(".apk")
                if (matchesFile) {
                    fileName = fileName.dropLast(4)
                    matchesFile = incWords.all { fileName.contains(it.lowercase()) }
                    if (matchesFile) {
                        matchesFile = excWords.none { fileName.contains(it.lowercase()) }
                    }
                }
                matchesFile
            }

            // Same, but for message text
            val matchesText by lazy {
                val msgText = content.caption?.text ?: ""
                var matchesText = msgIncWords.all { msgText.contains(it.lowercase()) }
                if (matchesText) {
                    matchesText = msgExcWords.none { msgText.contains(it.lowercase()) }
                }
                matchesText
            }

            val albumId = msg.mediaAlbumId
            if (albumId == 0L) {  // Single message, insert after latest. Only if everything matches
                if (matchesFile && matchesText) {
                    latestId += 1
                    res.put(latestId, Pair(true, msg))
                }
            } else {
                val album = res.get(albumId)
                // Add new album if matches text. It can not match filename, because can be overwritten later
                if (album == null) {
                    if (matchesText) {
                        res.put(albumId, Pair(matchesFile, msg))
                        if (albumId > latestId) latestId = albumId  // Remember latest groupId
                    }
                } else {
                    // If previous filename not matches, but new does, replace
                    if (!album.first && matchesFile) {
                        // Preserve message text for UI
                        (msg.content as TdApi.MessageDocument).caption.text = (album.second.content as TdApi.MessageDocument).caption.text
                        // We found right message, no need to search anymore
                        res.put(albumId, Pair(true, msg))
                    }
                }
            }
        }

        // We need to filter albums with no matching filenames and convert it to simple list
        val finalRes = mutableListOf<TdApi.Message>()
        for (i in 0 until res.size) {
            val value = res.valueAt(i)
            if (value.first) finalRes.add(value.second)
        }
        finalRes
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val targetApkMessage = filteredApkMessages
        .map { it.firstOrNull() }
        .also {  // Switch state to show that update is available
            if ((_uiState.value?.version ?: "") == "") return@also
            viewModelScope.launch {
                val currMessageId = it.firstOrNull()?.id?.toString() ?: return@launch
                if (currMessageId != _uiState.value!!.version) {
                    newVersion = currMessageId
                    _shouldUpdate.value = true
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
                _uiState.value = fromSearch.toTgUiState()
            }
            loadApkMessages()
        }
        viewModelScope.launch {
            installEvents.collect { installRes ->
                if (installRes.succeeded) {
                    _uiState.update { it?.copy(version = newVersion) }
                    if (installRes.packageName != null) {
                        _uiState.update { it?.copy(packageName = installRes.packageName) }
                    }
                    if (_shouldUpdate.value) _shouldUpdate.value = false

                    if (_uiState.value?.isFromDb ?: false) {
                        repository.updateApp(genTgApp())
                    } else {
                        repository.addApp(genTgApp())
                    }
                } else {
                    _snackbarEvents.emit(installRes.errorMessage ?: "Installation error")
                }
            }
        }
    }

    private fun genTgApp(): AppType.TelegramApp {
        val app = AppEntity(
            sourceType = 2,
            packageName = _uiState.value?.packageName ?: "",
            name = _uiState.value?.name ?: "",
            version = _uiState.value?.version ?: "",
            autoupdate = _uiState.value?.autoupdate ?: true,
            filterInclude = _includeFilter.value,
            filterExclude = _excludeFilter.value,
        )
        val details = TelegramDetailsEntity(
            id = 0,
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
            val doc = (targetApkMessage.firstOrNull()?.content as? TdApi.MessageDocument)?.document ?: return@launch
            val fileId = doc.document.id
            var localFile: File? = null
            try {
                _isInstalling.value = true

                val localPath = telegramManager.downloadFile(fileId)
                localFile = File(localPath)

                installManager.installFromFile(localFile, doc.document.size).collect {
                    _installProgress.value = it
                }
            } catch (e: Exception) {
                _snackbarEvents.emit(e.message ?: "Installation error")
            } finally {
                _installProgress.value = 0f
                _isInstalling.value = false
                cleanupFile(fileId, localFile)
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

    fun getAppIcon(packageName: String): Drawable? {
        return installManager.getAppIcon(packageName)
    }

    fun onAutoUpdateChange(enabled: Boolean) {
        _uiState.update { it?.copy(autoupdate = enabled) }
    }

    fun onIncludeFilterChange(text: String) { _includeFilter.value = text }
    fun onExcludeFilterChange(text: String) { _excludeFilter.value = text }

    fun onMsgIncludeFilterChange(text: String) { _msgIncludeFilter.value = text }
    fun onMsgExcludeFilterChange(text: String) { _msgExcludeFilter.value = text }
}