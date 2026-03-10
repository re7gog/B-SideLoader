package dev.re7gog.b_sideloader.data.logic

import android.os.Build
import dev.re7gog.b_sideloader.data.remote.dto.GithubReleaseAssetDto

fun findCurrentAbiApk(assets: List<GithubReleaseAssetDto>): String? {
    val deviceAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "" // For example "arm64-v8a"
    val bestMatch = assets.find {
        it.name.contains(deviceAbi, ignoreCase = true) && it.name.endsWith(".apk")
    }
    return (bestMatch ?: assets.find { it.name.endsWith(".apk") })?.browserDownloadUrl
}