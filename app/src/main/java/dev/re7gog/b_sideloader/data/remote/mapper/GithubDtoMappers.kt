package dev.re7gog.b_sideloader.data.remote.mapper

import dev.re7gog.b_sideloader.data.remote.dto.GithubReleaseAssetDto
import dev.re7gog.b_sideloader.data.remote.dto.GithubReleaseDto
import dev.re7gog.b_sideloader.data.remote.dto.GithubRepoDto
import dev.re7gog.b_sideloader.domain.model.GithubAsset
import dev.re7gog.b_sideloader.domain.model.GithubRelease
import dev.re7gog.b_sideloader.domain.model.GithubRepoSummary

/** Wire DTOs -> domain models. The only place GitHub's field names are known. */

fun GithubRepoDto.toDomain(): GithubRepoSummary = GithubRepoSummary(
    owner = owner.login,
    name = name,
    description = description?.takeIf { it.isNotBlank() },
    stars = stars,
    avatarUrl = owner.avatarUrl?.takeIf { it.isNotBlank() },
)

/**
 * Drafts are dropped: their assets are not publicly downloadable, so offering one would resolve to
 * a URL that 404s for the user.
 *
 * A release with no title falls back to its tag, because the title is what gets stored as the
 * app's version marker and an empty marker would make every check look like a fresh install.
 */
fun GithubReleaseDto.toDomainOrNull(): GithubRelease? {
    if (draft) return null
    val label = name.ifBlank { tagName }
    if (label.isBlank()) return null
    return GithubRelease(
        name = label,
        notes = body,
        isPrerelease = prerelease,
        assets = assets.map { it.toDomain() },
    )
}

fun List<GithubReleaseDto>.toDomain(): List<GithubRelease> = mapNotNull { it.toDomainOrNull() }

fun GithubReleaseAssetDto.toDomain(): GithubAsset = GithubAsset(
    name = name,
    downloadUrl = browserDownloadUrl,
    sizeBytes = size,
)
