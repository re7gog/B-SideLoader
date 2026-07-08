package dev.re7gog.b_sideloader.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.TextStyle
import coil3.compose.AsyncImage

/**
 * Circular Telegram avatar. When [photoFileId] is present it downloads that photo
 * lazily via [downloadPhoto] and shows it; otherwise (or while downloading) it falls
 * back to [fallbackText] on a colored circle. Used for channel and topic avatars.
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
    textStyle: TextStyle = MaterialTheme.typography.titleMedium
) {
    val photoPath by produceState<String?>(initialValue = null, key1 = photoFileId) {
        value = photoFileId?.let { downloadPhoto(it) }
    }

    Surface(shape = shape, color = containerColor, modifier = modifier) {
        val currentPath = photoPath
        if (currentPath != null) {
            AsyncImage(
                model = "file://$currentPath",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(text = fallbackText, color = contentColor, style = textStyle)
            }
        }
    }
}
