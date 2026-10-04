package dev.re7gog.b_sideloader.testing

import dev.re7gog.b_sideloader.core.log.NoopLogger
import dev.re7gog.b_sideloader.domain.background.BackgroundWorkScheduler
import dev.re7gog.b_sideloader.domain.device.DeviceInfo
import dev.re7gog.b_sideloader.domain.device.SelfAppInfo
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.installer.ApkStagingArea
import dev.re7gog.b_sideloader.domain.installer.InstalledPackage
import dev.re7gog.b_sideloader.domain.installer.InstallerGateway
import dev.re7gog.b_sideloader.domain.installer.PackageChange
import dev.re7gog.b_sideloader.domain.installer.PackageInspector
import dev.re7gog.b_sideloader.domain.model.AppSettings
import dev.re7gog.b_sideloader.domain.model.AppSource
import dev.re7gog.b_sideloader.domain.model.AppVersion
import dev.re7gog.b_sideloader.domain.model.BackgroundMode
import dev.re7gog.b_sideloader.domain.model.DownloadProgress
import dev.re7gog.b_sideloader.domain.model.DownloadRef
import dev.re7gog.b_sideloader.domain.model.DownloadedApk
import dev.re7gog.b_sideloader.domain.model.GithubRelease
import dev.re7gog.b_sideloader.domain.model.GithubRepoSummary
import dev.re7gog.b_sideloader.domain.model.InstallOutcome
import dev.re7gog.b_sideloader.domain.model.InstallProgress
import dev.re7gog.b_sideloader.domain.model.InstallerMode
import dev.re7gog.b_sideloader.domain.model.PendingSelfUpdate
import dev.re7gog.b_sideloader.domain.model.PreapprovalDecision
import dev.re7gog.b_sideloader.domain.model.PreapprovalSession
import dev.re7gog.b_sideloader.domain.model.LocalApk
import dev.re7gog.b_sideloader.domain.model.PrivilegedAccess
import dev.re7gog.b_sideloader.domain.model.PrivilegedIdentity
import dev.re7gog.b_sideloader.domain.model.ResultPage
import dev.re7gog.b_sideloader.domain.model.SelfUpdateState
import dev.re7gog.b_sideloader.domain.model.TelegramAccount
import dev.re7gog.b_sideloader.domain.model.TelegramApkDocument
import dev.re7gog.b_sideloader.domain.model.TelegramAuthState
import dev.re7gog.b_sideloader.domain.model.TelegramChatSummary
import dev.re7gog.b_sideloader.domain.model.TelegramTopicSummary
import dev.re7gog.b_sideloader.domain.model.ThemeMode
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.model.UninstallOutcome
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import dev.re7gog.b_sideloader.domain.repository.GithubRepository
import dev.re7gog.b_sideloader.domain.repository.SecretsRepository
import dev.re7gog.b_sideloader.domain.repository.SelfUpdateStateRepository
import dev.re7gog.b_sideloader.domain.repository.SettingsRepository
import dev.re7gog.b_sideloader.domain.repository.TelegramDownload
import dev.re7gog.b_sideloader.domain.repository.TelegramRepository
import dev.re7gog.b_sideloader.domain.usecase.ReconcileSelfUpdateUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory stand-ins for the domain ports.
 *
 * Fakes rather than mocks: they behave like the real thing (a repository that actually stores
 * what you put in it), so a test asserts on observable behaviour instead of on which methods were
 * called — which is what makes the tests survive a refactor.
 */

class FakeAppsRepository(initial: List<TrackedApp> = emptyList()) : AppsRepository {

    private val apps = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    /** Set to have every write throw, to exercise error paths. */
    var failure: AppError? = null

    override fun observeApps(): Flow<List<TrackedApp>> = apps

    override fun observeApp(id: Long): Flow<TrackedApp?> = apps.map { list ->
        list.firstOrNull { it.id == id }
    }

    override suspend fun getApps(): List<TrackedApp> = apps.value

    override suspend fun getApp(id: Long): TrackedApp? = apps.value.firstOrNull { it.id == id }

    override suspend fun findBySource(source: AppSource): TrackedApp? =
        apps.value.firstOrNull { it.source.sameTargetAs(source) }

    override suspend fun add(app: TrackedApp): Long {
        failure?.let { throw it }
        val id = nextId++
        apps.update { it + app.copy(id = id) }
        return id
    }

    override suspend fun update(app: TrackedApp) {
        failure?.let { throw it }
        apps.update { list -> list.map { if (it.id == app.id) app else it } }
    }

    override suspend fun delete(app: TrackedApp) = deleteAll(listOf(app))

    override suspend fun deleteAll(apps: Collection<TrackedApp>) {
        failure?.let { throw it }
        val ids = apps.mapTo(mutableSetOf()) { it.id }
        this.apps.update { list -> list.filterNot { it.id in ids } }
    }

    /** Identity of a source ignores its filters, matching what the DAO lookups key on. */
    private fun AppSource.sameTargetAs(other: AppSource): Boolean = when {
        this is AppSource.GitHub && other is AppSource.GitHub ->
            owner.equals(other.owner, ignoreCase = true) && repo.equals(other.repo, ignoreCase = true)

        this is AppSource.Telegram && other is AppSource.Telegram ->
            chatId == other.chatId && topicId == other.topicId

        else -> false
    }
}

/**
 * A pinned build identity, so a test can say "this is what is running now" without `BuildConfig`.
 *
 * The default package name matches [dev.re7gog.b_sideloader.testing.selfApp], which is what makes
 * that fixture read as "B-SideLoader itself" to the code under test. The default release tag is
 * unknown — a local build — so a reconciliation run by some other test leaves the rows alone.
 */
class FakeSelfAppInfo(
    override val packageName: String = SELF_PACKAGE,
    override val versionCode: Long = 1L,
    override val releaseTag: AppVersion = AppVersion.Unknown,
) : SelfAppInfo {
    companion object {
        const val SELF_PACKAGE: String = "dev.re7gog.b_sideloader"
    }
}

class FakeSelfUpdateStateRepository(
    /** Readable and writable, so a test can both plant state and assert on what was written. */
    var rememberedVersionCode: Long? = null,
    var pending: PendingSelfUpdate? = null,
) : SelfUpdateStateRepository {

    /** How many writes happened at all, so a test can assert that nothing was written. */
    var writeCount = 0
        private set

    override suspend fun get(): SelfUpdateState = SelfUpdateState(rememberedVersionCode, pending)

    override suspend fun markPending(pending: PendingSelfUpdate) {
        this.pending = pending
        writeCount++
    }

    override suspend fun clearPending() {
        pending = null
        writeCount++
    }

    override suspend fun settle(versionCode: Long) {
        pending = null
        rememberedVersionCode = versionCode
        writeCount++
    }
}

/** The real reconciliation over fakes. With the defaults it finds nothing to change. */
fun reconcileSelfUpdate(
    apps: AppsRepository,
    state: SelfUpdateStateRepository = FakeSelfUpdateStateRepository(),
    selfInfo: SelfAppInfo = FakeSelfAppInfo(),
): ReconcileSelfUpdateUseCase = ReconcileSelfUpdateUseCase(state, apps, selfInfo, NoopLogger)

class FakeGithubRepository(
    var releases: List<GithubRelease> = emptyList(),
    var repositories: List<GithubRepoSummary> = emptyList(),
) : GithubRepository {

    var failure: AppError? = null
    var releaseCallCount = 0
        private set

    /** Results per search page; the matches are served [pageSize] at a time. */
    var pageSize = Int.MAX_VALUE
    val searchedPages = mutableListOf<Int>()

    override suspend fun searchRepositories(query: String, page: Int): ResultPage<GithubRepoSummary> {
        searchedPages += page
        failure?.let { throw it }
        return repositories.filter { it.name.contains(query, ignoreCase = true) }.page(page - 1, pageSize)
    }

    override suspend fun getRepository(owner: String, repo: String): GithubRepoSummary {
        failure?.let { throw it }
        return repositories.firstOrNull { it.owner == owner && it.name == repo }
            ?: GithubRepoSummary(owner = owner, name = repo)
    }

    override suspend fun getReleases(owner: String, repo: String, page: Int?): List<GithubRelease> {
        releaseCallCount++
        failure?.let { throw it }
        return releases
    }
}

class FakeTelegramRepository(
    var documents: List<TelegramApkDocument> = emptyList(),
    var chats: List<TelegramChatSummary> = emptyList(),
    var topics: List<TelegramTopicSummary> = emptyList(),
) : TelegramRepository {

    var failure: AppError? = null
    val discardedFileIds = mutableListOf<Int>()

    private val _authState = MutableStateFlow<TelegramAuthState>(TelegramAuthState.Ready)
    private val _authErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)

    override val authState: Flow<TelegramAuthState> = _authState
    override val authErrors: SharedFlow<String> = _authErrors.asSharedFlow()

    fun setAuthState(state: TelegramAuthState) {
        _authState.value = state
    }

    fun emitAuthError(message: String) {
        _authErrors.tryEmit(message)
    }

    /** Everything sent to TDLib during sign-in, in order, so a test can assert on the wire format. */
    val sentPhoneNumbers = mutableListOf<String>()
    val sentCodes = mutableListOf<String>()
    val sentPasswords = mutableListOf<String>()

    override suspend fun sendPhoneNumber(phoneNumber: String) {
        sentPhoneNumbers += phoneNumber
    }

    override suspend fun sendCode(code: String) {
        sentCodes += code
    }

    override suspend fun sendPassword(password: String) {
        sentPasswords += password
    }

    override suspend fun logOut() {
        _authState.value = TelegramAuthState.LoggedOut
    }

    override suspend fun getAccount(): TelegramAccount? =
        if (_authState.value is TelegramAuthState.Ready) TelegramAccount("Tester", "tester") else null

    val searchedQueries = mutableListOf<String>()

    override suspend fun searchChats(query: String, offset: Int, limit: Int): ResultPage<TelegramChatSummary> {
        searchedQueries += query
        failure?.let { throw it }
        val found = chats.filter { it.title.contains(query, ignoreCase = true) }
        return ResultPage(found.drop(offset).take(limit), hasMore = found.size > offset + limit)
    }

    override suspend fun getChat(chatId: Long): TelegramChatSummary? =
        chats.firstOrNull { it.id == chatId }

    override suspend fun getTopics(chatId: Long, limit: Int): List<TelegramTopicSummary> = topics

    override suspend fun getApkDocuments(
        chatId: Long,
        topicId: Int,
        limit: Int,
    ): List<TelegramApkDocument> {
        failure?.let { throw it }
        return documents
    }

    override fun downloadFile(fileId: Int): Flow<TelegramDownload> = flow {
        emit(TelegramDownload.Progress(0.5f))
        emit(TelegramDownload.Completed("/tmp/$fileId.apk"))
    }

    override suspend fun downloadPhoto(fileId: Int): String? = null

    override suspend fun discardLocalCopy(fileId: Int) {
        discardedFileIds += fileId
    }
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {

    private val state = MutableStateFlow(initial)

    override val settings: Flow<AppSettings> = state
    override suspend fun current(): AppSettings = state.value

    override suspend fun setInstallerMode(mode: InstallerMode) =
        state.update { it.copy(installerMode = mode) }

    override suspend fun setAutoUpdate(enabled: Boolean) =
        state.update { it.copy(autoUpdate = enabled) }

    override suspend fun setAllowMeteredNetwork(enabled: Boolean) =
        state.update { it.copy(allowMeteredNetwork = enabled) }

    override suspend fun setUseDynamicColor(enabled: Boolean) =
        state.update { it.copy(useDynamicColor = enabled) }

    override suspend fun setThemeMode(mode: ThemeMode) =
        state.update { it.copy(themeMode = mode) }

    override suspend fun setParallelUpdates(enabled: Boolean) =
        state.update { it.copy(parallelUpdates = enabled) }

    override suspend fun setBackgroundMode(mode: BackgroundMode) =
        state.update { it.copy(backgroundMode = mode) }

    override suspend fun setLongPressHintSeen(seen: Boolean) =
        state.update { it.copy(longPressHintSeen = seen) }
}

/** Records what it was asked to download and install, and replays a scripted outcome. */
class FakeInstallerGateway(
    var outcome: InstallOutcome = InstallOutcome.Success("com.example"),
) : InstallerGateway {

    /** Every download started — one per install attempt. */
    val installed = mutableListOf<DownloadRef>()

    /** Every install of a downloaded APK started, in order. */
    val committed = mutableListOf<DownloadedApk>()

    /** Every downloaded APK handed back. */
    val discarded = mutableListOf<DownloadedApk>()

    val uninstalled = mutableListOf<String>()
    var privilegedAccess: PrivilegedAccess = PrivilegedAccess.Granted(PrivilegedIdentity.Adb)

    /** When set, every download fails with it instead of producing a file. */
    var downloadFailure: AppError? = null

    /**
     * Runs the moment an install begins, at the start of its download.
     *
     * Lets a test observe the world as the installer was handed it, which is the only reliable way
     * to assert "this happened *before* the install": the flow's producer runs ahead of its
     * collector, so a value read after the first emission may already have moved on.
     */
    var onInstall: () -> Unit = {}

    /**
     * Runs half-way through a download. Suspend in it (on a `CompletableDeferred`, say) to hold
     * downloads in flight while the test looks at them.
     */
    var duringDownload: suspend (DownloadRef) -> Unit = {}

    /**
     * Runs half-way through installing a downloaded APK, before the verdict. Suspend in it to hold
     * an install in flight while the test looks at it.
     */
    var beforeVerdict: suspend () -> Unit = {}

    override fun download(source: DownloadRef): Flow<DownloadProgress> = flow {
        installed += source
        onInstall()
        emit(DownloadProgress.Downloading(0.5f))
        duringDownload(source)
        val failure = downloadFailure
        if (failure != null) {
            emit(DownloadProgress.Failed(failure))
        } else {
            emit(DownloadProgress.Downloaded(DownloadedApk("/fake/${installed.size}.apk", 1_000L, source)))
        }
    }

    override fun installDownloaded(apk: DownloadedApk, preapproved: PreapprovalSession?): Flow<InstallProgress> =
        flow {
            committed += apk
            committedSessions += preapproved
            emit(InstallProgress.Staging(0.5f))
            beforeVerdict()
            emit(InstallProgress.Finished(outcome))
        }

    // ---- pre-approval -----------------------------------------------------------------------

    /**
     * What the user answers when asked up front, or null when asking would not help — then no
     * session is opened at all, like on a device or for an app where it would not spare a dialog.
     */
    var preapproval: PreapprovalDecision? = null

    /** Runs when the user is asked, before they answer. Suspend in it to keep the dialog up. */
    var whileAsking: suspend (PreapprovalSession) -> Unit = {}

    val openedSessions = mutableListOf<PreapprovalSession>()
    val askedSessions = mutableListOf<PreapprovalSession>()
    val abandonedSessions = mutableListOf<PreapprovalSession>()

    /** The session each install went into, in the order of [committed]; null for a new session. */
    val committedSessions = mutableListOf<PreapprovalSession?>()

    override suspend fun openPreapprovalSession(packageName: String): PreapprovalSession? {
        if (preapproval == null) return null
        return PreapprovalSession(sessionId = openedSessions.size + 1, packageName = packageName)
            .also { openedSessions += it }
    }

    override suspend fun requestPreapproval(session: PreapprovalSession): PreapprovalDecision {
        askedSessions += session
        whileAsking(session)
        return preapproval ?: PreapprovalDecision.Unavailable
    }

    override suspend fun abandon(session: PreapprovalSession) {
        abandonedSessions += session
    }

    override suspend fun discard(apk: DownloadedApk) {
        discarded += apk
    }

    val installedLocal = mutableListOf<LocalApk>()

    override fun installLocal(apk: LocalApk): Flow<InstallProgress> = flow {
        installedLocal += apk
        emit(InstallProgress.Preparing)
        emit(InstallProgress.Finished(outcome))
    }

    override suspend fun uninstall(packageName: String): UninstallOutcome {
        uninstalled += packageName
        return UninstallOutcome.Success(packageName)
    }

    override suspend fun checkPrivilegedAccess(mode: InstallerMode): PrivilegedAccess = privilegedAccess
}

class FakePackageInspector(
    installedPackages: Set<String> = emptySet(),
) : PackageInspector {

    private val installed = installedPackages.toMutableSet()
    private val changes = MutableSharedFlow<PackageChange>(extraBufferCapacity = 8)
    val launched = mutableListOf<String>()

    override val packageChanges: Flow<PackageChange> = changes

    override fun isInstalled(packageName: String): Boolean = packageName in installed

    override fun installedVersion(packageName: String): InstalledPackage? =
        if (packageName in installed) InstalledPackage(packageName, "1.0", 1L) else null

    override fun launch(packageName: String): Boolean {
        launched += packageName
        return packageName in installed
    }

    fun markInstalled(packageName: String) {
        installed += packageName
        changes.tryEmit(PackageChange.Installed(packageName))
    }

    fun markRemoved(packageName: String) {
        installed -= packageName
        changes.tryEmit(PackageChange.Removed(packageName))
    }
}

class FakeDeviceInfo(
    override val supportedAbis: List<String> = ARM64_ABIS,
    override val sdkInt: Int = 34,
    override val manufacturer: String = "google",
    override val model: String = "Pixel Test",
    override val hasAggressiveBackgroundLimits: Boolean = false,
    override val supportsSilentSelfUpdates: Boolean = true,
) : DeviceInfo

class FakeSecretsRepository(var githubToken: String? = null) : SecretsRepository {
    override suspend fun getGithubToken(): String? = githubToken

    override suspend fun setGithubToken(token: String) {
        githubToken = token.takeIf { it.isNotBlank() }
    }
}

/** Remembers every settings snapshot it was asked to reconcile with. */
class FakeBackgroundWorkScheduler : BackgroundWorkScheduler {
    val synced = mutableListOf<AppSettings>()
    var ranOnce = 0
        private set

    override suspend fun sync(settings: AppSettings) {
        synced += settings
    }

    override suspend fun runOnce() {
        ranOnce++
    }
}

class FakeApkStagingArea(var staged: LocalApk? = null) : ApkStagingArea {
    var cleared = 0
        private set

    override suspend fun stage(uri: String): LocalApk =
        staged ?: throw AppError.Storage("nothing staged for $uri")

    override suspend fun clear() {
        cleared++
    }
}

private fun <T> List<T>.page(index: Int, size: Int): ResultPage<T> {
    val from = index.toLong() * size
    if (from >= this.size) return ResultPage(emptyList(), hasMore = false)
    val to = minOf(from + size, this.size.toLong()).toInt()
    return ResultPage(subList(from.toInt(), to), hasMore = to < this.size)
}
