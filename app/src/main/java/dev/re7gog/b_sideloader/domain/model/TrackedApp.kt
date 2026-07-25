package dev.re7gog.b_sideloader.domain.model

/**
 * An app the user asked B-SideLoader to keep up to date.
 *
 * Pure data: no Room annotations, no `TdApi` types, no Android imports. The persistence shape
 * lives in `data/local/entity` and is translated by `data/mapper/AppMappers.kt`; the screen shapes
 * live under `ui/feature` and are translated by each feature's own mapper.
 */
data class TrackedApp(
    /** Row id, or [NEW_APP_ID] for an app that has never been saved. */
    val id: Long = NEW_APP_ID,
    val packageName: String,
    val name: String,
    /** What is installed right now, in the source's own versioning scheme. */
    val version: AppVersion,
    val autoUpdate: Boolean,
    /** Applied to candidate APK file names. */
    val assetFilter: FilterRule,
    /** How [assetFilter] and the source's own filter are interpreted. */
    val filterMode: FilterMode,
    val source: AppSource,
) {
    val isSaved: Boolean get() = id != NEW_APP_ID

    companion object {
        const val NEW_APP_ID: Long = 0L
    }
}

/**
 * Opaque version marker. GitHub apps store the release name, Telegram apps store the id of the
 * message carrying the APK — neither is comparable as a number, and both are only ever tested for
 * equality against the newest candidate, so the type deliberately offers no ordering.
 */
@JvmInline
value class AppVersion(val raw: String) {
    val isKnown: Boolean get() = raw.isNotEmpty()

    override fun toString(): String = raw

    companion object {
        val Unknown = AppVersion("")
    }
}

/** An include/exclude pair. Blank on either side means "no constraint from that side". */
data class FilterRule(
    val include: String = "",
    val exclude: String = "",
) {
    val isEmpty: Boolean get() = include.isBlank() && exclude.isBlank()

    companion object {
        val None = FilterRule()
    }
}

/** How the strings in a [FilterRule] are interpreted. */
enum class FilterMode {
    /** Whitespace-separated words; case-insensitive substring match. */
    Words,

    /** Case-insensitive regular expressions. */
    Regex,
    ;

    val isAdvanced: Boolean get() = this == Regex

    companion object {
        fun of(advanced: Boolean): FilterMode = if (advanced) Regex else Words
    }
}

/** Where an app's APKs come from. Adding a source means adding a variant here. */
sealed interface AppSource {
    val kind: AppSourceKind

    data class GitHub(
        val owner: String,
        val repo: String,
        val usePrereleases: Boolean = false,
        /** Applied to release titles before their assets are considered. */
        val releaseFilter: FilterRule = FilterRule.None,
    ) : AppSource {
        override val kind: AppSourceKind get() = AppSourceKind.GitHub

        /** `owner/repo`, the form GitHub itself uses. */
        val slug: String get() = "$owner/$repo"
    }

    data class Telegram(
        val chatId: Long,
        /** Forum topic id, or [NO_TOPIC] for a plain channel. */
        val topicId: Int = NO_TOPIC,
        /** Applied to the message text/caption that accompanies an APK. */
        val messageFilter: FilterRule = FilterRule.None,
    ) : AppSource {
        override val kind: AppSourceKind get() = AppSourceKind.Telegram

        companion object {
            const val NO_TOPIC: Int = 0
        }
    }
}

/**
 * Discriminator persisted alongside an app row. The ordinals are *not* used — the stored values
 * are the explicit [storedValue]s, so reordering this enum cannot corrupt an existing database.
 */
enum class AppSourceKind(val storedValue: Int) {
    GitHub(1),
    Telegram(2),
    ;

    companion object {
        fun fromStoredValue(value: Int): AppSourceKind? = entries.firstOrNull { it.storedValue == value }
    }
}
