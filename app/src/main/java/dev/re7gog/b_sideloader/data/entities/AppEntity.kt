package dev.re7gog.b_sideloader.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "apps")
data class AppEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sourceType: String,
    val name: String
)
