package dev.re7gog.b_sideloader.testing

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
import dev.re7gog.b_sideloader.domain.model.BackgroundMode
import dev.re7gog.b_sideloader.domain.model.DownloadRef
import dev.re7gog.b_sideloader.domain.model.GithubRelease
import dev.re7gog.b_sideloader.domain.model.GithubRepoSummary
import dev.re7gog.b_sideloader.domain.model.InstallOutcome
import dev.re7gog.b_sideloader.domain.model.InstallProgress
import dev.re7gog.b_sideloader.domain.model.InstallerMode
import dev.re7gog.b_sideloader.domain.model.PendingSelfUpdate
import dev.re7gog.b_sideloader.domain.model.LocalApk
import dev.re7gog.b_sideloader.domain.model.PrivilegedAccess
import dev.re7gog.b_sideloader.domain.model.PrivilegedIdentity
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
import dev.re7gog.b_sideloader.domain.repository.PendingSelfUpdateRepository
import dev.re7gog.b_sideloader.domain.repository.SettingsRepository
import dev.re7gog.b_sideloader.domain.repository.TelegramDownload
import dev.re7gog.b_sideloader.domain.repository.TelegramRepository
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
 * that fixture read as "B-SideLoader itself" to the code under test.
 */
class FakeSelfAppInfo(
    override val packageName: String = SELF_PACKAGE,
    override val versionName: String = "1.0.0",
    override val versionCode: Long = 1L,
) : SelfAppInfo {
    companion object {
        const val SELF_PACKAGE: String = "dev.re7gog.b_sideloader"
    }
}

class FakePendingSelfUpdateRepository(
    /** Readable and writable, so a test can both plant a record and assert on the one written. */
    var pending: PendingSelfUpdate? = null,
) : PendingSelfUpdateRepository {

    var clearCount = 0
        private set

    override suspend fun get(): PendingSelfUpdate? = pending

    override suspend fun put(pending: PendingSelfUpdate) {
        this.pending = pending
    }

    override suspend fun clear() {
        pending = null
        clearCount++
    }
}

class FakeGithubRepository(
    var releases: List<GithubRelease> = emptyList(),
    var repositories: List<GithubRepoSummary> = emptyList(),
) : GithubRepository {

    var failure: AppError? = null
    var releaseCallCount = 0
        private set

    override suspend fun searchRepositories(query: String, page: Int?): List<GithubRepoSummary> {
        failure?.let { throw it }
        return repositories.filter { it.name.contains(query, ignoreCase = true) }
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

    override suspend fun sendPhoneNumber(phoneNumber: String) = Unit
    override suspend fun sendCode(code: String) = Unit
    override suspend fun sendPassword(password: String) = Unit
    override suspend fun logOut() {
        _authState.value = TelegramAuthState.LoggedOut
    }

    override suspend fun getAccount(): TelegramAccount? =
        if (_authState.value is TelegramAuthState.Ready) TelegramAccount("Tester", "tester") else null

    override suspend fun searchChats(query: String, limit: Int): List<TelegramChatSummary> {
        failure?.let { throw it }
        return chats.filter { it.title.contains(query, ignoreCase = true) }
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

    override suspend fun setParallelUpdateChecks(enabled: Boolean) =
        state.update { it.copy(parallelUpdateChecks = enabled) }

    override suspend fun setBackgroundMode(mode: BackgroundMode) =
        state.update { it.copy(backgroundMode = mode) }

    override suspend fun setLongPressHintSeen(seen: Boolean) =
        state.update { it.copy(longPressHintSeen = seen) }
}

/** Records what it was asked to install and replays a scripted outcome. */
class FakeInstallerGateway(
    var outcome: InstallOutcome = InstallOutcome.Success("com.example"),
) : InstallerGateway {

    val installed = mutableListOf<DownloadRef>()
    val uninstalled = mutableListOf<String>()
    var privilegedAccess: PrivilegedAccess = PrivilegedAccess.Granted(PrivilegedIdentity.Adb)

    /**
     * Runs the moment an install begins.
     *
     * Lets a test observe the world as the installer was handed it, which is the only reliable way
     * to assert "this happened *before* the install": the flow's producer runs ahead of its
     * collector, so a value read after the first emission may already have moved on.
     */
    var onInstall: () -> Unit = {}

    override fun install(source: DownloadRef): Flow<InstallProgress> = flow {
        installed += source
        onInstall()
        emit(InstallProgress.Preparing)
        emit(InstallProgress.Downloading(0.5f))
        emit(InstallProgress.Finished(outcome))
    }

    override fun installLocal(apk: LocalApk): Flow<InstallProgress> = flow {
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

class FakeApkStagingArea(var staged: LocalApk? = null) : ApkStagingArea {
    var cleared = 0
        private set

    override suspend fun stage(uri: String): LocalApk =
        staged ?: throw AppError.Storage("nothing staged for $uri")

    override suspend fun clear() {
        cleared++
    }
}
