package io.github.numynox.nexus.abeona.ui.bahncards

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.numynox.nexus.abeona.data.BahnCard
import io.github.numynox.nexus.abeona.data.BahnCardInput
import io.github.numynox.nexus.abeona.ui.AbeonaType
import io.github.numynox.nexus.abeona.ui.bad
import io.github.numynox.nexus.abeona.ui.muted
import io.github.numynox.nexus.abeona.ui.well
import io.github.numynox.nexus.abeona.ui.common.PickedFile
import io.github.numynox.nexus.abeona.ui.common.PrimaryButton
import io.github.numynox.nexus.abeona.ui.common.Segmented
import io.github.numynox.nexus.abeona.ui.common.StatusChip
import io.github.numynox.nexus.abeona.ui.common.StatusTone
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import io.github.numynox.nexus.abeona.ui.common.WellField
import io.github.numynox.nexus.abeona.ui.common.FieldRow
import io.github.numynox.nexus.core.ui.theme.tabular
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The card as an ink chip: the card icon and its number ("25"), squared like
 * a line chip. The number is the word; a screen reader hears "BahnCard 25".
 */
@Composable
fun BahnCardBadge(kind: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.onSurface,
        contentColor = scheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.clearAndSetSemantics { contentDescription = "BahnCard $kind" },
    ) {
        Row(Modifier.height(28.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Rounded.CreditCard, contentDescription = null, modifier = Modifier.size(14.dp))
            Text("$kind", style = AbeonaType.meta.copy(fontWeight = FontWeight.Bold))
        }
    }
}

/** Within this many days of the cancellation deadline the renewal chip turns watch. */
private const val DEADLINE_WATCH_DAYS = 14L

/**
 * One card in the household's list, one row: the ink chip, its name, class
 * and validity in meta, the renewal deadline as a chip, then show and edit.
 * (DB Navigator cannot open one card, so there is no button for it here.)
 */
@Composable
fun BahnCardRow(
    card: BahnCard,
    onShow: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    val expired = card.to.isBefore(today)
    val named = !card.name.isNullOrBlank()
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).alpha(if (expired) 0.6f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BahnCardBadge(card.kind)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (named) card.displayName else "BahnCard ${card.kind}",
                style = AbeonaType.body.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(
                    if (card.travelClass == 1) "1st class" else "2nd class",
                    when {
                        expired -> "expired ${TripFormat.date(card.to)}"
                        card.from.isAfter(today) -> "from ${TripFormat.date(card.from)}"
                        else -> "until ${TripFormat.date(card.to)}"
                    },
                ).joinToString(" · "),
                style = AbeonaType.meta,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (card.subscription && !expired) {
                val near = !card.deadline.isAfter(today.plusDays(DEADLINE_WATCH_DAYS))
                StatusChip(
                    if (near) StatusTone.Watch else StatusTone.Quiet,
                    Icons.Rounded.Event,
                    label = TripFormat.date(card.deadline),
                    description = "Renews unless cancelled by ${TripFormat.date(card.deadline)}",
                )
            }
        }
        if (card.filePath != null) {
            IconButton(onClick = onShow) { Icon(Icons.Rounded.Fullscreen, contentDescription = "Show ${card.displayName}", tint = muted) }
        }
        IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = "Edit ${card.displayName}", tint = muted) }
    }
}

/** Adding or editing a card: name, kind, class, validity, subscription and its notice period, and the file. */
@Composable
fun BahnCardSheet(
    card: BahnCard?,
    onSave: (input: BahnCardInput, file: Uri?, removeFile: Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    var name by remember { mutableStateOf(card?.name.orEmpty()) }
    var kind by remember { mutableStateOf(card?.kind ?: 25) }
    var travelClass by remember { mutableStateOf(card?.travelClass ?: 2) }
    var from by remember { mutableStateOf(card?.from ?: today) }
    // A BahnCard runs for a year: today until the day before next year's date.
    var to by remember { mutableStateOf(card?.to ?: today.plusYears(1).minusDays(1)) }
    var number by remember { mutableStateOf(card?.cardNumber.orEmpty()) }
    var subscription by remember { mutableStateOf(card?.subscription ?: true) }
    var notice by remember { mutableStateOf(card?.noticeWeeks ?: 4) }
    var file by remember { mutableStateOf<Uri?>(null) }
    var removeFile by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { file = uri; removeFile = false }
    }
    val existing = card?.fileName?.takeIf { !removeFile }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 16.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(if (card == null) "Add a BahnCard" else "Edit BahnCard", style = AbeonaType.headline, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            FieldRow("Name", info = "Whose card it is, or what it is for. Empty shows the kind.") {
                WellField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    placeholder = "BahnCard $kind",
                    description = "Name",
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(180.dp),
                )
            }
            FieldRow("Card") { Segmented(listOf(25, 50, 100), kind, { kind = it }, { "$it" }, description = "Card") }
            FieldRow("Class") { Segmented(listOf(1, 2), travelClass, { travelClass = it }, { if (it == 1) "1st" else "2nd" }, description = "Class") }
            FieldRow("Valid from") { DateButton(from, "Valid from") { picking = "from" } }
            FieldRow("Valid until") { DateButton(to, "Valid until") { picking = "to" } }
            FieldRow("Card number") {
                WellField(
                    value = number,
                    onValueChange = { number = it.take(40) },
                    placeholder = "optional",
                    description = "Card number",
                    keyboard = KeyboardType.Number,
                    textAlign = TextAlign.End,
                    style = AbeonaType.body.tabular(),
                    modifier = Modifier.width(200.dp),
                )
            }
            FieldRow(
                "Renews automatically",
                info = "A subscription runs on unless it is cancelled in time. Turn this off once it is cancelled.",
                toggle = subscription,
                onToggle = { subscription = it },
            ) { Switch(checked = subscription, onCheckedChange = null) }
            if (subscription) {
                FieldRow("Notice period") {
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { notice = (notice - 1).coerceAtLeast(0) }, enabled = notice > 0) {
                                Icon(Icons.Rounded.Remove, contentDescription = "One week less")
                            }
                            Text("$notice wk", style = AbeonaType.body.copy(fontWeight = FontWeight.SemiBold).tabular())
                            IconButton(onClick = { notice = (notice + 1).coerceAtMost(26) }, enabled = notice < 26) {
                                Icon(Icons.Rounded.Add, contentDescription = "One week more")
                            }
                        }
                    }
                }
                Text(
                    "Cancel by ${TripFormat.date(to.minusWeeks(notice.toLong()))}",
                    style = AbeonaType.meta,
                    color = muted,
                    modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                    textAlign = TextAlign.End,
                )
            }
            if (file != null || existing != null) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.Description, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
                    Text(
                        if (file != null) "New file chosen" else existing.orEmpty(),
                        style = AbeonaType.body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { file = null; removeFile = true }) { Icon(Icons.Rounded.Delete, contentDescription = "Remove file", tint = muted) }
                }
            } else {
                TextButton(onClick = { pick.launch(PickedFile.TYPES) }) {
                    Icon(Icons.Rounded.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Add the PDF or a screenshot", modifier = Modifier.padding(start = 8.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = bad.soft.content, modifier = Modifier.size(18.dp))
                        Text("Delete", color = bad.soft.content, modifier = Modifier.padding(start = 6.dp))
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
                PrimaryButton(
                    "Save card",
                    onClick = {
                        saving = true
                        onSave(
                            BahnCardInput(name.trim().ifEmpty { null }, kind, travelClass, from, to, number, subscription, notice),
                            file,
                            removeFile,
                        )
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    enabled = !saving && !to.isBefore(from),
                )
            }
        }
    }

    picking?.let { which ->
        val initial = if (which == "from") from else to
        val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        if (which == "from") {
                            from = day
                            // Moving the start moves a fresh card's year with it.
                            if (card == null) to = day.plusYears(1).minusDays(1)
                        } else {
                            to = day
                        }
                    }
                    picking = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

/** A date in the well, opening the date picker. */
@Composable
private fun DateButton(date: LocalDate, description: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = well.container, contentColor = well.content, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.heightIn(min = 40.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(TripFormat.date(date), style = AbeonaType.body.tabular(), modifier = Modifier.semantics { contentDescription = "$description ${TripFormat.date(date)}" })
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = muted, modifier = Modifier.size(18.dp))
        }
    }
}
