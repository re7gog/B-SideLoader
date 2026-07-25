package dev.re7gog.b_sideloader.ui.common.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.re7gog.b_sideloader.R

/**
 * Section, group and row primitives shared by the settings page and both detail pages.
 *
 * These existed three times over — `SettingsSectionHeader`/`SettingsGroup`/`SettingsSwitchItem`
 * in the settings feature and `DetailSectionLabel`/`DetailSwitchRow`/`DetailFilterField` in the
 * details feature, with subtly different padding and corner radii. One copy means the two pages
 * cannot drift apart again.
 */

/** Primary-coloured label that introduces a group. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = SectionDefaults.LabelStartPadding, bottom = 8.dp),
    )
}

/** Rounded tonal container that visually groups related rows. */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(SectionDefaults.GroupCorner),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

/** A labelled toggle. Tapping anywhere on the row flips it, not just the switch. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    ListItem(
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable(enabled = enabled) { onCheckedChange(!checked) },
    ) { Text(title) }
}

/** Standalone card version of [SwitchRow], for pages that are not built out of groups. */
@Composable
fun SwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(SectionDefaults.CardCorner),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        SwitchRow(
            title = title,
            subtitle = subtitle,
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

/** A row that opens something else. */
@Composable
fun NavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingContent: (@Composable () -> Unit)? = null,
) {
    ListItem(
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = leadingContent,
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.chevron_right_24px),
                contentDescription = null,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable(onClick = onClick),
    ) { Text(title) }
}

/** Rounded text field used for every include/exclude filter. */
@Composable
fun FilterField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        singleLine = true,
        shape = RoundedCornerShape(SectionDefaults.FieldCorner),
        modifier = modifier.fillMaxWidth(),
    )
}

object SectionDefaults {
    val LabelStartPadding = 4.dp
    val GroupCorner = 24.dp
    val CardCorner = 20.dp
    val FieldCorner = 16.dp
}
