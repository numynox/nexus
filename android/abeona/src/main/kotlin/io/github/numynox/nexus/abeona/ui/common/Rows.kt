package io.github.numynox.nexus.abeona.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.numynox.nexus.abeona.domain.delayMinutes
import io.github.numynox.nexus.abeona.ui.AbeonaType
import io.github.numynox.nexus.abeona.ui.card
import io.github.numynox.nexus.abeona.ui.muted
import io.github.numynox.nexus.abeona.ui.burgundy
import io.github.numynox.nexus.abeona.ui.scene.Scene
import io.github.numynox.nexus.abeona.ui.scene.SceneSize
import io.github.numynox.nexus.abeona.ui.scene.SceneState
import io.github.numynox.nexus.abeona.ui.watch
import java.time.Instant

/** The row grid's columns (abeona-design.md §4): the time, the mark, then the name; the platform at the right edge. */
object RowGrid {
    val Time = 52.dp
    val Mark = 24.dp
    val Gap = 8.dp
}

/** A raised surface: corner 24, padding 16, no shadow. Holds rows, insets and chips, never another card. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    color: Color = card,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val inner: @Composable () -> Unit = { Column(Modifier.padding(padding), content = content) }
    if (onClick != null) {
        Surface(onClick = onClick, color = color, contentColor = MaterialTheme.colorScheme.onSurface, shape = shape, modifier = modifier.fillMaxWidth(), content = inner)
    } else {
        Surface(color = color, contentColor = MaterialTheme.colorScheme.onSurface, shape = shape, modifier = modifier.fillMaxWidth(), content = inner)
    }
}

/** How the rail passes through a row's mark column, for rows that follow each other on one continuous rail. */
enum class RailLine { None, Solid, Faint, Dashed }

/** What sits on the rail in a row: a stop where the traveller gets on or off, or one passed through. */
enum class RailDot { None, Stop, Through }

/** The rail through one row: the line above the dot and below it. */
data class Rail(val above: RailLine = RailLine.None, val below: RailLine = RailLine.None, val dot: RailDot = RailDot.Stop)

/**
 * The row grid: time 52, mark 24, the name taking the room, the platform
 * boxed at the right edge. Every row about a stop or a train is one of these,
 * so the columns line up on every screen. A column with nothing to say stays
 * empty; it does not collapse.
 *
 * The mark is an icon from the vocabulary or a chip ([mark]), or the rail
 * ([rail]) drawn through the whole height of the row, so rows stacked without
 * a gap make one continuous line. [name] is plain text; [content] replaces it
 * with a line chip and its direction. [trailing] sits before the platform.
 */
@Composable
fun StopRow(
    name: String,
    modifier: Modifier = Modifier,
    planned: Instant? = null,
    expected: Instant? = null,
    cancelled: Boolean = false,
    platformPlanned: String? = null,
    platformCurrent: String? = null,
    strong: Boolean = false,
    quiet: Boolean = false,
    rail: Rail? = null,
    minHeight: Dp = 40.dp,
    onClick: (() -> Unit)? = null,
    mark: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val ink = if (quiet || cancelled) muted else scheme.onSurface
    val railColor = scheme.onSurface
    val hole = scheme.surface
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = if (onClick != null) maxOf(minHeight, 48.dp) else minHeight)
            .drawBehind {
                if (rail == null) return@drawBehind
                val x = (RowGrid.Time + RowGrid.Gap + RowGrid.Mark / 2).toPx()
                val y = size.height / 2
                fun line(kind: RailLine, from: Float, to: Float) {
                    when (kind) {
                        RailLine.None -> Unit
                        RailLine.Solid -> drawLine(railColor, Offset(x, from), Offset(x, to), strokeWidth = 3.dp.toPx())
                        RailLine.Faint -> drawLine(railColor.copy(alpha = 0.3f), Offset(x, from), Offset(x, to), strokeWidth = 3.dp.toPx())
                        RailLine.Dashed -> drawLine(
                            railColor.copy(alpha = 0.45f), Offset(x, from), Offset(x, to), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx())),
                        )
                    }
                }
                line(rail.above, 0f, y)
                line(rail.below, y, size.height)
                when (rail.dot) {
                    RailDot.None -> Unit
                    RailDot.Stop -> {
                        drawCircle(hole, radius = 6.dp.toPx(), center = Offset(x, y))
                        drawCircle(railColor, radius = 5.dp.toPx(), center = Offset(x, y), style = Stroke(2.5.dp.toPx()))
                    }
                    RailDot.Through -> {
                        drawCircle(hole, radius = 4.dp.toPx(), center = Offset(x, y))
                        drawCircle(railColor, radius = 3.dp.toPx(), center = Offset(x, y), style = Stroke(2.dp.toPx()))
                    }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGrid.Gap),
    ) {
        // The time, its delay under it: the column stays one width.
        Column(Modifier.width(RowGrid.Time)) {
            if (planned != null || expected != null) {
                val delay = if (cancelled) 0 else delayMinutes(planned, expected)
                val style = (if (strong) AbeonaType.body.copy(fontWeight = FontWeight.SemiBold) else AbeonaType.body)
                val shown = if (cancelled || delay <= 0) planned ?: expected else expected
                Text(
                    TripFormat.time(shown),
                    style = style.copy(textDecoration = if (cancelled) TextDecoration.LineThrough else null),
                    color = ink,
                    maxLines = 1,
                    modifier = if (delay > 0) Modifier.clearAndSetSemantics { contentDescription = "${TripFormat.time(expected)}, $delay min late" } else Modifier,
                )
                if (delay > 0) Text("+$delay", style = AbeonaType.label.copy(letterSpacing = AbeonaType.meta.letterSpacing), color = watch.soft.content, maxLines = 1)
            }
        }
        Box(Modifier.width(RowGrid.Mark), contentAlignment = Alignment.Center) { mark?.invoke() }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RowGrid.Gap)) {
            if (content != null) {
                content()
            } else {
                Text(
                    name,
                    style = (if (strong) AbeonaType.title else AbeonaType.body).copy(textDecoration = if (cancelled) TextDecoration.LineThrough else null),
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke(this)
        PlatformBox(platformPlanned, platformCurrent)
    }
}

/** An icon from the vocabulary in a row's mark column: 20, muted unless told otherwise. */
@Composable
fun MarkIcon(icon: ImageVector, description: String?, tint: Color = muted) {
    Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
}

/**
 * A supporting fact: an icon from the vocabulary (abeona-design.md §5) and
 * its value, in meta. [description] is what the icon stands for, read out
 * before the value ("Changes: 2").
 */
@Composable
fun Fact(icon: ImageVector, description: String, value: String, modifier: Modifier = Modifier, color: Color = muted) {
    Fact(icon, description, modifier.clearAndSetSemantics { contentDescription = "$description: $value" }, color) {
        Text(value, style = AbeonaType.meta, color = color, maxLines = 1)
    }
}

/** A fact whose value is not plain text: a time with its delay. */
@Composable
fun Fact(icon: ImageVector, description: String, modifier: Modifier = Modifier, color: Color = muted, content: @Composable RowScope.() -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = description, tint = color, modifier = Modifier.size(16.dp))
        content()
    }
}

/** A line of facts and chips; wraps when the screen is narrow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FactLine(modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A section's title, with its facts on the right. No supporting sentence. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, facts: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 32.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = AbeonaType.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        facts()
    }
}

/** The one primary action of a screen or sheet: a burgundy fill with white on it. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val colors = burgundy
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = colors.container, contentColor = colors.content),
        modifier = modifier,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(18.dp))
        Text(text, style = AbeonaType.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

/** Nothing here yet: the scene, one headline, the actions. No paragraph. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    state: SceneState = SceneState.Idle,
    actions: @Composable FlowRowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Scene(state, size = SceneSize.Full)
        Text(title, style = AbeonaType.headline)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}
