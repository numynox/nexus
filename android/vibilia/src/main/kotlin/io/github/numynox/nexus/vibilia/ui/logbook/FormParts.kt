package io.github.numynox.nexus.vibilia.ui.logbook

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Accepts both decimal separators — German keyboards offer a comma. */
fun String.toDecimal(): Double? = trim().replace(',', '.').toDoubleOrNull()

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    error: String? = null,
    supporting: String? = null,
    decimal: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> onValueChange(next.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }) },
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        suffix = suffix?.let { { Text(it) } },
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    )
}

/** A date chip and a time chip, each opening the Material picker. */
@Composable
fun DateTimeChips(value: LocalDateTime, onChange: (LocalDateTime) -> Unit, modifier: Modifier = Modifier) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val locale = Locale.getDefault()

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = { pickDate = true },
            label = { Text(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(value)) },
            leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
        )
        AssistChip(
            onClick = { pickTime = true },
            label = { Text(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(value)) },
            leadingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
        )
    }

    if (pickDate) {
        // The picker works in UTC midnights; convert at the edges only.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(LocalDateTime.of(date, value.toLocalTime()))
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }

    if (pickTime) {
        val state = rememberTimePickerState(initialHour = value.hour, initialMinute = value.minute)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(value.withHour(state.hour).withMinute(state.minute))
                    pickTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}

@Composable
fun FormError(message: String?) {
    AnimatedVisibility(visible = message != null) {
        Text(message.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

fun LocalDateTime.toInstantHere(): Instant = atZone(ZoneId.systemDefault()).toInstant()
fun Instant.toLocalDateTimeHere(): LocalDateTime = atZone(ZoneId.systemDefault()).toLocalDateTime()

