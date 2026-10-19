package io.github.numynox.nexus.core.ui.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.numynox.nexus.core.ui.theme.tabular
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

enum class SeriesStyle { Line, SteppedLine, Bars, Dots }

@Immutable
data class ChartPoint(val x: Double, val y: Double)

@Immutable
data class ChartSeries(
    val label: String,
    val points: List<ChartPoint>,
    val color: Color,
    val style: SeriesStyle = SeriesStyle.Line,
    val strokeWidth: Dp = 2.dp,
    val dashed: Boolean = false,
    /** A soft gradient under a line. */
    val fill: Boolean = false,
    val showInTooltip: Boolean = true,
)

/** A horizontal line across the plot, e.g. an average. */
@Immutable
data class ReferenceLine(val value: Double, val label: String, val color: Color)

/** A vertical line, e.g. "now" on a time-of-day axis. */
@Immutable
data class VerticalMarker(val x: Double, val color: Color)

/**
 * The one chart every Nexus app draws with. Lines reveal left to right and
 * bars grow from the baseline when data arrives; touching the plot shows the
 * values under the finger. Horizontal drags scrub, vertical ones are left to
 * the surrounding list. Bar series sharing an x are stacked in series order.
 */
@Composable
fun NexusChart(
    series: List<ChartSeries>,
    xFormatter: (Double) -> String,
    yFormatter: (Double) -> String,
    modifier: Modifier = Modifier,
    tooltipXFormatter: (Double) -> String = xFormatter,
    tooltipYFormatter: (Double) -> String = yFormatter,
    xRange: ClosedFloatingPointRange<Double>? = null,
    xTicks: List<Double>? = null,
    includeZero: Boolean = false,
    referenceLines: List<ReferenceLine> = emptyList(),
    verticalMarkers: List<VerticalMarker> = emptyList(),
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.tabular()
        .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val tooltipTitleStyle = MaterialTheme.typography.labelMedium.tabular()
        .copy(color = MaterialTheme.colorScheme.inverseOnSurface)
    val tooltipBodyStyle = MaterialTheme.typography.bodySmall.tabular()
        .copy(color = MaterialTheme.colorScheme.inverseOnSurface)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val tooltipColor = MaterialTheme.colorScheme.inverseSurface
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLow

    val reveal = remember { Animatable(0f) }
    LaunchedEffect(series) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }
    var selectedX by remember(series) { mutableStateOf<Double?>(null) }

    val geometry = remember(series, xRange, includeZero, referenceLines) {
        ChartGeometry.of(series, xRange, includeZero, referenceLines)
    }

    Canvas(
        modifier = modifier
            .pointerInput(geometry) {
                detectTapGestures { offset ->
                    val x = geometry?.let { g -> nearestX(g, layoutFrame(size.width.toFloat(), size.height.toFloat(), g, textMeasurer, labelStyle, yFormatter), offset.x) }
                    selectedX = if (selectedX != null && x == selectedX) null else x
                }
            }
            .pointerInput(geometry) {
                detectHorizontalDragGestures(
                    onDragEnd = {},
                ) { change, _ ->
                    change.consume()
                    selectedX = geometry?.let { g -> nearestX(g, layoutFrame(size.width.toFloat(), size.height.toFloat(), g, textMeasurer, labelStyle, yFormatter), change.position.x) }
                }
            },
    ) {
        val g = geometry ?: return@Canvas
        val frame = layoutFrame(size.width, size.height, g, textMeasurer, labelStyle, yFormatter)

        drawGrid(g, frame, gridColor, textMeasurer, labelStyle, yFormatter)
        drawXLabels(g, frame, xTicks, textMeasurer, labelStyle, xFormatter)

        verticalMarkers.forEach { marker ->
            if (marker.x in g.minX..g.maxX) {
                val x = frame.px(marker.x, g)
                drawLine(
                    marker.color,
                    Offset(x, frame.top),
                    Offset(x, frame.bottom),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())),
                )
            }
        }

        drawBars(series, g, frame, reveal.value, selectedX)

        clipRect(left = 0f, top = 0f, right = frame.left + frame.width * reveal.value, bottom = size.height) {
            series.filter { it.style != SeriesStyle.Bars }.forEach { s ->
                when (s.style) {
                    SeriesStyle.Line, SeriesStyle.SteppedLine -> drawLineSeries(s, g, frame)
                    SeriesStyle.Dots -> drawDots(s, g, frame, surfaceColor)
                    SeriesStyle.Bars -> Unit
                }
            }
        }

        referenceLines.forEach { line -> drawReferenceLine(line, surfaceColor, g, frame, textMeasurer, labelStyle, yFormatter) }

        selectedX?.let { x ->
            drawSelection(x, series, g, frame, surfaceColor, tooltipColor, textMeasurer, tooltipTitleStyle, tooltipBodyStyle, tooltipXFormatter, tooltipYFormatter)
        }
    }
}

// ── Geometry ─────────────────────────────────────────────────────────────────

private class ChartGeometry(
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double,
    val yTicks: List<Double>,
    val distinctX: List<Double>,
) {
    companion object {
        fun of(
            series: List<ChartSeries>,
            xRange: ClosedFloatingPointRange<Double>?,
            includeZero: Boolean,
            referenceLines: List<ReferenceLine>,
        ): ChartGeometry? {
            val all = series.flatMap { it.points }
            if (all.isEmpty()) return null
            val minX = xRange?.start ?: all.minOf { it.x }
            val maxX = xRange?.endInclusive ?: all.maxOf { it.x }

            val barTotals = series.filter { it.style == SeriesStyle.Bars }
                .flatMap { it.points }
                .groupBy { it.x }
                .mapValues { (_, points) -> points.sumOf { it.y } }
            val ys = series.filter { it.style != SeriesStyle.Bars }.flatMap { s -> s.points.map { it.y } } +
                barTotals.values + referenceLines.map { it.value }
            val hasBars = barTotals.isNotEmpty()
            var lo = ys.minOrNull() ?: 0.0
            var hi = ys.maxOrNull() ?: 1.0
            if (includeZero || hasBars) lo = min(lo, 0.0)
            if (hi == lo) {
                hi += if (hi == 0.0) 1.0 else abs(hi) * 0.1
                if (!(includeZero || hasBars)) lo -= abs(lo) * 0.1
            }
            val pad = (hi - lo) * 0.08
            val ticks = niceTicks(if (includeZero || hasBars) lo else lo - pad, hi + pad, 4)
            return ChartGeometry(
                minX = minX,
                maxX = if (maxX == minX) minX + 1 else maxX,
                minY = ticks.first(),
                maxY = ticks.last(),
                yTicks = ticks,
                distinctX = all.map { it.x }.distinct().sorted(),
            )
        }
    }
}

private class Frame(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    fun px(x: Double, g: ChartGeometry): Float = left + ((x - g.minX) / (g.maxX - g.minX) * width).toFloat()
    fun py(y: Double, g: ChartGeometry): Float = bottom - ((y - g.minY) / (g.maxY - g.minY) * height).toFloat()
}

private fun layoutFrame(
    width: Float,
    height: Float,
    g: ChartGeometry,
    measurer: TextMeasurer,
    style: TextStyle,
    yFormatter: (Double) -> String,
): Frame {
    val yLabelWidth = g.yTicks.maxOf { measurer.measure(yFormatter(it), style).size.width }
    val xLabelHeight = measurer.measure("0", style).size.height
    return Frame(
        left = yLabelWidth + 12f,
        top = xLabelHeight / 2f + 4f,
        right = width - 8f,
        bottom = height - xLabelHeight - 12f,
    )
}

/** Round-number ticks spanning [lo, hi]: 0, 250, 500… rather than 0, 237, 474… */
private fun niceTicks(lo: Double, hi: Double, count: Int): List<Double> {
    val span = hi - lo
    val raw = span / count
    val magnitude = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= raw }
    val start = floor(lo / step) * step
    val end = ceil(hi / step) * step
    val ticks = mutableListOf<Double>()
    var v = start
    while (v <= end + step / 2) {
        ticks += v
        v += step
    }
    return ticks
}

private fun nearestX(g: ChartGeometry, frame: Frame, touchX: Float): Double? =
    g.distinctX.minByOrNull { abs(frame.px(it, g) - touchX) }

// ── Drawing ──────────────────────────────────────────────────────────────────

private fun DrawScope.drawGrid(
    g: ChartGeometry,
    frame: Frame,
    color: Color,
    measurer: TextMeasurer,
    style: TextStyle,
    yFormatter: (Double) -> String,
) {
    g.yTicks.forEach { tick ->
        val y = frame.py(tick, g)
        drawLine(color, Offset(frame.left, y), Offset(frame.right, y), strokeWidth = 1.dp.toPx())
        val label = measurer.measure(yFormatter(tick), style)
        drawText(label, topLeft = Offset(frame.left - label.size.width - 8f, y - label.size.height / 2f))
    }
}

private fun DrawScope.drawXLabels(
    g: ChartGeometry,
    frame: Frame,
    ticks: List<Double>?,
    measurer: TextMeasurer,
    style: TextStyle,
    xFormatter: (Double) -> String,
) {
    val values = ticks ?: (0..4).map { g.minX + (g.maxX - g.minX) * it / 4.0 }
    // Labels that would touch the previous one are skipped, so the last
    // (clamped to the edge) never lands on top of its neighbour.
    var lastRight = Float.NEGATIVE_INFINITY
    val gap = 8.dp.toPx()
    val labels = values.filter { it in g.minX..g.maxX }.map { value ->
        val label = measurer.measure(xFormatter(value), style)
        val x = (frame.px(value, g) - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
        x to label
    }
    labels.forEachIndexed { index, (x, label) ->
        val next = labels.getOrNull(index + 1)
        val collidesWithLast = index == labels.lastIndex - 1 && next != null && x + label.size.width + gap > next.first
        if (x >= lastRight + gap && !collidesWithLast) {
            drawText(label, topLeft = Offset(x, frame.bottom + 8f))
            lastRight = x + label.size.width
        }
    }
}

private fun DrawScope.drawLineSeries(s: ChartSeries, g: ChartGeometry, frame: Frame) {
    if (s.points.isEmpty()) return
    val sorted = s.points.sortedBy { it.x }
    val path = Path()
    sorted.forEachIndexed { index, p ->
        val x = frame.px(p.x, g)
        val y = frame.py(p.y, g)
        when {
            index == 0 -> path.moveTo(x, y)
            s.style == SeriesStyle.SteppedLine -> {
                path.lineTo(x, frame.py(sorted[index - 1].y, g))
                path.lineTo(x, y)
            }
            else -> path.lineTo(x, y)
        }
    }
    if (s.fill && sorted.size > 1) {
        val area = Path().apply {
            addPath(path)
            lineTo(frame.px(sorted.last().x, g), frame.bottom)
            lineTo(frame.px(sorted.first().x, g), frame.bottom)
            close()
        }
        drawPath(
            area,
            Brush.verticalGradient(listOf(s.color.copy(alpha = 0.28f), s.color.copy(alpha = 0f)), frame.top, frame.bottom),
        )
    }
    drawPath(
        path,
        s.color,
        // A stepped line has a corner every bucket; round joins bulge there
        // into a string of beads, square ones keep the steps crisp.
        style = Stroke(
            width = s.strokeWidth.toPx(),
            cap = if (s.style == SeriesStyle.SteppedLine) StrokeCap.Butt else StrokeCap.Round,
            join = if (s.style == SeriesStyle.SteppedLine) StrokeJoin.Miter else StrokeJoin.Round,
            pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())) else null,
        ),
    )
    if (sorted.size == 1) {
        drawCircle(s.color, radius = s.strokeWidth.toPx() * 1.6f, center = Offset(frame.px(sorted[0].x, g), frame.py(sorted[0].y, g)))
    }
}

private fun DrawScope.drawDots(s: ChartSeries, g: ChartGeometry, frame: Frame, ring: Color) {
    s.points.forEach { p ->
        val c = Offset(frame.px(p.x, g), frame.py(p.y, g))
        drawCircle(ring, radius = 6.5.dp.toPx(), center = c)
        drawCircle(s.color, radius = 4.5.dp.toPx(), center = c)
    }
}

private fun DrawScope.drawBars(
    series: List<ChartSeries>,
    g: ChartGeometry,
    frame: Frame,
    progress: Float,
    selectedX: Double?,
) {
    val bars = series.filter { it.style == SeriesStyle.Bars }
    if (bars.isEmpty()) return
    val xs = bars.flatMap { s -> s.points.map { it.x } }.distinct().sorted()
    val spacing = xs.zipWithNext { a, b -> frame.px(b, g) - frame.px(a, g) }.minOrNull() ?: frame.width
    val barWidth = min(spacing * 0.62f, 28.dp.toPx()).coerceAtLeast(2.dp.toPx())
    val radius = min(barWidth / 2f, 8.dp.toPx())
    xs.forEach { x ->
        var base = 0.0
        val stack = bars.mapNotNull { s -> s.points.firstOrNull { it.x == x }?.let { s to it } }
        stack.forEachIndexed { index, (s, p) ->
            if (p.y <= 0) return@forEachIndexed
            val top = base + p.y * progress
            val left = frame.px(x, g) - barWidth / 2f
            val rect = androidx.compose.ui.geometry.Rect(left, frame.py(top, g), left + barWidth, frame.py(base, g))
            val isTop = index == stack.lastIndex || stack.drop(index + 1).all { it.second.y <= 0 }
            val corner = if (isTop) CornerRadius(radius) else CornerRadius.Zero
            val alpha = if (selectedX == null || selectedX == x) 1f else 0.45f
            drawPath(
                Path().apply {
                    addRoundRect(RoundRect(rect, topLeft = corner, topRight = corner, bottomLeft = CornerRadius.Zero, bottomRight = CornerRadius.Zero))
                },
                s.color.copy(alpha = s.color.alpha * alpha),
            )
            base = top
        }
    }
}

private fun DrawScope.drawReferenceLine(
    line: ReferenceLine,
    background: Color,
    g: ChartGeometry,
    frame: Frame,
    measurer: TextMeasurer,
    style: TextStyle,
    yFormatter: (Double) -> String,
) {
    val y = frame.py(line.value, g)
    drawLine(
        line.color,
        Offset(frame.left, y),
        Offset(frame.right, y),
        strokeWidth = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
    )
    val label = measurer.measure("${line.label} ${yFormatter(line.value)}", style.copy(color = line.color))
    val pad = 4.dp.toPx()
    val topLeft = Offset(frame.right - label.size.width - pad, y - label.size.height - pad * 1.5f)
    drawRoundRect(
        background,
        topLeft = Offset(topLeft.x - pad, topLeft.y - pad / 2),
        size = Size(label.size.width + pad * 2, label.size.height + pad),
        cornerRadius = CornerRadius(pad * 2),
    )
    drawText(label, topLeft = topLeft)
}

private fun DrawScope.drawSelection(
    x: Double,
    series: List<ChartSeries>,
    g: ChartGeometry,
    frame: Frame,
    ring: Color,
    tooltipColor: Color,
    measurer: TextMeasurer,
    titleStyle: TextStyle,
    bodyStyle: TextStyle,
    xFormatter: (Double) -> String,
    yFormatter: (Double) -> String,
) {
    val px = frame.px(x, g)
    drawLine(tooltipColor.copy(alpha = 0.35f), Offset(px, frame.top), Offset(px, frame.bottom), strokeWidth = 1.dp.toPx())

    val tolerance = 18.dp.toPx()
    val hits = series.filter { it.showInTooltip }.mapNotNull { s ->
        s.points.minByOrNull { abs(it.x - x) }
            ?.takeIf { abs(frame.px(it.x, g) - px) <= tolerance }
            ?.let { s to it }
    }
    hits.filter { it.first.style != SeriesStyle.Bars }.forEach { (s, p) ->
        val c = Offset(frame.px(p.x, g), frame.py(p.y, g))
        drawCircle(ring, radius = 7.dp.toPx(), center = c)
        drawCircle(s.color, radius = 5.dp.toPx(), center = c)
    }
    if (hits.isEmpty()) return

    val title = measurer.measure(xFormatter(x), titleStyle)
    val lines = hits.map { (s, p) -> s.color to measurer.measure("${s.label}  ${yFormatter(p.y)}", bodyStyle) }
    val pad = 10.dp.toPx()
    val dot = 8.dp.toPx()
    val width = max(title.size.width.toFloat(), lines.maxOf { it.second.size.width + dot + 6.dp.toPx() }) + pad * 2
    val height = title.size.height + lines.sumOf { it.second.size.height } + pad * 2 + 4.dp.toPx()
    val left = (if (px + 12.dp.toPx() + width < size.width) px + 12.dp.toPx() else px - 12.dp.toPx() - width)
        .coerceIn(0f, size.width - width)
    val top = frame.top
    drawRoundRect(tooltipColor, Offset(left, top), Size(width, height), CornerRadius(12.dp.toPx()))
    drawText(title, topLeft = Offset(left + pad, top + pad))
    var y = top + pad + title.size.height + 4.dp.toPx()
    lines.forEach { (color, layout) ->
        drawCircle(color, radius = dot / 2f, center = Offset(left + pad + dot / 2f, y + layout.size.height / 2f))
        drawText(layout, topLeft = Offset(left + pad + dot + 6.dp.toPx(), y))
        y += layout.size.height
    }
}
