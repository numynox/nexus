package io.github.numynox.nexus.abeona.ui.trip

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Picture
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import io.github.numynox.nexus.abeona.R
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.data.TripSummary
import io.github.numynox.nexus.abeona.ui.AbeonaType
import io.github.numynox.nexus.abeona.ui.brand
import io.github.numynox.nexus.abeona.ui.common.StatusTone
import io.github.numynox.nexus.abeona.ui.common.SurfaceCard
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import io.github.numynox.nexus.abeona.ui.live.sceneTrain
import io.github.numynox.nexus.abeona.ui.muted
import io.github.numynox.nexus.abeona.ui.ok
import io.github.numynox.nexus.abeona.ui.scene.Scene
import io.github.numynox.nexus.abeona.ui.scene.SceneSize
import io.github.numynox.nexus.abeona.ui.scene.SceneState
import io.github.numynox.nexus.abeona.ui.watch
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One figure of the postcard: its icon, value and small label; [tone] for the one that is a status (how it ended). */
internal data class PostcardFigure(val icon: ImageVector, val value: String, val label: String, val tone: StatusTone? = null)

/** Under six minutes late is on time, as DB counts it. */
private const val ON_TIME_MINUTES = 6

/**
 * The postcard's figures, from abeona_trip_summary: the distance, the time
 * on board, the CO₂ saved against the car, the top speed when the train's
 * Wi-Fi gave one, and how it ended. A figure that is nothing is left out;
 * the ending always says something ("On time", "+22 min").
 */
internal fun postcardFigures(summary: TripSummary): List<PostcardFigure> {
    val delay = summary.delayMinutes.roundToInt()
    val co2 = summary.co2SavedKg
    return listOfNotNull(
        summary.km.roundToInt().takeIf { it > 0 }?.let { PostcardFigure(Icons.Rounded.Route, "$it km", "travelled") },
        summary.minutesOnTrain.roundToLong().takeIf { it > 0 }?.let { PostcardFigure(Icons.Rounded.Train, TripFormat.shortDuration(it), "on board") },
        co2.takeIf { it >= 0.05 }?.let {
            PostcardFigure(Icons.Rounded.Eco, "${String.format(Locale.ENGLISH, if (it < 10) "%.1f" else "%.0f", it)} kg", "CO₂ saved")
        },
        summary.topSpeedKmh?.takeIf { it > 0 }?.let { PostcardFigure(Icons.Rounded.Speed, "$it km/h", "top speed") },
        if (delay < ON_TIME_MINUTES) {
            PostcardFigure(Icons.Rounded.Flag, "On time", "at the end", StatusTone.Ok)
        } else {
            PostcardFigure(Icons.Rounded.Flag, "+$delay min", "at the end", StatusTone.Watch)
        },
    )
}

/** "22 November 2026": a postcard is kept, so it has the year. */
private fun postcardDate(trip: Trip): String =
    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).withZone(java.time.ZoneId.systemDefault()).format(trip.departureAt)

private fun route(trip: Trip) = "${trip.originName} → ${trip.destinationName}"

/**
 * The figures in a grid: three to a row, two when the text is large, so
 * nothing is cut off; a value that still does not fit wraps. A short last
 * row keeps the columns.
 */
@Composable
internal fun PostcardFigures(figures: List<PostcardFigure>, modifier: Modifier = Modifier, narrow: Boolean = false) {
    val columns = if (LocalDensity.current.fontScale >= 1.5f) 2 else 3
    // The picture to share is narrower than a phone: a little less room between and a little smaller.
    val gap = if (narrow) 8.dp else 12.dp
    val style = if (narrow) AbeonaType.title.copy(fontSize = 16.sp) else AbeonaType.title
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        figures.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { figure -> Figure(figure, Modifier.weight(1f), style) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Figure(figure: PostcardFigure, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = AbeonaType.title) {
    val colour = when (figure.tone) {
        StatusTone.Ok -> ok.soft.content
        StatusTone.Watch -> watch.soft.content
        else -> Color.Unspecified
    }
    // One reading: "220 km travelled".
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(figure.icon, contentDescription = null, tint = if (figure.tone != null) colour else muted, modifier = Modifier.size(18.dp))
            Text(figure.value, style = style, color = colour)
        }
        Text(figure.label, style = AbeonaType.meta, color = muted)
    }
}

/**
 * The arrival postcard (moment 11, after the compensation): where from and
 * to, the day, and what the trip added up to; "Share" sends it as a picture
 * (PostcardImage), made ready while the card is on screen.
 */
@Composable
internal fun PostcardCard(trip: Trip, summary: TripSummary, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val file = rememberPostcardFile(trip, summary)
    SurfaceCard(modifier) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(route(trip), style = AbeonaType.title, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(postcardDate(trip), style = AbeonaType.meta, color = muted)
            }
            IconButton(onClick = { file?.let { sharePostcard(context, it) } }, enabled = file != null) {
                Icon(Icons.Rounded.Share, contentDescription = "Share postcard")
            }
        }
        PostcardFigures(postcardFigures(summary), Modifier.padding(top = 12.dp))
    }
}

/** The shared picture: 1080 × 1350 pixels, drawn at this density whatever the phone's. */
private const val SHARE_WIDTH = 1080
private const val SHARE_HEIGHT = 1350
private const val SHARE_DENSITY = 3f

/**
 * The postcard as a picture to share (1080 × 1350 at [SHARE_DENSITY]): the
 * arrival scene on top, the route and the day, the figures, and the
 * wordmark. In the app's theme; the text at its own size, not the phone's.
 */
@Composable
fun PostcardImage(trip: Trip, summary: TripSummary, modifier: Modifier = Modifier) {
    val last = trip.legs.lastOrNull { !it.isWalk }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Scene(
            SceneState.Arrived,
            size = SceneSize.Full,
            train = sceneTrain(last),
            at = last?.to?.expectedAt,
            lat = last?.to?.lat,
            lon = last?.to?.lon,
            still = true,
        )
        Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp, vertical = 16.dp)) {
            // Two lines of route and two rows of figures under the full scene: 450 high is just enough.
            Text(route(trip), style = AbeonaType.headline.copy(fontSize = 20.sp, lineHeight = 26.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(postcardDate(trip), style = AbeonaType.meta, color = muted, modifier = Modifier.padding(top = 2.dp))
            PostcardFigures(postcardFigures(summary), Modifier.padding(top = 12.dp), narrow = true)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(painterResource(R.drawable.ic_notification), contentDescription = null, tint = brand, modifier = Modifier.size(16.dp))
                Text("Abeona", style = AbeonaType.body.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp), color = brand)
            }
        }
    }
}

/**
 * The postcard's picture, drawn off screen once and written to the cache
 * (cacheDir/shared/, which FileProvider serves): ready before "Share" is
 * pressed. Null until then. Recorded as a Picture and drawn into a plain
 * bitmap: a software canvas works on every phone, and in the screenshot
 * tests, where a hardware layer cannot be read back.
 */
@Composable
private fun rememberPostcardFile(trip: Trip, summary: TripSummary): File? {
    val context = LocalContext.current
    var file by remember(trip.id, summary) { mutableStateOf<File?>(null) }
    if (file != null) return file
    val picture = remember(trip.id, summary) { Picture() }
    var drawn by remember(trip.id, summary) { mutableStateOf(false) }
    val size = with(LocalDensity.current) { DpSize(SHARE_WIDTH.toDp(), SHARE_HEIGHT.toDp()) }
    // A box of no size holds it: laid out and recorded, never drawn onto the screen.
    Box(Modifier.size(0.dp)) {
        Box(
            Modifier
                .requiredSize(size)
                .drawWithCache {
                    val width = this.size.width.toInt()
                    val height = this.size.height.toInt()
                    onDrawWithContent {
                        val canvas = androidx.compose.ui.graphics.Canvas(picture.beginRecording(width, height))
                        draw(this, layoutDirection, canvas, this.size) { this@onDrawWithContent.drawContent() }
                        picture.endRecording()
                        if (!drawn) drawn = true
                    }
                },
        ) {
            CompositionLocalProvider(LocalDensity provides Density(SHARE_DENSITY, fontScale = 1f)) { PostcardImage(trip, summary) }
        }
    }
    LaunchedEffect(drawn) {
        if (!drawn) return@LaunchedEffect
        file = runCatching {
            withContext(Dispatchers.IO) {
                val bitmap = Bitmap.createBitmap(SHARE_WIDTH, SHARE_HEIGHT, Bitmap.Config.ARGB_8888)
                android.graphics.Canvas(bitmap).drawPicture(picture)
                writePostcard(context, trip, bitmap)
            }
        }.getOrNull()
    }
    return null
}

private fun writePostcard(context: Context, trip: Trip, bitmap: Bitmap): File {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    return File(dir, "abeona-postcard-${trip.id.take(8)}.png").apply {
        outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

/** The picture to another app, through the share sheet. */
private fun sharePostcard(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(null, uri)
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}
