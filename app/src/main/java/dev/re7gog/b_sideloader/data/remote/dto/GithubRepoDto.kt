package dev.re7gog.b_sideloader.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubSearchResponse(
    val items: List<GithubRepoDto>
)

@Serializable
data class GithubRepoDto(
    val name: String,
    val description: String?,
    @SerialName("stargazers_count") val stars: Int,
    val owner: GithubOwnerDto
)

@Serializable
data class GithubOwnerDto(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String
)

@Serializable
data class GithubReleaseDto(
    // GitHub returns null for these when a release has no title/notes (only a tag). Defaults +
    // coerceInputValues turn that null into "" instead of throwing and aborting the whole check.
    val name: String = "",
    val body: String = "",  // Description
    val prerelease: Boolean = false,
    val assets: List<GithubReleaseAssetDto> = emptyList()
)

@Serializable
data class GithubReleaseAssetDto(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String
)