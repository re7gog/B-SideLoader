package dev.re7gog.b_sideloader.domain.model

/**
 * Where B-SideLoader publishes itself.
 *
 * The app tracks itself through exactly the same machinery as any other app — a row in the apps
 * table pointing at this repository, seeded by `data/local/SelfAppSeed`. The identity lives in the
 * domain because both that seed and the self-update reconciliation need it, and a second copy of
 * "re7gog/B-SideLoader" would be one copy too many.
 */
object SelfApp {
    const val OWNER: String = "re7gog"
    const val REPO: String = "B-SideLoader"

    /** The name the seeded row carries; the user can rename it like any other app. */
    const val NAME: String = "B-SideLoader"

    val source: AppSource.GitHub get() = AppSource.GitHub(owner = OWNER, repo = REPO)

    /**
     * Whether [source] is this repository, whatever its filters — the only source whose release
     * names a build's own tag can stand in for. GitHub treats names case-insensitively.
     */
    fun isPublishedBy(source: AppSource): Boolean =
        source is AppSource.GitHub &&
            source.owner.equals(OWNER, ignoreCase = true) &&
            source.repo.equals(REPO, ignoreCase = true)
}

/**
 * What B-SideLoader remembers about updating itself, across the process death that every
 * self-update is.
 *
 * @param rememberedVersionCode version code of the build that last reconciled its own row, or
 *   `null` before this build — or any build that knows about this record — has ever run.
 * @param pending an update handed to the installer that no process has judged yet.
 */
data class SelfUpdateState(
    val rememberedVersionCode: Long?,
    val pending: PendingSelfUpdate?,
)

/**
 * A self-update that was handed to the system installer but is not recorded in the database yet.
 *
 * Installing B-SideLoader over itself kills the process the moment the package is replaced, so
 * `InstallAppUseCase` never reaches its own write: the flow, its coroutine and the whole process
 * are gone before `PackageInstaller` reports success. This record is written *before* the install
 * starts, and [dev.re7gog.b_sideloader.domain.usecase.ReconcileSelfUpdateUseCase] decides its fate
 * in the next process by comparing version codes against [SelfUpdateState.rememberedVersionCode].
 *
 * @param appId row the version belongs to. Only saved apps are recorded — an unsaved one has no id
 *   to write back to, and the seeded self row means that case does not arise in practice.
 * @param releaseName the GitHub release being installed, i.e. what the row's version becomes.
 */
data class PendingSelfUpdate(
    val appId: Long,
    val releaseName: AppVersion,
)
