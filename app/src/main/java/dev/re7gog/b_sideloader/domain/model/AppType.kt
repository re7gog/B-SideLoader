package dev.re7gog.b_sideloader.domain.model

import dev.re7gog.b_sideloader.data.local.entities.AppEntity
import dev.re7gog.b_sideloader.data.local.entities.GithubDetailsEntity

sealed class AppType {
    data class GithubApp(val app: AppEntity, val details: GithubDetailsEntity) : AppType()
}