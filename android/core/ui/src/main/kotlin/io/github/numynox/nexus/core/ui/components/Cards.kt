package io.github.numynox.nexus.core.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.numynox.nexus.core.ui.theme.tabular

/** A heading over a group of content, with an optional trailing action. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/** The standard surface for a block of content. */
@Composable
fun NexusCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.animateContentSize().padding(16.dp), content = content)
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, colors = colors, shape = MaterialTheme.shapes.large, content = inner)
    } else {
        Card(modifier = modifier, colors = colors, shape = MaterialTheme.shapes.large, content = inner)
    }
}

/** One labelled figure inside a metric card. */
data class MetricRow(val label: String, val value: String)

/**
 * A headline figure with a few supporting rows — the statistics page's
 * building block. The headline springs into place.
 */
@Composable
fun MetricCard(
    title: String,
    value: Double,
    format: (Double) -> String,
    icon: ImageVector,
    accent: Color,
    rows: List<MetricRow>,
    modifier: Modifier = Modifier,
    footnote: String? = null,
) {
    NexusCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AnimatedNumberText(
            value = value,
            format = format,
            style = MaterialTheme.typography.headlineMedium,
            color = accent,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    row.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(row.value, style = MaterialTheme.typography.bodyMedium.tabular())
            }
        }
        if (footnote != null) {
            Text(
                footnote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
