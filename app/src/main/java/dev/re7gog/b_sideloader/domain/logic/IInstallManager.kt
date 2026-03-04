package dev.re7gog.b_sideloader.domain.logic

interface IInstallManager {
    suspend fun downloadAndInstall(url: String, onProgress: (Float) -> Unit)
}