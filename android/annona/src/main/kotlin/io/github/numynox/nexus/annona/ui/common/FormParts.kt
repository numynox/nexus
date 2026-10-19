package io.github.numynox.nexus.annona.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.numynox.nexus.annona.data.Category
import io.github.numynox.nexus.annona.data.StorageLocation
import io.github.numynox.nexus.annona.data.expiryLabel
import io.github.numynox.nexus.annona.data.firstOfMonth
import io.github.numynox.nexus.core.ui.NexusFormat
import io.github.numynox.nexus.core.ui.theme.tabular
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/**
 * When an item goes off. Most packets print a month, so — like the web form —
 * a year and a month set the first of that month; quick picks cover the
 * common shelf lives, and a calendar the exact day.
 */
@Composable
fun ExpiryPicker(value: LocalDate?, onChange: (LocalDate?) -> Unit, modifier: Modifier = Modifier, today: LocalDate = LocalDate.now()) {
    var calendar by remember { mutableStateOf(false) }
    val year = value?.year
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(value?.let(NexusFormat::date) ?: "Choose a date", style = MaterialTheme.typography.titleMedium.tabular())
                Text(expiryLabel(value, today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = { calendar = true }) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Exact day", modifier = Modifier.padding(start = 6.dp))
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val quick = listOf("1 week" to today.plusWeeks(1), "2 weeks" to today.plusWeeks(2), "1 month" to today.plusMonths(1), "3 months" to today.plusMonths(3), "1 year" to today.plusYears(1))
            items(quick) { (label, date) -> FilterChip(selected = value == date, onClick = { onChange(date) }, label = { Text("In $label") }) }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items((0..5).map { today.year + it }) { option ->
                FilterChip(
                    selected = year == option,
                    onClick = { onChange(firstOfMonth(option, value?.monthValue ?: today.monthValue).coerceAtLeast(today.withDayOfMonth(1))) },
                    label = { Text("$option") },
                )
            }
        }
        (1..12).chunked(4).forEach { months ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                months.forEach { month ->
                    val date = firstOfMonth(year ?: today.year, month)
                    FilterChip(
                        selected = value?.year == date.year && value.monthValue == month,
                        onClick = { onChange(date) },
                        label = { Text(java.time.Month.of(month).getDisplayName(TextStyle.SHORT, Locale.getDefault()), maxLines = 1) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    if (calendar) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (value ?: today).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { calendar = false },
            confirmButton = {
                TextButton(onClick = {
                    calendar = false
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { calendar = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

private fun LocalDate.coerceAtLeast(other: LocalDate) = if (isBefore(other)) other else this

/** Where an item is kept: a chip per location, or none. */
@Composable
fun LocationPicker(locations: List<StorageLocation>, selected: Long?, onSelect: (Long?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(locations, key = { it.id }) { location ->
            FilterChip(selected = selected == location.id, onClick = { onSelect(if (selected == location.id) null else location.id) }, label = { Text(location.name) })
        }
    }
}

/** How many packages — a stepper, since buying three of a thing is common. */
@Composable
fun CountStepper(count: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledTonalIconButton(onClick = { onChange((count - 1).coerceAtLeast(1)) }, enabled = count > 1) { Icon(Icons.Rounded.Remove, contentDescription = "One fewer") }
        Text("$count", style = MaterialTheme.typography.headlineSmall.tabular())
        FilledTonalIconButton(onClick = { onChange((count + 1).coerceAtMost(50)) }) { Icon(Icons.Rounded.Add, contentDescription = "One more") }
    }
}

/** A product's own fields, for adding a new one or editing it. */
data class ProductFields(
    val name: String = "",
    val brand: String = "",
    val quantity: String = "",
    val ean: String = "",
    val categoryId: Long? = null,
    val imageUrl: String = "",
)

@Composable
fun ProductFieldsForm(fields: ProductFields, categories: List<Category>, onChange: (ProductFields) -> Unit, showImage: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = fields.brand,
            onValueChange = { onChange(fields.copy(brand = it)) },
            label = { Text("Brand") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = fields.name,
            onValueChange = { onChange(fields.copy(name = it)) },
            label = { Text("Product name") },
            singleLine = true,
            isError = fields.name.isBlank(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = fields.quantity,
                onValueChange = { onChange(fields.copy(quantity = it)) },
                label = { Text("Quantity") },
                placeholder = { Text("500 g") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = fields.ean,
                onValueChange = { value -> onChange(fields.copy(ean = value.filter(Char::isDigit))) },
                label = { Text("Barcode") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1.3f),
            )
        }
        Text("Category", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories, key = { it.id }) { category ->
                FilterChip(
                    selected = fields.categoryId == category.id,
                    onClick = { onChange(fields.copy(categoryId = if (fields.categoryId == category.id) null else category.id)) },
                    label = { Text(category.name) },
                    leadingIcon = {
                        Icon(
                            categoryIcon(category.icon),
                            contentDescription = null,
                            tint = categoryColor(category.color) ?: MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                )
            }
        }
        if (showImage) {
            OutlinedTextField(
                value = fields.imageUrl,
                onValueChange = { onChange(fields.copy(imageUrl = it.trim())) },
                label = { Text("Image URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
