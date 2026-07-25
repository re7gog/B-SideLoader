package dev.re7gog.b_sideloader.ui.common.text

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Text that a ViewModel can produce without holding a `Context`.
 *
 * Every ViewModel used to take `@ApplicationContext` purely to call `getString`, which made them
 * un-unit-testable and meant a message resolved in the ViewModel could not follow a per-app
 * language change. A [UiText] is resolved at render time by the composable that shows it.
 *
 * [Immutable] so Compose can skip recomposition when the value has not changed.
 */
@Immutable
sealed interface UiText {

    /** Text that already exists as a string, e.g. a server message or a file name. */
    @Immutable
    data class Raw(val value: String) : UiText

    /** A string resource, optionally with format arguments. */
    @Immutable
    data class Res(
        @param:StringRes val id: Int,
        val args: ImmutableList<Any> = persistentListOf(),
    ) : UiText

    companion object {
        fun of(@StringRes id: Int, vararg args: Any): UiText =
            Res(id, args.toList().toImmutableList())
    }
}

/** Resolves this text against the current configuration. */
@Composable
@ReadOnlyComposable
fun UiText.asString(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Res -> if (args.isEmpty()) {
        stringResource(id)
    } else {
        stringResource(id, *args.toTypedArray())
    }
}
