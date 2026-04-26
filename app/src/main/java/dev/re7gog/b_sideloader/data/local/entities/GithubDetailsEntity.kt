package dev.re7gog.b_sideloader.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "github_details",
    foreignKeys = [
        ForeignKey(
            entity = AppEntity::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("id")]
)
data class GithubDetailsEntity(
    @PrimaryKey val id: Long,
    val owner: String,
    val repo: String,
    val usePrereleases: Boolean,
    val releasesInclude: String,
    val releasesExclude: String
)
