package dev.re7gog.b_sideloader.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "telegram_details",
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
data class TelegramDetailsEntity(
    @PrimaryKey val id: Long,
    val chatId: Long,
    val topicId: Int
)
