package dev.re7gog.b_sideloader.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * Renders a list entry and (optionally) a detail entry side by side.
 *
 * [key] is deliberately constant for every instance of this scene. `NavDisplay` keys its
 * `AnimatedContent` on `(scene class, scene key)`, so a constant key means selecting a different
 * app swaps the *contents* of the right-hand pane in place instead of animating both panes
 * sideways — which is what a list-detail layout is supposed to look like. Moving to or from a
 * single-pane scene still animates, because the scene class changes.
 */
internal class ListDetailScene<T : Any>(
    private val listEntry: NavEntry<T>,
    private val detailEntry: NavEntry<T>?,
    override val previousEntries: List<NavEntry<T>>,
    private val listPaneWidth: Dp,
    private val placeholder: @Composable () -> Unit,
) : Scene<T> {

    override val key: Any = KEY

    override val entries: List<NavEntry<T>> = listOfNotNull(listEntry, detailEntry)

    override val content: @Composable () -> Unit = {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(listPaneWidth)) { listEntry.Content() }
            VerticalDivider(
                modifier = Modifier.fillMaxHeight(),
                thickness = ListDetailWidths.DIVIDER,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Box(Modifier.weight(1f)) {
                if (detailEntry != null) detailEntry.Content() else placeholder()
            }
        }
    }

    // Equality ignores `content` and `placeholder`: both are freshly allocated lambdas on every
    // recomposition, so including them would make every scene unequal to the last and defeat the
    // stable-key behaviour described above.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ListDetailScene<*>) return false
        return entries == other.entries &&
            previousEntries == other.previousEntries &&
            listPaneWidth == other.listPaneWidth
    }

    override fun hashCode(): Int {
        var result = entries.hashCode()
        result = 31 * result + previousEntries.hashCode()
        result = 31 * result + listPaneWidth.hashCode()
        return result
    }

    override fun toString(): String = "ListDetailScene(entries=$entries)"

    private companion object {
        const val KEY = "list-detail"
    }
}

/**
 * Marks which side of a list-detail layout a destination belongs on.
 *
 * Nav3 keeps `NavEntry.key` private and exposes only `contentKey` and `metadata`, so a strategy
 * cannot type-check the key it was built from. Metadata is the extension point the library
 * provides for exactly this, and it has the nicer property that the graph declares the roles in
 * one place instead of the strategy hard-coding a list of destination types.
 */
internal object ListDetailPane {
    private const val ROLE = "dev.re7gog.b_sideloader.listDetailRole"
    private const val LIST = "list"
    private const val DETAIL = "detail"

    val list: Map<String, Any> = mapOf(ROLE to LIST)
    val detail: Map<String, Any> = mapOf(ROLE to DETAIL)

    fun isList(entry: NavEntry<*>): Boolean = entry.metadata[ROLE] == LIST
    fun isDetail(entry: NavEntry<*>): Boolean = entry.metadata[ROLE] == DETAIL
}

/**
 * How a list-detail layout shares out the width it is given.
 *
 * The details pane is the one with something to read, so it is the one protected: it never gets
 * less than [DETAIL_MIN]. The list gives way first, down to [LIST_MIN] — narrow enough that the
 * apps list falls back to one column of cards — and when there is width to spare the list stops
 * at [LIST_MAX] and everything else goes to the details. When even the two minimums do not fit,
 * there are no two panes at all: one pane at a time, as on a phone.
 *
 * The width meant is the content area beside the navigation rail, not the window. On a fold the
 * rail alone costs a quarter of what the details pane needs.
 */
internal object ListDetailWidths {
    /** Material's list pane width for a list-detail layout. */
    val LIST_MAX: Dp = 360.dp

    /** One column of the apps list's cards, with its padding. */
    val LIST_MIN: Dp = 200.dp

    /** A phone's worth: the details page was laid out for a phone, and reads badly any narrower. */
    val DETAIL_MIN: Dp = 400.dp

    val DIVIDER: Dp = 1.dp

    fun fitsTwoPanes(width: Dp): Boolean = width >= LIST_MIN + DIVIDER + DETAIL_MIN

    fun listPane(width: Dp): Dp = (width - DIVIDER - DETAIL_MIN).coerceIn(LIST_MIN, LIST_MAX)
}

/**
 * Puts the apps list and one app's details on screen together when there is room.
 *
 * Only claims a back stack whose *last* entry is one it recognises, so switching to the search or
 * settings tab (which concatenates onto the apps stack) falls through to the single-pane default.
 * Returning `null` when [enabled] is false is what makes this a no-op on a phone. Both arguments
 * come from [ListDetailWidths].
 */
internal class ListDetailSceneStrategy<T : Any>(
    private val enabled: Boolean,
    private val listPaneWidth: Dp,
    private val placeholder: @Composable () -> Unit,
) : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        if (!enabled || entries.isEmpty()) return null
        val last = entries.last()

        if (ListDetailPane.isList(last)) {
            return ListDetailScene(
                listEntry = last,
                detailEntry = null,
                previousEntries = entries.dropLast(1),
                listPaneWidth = listPaneWidth,
                placeholder = placeholder,
            )
        }

        val listEntry = entries.getOrNull(entries.lastIndex - 1)?.takeIf { ListDetailPane.isList(it) }
        if (ListDetailPane.isDetail(last) && listEntry != null) {
            return ListDetailScene(
                listEntry = listEntry,
                detailEntry = last,
                // Back drops the detail, which resolves to this same scene showing the
                // placeholder — so predictive back previews an emptying pane, not a pop.
                previousEntries = entries.dropLast(1),
                listPaneWidth = listPaneWidth,
                placeholder = placeholder,
            )
        }
        return null
    }
}
