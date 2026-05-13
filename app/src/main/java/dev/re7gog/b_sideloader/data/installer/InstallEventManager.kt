package dev.re7gog.b_sideloader.data.installer

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class InstallResult(
    val succeeded: Boolean,
    val errorMessage: String? = null,
    val packageName: String? = null
)

data class UninstallResult(
    val succeeded: Boolean,
    val errorMessage: String? = null
)

object InstallEventManager {
    private val _installEvents = MutableSharedFlow<InstallResult>(extraBufferCapacity = 1)
    val installEvents = _installEvents.asSharedFlow()

    private val _uninstallEvents = MutableSharedFlow<UninstallResult>(extraBufferCapacity = 1)
    val uninstallEvents = _uninstallEvents.asSharedFlow()

    fun emitInstalledPackage(
        succeeded: Boolean, errorMessage: String? = null, packageName: String? = null
    ) {
        _installEvents.tryEmit(InstallResult(succeeded, errorMessage, packageName))
    }

    fun emitUninstalledPackage(
        succeeded: Boolean, errorMessage: String? = null
    ) {
        _uninstallEvents.tryEmit(UninstallResult(succeeded, errorMessage))
    }
}