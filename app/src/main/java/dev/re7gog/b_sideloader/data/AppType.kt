package dev.re7gog.b_sideloader.data

import dev.re7gog.b_sideloader.data.entities.AppEntity
import dev.re7gog.b_sideloader.data.entities.GithubDetailsEntity

sealed class AppType {
    data class GithubApp(val app: AppEntity, val details: GithubDetailsEntity) : AppType()
}