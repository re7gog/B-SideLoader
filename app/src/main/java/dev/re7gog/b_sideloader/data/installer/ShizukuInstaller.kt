package dev.re7gog.b_sideloader.data.installer

import android.annotation.SuppressLint
import android.content.Context
import android.content.IIntentReceiver
import android.content.IIntentSender
import android.content.Intent
import android.content.IntentSender
import android.content.pm.IPackageInstaller
import android.content.pm.IPackageInstallerSession
import android.content.pm.IPackageManager
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstallerHidden
import android.content.pm.PackageManager
import android.content.pm.PackageManagerHidden
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.IInterface
import android.os.RemoteException
import android.system.Os
import android.util.Log
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener
import com.rosan.dhizuku.shared.DhizukuVariables
import dev.rikka.tools.refine.Refine
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.resume

enum class ShizukuPermission {
    GRANTED_ADB,
    GRANTED_OWNER,
    GRANTED_ROOT,
    DENIED,
    SERVICES_NOT_FOUND,
    OLD_SHIZUKU,
    OLD_ANDROID_WITH_ADB
}

const val SHELL_PACKAGE = "com.android.shell"

class ShizukuInstaller(private val context: Context) : ApkInstaller {
    private var isBinderAvailable = false
    private val requestPermissionCode by lazy { (1000..2000).random() }
    private val requestPermissionMutex by lazy { Mutex(locked = true) }
    private var permissionGranted = false
    private var isRoot = false

    private var dInitSucceeded = false
    private val dRequestPermissionMutex by lazy { Mutex(locked = true) }
    private var dPermissionGranted = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { isBinderAvailable = true }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { isBinderAvailable = false }
    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode: Int, grantResult: Int ->
            if (requestCode == requestPermissionCode) {
                permissionGranted = grantResult == PackageManager.PERMISSION_GRANTED
                requestPermissionMutex.unlock()
            }
        }

    private val contextS by lazy {
        context.createPackageContext(SHELL_PACKAGE, Context.CONTEXT_IGNORE_SECURITY)
    }

    private val contextD by lazy {
        context.createPackageContext(
            Dhizuku.getOwnerComponent().packageName, Context.CONTEXT_IGNORE_SECURITY
        )
    }

    fun init() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            HiddenApiBypass.addHiddenApiExemptions("Landroid/content", "Landroid/os")
        dInitSucceeded = Dhizuku.init(context)
        if (!dInitSucceeded) {
            /*
            val isSui = Sui.init(context.packageName)
            if (!isSui) {
                ShizukuProvider.enableMultiProcessSupport(false)
                ShizukuProvider.requestBinderForNonProviderProcess(context)
            }
            */
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        }
    }

    fun exit() {
        if (dInitSucceeded) return
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
    }

    private suspend fun checkShizukuPermission(): ShizukuPermission {
        return if (Shizuku.isPreV11()) {
            ShizukuPermission.OLD_SHIZUKU
        } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            if (!registerUidObserverPermissionLimitedCheck()) {
                if (isRoot) ShizukuPermission.GRANTED_ROOT else ShizukuPermission.GRANTED_ADB
            } else ShizukuPermission.OLD_ANDROID_WITH_ADB
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {  // "Deny and don't ask again"
            ShizukuPermission.DENIED
        } else {
            Shizuku.requestPermission(requestPermissionCode)
            requestPermissionMutex.lock()
            if (!registerUidObserverPermissionLimitedCheck()) {
                if (permissionGranted) {
                    if (isRoot) ShizukuPermission.GRANTED_ROOT else ShizukuPermission.GRANTED_ADB
                } else ShizukuPermission.DENIED
            } else ShizukuPermission.OLD_ANDROID_WITH_ADB
        }
    }

    suspend fun checkPermission(): ShizukuPermission {
        return if (!isBinderAvailable && !dInitSucceeded) {
            ShizukuPermission.SERVICES_NOT_FOUND
        } else if (dInitSucceeded) {
            if (Dhizuku.isPermissionGranted())
                ShizukuPermission.GRANTED_OWNER
            else {
                Dhizuku.requestPermission(object : DhizukuRequestPermissionListener() {
                    @Throws(RemoteException::class)
                    override fun onRequestPermission(grantResult: Int) {
                        dPermissionGranted = grantResult == PackageManager.PERMISSION_GRANTED
                        dRequestPermissionMutex.unlock()
                    }
                })
                dRequestPermissionMutex.lock()
                if (dPermissionGranted) ShizukuPermission.GRANTED_OWNER
                else if (isBinderAvailable) checkShizukuPermission()
                else ShizukuPermission.DENIED
            }
        } else checkShizukuPermission()
    }

    /**
     * Android 8.0 with ADB lacks IActivityManager#registerUidObserver permission,
     * so we can't install apps without activity
     */
    private fun registerUidObserverPermissionLimitedCheck(): Boolean {
        isRoot = Shizuku.getUid() == 0
        return !isRoot and (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1)
    }

    private fun IBinder.wrap() = ShizukuBinderWrapper(this)
    private fun IInterface.asShizukuBinder() = this.asBinder().wrap()

    private fun IBinder.dwrap() = Dhizuku.binderWrapper(this)
    private fun IInterface.asDhizukuBinder() = this.asBinder().dwrap()

    private val iPackageInstaller: IPackageInstaller by lazy {
        val iPackageManager = IPackageManager.Stub.asInterface(
            SystemServiceHelper.getSystemService("package").wrap())
        IPackageInstaller.Stub.asInterface(iPackageManager.packageInstaller.asShizukuBinder())
    }

    private val iPackageInstallerD: IPackageInstaller by lazy {
        val iPackageManager = IPackageManager.Stub.asInterface(
            SystemServiceHelper.getSystemService("package").dwrap())
        IPackageInstaller.Stub.asInterface(iPackageManager.packageInstaller.asDhizukuBinder())
    }

    private val packageInstaller: PackageInstaller by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Refine.unsafeCast(
                PackageInstallerHidden(
                    iPackageInstaller, SHELL_PACKAGE, contextS.attributionTag, 0
                )
            )
        } else {
            Refine.unsafeCast(
                PackageInstallerHidden(iPackageInstaller, SHELL_PACKAGE, 0)
            )
        }
        /*
        DEPRECATED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        ...
        else {
            Refine.unsafeCast(PackageInstallerHidden(
                appContext, appContext.packageManager, iPackageInstaller, installerPackageName, userId))
        }
        */
    }

    private val packageInstallerD: PackageInstaller by lazy {
        val installerPackageName = DhizukuVariables.OFFICIAL_PACKAGE_NAME
        val userId = Os.getuid() / 100000
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Refine.unsafeCast(PackageInstallerHidden(
                iPackageInstallerD, installerPackageName, contextD.attributionTag, userId))
        } else {
            Refine.unsafeCast(
                PackageInstallerHidden(iPackageInstallerD, installerPackageName, userId))
        }
    }

    private val sessionParams: PackageInstaller.SessionParams by lazy {
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        var flags = Refine.unsafeCast<PackageInstallerHidden.SessionParamsHidden>(params).installFlags

        flags = flags or PackageManagerHidden.INSTALL_REPLACE_EXISTING or PackageManagerHidden.INSTALL_ALLOW_TEST
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            flags = flags or PackageManagerHidden.INSTALL_BYPASS_LOW_TARGET_SDK_BLOCK

        Refine.unsafeCast<PackageInstallerHidden.SessionParamsHidden>(params).installFlags = flags
        params
    }

    private fun createPackageInstallerSession(): PackageInstaller.Session {
        val sessionId = packageInstaller.createSession(sessionParams)
        val iSession = IPackageInstallerSession.Stub.asInterface(
            iPackageInstaller.openSession(sessionId).asShizukuBinder())
        return Refine.unsafeCast(PackageInstallerHidden.SessionHidden(iSession))
    }

    private fun createPackageInstallerSessionD(): PackageInstaller.Session {
        val sessionId = packageInstallerD.createSession(sessionParams)
        val iSession = IPackageInstallerSession.Stub.asInterface(
            iPackageInstallerD.openSession(sessionId).asDhizukuBinder())
        return Refine.unsafeCast(PackageInstallerHidden.SessionHidden(iSession))
    }

    private fun createPackageInstallerSessionUniversal(): PackageInstaller.Session {
        return if (dInitSucceeded) createPackageInstallerSessionD()
        else createPackageInstallerSession()
    }

    override suspend fun installApk(
        stream: InputStream, lengthBytes: Long, progressCollector: FlowCollector<Float>
    ) {
        if (lengthBytes == 0L) throw Exception("Download size is zero")
        runCatching {
            createPackageInstallerSessionUniversal().use { session ->
                session.openWrite(
                    "privileged_b_side_install", 0, lengthBytes
                ).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead = 0
                    var totalBytesRead = 0L

                    stream.use { input ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            progressCollector.emit(totalBytesRead.toFloat() / lengthBytes)
                        }
                    }
                }
                var result: Intent? = null
                suspendCancellableCoroutine { cont ->
                    val adapter = IntentSenderHelper.IIntentSenderAdaptor { intent ->
                        result = intent
                        cont.resume(null)
                    }
                    val intentSender = IntentSenderHelper.newIntentSender(adapter)
                    @SuppressLint("RequestInstallPackagesPolicy")
                    session.commit(intentSender)
                }
                result?.let {
                    val status = it.getIntExtra(
                        PackageInstaller.EXTRA_STATUS,
                        PackageInstaller.STATUS_FAILURE
                    )
                    when (status) {
                        PackageInstaller.STATUS_SUCCESS -> {
                            Log.i("B-SideLoader", "Privileged install successful")

                            val packageName = it.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
                            InstallEventManager.emitInstalledPackage(succeeded = true, packageName = packageName)
                        }
                        PackageInstaller.STATUS_FAILURE,
                        PackageInstaller.STATUS_FAILURE_ABORTED,
                        PackageInstaller.STATUS_FAILURE_STORAGE -> {
                            val message = it.getStringExtra(
                                PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "No message"
                            Log.e("B-SideLoader",
                                "Privileged install failed ($status): $message")
                            InstallEventManager.emitInstalledPackage(succeeded = false, errorMessage = message)
                        }
                    }
                } ?: throw IOException("Intent is null")
            }
        }.onFailure {
            val message = it.message + "\n" + it.stackTraceToString()
            Log.e("B-SideLoader", "Installing error: $message")
            InstallEventManager.emitInstalledPackage(succeeded = false, errorMessage = message)
        }
    }

    override suspend fun uninstallPackage(packageName: String) {
        runCatching {
            var result: Intent? = null
            suspendCancellableCoroutine { cont ->
                val adapter = IntentSenderHelper.IIntentSenderAdaptor { intent ->
                    result = intent
                    cont.resume(Unit)
                }
                val intentSender = IntentSenderHelper.newIntentSender(adapter)
                if (dInitSucceeded)
                    packageInstallerD.uninstall(packageName, intentSender)
                else
                    packageInstaller.uninstall(packageName, intentSender)
            }
            result?.let {
                val status = it.getIntExtra(
                    PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE
                )
                when (status) {
                    PackageInstaller.STATUS_SUCCESS -> {
                        Log.i("B-SideLoader", "Privileged uninstall successful")
                        InstallEventManager.emitUninstalledPackage(succeeded = true)
                    }
                    PackageInstaller.STATUS_FAILURE,
                    PackageInstaller.STATUS_FAILURE_ABORTED -> {
                        val message = it.getStringExtra(
                            PackageInstaller.EXTRA_STATUS_MESSAGE)
                        Log.e("B-SideLoader",
                            "Privileged uninstall failed ($status): ${message ?: "no message"}")
                        InstallEventManager.emitUninstalledPackage(succeeded = false, errorMessage = message)
                    }
                }
            } ?: throw IOException("Intent is null")
        }.onFailure {
            val message = it.message + "\n" + it.stackTraceToString()
            Log.e("B-SideLoader", "Uninstalling error: $message")
            InstallEventManager.emitUninstalledPackage(succeeded = false, errorMessage = message)
        }
    }
}

object IntentSenderHelper {
    fun newIntentSender(binder: IIntentSender): IntentSender {
        return IntentSender::class.java.getConstructor(IIntentSender::class.java).newInstance(binder)
    }

    class IIntentSenderAdaptor(private val listener: (Intent) -> Unit) : IIntentSender.Stub() {
        override fun send(
            code: Int,
            intent: Intent,
            resolvedType: String?,
            finishedReceiver: IIntentReceiver?,
            requiredPermission: String?,
            options: Bundle?
        ): Int {
            listener(intent)
            return 0
        }

        override fun send(
            code: Int,
            intent: Intent,
            resolvedType: String?,
            whitelistToken: IBinder?,
            finishedReceiver: IIntentReceiver?,
            requiredPermission: String?,
            options: Bundle?
        ) {
            listener(intent)
        }
    }
}