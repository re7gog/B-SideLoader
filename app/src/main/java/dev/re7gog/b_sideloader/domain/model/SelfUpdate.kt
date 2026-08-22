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
}

/**
 * A self-update that was handed to the system installer but is not recorded in the database yet.
 *
 * Installing B-SideLoader over itself kills the process the moment the package is replaced, so
 * `InstallAppUseCase` never reaches its own write: the flow, its coroutine and the whole process
 * are gone before `PackageInstaller` reports success. This record is written *before* the install
 * starts and [dev.re7gog.b_sideloader.domain.usecase.ConfirmSelfUpdateUseCase] finishes the write
 * from the new version's process.
 *
 * @param appId row the version belongs to. Only saved apps are recorded — an unsaved one has no id
 *   to write back to, and the seeded self row means that case does not arise in practice.
 * @param previousLastUpdateTime, [previousVersionCode] what was on the device when the install
 *   began, so the new process can tell "the package was replaced" from "the user declined it".
 */
data class PendingSelfUpdate(
    val appId: Long,
    val packageName: String,
    val version: AppVersion,
    val previousLastUpdateTime: Long,
    val previousVersionCode: Long,
)
