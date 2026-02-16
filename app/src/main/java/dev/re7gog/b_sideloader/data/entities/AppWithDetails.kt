package dev.re7gog.b_sideloader.data.entities

import androidx.room.Embedded
import androidx.room.Relation

data class AppWithDetails(
    @Embedded val app: AppEntity,

    @Relation(parentColumn = "id", entityColumn = "id")
    val githubDetails: GithubDetailsEntity?
)