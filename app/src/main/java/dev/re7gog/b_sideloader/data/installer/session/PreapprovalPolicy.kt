package dev.re7gog.b_sideloader.data.installer.session

import android.os.Build
import dev.re7gog.b_sideloader.domain.model.InstallerMode

/**
 * Whether asking the user to approve an install up front (Android 14's pre-approval) would spare
 * them a dialog — the only reason to ask. Pure, so the rule is pinned down by plain unit tests.
 *
 * It does only for an installed app the standard installer would have to ask about anyway
 * ([UserActionPolicy]): one whose installer of record is someone else (Play, another store, a file
 * manager), or that another app has claimed update ownership of. When the commit would be silent —
 * this app installed it and nobody else owns its updates, or it is this app — asking would *add* a
 * dialog. Privileged installers are silent already, and a first install is out of reach: the
 * request must carry the app's label, which is unknown until the APK is downloaded.
 */
internal object PreapprovalPolicy {

    fun sparesADialog(mode: InstallerMode, facts: UpdateFacts): Boolean {
        if (facts.sdkInt < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        if (mode != InstallerMode.Session) return false
        return facts.installed && UserActionPolicy.isRequired(facts)
    }
}
