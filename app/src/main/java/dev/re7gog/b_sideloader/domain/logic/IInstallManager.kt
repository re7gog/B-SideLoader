package dev.re7gog.b_sideloader.domain.logic

import kotlinx.coroutines.flow.Flow

interface IInstallManager {
    suspend fun downloadAndInstall(url: String): Flow<Int>
}