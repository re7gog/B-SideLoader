package dev.re7gog.b_sideloader.data.installer.privileged

import android.content.IIntentReceiver
import android.content.IIntentSender
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.os.IBinder

/**
 * Builds an [IntentSender] backed by an in-process callback.
 *
 * The privileged path commits its session as the *shell* user, so a `PendingIntent` created by
 * this app would be delivered to the wrong uid. Handing `PackageInstaller` a raw `IIntentSender`
 * binder instead keeps the callback inside this process, which is why the result comes back here
 * directly rather than through a broadcast receiver like the unprivileged path.
 *
 * `IntentSender` has no public constructor taking an `IIntentSender`, hence the reflection. Both
 * `send` overloads are implemented because their signature changed across API levels and the
 * framework picks whichever one it knows.
 *
 * Kept in its own file so the corresponding keep rule in `src/main/keepRules/rules.keep` names one
 * small class rather than an entire package.
 */
object IntentSenderHelper {

    fun newIntentSender(binder: IIntentSender): IntentSender =
        IntentSender::class.java
            .getConstructor(IIntentSender::class.java)
            .newInstance(binder)

    class IIntentSenderAdaptor(private val onResult: (Intent) -> Unit) : IIntentSender.Stub() {

        override fun send(
            code: Int,
            intent: Intent,
            resolvedType: String?,
            finishedReceiver: IIntentReceiver?,
            requiredPermission: String?,
            options: Bundle?,
        ): Int {
            onResult(intent)
            return 0
        }

        override fun send(
            code: Int,
            intent: Intent,
            resolvedType: String?,
            whitelistToken: IBinder?,
            finishedReceiver: IIntentReceiver?,
            requiredPermission: String?,
            options: Bundle?,
        ) {
            onResult(intent)
        }
    }
}
