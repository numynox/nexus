package io.github.numynox.nexus.vibilia.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.numynox.nexus.core.ui.NexusFormat
import io.github.numynox.nexus.core.ui.components.AnimatedNumberText
import io.github.numynox.nexus.core.ui.components.NexusCard
import io.github.numynox.nexus.core.ui.components.PlaceholderBlock
import io.github.numynox.nexus.core.ui.components.PriceText
import io.github.numynox.nexus.core.ui.theme.NexusThemeDefaults
import io.github.numynox.nexus.core.ui.theme.tabular
import io.github.numynox.nexus.vibilia.data.Car
import io.github.numynox.nexus.vibilia.data.FuelLevel
import io.github.numynox.nexus.vibilia.data.LastDrive
import io.github.numynox.nexus.vibilia.data.StationPrice
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay

/** A German number plate in miniature: white, black rim, the blue EU strip. */
@Composable
fun PlateBadge(plate: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(22.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White)
            .border(1.5.dp, Color(0xFF1B1B1B), RoundedCornerShape(4.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(9.dp)
                .fillMaxHeight()
                .background(Color(0xFF1F4BA5)),
        )
        Text(
            plate.uppercase(),
            color = Color(0xFF1B1B1B),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            // Its own line height, not the caller's: inside a 28 sp title the
            // label otherwise sits low in the 22 dp plate.
            lineHeight = 12.sp,
            style = LocalTextStyle.current.copy(
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
            modifier = Modifier.padding(horizontal = 6.dp),
            maxLines = 1,
        )
    }
}

/**
 * The selected vehicle as a screen title; with more than one car it opens a
 * menu to switch. The selection is shared by every screen.
 */
@Composable
fun CarSwitcher(cars: List<Car>, selected: Car?, onSelect: (Car) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.medium)
                .clickable(enabled = cars.size > 1) { open = true }
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column {
                Text(
                    selected?.name ?: "No vehicle",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                selected?.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            selected?.plate?.takeIf { it.isNotBlank() }?.let { PlateBadge(it) }
            if (cars.size > 1) {
                val rotation by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = "Switch vehicle",
                    modifier = Modifier.graphicsLayer { rotationZ = rotation },
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
            cars.forEach { car ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(car.name, style = MaterialTheme.typography.bodyLarge)
                            car.plate?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    onClick = {
                        open = false
                        onSelect(car)
                    },
                )
            }
        }
    }
}

/** One quiet line under the gauge: when the car was last driven, for how long and roughly how far. */
@Composable
fun LastDriveLine(drive: LastDrive, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            listOfNotNull(
                "Last drive ${NexusFormat.dateTime(drive.startedAt)}",
                NexusFormat.duration(drive.hours),
                drive.estimatedKm?.let { NexusFormat.km(it) },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Re-composes every [periodMillis], for anything showing elapsed time. */
@Composable
fun rememberNow(periodMillis: Long = 30_000): Instant {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMillis) {
        while (true) {
            delay(periodMillis)
            now = System.currentTimeMillis()
        }
    }
    return Instant.ofEpochMilli(now)
}

/** "Driving · Golf · 24 min", with a gently pulsing dot, while a tracked drive is open. */
@Composable
fun LiveDriveBanner(visible: Boolean, carName: String, startedAt: Instant?, modifier: Modifier = Modifier) {
    val now = rememberNow(15_000)
    AnimatedVisibility(visible = visible, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut(), modifier = modifier) {
        val pulse = rememberInfiniteTransition(label = "pulse")
        val scale by pulse.animateFloat(0.7f, 1.15f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "scale")
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = CircleShape,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .scale(scale)
                        .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                )
                Icon(Icons.Rounded.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                val elapsed = startedAt?.let { NexusFormat.duration(Duration.between(it, now).toMillis() / 3_600_000.0) }
                Text(
                    listOfNotNull("Driving", carName, elapsed).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/**
 * How full the tank is: an arc from E to F that sweeps to its level, and
 * beside it what follows — litres left, range, the cost of filling up at
 * [cheapest] (our cheapest station now, after discounts), and when it runs dry.
 */
@Composable
fun FuelGaugeCard(level: FuelLevel, carName: String, cheapest: StationPrice?, modifier: Modifier = Modifier) {
    val extended = NexusThemeDefaults.extendedColors
    val ratio = level.fillRatio
    val targetColor = when {
        ratio < 0.15 -> MaterialTheme.colorScheme.error
        ratio < 0.3 -> extended.warning
        else -> extended.success
    }
    val color by animateColorAsState(targetColor, label = "gauge colour")
    val sweep by animateFloatAsState(
        targetValue = ratio.toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessVeryLow),
        label = "gauge",
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val now = rememberNow(60_000)
    val zone = ZoneId.systemDefault()

    NexusCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.size(width = 120.dp, height = 76.dp), contentAlignment = Alignment.BottomCenter) {
                // A half circle: the arc's bounding box is twice as tall as the canvas.
                Canvas(Modifier.size(width = 120.dp, height = 66.dp).align(Alignment.TopCenter)) {
                    val stroke = 12.dp.toPx()
                    val arcSize = Size(size.width - stroke, (size.width - stroke))
                    val topLeft = Offset(stroke / 2, stroke / 2)
                    drawArc(track, 180f, 180f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    if (sweep > 0.005f) {
                        drawArc(color, 180f, 180f * sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedNumberText(
                        value = ratio * 100,
                        format = { "${it.toInt()}%" },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Row(Modifier.widthIn(min = 96.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("E", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("F", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // Four facts beside the gauge, in the height it takes anyway: what
            // is left, how far it goes, what filling up costs, when it runs dry.
            val price = cheapest?.effectivePrice
            val facts = listOfNotNull(
                Fact(carName, "${NexusFormat.number(level.remainingLiters)} of ${NexusFormat.liters(level.tankCapacity, 0)}"),
                level.rangeKm?.let { Fact("Range", NexusFormat.km(it), color) },
                Fact("Fill up", price?.let { NexusFormat.euro(level.litersToFull * it) } ?: NexusFormat.liters(level.litersToFull, 0)),
                level.emptyAt(now)?.let { Fact("Empty", NexusFormat.weekdayDay(it.atZone(zone).toLocalDate())) },
            ).take(4)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                facts.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { FactCell(it, Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private data class Fact(val label: String, val value: String, val color: Color = Color.Unspecified)

@Composable
private fun FactCell(fact: Fact, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(fact.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(fact.value, style = MaterialTheme.typography.titleMedium.tabular(), color = fact.color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The fuel card's place while its level loads: the gauge and its four facts
 * as dim shapes, the same size as the card that replaces them, so the chart
 * below does not jump when it arrives.
 */
@Composable
fun FuelGaugePlaceholder(modifier: Modifier = Modifier) {
    NexusCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            PlaceholderBlock(width = 120.dp, height = 76.dp, shape = MaterialTheme.shapes.large)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(2) {
                            Column(Modifier.weight(1f).height(40.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                                PlaceholderBlock(width = 48.dp, height = 10.dp)
                                PlaceholderBlock(Modifier.fillMaxWidth(0.8f), height = 16.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The fuel gauge in miniature — E, a bar, F and the litres — like the web
 * app's badge, for places without room for the full card.
 */
@Composable
fun FuelLevelPill(level: FuelLevel, modifier: Modifier = Modifier) {
    val extended = NexusThemeDefaults.extendedColors
    val ratio = level.fillRatio.toFloat()
    val color = when {
        ratio < 0.15f -> MaterialTheme.colorScheme.error
        ratio < 0.3f -> extended.warning
        else -> extended.success
    }
    val fill by animateFloatAsState(ratio, spring(stiffness = Spring.StiffnessVeryLow), label = "pill gauge")
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("E", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(
                Modifier
                    .size(width = 48.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(color, CircleShape))
            }
            Text("F", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(NexusFormat.liters(level.remainingLiters, 0), style = MaterialTheme.typography.labelMedium.tabular())
        }
    }
}

/**
 * A station with its price as stations print it. The cheapest one is
 * highlighted; the others say what 50 litres would cost extra, as on the web.
 */
@Composable
fun StationCard(
    station: StationPrice,
    lowestPrice: Double?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    expandable: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val price = station.effectivePrice
    val cheapest = price != null && lowestPrice != null && kotlin.math.abs(price - lowestPrice) < 1e-6
    val container by animateColorAsState(
        when {
            selected -> MaterialTheme.colorScheme.secondaryContainer
            // Faint enough for the red "Cheapest" and its price to read on it.
            cheapest -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "station",
    )
    NexusCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = container,
        onClick = { if (expandable) expanded = !expanded; onClick?.invoke() },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        station.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (station.displayPlace.isNotBlank()) {
                        Text(
                            "· ${station.displayPlace}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    if (station.wholeDay) Tag("24 h", MaterialTheme.colorScheme.tertiary)
                    if (station.discount > 0) Tag("−${(station.discount * 100).toInt()} ct", MaterialTheme.colorScheme.primary)
                    station.isOpen?.let { open ->
                        Tag(if (open) "Open" else "Closed", if (open) NexusThemeDefaults.extendedColors.success else MaterialTheme.colorScheme.error)
                    }
                    station.distanceKm?.let {
                        Text("${NexusFormat.number(it, 1)} km", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        station.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (price != null) {
                    PriceText(
                        price,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (cheapest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    if (!cheapest && lowestPrice != null) {
                        Text(
                            "+${NexusFormat.euro((price - lowestPrice) * 50)} / 50 L",
                            style = MaterialTheme.typography.labelSmall.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (cheapest) {
                        Text("Cheapest", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    Text("No price", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (expandable) {
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Opening hours", style = MaterialTheme.typography.labelLarge)
                    }
                    when {
                        station.wholeDay -> Text("Open 24 hours", style = MaterialTheme.typography.bodyMedium)
                        station.openingTimes.isEmpty() -> Text(
                            "No opening hours available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> station.openingTimes.forEach { time ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(time.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(time.range, style = MaterialTheme.typography.bodyMedium.tabular())
                            }
                        }
                    }
                    station.checkedAt?.let {
                        Text(
                            "Price checked ${NexusFormat.time(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Tag(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** "Add one in Settings" — what every vehicle screen says without a vehicle. */
@Composable
fun NoVehicleAction(onOpenSettings: (() -> Unit)?) {
    if (onOpenSettings != null) TextButton(onClick = onOpenSettings) { Text("Open settings") }
}
