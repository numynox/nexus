package io.github.numynox.nexus.noctua.ui.feed

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The web's thin bar under the header: how much of what was new is seen.
 * Plain on purpose — Material's progress bars end in a stop dot and, the wavy
 * one, keep moving, both of which read as something happening.
 */
@Composable
fun ReadingProgress(progress: Float, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(progress, tween(300), label = "progress")
    Box(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
            .semantics {
                contentDescription = "${(progress * 100).toInt()} % seen"
                progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
            },
    ) {
        Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(MaterialTheme.colorScheme.primary, CircleShape))
    }
}
