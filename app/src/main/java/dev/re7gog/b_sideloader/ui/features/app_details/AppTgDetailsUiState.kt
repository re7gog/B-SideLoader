package dev.re7gog.b_sideloader.ui.features.app_details

import dev.re7gog.b_sideloader.domain.model.AppWithDetails
import dev.re7gog.b_sideloader.ui.navigation.AppTgDetailsFromSearchRoute

data class AppTgDetailsUiState(
    val id: Long,
    val packageName: String,
    val name: String,
    val installed: Boolean,
    val version: String,
    val autoupdate: Boolean,
    //val filterInclude: String,
    //val filterExclude: String,
    val messageInclude: String,
    val messageExclude: String,
    val chatId: Long,
    val topicId: Int?,
    val isFromDb: Boolean
)

// From DB to UI
fun AppWithDetails.toTgUiState(installed: Boolean): AppTgDetailsUiState {
    return AppTgDetailsUiState(
        id = this.app.id,
        packageName = this.app.packageName,
        name = this.app.name,
        installed = installed,
        version = this.app.version,
        autoupdate = this.app.autoupdate,
        //filterInclude = this.app.filterInclude,
        //filterExclude = this.app.filterExclude,
        messageInclude = this.telegramDetails!!.messageInclude,
        messageExclude = this.telegramDetails.messageExclude,
        chatId = this.telegramDetails.chatId,
        topicId = this.telegramDetails.topicId,
        isFromDb = true
    )
}

// From Search to UI
fun AppTgDetailsFromSearchRoute.toTgUiState(): AppTgDetailsUiState {
    return AppTgDetailsUiState(
        id = 0L,
        packageName = "",
        name = this.name,
        installed = false,
        version = "",
        autoupdate = true,
        //filterInclude = "",
        //filterExclude = "",
        messageInclude = "",
        messageExclude = "",
        chatId = this.chatId,
        topicId = this.topicId,
        isFromDb = false
    )
}
