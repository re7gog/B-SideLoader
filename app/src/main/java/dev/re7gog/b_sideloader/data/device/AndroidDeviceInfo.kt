package dev.re7gog.b_sideloader.data.device

import android.os.Build
import dev.re7gog.b_sideloader.domain.background.DeviceVendor
import dev.re7gog.b_sideloader.domain.device.DeviceInfo
import javax.inject.Inject
import javax.inject.Singleton

/** The one place `android.os.Build` is read. Everything else takes [DeviceInfo]. */
@Singleton
class AndroidDeviceInfo @Inject constructor() : DeviceInfo {

    override val supportedAbis: List<String> = Build.SUPPORTED_ABIS?.toList() ?: emptyList()

    override val sdkInt: Int = Build.VERSION.SDK_INT

    override val manufacturer: String = (Build.MANUFACTURER ?: "").lowercase()

    override val model: String = Build.MODEL ?: ""

    override val hasAggressiveBackgroundLimits: Boolean =
        DeviceVendor.fromManufacturer(manufacturer).restrictsBackgroundAggressively

    override val supportsSilentSelfUpdates: Boolean = sdkInt >= Build.VERSION_CODES.S

    /** Marketing-style device name, e.g. "Xiaomi 13T Pro", used as the TDLib device model. */
    val displayName: String
        get() {
            val vendor = Build.MANUFACTURER ?: ""
            return if (model.startsWith(vendor, ignoreCase = true)) {
                model.replaceFirstChar { it.uppercase() }
            } else {
                "${vendor.replaceFirstChar { it.uppercase() }} $model".trim()
            }
        }
}
