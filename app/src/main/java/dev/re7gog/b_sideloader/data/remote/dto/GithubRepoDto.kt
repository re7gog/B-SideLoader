package dev.re7gog.b_sideloader.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubSearchResponse(
    val items: List<GithubRepoDto>
)

@Serializable
data class GithubRepoDto(
    val id: Long,
    @SerialName("full_name") val fullName: String,
    @SerialName("description") val description: String?,
    @SerialName("html_url") val htmlUrl: String,
    val owner: GithubOwnerDto
)

@Serializable
data class GithubOwnerDto(
    @SerialName("avatar_url") val avatarUrl: String
)