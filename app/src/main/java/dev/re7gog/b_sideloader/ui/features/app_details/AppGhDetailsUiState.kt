package dev.re7gog.b_sideloader.ui.features.app_details

import dev.re7gog.b_sideloader.data.remote.GithubApi
import dev.re7gog.b_sideloader.domain.model.AppWithDetails
import dev.re7gog.b_sideloader.ui.navigation.AppGhDetailsFromSearchRoute

data class AppGhDetailsUiState(
    val name: String,
    val version: String,
    val autoupdate: Boolean,
    val filterInclude: String,
    val filterExclude: String,
    val owner: String,
    val repo: String,
    val usePrereleases: Boolean,
    val releasesInclude: String,
    val releasesExclude: String,
    val description: String?,
    val stars: Int,
    val iconUrl: String,
    val isFromDb: Boolean
)

// From DB to UI
suspend fun AppWithDetails.toGhUiState(githubApi: GithubApi, token: String?): AppGhDetailsUiState {
    val repo = githubApi.getRepository(
        owner = this.githubDetails?.owner ?: "", repo = this.githubDetails?.repo ?: "", token = token)
    return AppGhDetailsUiState(
        name = this.app.name,
        version = this.app.version,
        autoupdate = this.app.autoupdate,
        filterInclude = this.app.filterInclude,
        filterExclude = this.app.filterExclude,
        owner = this.githubDetails!!.owner,
        repo = this.githubDetails.repo,
        usePrereleases = this.githubDetails.usePrereleases,
        releasesInclude = this.githubDetails.releasesInclude,
        releasesExclude = this.githubDetails.releasesExclude,
        description = repo.description,
        stars = repo.stars,
        iconUrl = repo.owner.avatarUrl,
        isFromDb = true
    )
}

// From Search to UI
fun AppGhDetailsFromSearchRoute.toGhUiState(): AppGhDetailsUiState {
    return AppGhDetailsUiState(
        name = this.name,
        version = "",
        autoupdate = true,
        filterInclude = "",
        filterExclude = "",
        owner = this.owner,
        repo = this.repo,
        usePrereleases = false,
        releasesInclude = "",
        releasesExclude = "",
        description = this.description,
        stars = this.stars,
        iconUrl = this.iconUrl ?: "",
        isFromDb = false
    )
}
