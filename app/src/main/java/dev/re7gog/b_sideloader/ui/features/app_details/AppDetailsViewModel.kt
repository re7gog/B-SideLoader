package dev.re7gog.b_sideloader.ui.features.app_details

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.re7gog.b_sideloader.data.local.entities.AppEntity
import dev.re7gog.b_sideloader.data.local.entities.GithubDetailsEntity
import dev.re7gog.b_sideloader.data.logic.findCurrentAbiApk
import dev.re7gog.b_sideloader.data.remote.GithubApi
import dev.re7gog.b_sideloader.data.telegram.TelegramManager
import dev.re7gog.b_sideloader.domain.logic.IInstallManager
import dev.re7gog.b_sideloader.domain.model.AppType
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import dev.re7gog.b_sideloader.ui.navigation.AppDetailsFromDbRoute
import dev.re7gog.b_sideloader.ui.navigation.AppDetailsFromSearchRoute
import dev.re7gog.b_sideloader.ui.navigation.AppDetailsFromSearchTgRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.io.File
import javax.inject.Inject

@HiltViewModel
class AppDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AppsRepository,
    private val githubApi: GithubApi,
    private val installManager: IInstallManager,
    private val telegramManager: TelegramManager
) : ViewModel() {
    private val _uiState = MutableStateFlow<AppDetailsUiState?>(null)
    val uiState: StateFlow<AppDetailsUiState?> = _uiState.asStateFlow()

    private val _tgUiState = MutableStateFlow<AppDetailsTgUiState?>(null)
    val tgUiState: StateFlow<AppDetailsTgUiState?> = _tgUiState.asStateFlow()

    private val _apkMessages = MutableStateFlow<List<TdApi.Message>>(emptyList())
    private val _includeFilter = MutableStateFlow("")
    private val _excludeFilter = MutableStateFlow("")

    val includeFilter = _includeFilter.asStateFlow()
    val excludeFilter = _excludeFilter.asStateFlow()

    val filteredApkMessages = combine(
        _apkMessages, _includeFilter, _excludeFilter
    ) { messages, inc, exc ->
        val incWords = inc.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        val excWords = exc.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }

        messages.filter { message ->
            val doc = (message.content as? TdApi.MessageDocument)?.document
            val fileName = doc?.fileName?.lowercase() ?: ""

            val isApk = fileName.endsWith(".apk")
            if (!isApk) return@filter false

            val matchesInc = incWords.all { fileName.contains(it.lowercase()) }
            val matchesExc = excWords.none { fileName.contains(it.lowercase()) }

            matchesInc && matchesExc
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val targetApkMessage = filteredApkMessages
        .map { it.firstOrNull() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null
        )

    private val _isInstalling = MutableStateFlow(false)
    //val isInstalling = _isInstalling.asStateFlow()

    init {
        val fromDb = runCatching { savedStateHandle.toRoute<AppDetailsFromDbRoute>() }.getOrNull()
        val fromSearch = runCatching { savedStateHandle.toRoute<AppDetailsFromSearchRoute>() }.getOrNull()
        val fromTgSearch = runCatching { savedStateHandle.toRoute<AppDetailsFromSearchTgRoute>() }.getOrNull()

        viewModelScope.launch {
            if (fromTgSearch != null) {
                _tgUiState.value = fromTgSearch.toTgUiState()
            }
            else if (fromDb != null) {
                repository.getAppStream(fromDb.appId)
                    .collect { data ->
                        _uiState.value = data?.toUiState(githubApi)
                    }
            } else if (fromSearch != null) {
                _uiState.value = fromSearch.toUiState()
            }
        }
    }

    fun saveToDb() {
        // TODO: new DB fields
        viewModelScope.launch {
            val app = AppEntity(
                sourceType = 0,
                name = _uiState.value?.name ?: "",
                version = "TODO",
                autoupdate = true,
                filterInclude = "TODO",
                filterExclude = "TODO"
            )
            val details = GithubDetailsEntity(
                id = 0,
                fullName = _uiState.value?.fullName ?: "",
                usePrereleases = false,
                releasesInclude = "TODO",
                releasesExclude = "TODO"
            )
            val appWithDetails = AppType.GithubApp(app, details)
            repository.addApp(appWithDetails)
        }
    }

    fun installApp() {
        viewModelScope.launch {
            val assets = githubApi.getReleases(
                owner = _uiState.value?.owner ?: "", repo = _uiState.value?.name ?: ""
            )[0].assets
            val url = findCurrentAbiApk(assets) ?: return@launch
            try {
                _uiState.update { it?.copy(isInstalling = true, installProgress = 0f) }
                installManager.downloadAndInstall(url).collect { progress ->
                    _uiState.update { it?.copy(installProgress = progress) }
                }
            } catch (_: Exception) {
                _uiState.update { it?.copy(isInstalling = false, installProgress = null) }
                // TODO: Show error
            }
        }
    }

    fun onIncludeFilterChange(text: String) { _includeFilter.value = text }
    fun onExcludeFilterChange(text: String) { _excludeFilter.value = text }

    fun loadApkMessages(chatId: Long, topicId: Int?) {
        viewModelScope.launch {
            val result = telegramManager.searchApkMessages(chatId, topicId ?: 0)
            if (result != null) _apkMessages.value = result.messages.toList()
        }
    }

    fun startInstall(message: TdApi.Message) {
        val doc = (message.content as? TdApi.MessageDocument)?.document ?: return
        val fileId = doc.document.id

        viewModelScope.launch(Dispatchers.IO) {
            var localFile: File? = null
            try {
                _isInstalling.value = true

                val localPath = telegramManager.downloadFile(fileId)
                localFile = File(localPath)

                // TODO: Show installation progress
                installManager.installFromFile(localFile, doc.document.size)
            } catch (e: Exception) {
                Log.e("Install", "Installation error: ${e.message}")
            } finally {
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
}