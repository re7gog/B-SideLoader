package dev.re7gog.b_sideloader.data.mapper

import dev.re7gog.b_sideloader.data.local.entity.AppEntity
import dev.re7gog.b_sideloader.data.local.entity.AppWithDetails
import dev.re7gog.b_sideloader.data.local.entity.GithubDetailsEntity
import dev.re7gog.b_sideloader.data.local.entity.TelegramDetailsEntity
import dev.re7gog.b_sideloader.domain.model.AppSource
import dev.re7gog.b_sideloader.domain.model.AppSourceKind
import dev.re7gog.b_sideloader.domain.model.AppVersion
import dev.re7gog.b_sideloader.domain.model.FilterMode
import dev.re7gog.b_sideloader.domain.model.FilterRule
import dev.re7gog.b_sideloader.domain.model.TrackedApp

/**
 * Room entities <-> domain models.
 *
 * The only file that knows both shapes. Every conversion is total in one direction (domain ->
 * rows always succeeds) and partial in the other ([toDomainOrNull] returns null for a row whose
 * details table is missing), because a database can hold data the domain considers impossible and
 * silently dropping one bad row beats crashing the whole list.
 */

/** Row -> domain, or `null` when the row has no matching details record. */
fun AppWithDetails.toDomainOrNull(): TrackedApp? {
    val source = resolveSource() ?: return null
    return TrackedApp(
        id = app.id,
        packageName = app.packageName,
        name = app.name,
        version = AppVersion(app.version),
        autoUpdate = app.autoupdate,
        assetFilter = FilterRule(include = app.filterInclude, exclude = app.filterExclude),
        filterMode = FilterMode.of(app.advancedMode),
        source = source,
    )
}

/** Convenience for list queries: maps and drops rows that cannot be represented. */
fun List<AppWithDetails>.toDomain(): List<TrackedApp> = mapNotNull { it.toDomainOrNull() }

private fun AppWithDetails.resolveSource(): AppSource? {
    // Trust the joined table over `sourceType`: the column is only a hint, and a row whose details
    // are missing is unusable regardless of what the discriminator claims.
    githubDetails?.let { details ->
        return AppSource.GitHub(
            owner = details.owner,
            repo = details.repo,
            usePrereleases = details.usePrereleases,
            releaseFilter = FilterRule(
                include = details.releasesInclude,
                exclude = details.releasesExclude,
            ),
        )
    }
    telegramDetails?.let { details ->
        return AppSource.Telegram(
            chatId = details.chatId,
            topicId = details.topicId,
            messageFilter = FilterRule(
                include = details.messageInclude,
                exclude = details.messageExclude,
            ),
        )
    }
    return null
}

/** Domain -> the `apps` row. */
fun TrackedApp.toEntity(): AppEntity = AppEntity(
    id = id,
    sourceType = source.kind.storedValue,
    packageName = packageName,
    name = name,
    version = version.raw,
    autoupdate = autoUpdate,
    filterInclude = assetFilter.include,
    filterExclude = assetFilter.exclude,
    advancedMode = filterMode.isAdvanced,
)

/** Domain -> the GitHub details row, for the given (possibly freshly assigned) [id]. */
fun AppSource.GitHub.toEntity(id: Long): GithubDetailsEntity = GithubDetailsEntity(
    id = id,
    owner = owner,
    repo = repo,
    usePrereleases = usePrereleases,
    releasesInclude = releaseFilter.include,
    releasesExclude = releaseFilter.exclude,
)

/** Domain -> the Telegram details row. */
fun AppSource.Telegram.toEntity(id: Long): TelegramDetailsEntity = TelegramDetailsEntity(
    id = id,
    chatId = chatId,
    topicId = topicId,
    messageInclude = messageFilter.include,
    messageExclude = messageFilter.exclude,
)

/** Kept for readability at call sites that only have the discriminator. */
fun Int.toAppSourceKind(): AppSourceKind? = AppSourceKind.fromStoredValue(this)
