package dev.re7gog.b_sideloader.domain.model

import dev.re7gog.b_sideloader.data.local.entities.*

sealed class AppType {
    data class GithubApp(val app: AppEntity, val details: GithubDetailsEntity) : AppType()
    data class TelegramApp(val app: AppEntity, val details: TelegramDetailsEntity) : AppType()
}