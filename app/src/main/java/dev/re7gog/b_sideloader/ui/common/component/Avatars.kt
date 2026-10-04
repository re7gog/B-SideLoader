package dev.re7gog.b_sideloader.ui.common.component

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Circular Telegram avatar. Downloads [photoFileId] lazily through [downloadPhoto]; until then (or
 * when the chat has no photo) it shows [fallbackText] on a tonal circle.
 */
@Composable
fun TelegramAvatar(
    fallbackText: String,
    modifier: Modifier = Modifier,
    photoFileId: Int? = null,
    downloadPhoto: suspend (Int) -> String? = { null },
    shape: Shape = CircleShape,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val photoPath by produceState<String?>(initialValue = null, key1 = photoFileId) {
        value = photoFileId?.let { downloadPhoto(it) }
    }

    Surface(shape = shape, color = containerColor, modifier = modifier) {
        val path = photoPath
        if (path != null) {
            AsyncImage(
                model = "file://$path",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = fallbackText,
                    color = contentColor,
                    style = textStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

/**
 * The launcher icon of an installed package, loaded off the main thread.
 *
 * `PackageManager.getApplicationIcon` hits the disk and can inflate an adaptive-icon drawable, so
 * the previous code — which called it straight from a `remember` inside the list item — did that
 * work on the main thread once per visible row. Here it is a `produceState` on IO, keyed by
 * package, so scrolling never blocks a frame on it.
 *
 * Returns `null` while loading and when the package is not installed.
 */
/**
 * Stands in for the launcher icon of an app that is not on the device: the first letter of its
 * name on a rounded tonal tile, shaped like an adaptive icon so the list keeps its rhythm.
 */
@Composable
fun AppIconPlaceholder(
    name: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleLarge,
) {
    Surface(
        shape = RoundedCornerShape(percent = 28),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = name.trim().take(1).uppercase().ifEmpty { "?" },
                style = textStyle,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun rememberInstalledAppIcon(packageName: String, enabled: Boolean = true): Drawable? {
    val context = LocalContext.current
    val icon by produceState<Drawable?>(initialValue = null, key1 = packageName, key2 = enabled) {
        value = if (!enabled || packageName.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
            }
        }
    }
    return icon
}
