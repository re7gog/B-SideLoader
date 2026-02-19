package dev.re7gog.b_sideloader.domain.model

import androidx.room.Embedded
import androidx.room.Relation
import dev.re7gog.b_sideloader.data.local.entities.AppEntity
import dev.re7gog.b_sideloader.data.local.entities.GithubDetailsEntity

data class AppWithDetails(
    @Embedded val app: AppEntity,

    @Relation(parentColumn = "id", entityColumn = "id")
    val githubDetails: GithubDetailsEntity?
)