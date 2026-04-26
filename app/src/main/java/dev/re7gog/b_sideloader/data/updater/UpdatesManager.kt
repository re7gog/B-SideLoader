package dev.re7gog.b_sideloader.data.updater

import android.os.Build
import android.util.Log
import dev.re7gog.b_sideloader.data.encrypt.SecureStorage
import dev.re7gog.b_sideloader.data.remote.GithubApi
import dev.re7gog.b_sideloader.data.telegram.TelegramManager
import dev.re7gog.b_sideloader.domain.logic.IInstallManager
import dev.re7gog.b_sideloader.domain.model.AppType
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import kotlinx.coroutines.flow.firstOrNull
import org.drinkless.tdlib.TdApi
import java.io.File
import javax.inject.Inject

data class GhUpdateRes(
    var version: String,
    var downloadUrl: String
)

class UpdatesManager @Inject constructor(
    private val secureStorage: SecureStorage,
    private val githubApi: GithubApi,
    private val appsRepository: AppsRepository,
    private val installManager: IInstallManager,
    private val telegramManager: TelegramManager
) {
    suspend fun checkAllUpdates(install: Boolean, showUpdateNotification: (String) -> Unit) {
        val apps = appsRepository.getAllAppsStream().firstOrNull() ?: return
        var updates = ""
        for (app in apps) {
            if (app.githubDetails != null) {
                val ghApp = AppType.GithubApp(app = app.app, details = app.githubDetails)
                val res = checkGhUpdate(ghApp) ?: continue
                if (res.version != ghApp.app.version) {
                    if (!install) {
                        updates += " " + ghApp.app.name
                    } else {
                        installManager.downloadAndInstall(res.downloadUrl)
                        val updatedApp = ghApp.copy(app = ghApp.app.copy(version = res.version))
                        appsRepository.updateApp(updatedApp)
                    }
                }
            } else if (app.telegramDetails != null) {
                val tgApp = AppType.TelegramApp(app = app.app, details = app.telegramDetails)
                if (doTgUpdate(tgApp, install)) updates += " " + tgApp.app.name
            }
        }
        if (!install && updates != "") showUpdateNotification(updates)
    }

    suspend fun checkGhUpdate(githubApp: AppType.GithubApp): GhUpdateRes? {
        val incWords = githubApp.app.filterInclude.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        val excWords = githubApp.app.filterExclude.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        val incAssWords = githubApp.details.releasesInclude.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        val excAssWords = githubApp.details.releasesExclude.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }

        val deviceAbi = Build.SUPPORTED_ABIS.firstOrNull()!!

        val releases = githubApi.getReleases(
            owner = githubApp.details.owner, repo = githubApp.details.repo,
            token = secureStorage.getGithubToken()
        )

        for (release in releases) {
            val matchesInc = incWords.all { release.name.contains(it.lowercase()) }
            val matchesExc = excWords.none { release.name.contains(it.lowercase()) }
            val usesPre = release.prerelease == githubApp.details.usePrereleases
            if (!matchesInc || !matchesExc || !usesPre) continue

            val assets = release.assets.filter { asset ->
                val matchesInc = incAssWords.all { asset.name.contains(it.lowercase()) }
                val matchesExc = excAssWords.none { asset.name.contains(it.lowercase()) }
                val isApk = asset.name.endsWith(".apk")
                matchesInc && matchesExc && isApk
            }
            if (assets.isEmpty()) continue
            val bestMatch = assets.find { it.name.contains(deviceAbi, ignoreCase = true) }
            val downloadUrl = bestMatch?.browserDownloadUrl ?: assets[0].browserDownloadUrl
            return GhUpdateRes(release.name, downloadUrl)
        }
        return null
    }

    suspend fun doTgUpdate(telegramApp: AppType.TelegramApp, install: Boolean): Boolean {
        val messages = telegramManager.searchApkMessages(
            telegramApp.details.chatId, telegramApp.details.topicId
        )?.messages?.toList() ?: return false
        val incWords = telegramApp.app.filterInclude.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        val excWords = telegramApp.app.filterExclude.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        var resDoc: TdApi.Document? = null
        var messageId = ""
        for (message in messages) {
            val doc = (message.content as? TdApi.MessageDocument)?.document ?: continue
            val fileName = doc.fileName.lowercase()
            val isApk = fileName.endsWith(".apk")
            if (!isApk) continue

            val matchesInc = incWords.all { fileName.contains(it.lowercase()) }
            val matchesExc = excWords.none { fileName.contains(it.lowercase()) }
            if (matchesInc && matchesExc) {
                messageId = message.id.toString()
                if (messageId == telegramApp.app.version) return false
                resDoc = doc
                break
            }
        }
        if (resDoc == null) return false
        if (!install) return true
        val fileId = resDoc.document.id
        var localFile: File? = null
        try {
            val localPath = telegramManager.downloadFile(fileId)
            localFile = File(localPath)

            installManager.installFromFile(localFile, resDoc.document.size)

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