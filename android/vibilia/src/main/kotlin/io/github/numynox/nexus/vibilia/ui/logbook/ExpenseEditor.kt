package io.github.numynox.nexus.vibilia.ui.logbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.core.data.describe
import io.github.numynox.nexus.core.ui.NexusFormat
import io.github.numynox.nexus.vibilia.AppContainer
import io.github.numynox.nexus.vibilia.data.CarExpense
import io.github.numynox.nexus.vibilia.data.ExpenseCategories
import io.github.numynox.nexus.vibilia.data.ExpenseDraft
import io.github.numynox.nexus.vibilia.ui.EditorRequest
import java.time.LocalDateTime
import kotlinx.coroutines.launch

/** The expense form, with the web form's limits (ExpenseForm.svelte). */
class ExpenseEditorViewModel(private val container: AppContainer, request: EditorRequest) : ViewModel() {
    private val editing: CarExpense? = (request as? EditorRequest.EditExpense)?.expense
    private val carId: String = editing?.carId ?: (request as EditorRequest.NewExpense).carId
    val isNew get() = editing == null

    var title by mutableStateOf(editing?.title.orEmpty())
    var amount by mutableStateOf(editing?.amount?.let { NexusFormat.number(it, 2) }.orEmpty())
    var mileage by mutableStateOf(editing?.mileage?.takeIf { it > 0 }?.toString().orEmpty())
    var category by mutableStateOf(editing?.category ?: "Other")
    var notes by mutableStateOf(editing?.notes.orEmpty())
    var expensedAt by mutableStateOf(editing?.expensedAt?.toLocalDateTimeHere() ?: LocalDateTime.now().withSecond(0).withNano(0))

    var submitted by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val titleError: String?
        get() = when {
            !submitted && title.isEmpty() -> null
            title.isBlank() -> "Enter a title."
            title.trim().length > 120 -> "Title must be at most 120 characters."
            else -> null
        }

    val amountError: String?
        get() {
            val value = amount.toDecimal()
            if (!submitted && (value == null || value == 0.0)) return null
            return when {
                value == null || value <= 0 -> "Amount must be greater than 0 €."
                value > 100_000 -> "Amount is too large."
                else -> null
            }
        }

    val mileageError: String?
        get() = mileage.takeIf { it.isNotEmpty() }?.toIntOrNull()?.let {
            if (it >= 1_000_000) "Mileage must be below 1,000,000 km." else null
        }

    fun save(onSaved: () -> Unit) {
        submitted = true
        if (titleError != null || amountError != null || mileageError != null || saving) return
        val draft = ExpenseDraft(
            expensedAt = expensedAt.toInstantHere(),
            title = title.trim(),
            amount = amount.toDecimal() ?: return,
            mileage = mileage.toIntOrNull(),
            category = category,
            notes = notes.trim().ifEmpty { null },
        )
        saving = true
        error = null
        viewModelScope.launch {
            runCatching {
                if (editing != null) {
                    container.logbook.updateExpense(editing.id, draft)
                } else {
                    val userId = container.auth.currentUserId ?: error("You must be signed in to add an expense.")
                    container.logbook.createExpense(carId, userId, draft)
                }
            }.onSuccess { onSaved() }
                .onFailure { error = it.describe() }
            saving = false
        }
    }
}

@Composable
fun ExpenseEditor(viewModel: ExpenseEditorViewModel, onCancel: () -> Unit, onSaved: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (viewModel.isNew) "Log expense" else "Edit expense", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = viewModel.title,
            onValueChange = { viewModel.title = it },
            label = { Text("Title") },
            leadingIcon = { Icon(Icons.Rounded.Title, contentDescription = null) },
            isError = viewModel.titleError != null,
            supportingText = viewModel.titleError?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(
                value = viewModel.amount,
                onValueChange = { viewModel.amount = it },
                label = "Amount",
                icon = Icons.Rounded.Euro,
                suffix = "€",
                error = viewModel.amountError,
                modifier = Modifier.weight(1f),
            )
            NumberField(
                value = viewModel.mileage,
                onValueChange = { viewModel.mileage = it },
                label = "Odometer",
                icon = Icons.Rounded.Speed,
                suffix = "km",
                decimal = false,
                error = viewModel.mileageError,
                modifier = Modifier.weight(1f),
            )
        }
        DateTimeChips(viewModel.expensedAt, onChange = { viewModel.expensedAt = it })
        Text("Category", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpenseCategories.forEach { category ->
                FilterChip(
                    selected = viewModel.category == category,
                    onClick = { viewModel.category = category },
                    label = { Text(category) },
                    leadingIcon = { Icon(expenseIcon(category), contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        OutlinedTextField(
            value = viewModel.notes,
            onValueChange = { viewModel.notes = it },
            label = { Text("Notes") },
            leadingIcon = { Icon(Icons.Rounded.Notes, contentDescription = null) },
            minLines = 2,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        FormError(viewModel.error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = { viewModel.save(onSaved) }, enabled = !viewModel.saving, modifier = Modifier.height(48.dp)) {
                if (viewModel.saving) {
                    LoadingIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(if (viewModel.isNew) "Save expense" else "Update expense")
                }
            }
        }
    }
}
