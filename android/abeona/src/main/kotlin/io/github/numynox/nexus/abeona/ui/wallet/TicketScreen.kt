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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material3.FilterChip
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
import io.github.numynox.nexus.abeona.domain.intake.seatText
import io.github.numynox.nexus.abeona.ui.common.SeatFact
import io.github.numynox.nexus.abeona.data.FareType
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.domain.TicketNeeds
import io.github.numynox.nexus.abeona.ui.AbeonaColors
import io.github.numynox.nexus.abeona.ui.AbeonaType
import androidx.compose.ui.text.font.FontWeight
import io.github.numynox.nexus.abeona.ui.AppViewModel
import io.github.numynox.nexus.abeona.ui.Navigator
import io.github.numynox.nexus.abeona.ui.common.DbNavigator
import io.github.numynox.nexus.abeona.ui.common.OfflinePill
import io.github.numynox.nexus.abeona.ui.common.PickedFile
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

/**
 * A ticket ready to show: its pages as images and, when the PDF embeds one,
 * its barcode on its own, for the trip it belongs to.
 */
data class TicketPages(val trip: Trip, val pages: List<Bitmap>, val code: Bitmap?, val offlineSince: Instant?)

/** Something else to show at the gate with the ticket: a traveller's BahnCard, its file as pages and its code. */
data class TicketDocument(val key: String, val label: String, val pages: List<Bitmap>, val code: Bitmap?)

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

class TicketViewModel(
    private val container: AppContainer,
    private val tripId: String,
    private val context: android.content.Context,
) : ViewModel() {
    private val _ticket = MutableStateFlow<Loadable<TicketPages>>(Loadable.Loading)
    val ticket: StateFlow<Loadable<TicketPages>> = _ticket.asStateFlow()

    /** The household's BahnCards the ticket needs, those with a file, loaded after the ticket. */
    private val _cards = MutableStateFlow<List<TicketDocument>>(emptyList())
    val cards: StateFlow<List<TicketDocument>> = _cards.asStateFlow()

    /**
     * The cards the ticket was booked with (TicketNeeds.cardsForTrip): one
     * per BahnCard on the trip, valid on the day; a BahnCard 100 fare one per
     * traveller. A card that fails to load is left out; the ticket stands on
     * its own.
     */
    private suspend fun loadCards(trip: Trip) {
        val cards = runCatching { container.trips.bahnCards().value }.getOrDefault(emptyList())
        if (cards.none { it.filePath != null }) return
        val day = trip.departureAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val ids = TicketNeeds.cardsForTrip(
            cards.map { TicketNeeds.CardOnFile(it.id, it.kind, it.from, it.to, it.filePath != null) },
            trip.bahncardDiscounts,
            trip.travellerCount,
            trip.fareType == FareType.BahnCard100,
            day,
        )
        val chosen = ids.mapNotNull { id -> cards.firstOrNull { it.id == id } }
        _cards.value = chosen.mapNotNull { card ->
            runCatching {
                val file = container.trips.bahnCardFile(card)
                val (pages, code) = withContext(Dispatchers.Default) { TicketRenderer.render(file) to TicketCode.extract(context, file) }
                TicketDocument(card.id, listOfNotNull(card.name?.takeIf { it.isNotBlank() }, "BahnCard ${card.kind}").joinToString(" · "), pages, code)
            }.getOrNull()?.takeIf { it.pages.isNotEmpty() }
        }
    }

    fun load() = viewModelScope.launch {
        _ticket.value = Loadable.Loading
        _ticket.value = try {
            val fetched = container.trips.trip(tripId)
            val trip = fetched.value ?: error("This trip no longer exists.")
            val file = container.trips.ticket(trip)
            val (pages, code) = withContext(Dispatchers.Default) { TicketRenderer.render(file) to TicketCode.extract(context, file) }
            if (pages.isEmpty()) error("This ticket could not be opened.")
            viewModelScope.launch { loadCards(trip) }
            Loadable.Ready(TicketPages(trip, pages, code, fetched.offlineSince))
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
                val file = PickedFile.read(application, uri, "ticket.pdf")
                container.trips.attachTicket(trip, file.name, file.mimeType, file.bytes)
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
    val viewModel = containerViewModel(key = "ticket-$tripId") { TicketViewModel(it, tripId, context.applicationContext) }
    val ticket by viewModel.ticket.collectAsStateWithLifecycle()
    val replace = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.replace(uri, context.applicationContext as android.app.Application) { app.say(it) }
    }

    val cards by viewModel.cards.collectAsStateWithLifecycle()
    // The ticket first, then the BahnCards to show with it, one tap apart.
    val documents = (ticket as? Loadable.Ready)?.value?.let { listOf(TicketDocument(TICKET, "Ticket", it.pages, it.code)) + cards }.orEmpty()
    var shown by rememberSaveable { mutableStateOf(TICKET) }

    LaunchedEffect(tripId) { viewModel.load() }
    FullBrightness()

    val trip = (ticket as? Loadable.Ready)?.value?.trip
    val header: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(top = 4.dp, start = 4.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = navigator.back) { Icon(Icons.Rounded.Close, contentDescription = "Close ticket", tint = AbeonaColors.Ink) }
            Column(Modifier.weight(1f)) {
                Text(
                    trip?.displayTitle ?: "Ticket",
                    style = AbeonaType.title,
                    color = AbeonaColors.Ink,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                trip?.let {
                    Text(
                        "${TripFormat.day(it.departureAt)} · ${TripFormat.time(it.departureAt)} · ${if (it.travelClass == 1) "1st" else "2nd"} class",
                        style = AbeonaType.meta,
                        color = AbeonaColors.Muted,
                    )
                }
                (ticket as? Loadable.Ready)?.value?.offlineSince?.let { OfflinePill(it, Modifier.padding(top = 4.dp)) }
            }
            // Some proofs are only valid in DB's app: it is always one tap from the ticket.
            IconButton(onClick = { DbNavigator.open(context) }) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Open DB Navigator", tint = AbeonaColors.Ink)
            }
            IconButton(onClick = { replace.launch(PickedFile.TYPES) }) {
                Icon(Icons.Rounded.SwapHoriz, contentDescription = "Replace ticket", tint = AbeonaColors.Ink)
            }
        }
    }
    val tabs: @Composable () -> Unit = {
        if (documents.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                documents.forEach { document ->
                    FilterChip(
                        selected = document.key == shown,
                        onClick = { shown = document.key },
                        label = { Text(document.label) },
                        leadingIcon = {
                            Icon(
                                if (document.key == TICKET) Icons.Rounded.ConfirmationNumber else Icons.Rounded.Badge,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
        }
    }
    val document: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier) {
            when (val state = ticket) {
                Loadable.Loading -> LoadingState(Modifier.fillMaxSize())
                is Loadable.Failed -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.message, color = AbeonaColors.Ink)
                    TextButton(onClick = { viewModel.load() }) { Text("Try again") }
                }
                is Loadable.Ready -> {
                    val shownDocument = documents.firstOrNull { it.key == shown } ?: documents.first()
                    DocumentView(shownDocument, description = if (shownDocument.key == TICKET) "Ticket page" else "BahnCard")
                }
            }
        }
    }

    // White in both themes; on its side, clear of the cutout and the bars at the sides.
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        if (maxWidth > maxHeight) {
            // On its side: the code as large as it goes on the left, the rides and their seats on the right.
            Row(Modifier.fillMaxSize()) {
                document(Modifier.weight(1.2f).fillMaxHeight().statusBarsPadding())
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    header()
                    tabs()
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                        trip?.let { TicketRides(it, all = true) }
                    }
                    TicketFooter(trip?.fareType)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                header()
                tabs()
                // Upright, the code needs the room: only the rides with a seat, one line each.
                trip?.let { TicketRides(it, all = false, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                document(Modifier.weight(1f).fillMaxWidth())
                TicketFooter(trip?.fareType)
            }
        }
    }
}

/**
 * The ticket's rides, for the conductor and the seat: with [all], each ride's
 * train, where from and to and when, and its coach and seats; otherwise only
 * the rides with a seat, the train and the seat on one line. The screen is
 * white in both themes, so the light tokens.
 */
@Composable
private fun TicketRides(trip: Trip, all: Boolean, modifier: Modifier = Modifier) {
    val rides = trip.legs.filter { !it.isWalk && (all || seatText(it.coach, it.seats) != null) }
    if (rides.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (all) 12.dp else 4.dp)) {
        rides.forEach { ride ->
            if (all) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${TripFormat.time(ride.from.plannedAt)}  ${ride.label}",
                        style = AbeonaType.body.copy(fontWeight = FontWeight.SemiBold),
                        color = AbeonaColors.Ink,
                    )
                    Text(
                        "${ride.from.name} → ${ride.to.name} · ${TripFormat.time(ride.to.plannedAt)}",
                        style = AbeonaType.meta,
                        color = AbeonaColors.Muted,
                    )
                    SeatFact(ride.coach, ride.seats, color = AbeonaColors.Ink, style = AbeonaType.body)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(ride.label, style = AbeonaType.meta.copy(fontWeight = FontWeight.SemiBold), color = AbeonaColors.Ink)
                    SeatFact(ride.coach, ride.seats, color = AbeonaColors.Ink)
                }
            }
        }
    }
}

/**
 * Pinch to zoom around the fingers, drag to move while zoomed, double tap to
 * zoom in there or back out. One transform for all pages, kept inside the
 * content, so zooming out always ends back at the fit. At fit size the pages
 * scroll as usual.
 */
@Composable
internal fun ZoomablePages(pages: List<Bitmap>, description: String = "Ticket page") {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    // Content point p is drawn at p * scale + offset (origin top left), so the
    // offset may range from size - size * scale up to zero.
    fun clamp(value: Offset, at: Float) = Offset(
        value.x.coerceIn(size.width * (1 - at), 0f),
        value.y.coerceIn(size.height * (1 - at), 0f),
    )
    fun zoomTo(target: Float, focus: Offset) {
        val next = target.coerceIn(1f, 5f)
        offset = clamp(focus - (focus - offset) * (next / scale), next)
        scale = next
    }

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        // Initial pass: ahead of the page scroll, which only
                        // gets the gesture when this leaves it alone.
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2 || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (zoom != 1f) zoomTo(scale * zoom, event.calculateCentroid(useCurrent = true))
                            if (pan != Offset.Zero && scale > 1f) offset = clamp(offset + pan, scale)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { at -> if (scale > 1f) zoomTo(1f, at) else zoomTo(2.5f, at) })
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = scale; scaleY = scale
                    translationX = offset.x; translationY = offset.y
                }
                .verticalScroll(rememberScrollState(), enabled = scale == 1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            pages.forEach { page ->
                Image(
                    bitmap = page.asImageBitmap(),
                    contentDescription = description,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private const val TICKET = "ticket"

/**
 * A ticket or a BahnCard at the gate: its code first, when the file has one,
 * the whole file on request; and back.
 */
@Composable
internal fun DocumentView(document: TicketDocument, description: String) {
    var showPdf by rememberSaveable(document.key) { mutableStateOf(document.code == null) }
    val code = document.code
    Box(Modifier.fillMaxSize()) {
        if (code != null && !showPdf) {
            TicketCodeView(code, onShowPdf = { showPdf = true })
        } else {
            ZoomablePages(document.pages, description = description)
            if (code != null) {
                FilledTonalButton(
                    onClick = { showPdf = false },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                ) {
                    Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Show code", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/**
 * The ticket's barcode alone, as large as the screen allows, with the white
 * margin scanners need. Drawn without smoothing, so every module stays sharp.
 */
@Composable
private fun TicketCodeView(code: Bitmap, onShowPdf: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            bitmap = code.asImageBitmap(),
            contentDescription = "Ticket barcode",
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.None,
            modifier = Modifier.fillMaxWidth().aspectRatio(code.width.toFloat() / code.height).background(Color.White).padding(16.dp),
        )
        OutlinedButton(
            onClick = onShowPdf,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AbeonaColors.Ink),
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Show PDF", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** What counts as proof, in words: it has legal weight. The screen is white in both themes, so the light tokens. */
@Composable
private fun TicketFooter(fareType: FareType?) {
    val inApp = fareType == FareType.Deutschlandticket || fareType == FareType.BahnCard100
    Surface(color = AbeonaColors.Well, contentColor = AbeonaColors.Ink, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Badge, contentDescription = null, tint = AbeonaColors.Muted, modifier = Modifier.size(20.dp))
            Text(
                if (inApp) {
                    "A Deutschlandticket or a digital BahnCard must be shown in DB Navigator; keep this copy for your records."
                } else {
                    "DB accepts a ticket PDF shown on screen, together with an official photo ID. A Deutschlandticket or a digital BahnCard must be shown in DB Navigator."
                },
                style = AbeonaType.meta.copy(fontWeight = FontWeight.Normal),
            )
        }
    }
}

/** The window at full brightness and kept awake while the ticket is shown; restored after. */
@Composable
internal fun FullBrightness() {
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
