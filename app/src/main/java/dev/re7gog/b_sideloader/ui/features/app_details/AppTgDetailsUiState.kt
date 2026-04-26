package dev.re7gog.b_sideloader.ui.features.app_details

import dev.re7gog.b_sideloader.domain.model.AppWithDetails
import dev.re7gog.b_sideloader.ui.navigation.AppTgDetailsFromSearchRoute

data class AppTgDetailsUiState(
    val name: String,
    val version: String,
    val autoupdate: Boolean,
    //val filterInclude: String,
    //val filterExclude: String,
    val chatId: Long,
    val topicId: Int?,
    val isFromDb: Boolean
)

// From DB to UI
fun AppWithDetails.toTgUiState(): AppTgDetailsUiState {
    return AppTgDetailsUiState(
        name = this.app.name,
        version = this.app.version,
        autoupdate = this.app.autoupdate,
        //filterInclude = this.app.filterInclude,
        //filterExclude = this.app.filterExclude,
        chatId = this.telegramDetails!!.chatId,
        topicId = this.telegramDetails.topicId,
        isFromDb = true
    )
}

// From Search to UI
fun AppTgDetailsFromSearchRoute.toTgUiState(): AppTgDetailsUiState {
    return AppTgDetailsUiState(
        name = this.name,
        version = "",
        autoupdate = true,
        //filterInclude = "",
        //filterExclude = "",
        chatId = this.chatId,
        topicId = this.topicId,
        isFromDb = false
    )
}
