package dev.re7gog.b_sideloader.data.updater

import android.os.Build
import android.util.Log
import dev.re7gog.b_sideloader.data.encrypt.SecureStorage
import dev.re7gog.b_sideloader.data.filter.NameFilter
import dev.re7gog.b_sideloader.data.installer.InstallManager
import dev.re7gog.b_sideloader.data.remote.GithubApi
import dev.re7gog.b_sideloader.data.telegram.TelegramManager
import dev.re7gog.b_sideloader.data.telegram.TgApkSelector
import dev.re7gog.b_sideloader.domain.model.AppType
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import java.io.File
import javax.inject.Inject

private const val TAG = "UpdatesManager"

data class GhUpdateRes(
    var version: String,
    var downloadUrl: String
)

class UpdatesManager @Inject constructor(
    private val secureStorage: SecureStorage,
    private val githubApi: GithubApi,
    private val appsRepository: AppsRepository,
    private val installManager: InstallManager,
    private val telegramManager: TelegramManager
) {
    suspend fun checkAllUpdates(showUpdateNotification: (String) -> Unit) {
        val apps = appsRepository.getAllAppsStream().firstOrNull() ?: return
        var updates = ""
        for (app in apps) {
            // Isolate each app: a network/API failure for one must not abort the whole sweep.
            try {
                if (app.githubDetails != null) {
                    val ghApp = AppType.GithubApp(app = app.app, details = app.githubDetails)
                    val res = checkGhUpdate(ghApp) ?: continue
                    if (res.version != ghApp.app.version) {
                        updates += " " + ghApp.app.name
                    }
                } else if (app.telegramDetails != null) {
                    val tgApp = AppType.TelegramApp(app = app.app, details = app.telegramDetails)
                    if (doTgUpdate(tgApp, false) {}) updates += " " + tgApp.app.name
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed for ${app.app.name}: ${e.message}")
            }
        }
        if (updates != "") showUpdateNotification(updates)
    }

    suspend fun checkAllAndInstall(updateProgressNotification: suspend (String, Int) -> Unit) {
        val apps = appsRepository.getAllAppsStream().firstOrNull() ?: return
        for (app in apps) {
            // Isolate each app: a failure to check/download/install one must not abort the rest.
            try {
                if (app.githubDetails != null) {
                    val ghApp = AppType.GithubApp(app = app.app, details = app.githubDetails)
                    val res = checkGhUpdate(ghApp) ?: continue
                    if (res.version != ghApp.app.version) {
                        installManager.downloadAndInstall(res.downloadUrl).collect {
                            updateProgressNotification(app.app.name, (it * 100).toInt())
                        }
                        val updatedApp = ghApp.copy(app = ghApp.app.copy(version = res.version))
                        appsRepository.updateApp(updatedApp)
                    }
                } else if (app.telegramDetails != null) {
                    val tgApp = AppType.TelegramApp(app = app.app, details = app.telegramDetails)
                    doTgUpdate(tgApp, true) {
                        updateProgressNotification(app.app.name, it)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Update install failed for ${app.app.name}: ${e.message}")
            }
        }
    }

    suspend fun checkGhUpdate(githubApp: AppType.GithubApp): GhUpdateRes? {
        val advanced = githubApp.app.advancedMode
        val deviceAbi = Build.SUPPORTED_ABIS.firstOrNull()!!

        val releases = githubApi.getReleases(
            owner = githubApp.details.owner, repo = githubApp.details.repo,
            token = secureStorage.getGithubToken()
        )

        for (release in releases) {
            val matchesRelease = NameFilter.matches(
                release.name, githubApp.details.releasesInclude, githubApp.details.releasesExclude, advanced)
            val allowedPre = !release.prerelease || githubApp.details.usePrereleases
            if (!matchesRelease || !allowedPre) continue

            val assets = release.assets.filter { asset ->
                asset.name.endsWith(".apk", ignoreCase = true) && NameFilter.matches(
                    asset.name, githubApp.app.filterInclude, githubApp.app.filterExclude, advanced)
            }
            if (assets.isEmpty()) continue
            val bestMatch = assets.find { it.name.contains(deviceAbi, ignoreCase = true) }
            val downloadUrl = bestMatch?.browserDownloadUrl ?: assets[0].browserDownloadUrl
            return GhUpdateRes(release.name, downloadUrl)
        }
        return null
    }

    suspend fun doTgUpdate(telegramApp: AppType.TelegramApp, install: Boolean, notification: suspend (Int) -> Unit): Boolean {
        val messages = telegramManager.searchApkMessages(
            telegramApp.details.chatId, telegramApp.details.topicId
        )?.messages?.toList() ?: return false
        // Same selection the details screen uses: file-name filters + message-text filters +
        // album grouping, then the device-architecture-preferred target.
        val candidates = TgApkSelector.filter(
            messages = messages,
            filterInclude = telegramApp.app.filterInclude,
            filterExclude = telegramApp.app.filterExclude,
            messageInclude = telegramApp.details.messageInclude,
            messageExclude = telegramApp.details.messageExclude,
            advanced = telegramApp.app.advancedMode
        )
        val target = TgApkSelector.pickTarget(candidates, Build.SUPPORTED_ABIS.toList()) ?: return false
        val messageId = target.id.toString()
        if (messageId == telegramApp.app.version) return false
        if (!install) return true
        val resDoc = target.file
        val fileId = resDoc.document.id
        var localFile: File? = null
        try {
            val localPath = telegramManager.downloadFile(fileId)
            localFile = File(localPath)

            installManager.installFromFile(localFile, resDoc.document.size).collect {
                notification((it * 100).toInt())
            }

            val updatedApp = AppType.TelegramApp(
                telegramApp.app.copy(version = messageId), telegramApp.details)
            appsRepository.updateApp(updatedApp)
        } catch (e: Exception) {
            Log.e("Install", "Installation error: ${e.message}")
        } finally {
            telegramManager.deleteFile(fileId)
            if (localFile != null && localFile.exists()) {
                val deleted = localFile.delete()
                Log.d("Cleanup", "Physical file removed: $deleted")
            }
        }
        return false
    }
}