package dev.re7gog.b_sideloader.ui.features.search_app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.re7gog.b_sideloader.R

/** Branded sources keep their own colors; generic ones follow the content color. */
@Composable
fun SourceIcon(source: SearchSource, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(source.iconRes()),
        contentDescription = null,
        modifier = modifier,
        tint = if (source == SearchSource.LocalFile) LocalContentColor.current
               else Color.Unspecified
    )
}

/**
 * Compact source button that lives inside the search field, the way a browser puts its
 * search-engine picker at the left of the address bar.
 */
@Composable
fun SourceSelectorButton(
    source: SearchSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(start = 4.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 2.dp, top = 6.dp, bottom = 6.dp)
    ) {
        SourceIcon(source, Modifier.size(20.dp))
        Icon(
            painter = painterResource(R.drawable.arrow_drop_down_24px),
            contentDescription = stringResource(R.string.cd_change_source),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Stand-in for the search field shown by sources that take no query — keeps the picker one
 * tap away so the screen never traps the user in a non-searchable source.
 */
@Composable
fun SourceSelectorPill(
    source: SearchSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            SourceIcon(source, Modifier.size(20.dp))
            Text(
                text = stringResource(source.titleRes),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )
            Icon(
                painter = painterResource(R.drawable.arrow_drop_down_24px),
                contentDescription = stringResource(R.string.cd_change_source),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Bottom sheet listing every [SearchSource], selected one marked with a check. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcePickerSheet(
    currentSource: SearchSource,
    onSourceSelected: (SearchSource) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Text(
                text = stringResource(R.string.app_source),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
            )
            SearchSource.entries.forEach { source ->
                SourceRow(
                    source = source,
                    selected = source == currentSource,
                    onClick = { onSourceSelected(source) }
                )
            }
        }
    }
}

@Composable
private fun SourceRow(
    source: SearchSource,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                       else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SourceIcon(source, Modifier.size(22.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(source.titleRes), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(source.descriptionRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.check_24px),
                    contentDescription = stringResource(R.string.cd_selected)
                )
            }
        }
    }
}
