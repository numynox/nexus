package io.github.numynox.nexus.annona.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.numynox.nexus.annona.data.CategoryRef
import io.github.numynox.nexus.annona.data.Urgency
import io.github.numynox.nexus.annona.data.expiryLabel
import io.github.numynox.nexus.annona.data.urgencyOf
import io.github.numynox.nexus.core.ui.theme.NexusThemeDefaults
import java.time.LocalDate

/** The colours of an expiry, from "gone" to "plenty of time". */
@Composable
fun urgencyColors(urgency: Urgency): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    val extended = NexusThemeDefaults.extendedColors
    return when (urgency) {
        Urgency.Expired -> scheme.errorContainer to scheme.onErrorContainer
        Urgency.Today, Urgency.Week -> extended.warningContainer to extended.onWarningContainer
        Urgency.Month -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        Urgency.Later -> extended.successContainer to extended.onSuccessContainer
        Urgency.Undated -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
    }
}

/** "3 days left" in a pill coloured by how soon. */
@Composable
fun ExpiryBadge(date: LocalDate?, modifier: Modifier = Modifier) {
    val (container, content) = urgencyColors(urgencyOf(date))
    Surface(color = container, contentColor = content, shape = CircleShape, modifier = modifier) {
        Text(expiryLabel(date), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/** A category's icon in its colour, in a soft Material shape. */
@Composable
fun CategoryAvatar(category: CategoryRef?, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val tint = categoryColor(category?.color) ?: MaterialTheme.colorScheme.primary
    Box(
        modifier.size(size).background(tint.copy(alpha = 0.18f), MaterialShapes.Cookie7Sided.toShape()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(categoryIcon(category?.icon), contentDescription = category?.name, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

/** The product's photo, or its category's avatar when there is none. */
@Composable
fun ProductImage(url: String?, category: CategoryRef?, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    if (url == null) {
        CategoryAvatar(category, size, modifier)
    } else {
        val fallback = rememberVectorPainter(categoryIcon(category?.icon))
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            error = fallback,
            modifier = modifier
                .size(size)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
    }
}

/**
 * The Expressive FAB menu on every tab: scan to add, scan to use up, or add
 * by name. Scanning is a button, not a page — it is what the app is opened for.
 */
@Composable
fun StockMenu(onScanAdd: () -> Unit, onScanConsume: () -> Unit, onAddByName: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(expanded) { expanded = false }
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = { expanded = it },
                modifier = Modifier.semantics { contentDescription = if (expanded) "Close menu" else "Add or use up" },
            ) {
                Icon(
                    if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.animateIcon({ checkedProgress }),
                )
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = { expanded = false; onScanAdd() },
            icon = { Icon(Icons.Rounded.QrCodeScanner, contentDescription = null) },
            text = { Text("Scan to add") },
        )
        FloatingActionButtonMenuItem(
            onClick = { expanded = false; onScanConsume() },
            icon = { Icon(Icons.Rounded.Restaurant, contentDescription = null) },
            text = { Text("Scan to use up") },
        )
        FloatingActionButtonMenuItem(
            onClick = { expanded = false; onAddByName() },
            icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
            text = { Text("Add by name") },
        )
    }
}
