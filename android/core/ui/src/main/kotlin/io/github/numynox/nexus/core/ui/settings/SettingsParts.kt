package io.github.numynox.nexus.core.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.numynox.nexus.core.data.NexusConfig
import io.github.numynox.nexus.core.ui.components.ConnectedChoiceRow
import io.github.numynox.nexus.core.ui.components.NexusCard
import io.github.numynox.nexus.core.ui.theme.ThemeMode

/** The three Nexus apps, for "the same account signs you in to …". */
private val NexusApps = listOf("Noctua", "Vibilia", "Annona")

/** A small icon and a title, heading a group of controls inside a card. */
@Composable
fun SettingLabel(icon: ImageVector, title: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.titleSmall)
    }
}

/** A titled, explained switch — the settings screens' most common row. */
@Composable
fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // The whole row toggles, not just the switch: a bigger target, read as one control.
    Row(
        modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(start = 12.dp))
    }
}

/** Light, dark or the system's, and — from Android 12 — the wallpaper's colours. */
@Composable
fun AppearanceCard(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onDynamicColor: (Boolean) -> Unit,
    appColour: String,
    modifier: Modifier = Modifier,
) {
    NexusCard(modifier) {
        SettingLabel(Icons.Rounded.Palette, "Theme")
        ConnectedChoiceRow(
            options = ThemeMode.entries,
            selected = themeMode,
            onSelect = onThemeMode,
            label = { it.name },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SettingSwitch(
                title = "Wallpaper colours",
                description = "Use your wallpaper's palette instead of $appColour.",
                checked = dynamicColor,
                onCheckedChange = onDynamicColor,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** Who is signed in, and the way out. */
@Composable
fun AccountCard(appName: String, email: String?, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    // Full width like the cards around it; a card sizes to its content otherwise.
    NexusCard(modifier.fillMaxWidth()) {
        Text(email ?: "Signed in", style = MaterialTheme.typography.titleSmall)
        Text(
            "The same account signs you in to ${NexusApps.filter { it != appName }.joinToString(" and ")}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onSignOut, modifier = Modifier.padding(top = 12.dp)) {
            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Sign out", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** The installed version, and where newer ones come from. */
@Composable
fun AboutItem(config: NexusConfig, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    ListItem(
        headlineContent = { Text("${config.appName} ${config.versionName}") },
        supportingContent = { Text("Installed from GitHub Releases") },
        leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) },
        trailingContent = {
            TextButton(onClick = { uriHandler.openUri("https://github.com/${config.releaseRepository}/releases") }) {
                Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Releases", modifier = Modifier.padding(start = 6.dp))
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier,
    )
}
