package dev.re7gog.b_sideloader.ui.common.component

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.re7gog.b_sideloader.R
import kotlinx.coroutines.launch

/**
 * Paste button for a text field's trailing slot. Hands over the clipboard's text, trimmed, and
 * does nothing when the clipboard holds no text.
 */
@Composable
fun PasteIconButton(
    onPaste: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    IconButton(
        onClick = {
            scope.launch {
                val pasted = clipboard.getClipEntry()
                    ?.clipData
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.text
                    ?.toString()
                if (!pasted.isNullOrBlank()) onPaste(pasted.trim())
            }
        },
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(R.drawable.content_paste_24px),
            contentDescription = stringResource(R.string.cd_paste),
        )
    }
}
