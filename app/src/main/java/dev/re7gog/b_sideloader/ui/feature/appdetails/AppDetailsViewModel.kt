package dev.re7gog.b_sideloader.ui.feature.appdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.re7gog.b_sideloader.core.coroutines.suspendRunCatching
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.domain.device.DeviceInfo
import dev.re7gog.b_sideloader.domain.installer.PackageInspector
import dev.re7gog.b_sideloader.domain.model.AppSource
import dev.re7gog.b_sideloader.domain.model.AppVersion
import dev.re7gog.b_sideloader.domain.model.FilterMode
import dev.re7gog.b_sideloader.domain.model.FilterRule
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.model.UpdateCandidate
import dev.re7gog.b_sideloader.domain.model.UpdateCheck
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import dev.re7gog.b_sideloader.domain.repository.GithubRepository
import dev.re7gog.b_sideloader.domain.repository.TelegramRepository
import dev.re7gog.b_sideloader.domain.selection.AbiMatcher
import dev.re7gog.b_sideloader.domain.usecase.AppInstallEvent
import dev.re7gog.b_sideloader.domain.usecase.DeleteTrackedAppsUseCase
import dev.re7gog.b_sideloader.domain.usecase.InstallAppUseCase
import dev.re7gog.b_sideloader.domain.usecase.ListUpdateCandidatesUseCase
import dev.re7gog.b_sideloader.domain.usecase.OpenInstalledAppUseCase
import dev.re7gog.b_sideloader.domain.usecase.SaveTrackedAppUseCase
import dev.re7gog.b_sideloader.domain.usecase.UninstallAppsUseCase
import dev.re7gog.b_sideloader.ui.common.error.toUiText
import dev.re7gog.b_sideloader.ui.common.text.UiText
import dev.re7gog.b_sideloader.R
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * One ViewModel for both source types.
 *
 * Structure worth noting:
 *  - The user's edits live in a working copy of [TrackedApp]. Nothing is written to the database
 *    until the user saves or installs, and the *same* draft is what the candidate lookup uses, so
 *    the "available APKs" list always reflects the filters currently on screen.
 *  - Filter edits re-resolve through a debounced flow, so typing does not fire a request per
 *    keystroke, and an in-flight lookup for an older draft is discarded.
 *  - The install is a flow this ViewModel owns end to end. There is no global install bus and no
 *    `installRequested` flag: an install finishing elsewhere simply cannot be mistaken for ours.
 */
@OptIn(FlowPreview::class)
@HiltViewModel(assistedFactory = AppDetailsViewModel.Factory::class)
class AppDetailsViewModel @AssistedInject constructor(
    @Assisted private val args: AppDetailsArgs,
    private val appsRepository: AppsRepository,
    private val githubRepository: GithubRepository,
    private val telegramRepository: TelegramRepository,
    private val listCandidates: ListUpdateCandidatesUseCase,
    private val installApp: InstallAppUseCase,
    private val saveTrackedApp: SaveTrackedAppUseCase,
    private val deleteTrackedApps: DeleteTrackedAppsUseCase,
    private val uninstallApps: UninstallAppsUseCase,
    private val openInstalledApp: OpenInstalledAppUseCase,
    private val packageInspector: PackageInspector,
    private val deviceInfo: DeviceInfo,
    private val logger: Logger,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(args: AppDetailsArgs): AppDetailsViewModel
    }

    private val _uiState = MutableStateFlow(AppDetailsUiState())
    val uiState: StateFlow<AppDetailsUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<UiText>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<UiText> = _messages.asSharedFlow()

    /** The draft whose filters drive the lookup. Changed on every edit. */
    private val draft = MutableStateFlow<TrackedApp?>(null)

    private var installJob: Job? = null

    init {
        viewModelScope.launch { load() }
        observeDraftForCandidates()
        observePackageChanges()
    }

    // ---- loading ----------------------------------------------------------------------------

    private suspend fun load() {
        val loaded = when (args) {
            is AppDetailsArgs.Saved -> loadSaved(args.appId)
            is AppDetailsArgs.NewGithub -> loadNewGithub(args)
            is AppDetailsArgs.NewTelegram -> loadNewTelegram(args)
        }
        if (loaded == null) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        val (app, headline) = loaded
        draft.value = app
        _uiState.update {
            it.copy(
                isLoading = false,
                app = app,
                headline = headline,
                isInstalled = packageInspector.isInstalled(app.packageName),
            )
        }
    }

    private suspend fun loadSaved(appId: Long): Pair<TrackedApp, HeadlineUi>? {
        val app = suspendRunCatching { appsRepository.observeApp(appId).firstOrNull() }
            .onFailure { logger.e(TAG, it) { "Could not load app $appId" } }
            .getOrNull()
            ?: return null
        return app to headlineFor(app)
    }

    /**
     * A repository picked from search may already be tracked — opening it as the saved app avoids
     * creating a duplicate and gives the user Open/Update instead of "Save & install".
     */
    private suspend fun loadNewGithub(args: AppDetailsArgs.NewGithub): Pair<TrackedApp, HeadlineUi> {
        val source = AppSource.GitHub(owner = args.owner, repo = args.repo)
        appsRepository.findBySource(source)?.let { existing ->
            return existing to headlineFor(existing)
        }
        val app = TrackedApp(
            packageName = "",
            name = args.name,
            version = AppVersion.Unknown,
            autoUpdate = true,
            assetFilter = FilterRule.None,
            filterMode = FilterMode.Words,
            source = source,
        )
        return app to HeadlineUi.GitHub(
            title = args.name,
            owner = args.owner,
            description = args.description,
            stars = args.stars,
            avatarUrl = args.avatarUrl,
        )
    }

    private suspend fun loadNewTelegram(args: AppDetailsArgs.NewTelegram): Pair<TrackedApp, HeadlineUi> {
        val source = AppSource.Telegram(chatId = args.chatId, topicId = args.topicId)
        appsRepository.findBySource(source)?.let { existing ->
            return existing to headlineFor(existing)
        }
        val app = TrackedApp(
            packageName = "",
            name = args.title,
            version = AppVersion.Unknown,
            autoUpdate = true,
            assetFilter = FilterRule.None,
            filterMode = FilterMode.Words,
            source = source,
        )
        return app to HeadlineUi.Telegram(
            title = args.title,
            photoFileId = chatPhotoFileId(args.chatId),
        )
    }

    private suspend fun headlineFor(app: TrackedApp): HeadlineUi = when (val source = app.source) {
        is AppSource.GitHub -> {
            // Best-effort: a failed metadata fetch must not stop the page from rendering.
            val summary = suspendRunCatching {
                githubRepository.getRepository(source.owner, source.repo)
            }.getOrNull()
            HeadlineUi.GitHub(
                title = app.name,
                owner = source.owner,
                description = summary?.description,
                stars = summary?.stars ?: 0,
                avatarUrl = summary?.avatarUrl,
            )
        }

        is AppSource.Telegram -> HeadlineUi.Telegram(
            title = app.name,
            photoFileId = chatPhotoFileId(source.chatId),
        )
    }

    private suspend fun chatPhotoFileId(chatId: Long): Int? =
        suspendRunCatching { telegramRepository.getChat(chatId)?.photoFileId }.getOrNull()

    // ---- candidate resolution ---------------------------------------------------------------

    private fun observeDraftForCandidates() {
        viewModelScope.launch {
            draft
                .filterNotNull()
                // Only the parts that affect selection; renaming the app must not re-query.
                .map { SelectionInputs(it.assetFilter, it.filterMode, it.source) }
                .distinctUntilChanged()
                .debounce(RESOLVE_DEBOUNCE_MS.milliseconds)
                .collect { resolveCandidates() }
        }
    }

    private suspend fun resolveCandidates() {
        val app = draft.value ?: return
        _uiState.update { it.copy(isResolving = true) }
        try {
            val candidates = listCandidates(app)
            val target = AbiMatcher.pickInstallable(candidates, deviceInfo.supportedAbis) { it.fileName }
            _uiState.update {
                it.copy(
                    candidates = candidates.toImmutableList(),
                    target = target,
                    updateStatus = UpdateCheck(app, target).status,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.w(TAG, e) { "Could not resolve candidates for ${app.name}" }
            _messages.tryEmit(e.toUiText())
            _uiState.update { it.copy(candidates = persistentListOf(), target = null) }
        } finally {
            _uiState.update { it.copy(isResolving = false) }
        }
    }

    private fun observePackageChanges() {
        viewModelScope.launch {
            packageInspector.packageChanges.collect {
                val packageName = _uiState.value.app?.packageName ?: return@collect
                if (packageName.isBlank()) return@collect
                _uiState.update { it.copy(isInstalled = packageInspector.isInstalled(packageName)) }
            }
        }
    }

    // ---- edits ------------------------------------------------------------------------------

    fun onAutoUpdateChange(enabled: Boolean) = edit { it.copy(autoUpdate = enabled) }

    fun onFilterModeChange(advanced: Boolean) = edit { it.copy(filterMode = FilterMode.of(advanced)) }

    fun onAssetIncludeChange(value: String) =
        edit { it.copy(assetFilter = it.assetFilter.copy(include = value)) }

    fun onAssetExcludeChange(value: String) =
        edit { it.copy(assetFilter = it.assetFilter.copy(exclude = value)) }

    fun onReleaseIncludeChange(value: String) = editGithub { source, app ->
        app.copy(source = source.copy(releaseFilter = source.releaseFilter.copy(include = value)))
    }

    fun onReleaseExcludeChange(value: String) = editGithub { source, app ->
        app.copy(source = source.copy(releaseFilter = source.releaseFilter.copy(exclude = value)))
    }

    fun onPrereleasesChange(enabled: Boolean) = editGithub { source, app ->
        app.copy(source = source.copy(usePrereleases = enabled))
    }

    fun onMessageIncludeChange(value: String) = editTelegram { source, app ->
        app.copy(source = source.copy(messageFilter = source.messageFilter.copy(include = value)))
    }

    fun onMessageExcludeChange(value: String) = editTelegram { source, app ->
        app.copy(source = source.copy(messageFilter = source.messageFilter.copy(exclude = value)))
    }

    private inline fun edit(transform: (TrackedApp) -> TrackedApp) {
        val current = draft.value ?: return
        val updated = transform(current)
        if (updated == current) return
        draft.value = updated
        _uiState.update { it.copy(app = updated, hasUnsavedChanges = it.isSaved) }
    }

    private inline fun editGithub(transform: (AppSource.GitHub, TrackedApp) -> TrackedApp) =
        edit { app -> (app.source as? AppSource.GitHub)?.let { transform(it, app) } ?: app }

    private inline fun editTelegram(transform: (AppSource.Telegram, TrackedApp) -> TrackedApp) =
        edit { app -> (app.source as? AppSource.Telegram)?.let { transform(it, app) } ?: app }

    // ---- actions ----------------------------------------------------------------------------

    fun onPrimaryAction() {
        when (_uiState.value.primaryAction) {
            PrimaryAction.SaveChanges -> save()
            PrimaryAction.Open -> open()
            PrimaryAction.SaveAndInstall,
            PrimaryAction.Update,
            PrimaryAction.Install,
            -> install()
        }
    }

    private fun install() {
        val state = _uiState.value
        val app = state.app ?: return
        val candidate = state.target ?: run {
            _messages.tryEmit(UiText.of(R.string.error_no_release_matches))
            return
        }
        installJob?.cancel()
        installJob = viewModelScope.launch {
            installApp(app, candidate).collect { event ->
                when (event) {
                    is AppInstallEvent.Progress ->
                        _uiState.update { it.copy(install = event.progress) }

                    is AppInstallEvent.Completed -> {
                        draft.value = event.app
                        _uiState.update {
                            it.copy(
                                app = event.app,
                                install = null,
                                hasUnsavedChanges = false,
                                installSucceeded = true,
                                isInstalled = true,
                                updateStatus = UpdateCheck(event.app, it.target).status,
                            )
                        }
                    }

                    is AppInstallEvent.Failed -> {
                        _uiState.update { it.copy(install = null) }
                        _messages.tryEmit(event.error.toUiText())
                    }
                }
            }
            _uiState.update { it.copy(install = null) }
        }
    }

    private fun save() {
        val app = draft.value ?: return
        viewModelScope.launch {
            suspendRunCatching { saveTrackedApp(app) }
                .onSuccess { saved ->
                    draft.value = saved
                    _uiState.update { it.copy(app = saved, hasUnsavedChanges = false) }
                    resolveCandidates()
                }
                .onFailure { _messages.tryEmit(it.toUiText()) }
        }
    }

    private fun open() {
        val packageName = _uiState.value.app?.packageName.orEmpty()
        if (!openInstalledApp(packageName)) {
            _messages.tryEmit(UiText.of(R.string.unable_to_open_app))
        }
    }

    fun onUninstall() {
        val packageName = _uiState.value.app?.packageName ?: return
        viewModelScope.launch { uninstallApps(packageName) }
    }

    fun onDelete(onDeleted: () -> Unit) {
        val app = _uiState.value.app ?: return
        viewModelScope.launch {
            suspendRunCatching { deleteTrackedApps(app) }
                .onSuccess { onDeleted() }
                .onFailure { _messages.tryEmit(it.toUiText()) }
        }
    }

    /** Channel avatars are fetched lazily by the header that shows them. */
    suspend fun downloadPhoto(fileId: Int): String? =
        suspendRunCatching { telegramRepository.downloadPhoto(fileId) }.getOrNull()

    /** Only the parts of the draft that change what the source would return. */
    private data class SelectionInputs(
        val assetFilter: FilterRule,
        val mode: FilterMode,
        val source: AppSource,
    )

    private companion object {
        const val TAG = "AppDetails"
        const val RESOLVE_DEBOUNCE_MS = 400L
    }
}
