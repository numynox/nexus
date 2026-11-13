package io.github.numynox.nexus.abeona.ui.bahncards

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.numynox.nexus.abeona.data.BahnCard
import io.github.numynox.nexus.abeona.data.BahnCardInput
import io.github.numynox.nexus.abeona.ui.AbeonaColors
import io.github.numynox.nexus.abeona.ui.common.PickedFile
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import io.github.numynox.nexus.core.ui.components.ConnectedChoiceRow
import io.github.numynox.nexus.core.ui.settings.SettingSwitch
import io.github.numynox.nexus.core.ui.theme.tabular
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The card itself in miniature: the DB-green rectangle with its number, as on the plastic. */
@Composable
fun BahnCardBadge(kind: Int, modifier: Modifier = Modifier) {
    Surface(color = AbeonaColors.Green, contentColor = Color.White, shape = RoundedCornerShape(8.dp), modifier = modifier.size(width = 48.dp, height = 34.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text("$kind", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        }
    }
}

/**
 * One card in the household's list: its name, what it is, until when, when to cancel it,
 * and the two ways to show it — the stored copy, and DB Navigator next to it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BahnCardRow(
    card: BahnCard,
    onShow: () -> Unit,
    onEdit: () -> Unit,
    onDbNavigator: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    val expired = card.to.isBefore(today)
    Column(Modifier.fillMaxWidth().alpha(if (expired) 0.6f else 1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BahnCardBadge(card.kind)
            Column(Modifier.weight(1f)) {
                val named = !card.name.isNullOrBlank()
                Text(if (named) card.displayName else card.label, style = MaterialTheme.typography.titleSmall)
                if (named) {
                    Text(card.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    when {
                        expired -> "Expired ${TripFormat.date(card.to)}"
                        card.from.isAfter(today) -> "Valid from ${TripFormat.date(card.from)}"
                        else -> "Valid until ${TripFormat.date(card.to)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (card.subscription && !expired) {
                    Text(
                        "Renews unless cancelled by ${TripFormat.date(card.deadline)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = "Edit ${card.displayName}") }
        }
        // Wraps rather than cutting "Open in DB Navigator" short on a narrow phone.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (card.filePath != null) {
                FilledTonalButton(onClick = onShow) {
                    Icon(Icons.Rounded.Fullscreen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Show", modifier = Modifier.padding(start = 6.dp))
                }
            }
            OutlinedButton(onClick = onDbNavigator) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Open in DB Navigator", modifier = Modifier.padding(start = 6.dp), maxLines = 1)
            }
        }
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
            Modifier.padding(horizontal = 24.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (card == null) "Add a BahnCard" else "Edit BahnCard", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text("Name") },
                placeholder = { Text("BahnCard $kind") },
                supportingText = { Text("Whose card it is, or what it is for. Empty shows the kind.") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            ConnectedChoiceRow(
                options = listOf(25, 50, 100),
                selected = kind,
                onSelect = { kind = it },
                label = { "BC $it" },
                modifier = Modifier.fillMaxWidth(),
            )
            ConnectedChoiceRow(
                options = listOf(2, 1),
                selected = travelClass,
                onSelect = { travelClass = it },
                label = { if (it == 1) "1st class" else "2nd class" },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DateButton("Valid from", from, Modifier.weight(1f)) { picking = "from" }
                DateButton("Valid until", to, Modifier.weight(1f)) { picking = "to" }
            }
            OutlinedTextField(
                value = number,
                onValueChange = { number = it.take(40) },
                label = { Text("Card number (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyLarge.tabular(),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            SettingSwitch(
                title = "Renews automatically",
                description = "A subscription runs on unless it is cancelled in time. Turn this off once it is cancelled.",
                checked = subscription,
                onCheckedChange = { subscription = it },
            )
            if (subscription) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Notice period", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Cancel by ${TripFormat.date(to.minusWeeks(notice.toLong()))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalIconButton(onClick = { notice = (notice - 1).coerceAtLeast(0) }, enabled = notice > 0) {
                        Icon(Icons.Rounded.Remove, contentDescription = "One week less")
                    }
                    Text("$notice wk", style = MaterialTheme.typography.titleMedium.tabular())
                    FilledTonalIconButton(onClick = { notice = (notice + 1).coerceAtMost(26) }, enabled = notice < 26) {
                        Icon(Icons.Rounded.Add, contentDescription = "One week more")
                    }
                }
            }
            if (file != null || existing != null) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(start = 14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Description, contentDescription = null)
                        Text(
                            if (file != null) "New file chosen" else existing.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 10.dp),
                        )
                        IconButton(onClick = { file = null; removeFile = true }) { Icon(Icons.Rounded.Close, contentDescription = "Remove file") }
                    }
                }
            } else {
                OutlinedButton(onClick = { pick.launch(PickedFile.TYPES) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Add the PDF or a screenshot", modifier = Modifier.padding(start = 8.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = {
                            saving = true
                            onSave(
                                BahnCardInput(name.trim().ifEmpty { null }, kind, travelClass, from, to, number, subscription, notice),
                                file,
                                removeFile,
                            )
                        },
                        enabled = !saving && !to.isBefore(from),
                    ) { Text("Save card") }
                }
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

@Composable
private fun DateButton(label: String, date: LocalDate, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(TripFormat.date(date), style = MaterialTheme.typography.bodyLarge.tabular())
        }
    }
}
