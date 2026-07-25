package dev.re7gog.b_sideloader.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shapes. These mirror GitHub's JSON exactly and never leave the data layer — the domain sees
 * [dev.re7gog.b_sideloader.domain.model.GithubRepoSummary] and friends, produced by
 * `data/remote/mapper/GithubDtoMappers.kt`.
 *
 * Every optional field has a default so that a response missing it deserializes instead of
 * aborting the whole call; combined with `coerceInputValues` in the `Json` config, an explicit
 * `null` for a non-null field also falls back to the default rather than throwing.
 */

@Serializable
data class GithubSearchResponseDto(
    val items: List<GithubRepoDto> = emptyList(),
)

@Serializable
data class GithubRepoDto(
    val name: String,
    val description: String? = null,
    @SerialName("stargazers_count") val stars: Int = 0,
    val owner: GithubOwnerDto,
)

@Serializable
data class GithubOwnerDto(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class GithubReleaseDto(
    /** GitHub returns null when a release has only a tag and no title. */
    val name: String = "",
    @SerialName("tag_name") val tagName: String = "",
    /** Release notes. */
    val body: String = "",
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<GithubReleaseAssetDto> = emptyList(),
)

@Serializable
data class GithubReleaseAssetDto(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0L,
)
