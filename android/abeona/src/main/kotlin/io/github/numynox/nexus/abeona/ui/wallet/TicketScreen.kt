package io.github.numynox.nexus.abeona.ui.wallet

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.abeona.AppContainer
import io.github.numynox.nexus.abeona.data.FareType
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.ui.AbeonaColors
import io.github.numynox.nexus.abeona.ui.AppViewModel
import io.github.numynox.nexus.abeona.ui.Navigator
import io.github.numynox.nexus.abeona.ui.common.DbNavigator
import io.github.numynox.nexus.abeona.ui.common.OfflinePill
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import io.github.numynox.nexus.abeona.ui.common.containerViewModel
import io.github.numynox.nexus.core.data.describe
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.components.LoadingState
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A ticket ready to show: its pages as images, for the trip it belongs to. */
data class TicketPages(val trip: Trip, val pages: List<Bitmap>, val offlineSince: Instant?)

/** Draws PDF pages, or decodes an image, at a width sharp enough to scan. */
object TicketRenderer {
    private const val WIDTH = 1440

    fun render(file: File): List<Bitmap> =
        if (file.name.endsWith(".pdf", ignoreCase = true)) renderPdf(file) else listOfNotNull(BitmapFactory.decodeFile(file.path))

    private fun renderPdf(file: File): List<Bitmap> =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { pdf ->
                (0 until pdf.pageCount.coerceAtMost(8)).map { index ->
                    pdf.openPage(index).use { page ->
                        val height = (WIDTH.toFloat() * page.height / page.width).toInt()
                        Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                            bitmap.eraseColor(AndroidColor.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }
}

class TicketViewModel(private val container: AppContainer, private val tripId: String) : ViewModel() {
    private val _ticket = MutableStateFlow<Loadable<TicketPages>>(Loadable.Loading)
    val ticket: StateFlow<Loadable<TicketPages>> = _ticket.asStateFlow()

    fun load() = viewModelScope.launch {
        _ticket.value = Loadable.Loading
        _ticket.value = try {
            val fetched = container.trips.trip(tripId)
            val trip = fetched.value ?: error("This trip no longer exists.")
            val file = container.trips.ticket(trip)
            val pages = withContext(Dispatchers.Default) { TicketRenderer.render(file) }
            if (pages.isEmpty()) error("This ticket could not be opened.")
            Loadable.Ready(TicketPages(trip, pages, fetched.offlineSince))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Loadable.Failed(e.describe())
        }
    }

    fun replace(uri: android.net.Uri, application: android.app.Application, onError: (String) -> Unit) {
        val trip = (_ticket.value as? Loadable.Ready)?.value?.trip ?: return
        viewModelScope.launch {
            runCatching {
                val resolver = application.contentResolver
                val name = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                } ?: "ticket.pdf"
                val bytes = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { it.readBytes() } } ?: error("The file could not be read.")
                val mime = resolver.getType(uri) ?: "application/pdf"
                container.trips.attachTicket(trip, name, mime, bytes)
            }.onSuccess { load() }.onFailure { onError(it.describe()) }
        }
    }
}

/**
 * The ticket at the gate: white, as bright as the screen goes, and the screen
 * kept on, so the conductor's scanner reads it first time.
 */
@Composable
fun TicketScreen(app: AppViewModel, tripId: String, navigator: Navigator) {
    val context = LocalContext.current
    val viewModel = containerViewModel(key = "ticket-$tripId") { TicketViewModel(it, tripId) }
    val ticket by viewModel.ticket.collectAsStateWithLifecycle()
    val replace = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.replace(uri, context.applicationContext as android.app.Application) { app.say(it) }
    }

    LaunchedEffect(tripId) { viewModel.load() }
    FullBrightness()

    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(top = 4.dp, start = 4.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = navigator.back) { Icon(Icons.Rounded.Close, contentDescription = "Close ticket", tint = AbeonaColors.Ink) }
                val trip = (ticket as? Loadable.Ready)?.value?.trip
                Column(Modifier.weight(1f)) {
                    Text(
                        trip?.displayTitle ?: "Ticket",
                        style = MaterialTheme.typography.titleMedium,
                        color = AbeonaColors.Ink,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    trip?.let {
                        Text(
                            "${TripFormat.day(it.departureAt)} · ${TripFormat.time(it.departureAt)} · ${if (it.travelClass == 1) "1st" else "2nd"} class",
                            style = MaterialTheme.typography.bodySmall,
                            color = AbeonaColors.Ink.copy(alpha = 0.7f),
                        )
                    }
                    (ticket as? Loadable.Ready)?.value?.offlineSince?.let { OfflinePill(it, Modifier.padding(top = 4.dp)) }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val state = ticket) {
                    Loadable.Loading -> LoadingState(Modifier.fillMaxSize())
                    is Loadable.Failed -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message, color = AbeonaColors.Ink)
                        TextButton(onClick = { viewModel.load() }) { Text("Try again") }
                    }
                    is Loadable.Ready -> ZoomablePages(state.value.pages)
                }
            }
            TicketFooter(
                fareType = (ticket as? Loadable.Ready)?.value?.trip?.fareType,
                onDbNavigator = { DbNavigator.open(context) },
                onReplace = { replace.launch(arrayOf("application/pdf", "image/png", "image/jpeg")) },
            )
        }
    }
}

/** Pinch to zoom, drag to move, double tap to go back to fit. */
@Composable
private fun ZoomablePages(pages: List<Bitmap>) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero }) }
            .transformable(transform)
            .verticalScroll(rememberScrollState(), enabled = scale == 1f)
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        pages.forEach { page ->
            Image(
                bitmap = page.asImageBitmap(),
                contentDescription = "Ticket page",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = offset.x; translationY = offset.y
                },
            )
        }
    }
}

@Composable
private fun TicketFooter(fareType: FareType?, onDbNavigator: () -> Unit, onReplace: () -> Unit) {
    val inApp = fareType == FareType.Deutschlandticket || fareType == FareType.BahnCard100
    Surface(color = AbeonaColors.Mint, contentColor = AbeonaColors.Ink, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Badge, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    if (inApp) {
                        "A Deutschlandticket or a digital BahnCard must be shown in DB Navigator; keep this copy for your records."
                    } else {
                        "DB accepts a ticket PDF shown on screen, together with an official photo ID. A Deutschlandticket or a digital BahnCard must be shown in DB Navigator."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onDbNavigator,
                    colors = ButtonDefaults.buttonColors(containerColor = AbeonaColors.Green, contentColor = Color.White),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Open DB Navigator", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = onReplace, colors = ButtonDefaults.outlinedButtonColors(contentColor = AbeonaColors.Ink)) {
                    Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Replace", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

/** The window at full brightness and kept awake while the ticket is shown; restored after. */
@Composable
private fun FullBrightness() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val previous = window.attributes.screenBrightness
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window.attributes = window.attributes.apply { screenBrightness = previous }
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
