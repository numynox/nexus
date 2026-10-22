package io.github.numynox.nexus.noctua.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.numynox.nexus.core.ui.components.PlaceholderBlock
import io.github.numynox.nexus.noctua.data.Density
import io.github.numynox.nexus.noctua.ui.common.cardShape

/**
 * Article-shaped placeholders in the chosen layout while the feed loads: the
 * list's shape, without claiming anything about its content — no "no
 * sections", no "all caught up" that the load could then contradict.
 */
@Composable
fun FeedPlaceholders(density: Density, modifier: Modifier = Modifier) {
    Column(
        modifier.semantics { contentDescription = "Loading articles" },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlaceholderBlock(Modifier.padding(start = 4.dp, top = 10.dp, bottom = 4.dp), width = 64.dp, height = 14.dp)
        repeat(if (density == Density.Compact) 8 else 3) { ArticlePlaceholder(density) }
    }
}

@Composable
private fun ArticlePlaceholder(density: Density) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = cardShape(density), modifier = Modifier.fillMaxWidth()) {
        when (density) {
            Density.Stories -> Column {
                PlaceholderBlock(height = 150.dp, shape = cardShape(density))
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlaceholderBlock(width = 120.dp, height = 18.dp)
                    PlaceholderBlock(Modifier.fillMaxWidth(0.9f), height = 20.dp)
                    PlaceholderBlock()
                    PlaceholderBlock(Modifier.fillMaxWidth(0.6f))
                }
            }
            Density.Comfortable -> Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlaceholderBlock(width = 110.dp, height = 12.dp)
                    PlaceholderBlock(height = 18.dp)
                    PlaceholderBlock(Modifier.fillMaxWidth(0.7f))
                }
                Box(Modifier.size(84.dp)) { PlaceholderBlock(height = 84.dp, shape = MaterialTheme.shapes.medium) }
            }
            Density.Compact -> Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PlaceholderBlock(width = 96.dp, height = 10.dp)
                PlaceholderBlock(Modifier.fillMaxWidth(0.85f), height = 16.dp)
            }
        }
    }
}
