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
    @SerialName("full_name") val fullName: String,
    @SerialName("stargazers_count") val stars: Int,
    val owner: GithubOwnerDto
)

@Serializable
data class GithubOwnerDto(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String
)