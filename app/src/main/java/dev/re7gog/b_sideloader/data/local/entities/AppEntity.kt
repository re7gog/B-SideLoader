package dev.re7gog.b_sideloader.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "apps")
data class AppEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceType: Int,
    val packageName: String,
    val name: String,
    val version: String,
    val autoupdate: Boolean,
    val filterInclude: String,
    val filterExclude: String,
    // When true, filterInclude/filterExclude (and the source-specific include/exclude fields) are
    // interpreted as regular expressions instead of space-separated word lists. See NameFilter.
    val advancedMode: Boolean = false
)
