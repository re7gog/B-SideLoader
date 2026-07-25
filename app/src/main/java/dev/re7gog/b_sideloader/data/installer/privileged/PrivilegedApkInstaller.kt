package dev.re7gog.b_sideloader.data.installer.privileged

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.IPackageInstaller
import android.content.pm.IPackageInstallerSession
import android.content.pm.IPackageManager
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstallerHidden
import android.content.pm.PackageManager
import android.content.pm.PackageManagerHidden
import android.os.Build
import android.os.IBinder
import android.os.IInterface
import android.os.RemoteException
import android.system.Os
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener
import com.rosan.dhizuku.shared.DhizukuVariables
import dev.rikka.tools.refine.Refine
import dev.re7gog.b_sideloader.core.coroutines.runCatchingCancellable
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.data.installer.ApkInstallerBackend
import dev.re7gog.b_sideloader.data.installer.ApkPayload
import dev.re7gog.b_sideloader.data.installer.copyInto
import dev.re7gog.b_sideloader.data.installer.installFailureFor
import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.error.PrivilegedFailure
import dev.re7gog.b_sideloader.domain.model.InstallOutcome
import dev.re7gog.b_sideloader.domain.model.PrivilegedAccess
import dev.re7gog.b_sideloader.domain.model.PrivilegedIdentity
import dev.re7gog.b_sideloader.domain.model.UninstallOutcome
import kotlinx.coroutines.suspendCancellableCoroutine
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.Closeable
import kotlin.coroutines.resume

/**
 * Silent installs through a privileged helper — Shizuku/Sui (shell or root) or Dhizuku (device
 * owner).
 *
 * How it works: the helper hands us a binder for `IPackageManager`, from which we obtain
 * `IPackageInstaller` and drive a session *as that identity*. `dev.rikka.tools.refine` supplies
 * the hidden-API shapes at compile time and `hidden-api-bypass` lifts the runtime denylist.
 *
 * Lifetime is explicit ([Closeable]): the Shizuku listeners are process-global, so an instance
 * that is not closed leaks a permission callback that fires for somebody else's request. Use it
 * through [PrivilegedApkInstallerFactory.use].
 */
class PrivilegedApkInstaller(
    private val context: Context,
    private val useDhizuku: Boolean,
    private val logger: Logger,
) : ApkInstallerBackend, Closeable {

    private var binderAvailable = false
    private var runsAsRoot = false
    private var dhizukuInitialised = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { binderAvailable = true }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { binderAvailable = false }

    /** Set while a permission request is in flight; resumed from the Shizuku callback. */
    private var pendingPermission: ((Boolean) -> Unit)? = null
    private var pendingPermissionCode = 0

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != pendingPermissionCode) return@OnRequestPermissionResultListener
            val callback = pendingPermission
            pendingPermission = null
            callback?.invoke(grantResult == PackageManager.PERMISSION_GRANTED)
        }

    fun connect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/content", "Landroid/os")
        }
        if (useDhizuku) {
            dhizukuInitialised = Dhizuku.init(context)
        } else {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        }
    }

    override fun close() {
        if (useDhizuku) return
        runCatchingCancellable {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        }.onFailure { logger.w(TAG, it) { "Could not detach Shizuku listeners" } }
    }

    // ---- permission ------------------------------------------------------------------------

    suspend fun checkAccess(): PrivilegedAccess =
        if (useDhizuku) checkDhizukuAccess() else checkShizukuAccess()

    private suspend fun checkDhizukuAccess(): PrivilegedAccess = when {
        !dhizukuInitialised -> unavailable(PrivilegedFailure.ServiceNotFound)
        Dhizuku.isPermissionGranted() -> PrivilegedAccess.Granted(PrivilegedIdentity.DeviceOwner)
        requestDhizukuPermission() -> PrivilegedAccess.Granted(PrivilegedIdentity.DeviceOwner)
        else -> unavailable(PrivilegedFailure.Denied)
    }

    private suspend fun checkShizukuAccess(): PrivilegedAccess = when {
        !binderAvailable -> unavailable(PrivilegedFailure.ServiceNotFound)
        Shizuku.isPreV11() -> unavailable(PrivilegedFailure.OutdatedShizuku)
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> grantedShizuku()
        // "Deny and don't ask again" — asking again would silently do nothing.
        Shizuku.shouldShowRequestPermissionRationale() -> unavailable(PrivilegedFailure.Denied)
        requestShizukuPermission() -> grantedShizuku()
        else -> unavailable(PrivilegedFailure.Denied)
    }

    private fun grantedShizuku(): PrivilegedAccess {
        runsAsRoot = Shizuku.getUid() == ROOT_UID
        // Android 8.0 running as shell cannot register a uid observer, which the framework needs
        // to complete a session commit with no foreground activity — a silent install would hang.
        if (!runsAsRoot && Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            return unavailable(PrivilegedFailure.UnsupportedOnThisAndroid)
        }
        return PrivilegedAccess.Granted(
            if (runsAsRoot) PrivilegedIdentity.Root else PrivilegedIdentity.Adb
        )
    }

    private suspend fun requestShizukuPermission(): Boolean =
        suspendCancellableCoroutine { continuation ->
            pendingPermissionCode = PERMISSION_REQUEST_CODE
            pendingPermission = { granted ->
                if (continuation.isActive) continuation.resume(granted)
            }
            continuation.invokeOnCancellation { pendingPermission = null }
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        }

    private suspend fun requestDhizukuPermission(): Boolean =
        suspendCancellableCoroutine { continuation ->
            Dhizuku.requestPermission(object : DhizukuRequestPermissionListener() {
                @Throws(RemoteException::class)
                override fun onRequestPermission(grantResult: Int) {
                    if (continuation.isActive) {
                        continuation.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                    }
                }
            })
        }

    private fun unavailable(reason: PrivilegedFailure) =
        PrivilegedAccess.Unavailable(AppError.Privileged(reason))

    // ---- install / uninstall ---------------------------------------------------------------

    override suspend fun install(
        payload: ApkPayload,
        onProgress: suspend (Float) -> Unit,
    ): InstallOutcome {
        var session: PackageInstaller.Session? = null
        return try {
            session = openSession()
            session.openWrite(WRITE_NAME, 0, payload.lengthBytes).use { output ->
                payload.copyInto(output, onProgress)
                session.fsync(output)
            }
            val result = awaitIntent { sender ->
                @SuppressLint("RequestInstallPackagesPolicy")
                session.commit(sender)
            }
            result.toInstallOutcome()
        } catch (e: AppError) {
            session?.abandonQuietly()
            InstallOutcome.Failure(e)
        } catch (e: Throwable) {
            session?.abandonQuietly()
            if (e is kotlinx.coroutines.CancellationException) throw e
            logger.e(TAG, e) { "Privileged install failed" }
            InstallOutcome.Failure(AppError.Unexpected(e))
        } finally {
            runCatchingCancellable { session?.close() }
        }
    }

    override suspend fun uninstall(packageName: String): UninstallOutcome = try {
        val result = awaitIntent { sender ->
            activeInstaller().uninstall(packageName, sender)
        }
        if (result.status == PackageInstaller.STATUS_SUCCESS) {
            UninstallOutcome.Success(result.packageName ?: packageName)
        } else {
            UninstallOutcome.Failure(installFailureFor(result.status, result.message))
        }
    } catch (e: Throwable) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        logger.e(TAG, e) { "Privileged uninstall failed" }
        UninstallOutcome.Failure(AppError.Unexpected(e))
    }

    /** Runs [start] with a one-shot in-process `IntentSender` and waits for the framework's reply. */
    private suspend fun awaitIntent(start: (android.content.IntentSender) -> Unit): PrivilegedResult =
        suspendCancellableCoroutine { continuation ->
            val adaptor = IntentSenderHelper.IIntentSenderAdaptor { intent ->
                if (continuation.isActive) continuation.resume(intent.toPrivilegedResult())
            }
            start(IntentSenderHelper.newIntentSender(adaptor))
        }

    private fun Intent.toPrivilegedResult() = PrivilegedResult(
        status = getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE),
        message = getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
        packageName = getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME),
    )

    private fun PrivilegedResult.toInstallOutcome(): InstallOutcome =
        if (status == PackageInstaller.STATUS_SUCCESS) {
            InstallOutcome.Success(packageName)
        } else {
            InstallOutcome.Failure(installFailureFor(status, message))
        }

    private fun PackageInstaller.Session.abandonQuietly() {
        runCatchingCancellable { abandon() }
            .onFailure { logger.w(TAG, it) { "Could not abandon privileged session" } }
    }

    private data class PrivilegedResult(
        val status: Int,
        val message: String?,
        val packageName: String?,
    )

    // ---- hidden-API plumbing ---------------------------------------------------------------

    private fun IBinder.wrap(): IBinder =
        if (useDhizuku) Dhizuku.binderWrapper(this) else ShizukuBinderWrapper(this)

    private fun IInterface.wrapped(): IBinder = asBinder().wrap()

    private val remoteInstaller: IPackageInstaller by lazy {
        val packageManager = IPackageManager.Stub.asInterface(
            SystemServiceHelper.getSystemService("package").wrap()
        )
        IPackageInstaller.Stub.asInterface(packageManager.packageInstaller.wrapped())
    }

    /** Identity the session runs as: the shell package for Shizuku, Dhizuku's own for Dhizuku. */
    private val installerPackageName: String
        get() = if (useDhizuku) DhizukuVariables.OFFICIAL_PACKAGE_NAME else SHELL_PACKAGE

    private val userId: Int
        get() = if (useDhizuku) Os.getuid() / PER_USER_RANGE else 0

    private val attributionContext by lazy {
        val target = if (useDhizuku) Dhizuku.getOwnerComponent().packageName else SHELL_PACKAGE
        context.createPackageContext(target, Context.CONTEXT_IGNORE_SECURITY)
    }

    private fun activeInstaller(): PackageInstaller =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Refine.unsafeCast(
                PackageInstallerHidden(
                    remoteInstaller,
                    installerPackageName,
                    attributionContext.attributionTag,
                    userId,
                )
            )
        } else {
            Refine.unsafeCast(
                PackageInstallerHidden(remoteInstaller, installerPackageName, userId)
            )
        }

    private fun sessionParams(): PackageInstaller.SessionParams {
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )
        val hidden = Refine.unsafeCast<PackageInstallerHidden.SessionParamsHidden>(params)
        var flags = hidden.installFlags or
            PackageManagerHidden.INSTALL_REPLACE_EXISTING or
            PackageManagerHidden.INSTALL_ALLOW_TEST
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Lets an app targeting an SDK below the platform minimum still install, which is
            // common for the older sideloaded builds this app exists to manage.
            flags = flags or PackageManagerHidden.INSTALL_BYPASS_LOW_TARGET_SDK_BLOCK
        }
        hidden.installFlags = flags
        return params
    }

    private fun openSession(): PackageInstaller.Session {
        val sessionId = activeInstaller().createSession(sessionParams())
        val remoteSession = IPackageInstallerSession.Stub.asInterface(
            remoteInstaller.openSession(sessionId).wrapped()
        )
        return Refine.unsafeCast(PackageInstallerHidden.SessionHidden(remoteSession))
    }

    private companion object {
        const val TAG = "PrivilegedInstall"
        const val SHELL_PACKAGE = "com.android.shell"
        const val WRITE_NAME = "b_sideloader_privileged_install"
        const val PERMISSION_REQUEST_CODE = 4242
        const val ROOT_UID = 0
        const val PER_USER_RANGE = 100_000
    }
}
