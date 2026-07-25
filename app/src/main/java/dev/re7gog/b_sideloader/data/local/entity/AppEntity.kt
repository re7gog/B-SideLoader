package dev.re7gog.b_sideloader.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Persistence shape of a tracked app.
 *
 * Column names and types are frozen at schema version 1; the domain model
 * ([dev.re7gog.b_sideloader.domain.model.TrackedApp]) is free to change independently because
 * `data/mapper/AppMappers.kt` sits between them.
 *
 * The source-specific columns live in their own tables rather than as nullable columns here, so a
 * GitHub app cannot accidentally carry a chat id and adding a third source needs no migration of
 * the existing two.
 */
@Entity(tableName = "apps")
data class AppEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [dev.re7gog.b_sideloader.domain.model.AppSourceKind.storedValue]. */
    val sourceType: Int,
    val packageName: String,
    val name: String,
    val version: String,
    val autoupdate: Boolean,
    val filterInclude: String,
    val filterExclude: String,
    /** When true every filter column is a regex instead of a word list. */
    val advancedMode: Boolean = false,
)

@Entity(
    tableName = "github_details",
    foreignKeys = [
        ForeignKey(
            entity = AppEntity::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("id")],
)
data class GithubDetailsEntity(
    @PrimaryKey val id: Long,
    val owner: String,
    val repo: String,
    val usePrereleases: Boolean,
    val releasesInclude: String,
    val releasesExclude: String,
)

@Entity(
    tableName = "telegram_details",
    foreignKeys = [
        ForeignKey(
            entity = AppEntity::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("id")],
)
data class TelegramDetailsEntity(
    @PrimaryKey val id: Long,
    val chatId: Long,
    val topicId: Int,
    val messageInclude: String,
    val messageExclude: String,
)

/**
 * An app row joined with whichever details row belongs to it. Exactly one of the two is non-null
 * for a well-formed row; the mapper treats "neither" as a corrupt row and drops it rather than
 * throwing, so one bad row cannot take the whole list down.
 */
data class AppWithDetails(
    @Embedded val app: AppEntity,

    @Relation(parentColumn = "id", entityColumn = "id")
    val githubDetails: GithubDetailsEntity?,

    @Relation(parentColumn = "id", entityColumn = "id")
    val telegramDetails: TelegramDetailsEntity?,
)
