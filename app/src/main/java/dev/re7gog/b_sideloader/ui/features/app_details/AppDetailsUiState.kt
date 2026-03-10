package dev.re7gog.b_sideloader.ui.features.app_details

import dev.re7gog.b_sideloader.data.remote.GithubApi
import dev.re7gog.b_sideloader.domain.model.AppWithDetails
import dev.re7gog.b_sideloader.ui.navigation.AppDetailsFromSearchRoute

data class AppDetailsUiState(
    val name: String,
    val description: String?,
    val fullName: String,
    val stars: Int,
    val owner: String,
    val iconUrl: String,
    val installProgress: Int? = null,
    val isInstalling: Boolean = false
)

// From DB to UI
suspend fun AppWithDetails.toUiState(githubApi: GithubApi): AppDetailsUiState {
    // Temp ugly solution
    val ownerRepo = this.githubDetails?.fullName?.split("/")
    val repo = githubApi.getRepository(owner = ownerRepo?.get(0) ?: "", repo = ownerRepo?.get(1) ?: "")
    return AppDetailsUiState(
        name = this.app.name,
        description = repo.description,
        fullName = this.githubDetails?.fullName ?: "",
        stars = repo.stars,
        owner = repo.owner.login,
        iconUrl = repo.owner.avatarUrl
    )
}

// From Search to UI
fun AppDetailsFromSearchRoute.toUiState(): AppDetailsUiState {
    return AppDetailsUiState(
        name = this.name,
        description = this.description,
        fullName = this.fullName,
        stars = this.stars,
        owner = this.owner,
        iconUrl = this.iconUrl ?: ""
    )
}