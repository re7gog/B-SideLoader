package dev.re7gog.b_sideloader.testing

import dev.re7gog.b_sideloader.domain.model.AppSource
import dev.re7gog.b_sideloader.domain.model.AppVersion
import dev.re7gog.b_sideloader.domain.model.FilterMode
import dev.re7gog.b_sideloader.domain.model.FilterRule
import dev.re7gog.b_sideloader.domain.model.GithubAsset
import dev.re7gog.b_sideloader.domain.model.GithubRelease
import dev.re7gog.b_sideloader.domain.model.SelfApp
import dev.re7gog.b_sideloader.domain.model.TelegramApkDocument
import dev.re7gog.b_sideloader.domain.model.TrackedApp

/**
 * Builders with sane defaults, so a test only states the field it is actually about.
 *
 * Keeps the tests readable and, more importantly, keeps them from breaking every time a model
 * gains a field.
 */

fun githubApp(
    id: Long = 1L,
    name: String = "Example",
    packageName: String = "com.example",
    version: AppVersion = AppVersion.Unknown,
    assetInclude: String = "",
    assetExclude: String = "",
    releaseInclude: String = "",
    releaseExclude: String = "",
    usePrereleases: Boolean = false,
    filterMode: FilterMode = FilterMode.Words,
    autoUpdate: Boolean = true,
): TrackedApp = TrackedApp(
    id = id,
    packageName = packageName,
    name = name,
    version = version,
    autoUpdate = autoUpdate,
    assetFilter = FilterRule(assetInclude, assetExclude),
    filterMode = filterMode,
    source = AppSource.GitHub(
        owner = "octocat",
        repo = "example",
        usePrereleases = usePrereleases,
        releaseFilter = FilterRule(releaseInclude, releaseExclude),
    ),
)

/** B-SideLoader's own row: the app that, when installed, replaces the process installing it. */
fun selfApp(
    id: Long = 1L,
    version: AppVersion = AppVersion("1.0.0"),
    autoUpdate: Boolean = true,
): TrackedApp = TrackedApp(
    id = id,
    packageName = FakeSelfAppInfo.SELF_PACKAGE,
    name = SelfApp.NAME,
    version = version,
    autoUpdate = autoUpdate,
    assetFilter = FilterRule.None,
    filterMode = FilterMode.Words,
    source = SelfApp.source,
)

fun telegramApp(
    id: Long = 1L,
    name: String = "Example channel",
    packageName: String = "com.example",
    version: AppVersion = AppVersion.Unknown,
    assetInclude: String = "",
    assetExclude: String = "",
    messageInclude: String = "",
    messageExclude: String = "",
    filterMode: FilterMode = FilterMode.Words,
    autoUpdate: Boolean = true,
    chatId: Long = -100L,
    topicId: Int = 0,
): TrackedApp = TrackedApp(
    id = id,
    packageName = packageName,
    name = name,
    version = version,
    autoUpdate = autoUpdate,
    assetFilter = FilterRule(assetInclude, assetExclude),
    filterMode = filterMode,
    source = AppSource.Telegram(
        chatId = chatId,
        topicId = topicId,
        messageFilter = FilterRule(messageInclude, messageExclude),
    ),
)

fun release(
    name: String,
    prerelease: Boolean = false,
    vararg assets: GithubAsset,
): GithubRelease = GithubRelease(
    name = name,
    notes = "",
    isPrerelease = prerelease,
    assets = assets.toList(),
)

fun asset(name: String, size: Long = 1_000L): GithubAsset =
    GithubAsset(name = name, downloadUrl = "https://example.test/$name", sizeBytes = size)

fun tgDocument(
    messageId: Long,
    fileName: String,
    caption: String = "",
    albumId: Long = TelegramApkDocument.NO_ALBUM,
    fileId: Int = messageId.toInt(),
    sizeBytes: Long = 1_000L,
): TelegramApkDocument = TelegramApkDocument(
    messageId = messageId,
    fileId = fileId,
    fileName = fileName,
    sizeBytes = sizeBytes,
    caption = caption,
    albumId = albumId,
)

/** A typical 64-bit phone. */
val ARM64_ABIS = listOf("arm64-v8a", "armeabi-v7a", "armeabi")

/** An older 32-bit-only phone. */
val ARM32_ABIS = listOf("armeabi-v7a", "armeabi")
