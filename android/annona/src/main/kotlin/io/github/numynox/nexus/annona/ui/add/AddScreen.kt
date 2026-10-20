package io.github.numynox.nexus.annona.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.annona.ui.AddRoute
import io.github.numynox.nexus.annona.ui.AppViewModel
import io.github.numynox.nexus.annona.ui.Navigator
import io.github.numynox.nexus.annona.ui.common.CountStepper
import io.github.numynox.nexus.annona.ui.common.ExpiryPicker
import io.github.numynox.nexus.annona.ui.common.LocationPicker
import io.github.numynox.nexus.annona.ui.common.ProductFieldsForm
import io.github.numynox.nexus.annona.ui.common.ProductImage
import io.github.numynox.nexus.annona.ui.common.containerViewModel
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.components.ErrorState
import io.github.numynox.nexus.core.ui.components.LoadingState
import io.github.numynox.nexus.core.ui.components.NexusCard

@Composable
fun AddScreen(app: AppViewModel, route: AddRoute, navigator: Navigator) {
    val viewModel = containerViewModel(key = "add-$route") { AddViewModel(it, route) }
    val choices by viewModel.choices.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (form.product != null || choices is Loadable.Loading) "Add to stock" else "New product") },
                navigationIcon = { IconButton(onClick = navigator.back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.navigationBarsPadding().imePadding().padding(16.dp)) {
                    form.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
                    Button(
                        onClick = {
                            viewModel.save { message ->
                                app.changed(message)
                                navigator.back()
                            }
                        },
                        enabled = !form.saving && choices is Loadable.Ready,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        if (form.saving) LoadingIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary)
                        else Text(if (form.count == 1) "Add 1 item" else "Add ${form.count} items", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
    ) { padding ->
        when (val state = choices) {
            Loadable.Loading -> LoadingState(Modifier.fillMaxSize().padding(padding))
            is Loadable.Failed -> ErrorState(state.message, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            is Loadable.Ready -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val product = form.product
                if (product != null) {
                    NexusCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            ProductImage(product.imageUrl, product.category, size = 64.dp)
                            Column(Modifier.weight(1f)) {
                                Text(product.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    listOfNotNull(product.quantity, product.category?.name, "${product.stock.size} in stock").joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    LookupNotice(form.lookup, onRetry = { viewModel.lookup(form.fields.ean) })
                    NexusCard {
                        ProductFieldsForm(form.fields, state.value.categories, onChange = { fields -> viewModel.edit { it.copy(fields = fields) } })
                    }
                }
                NexusCard {
                    Text("Expires", style = MaterialTheme.typography.titleSmall)
                    ExpiryPicker(form.expiration, onChange = { date -> viewModel.edit { it.copy(expiration = date) } }, modifier = Modifier.padding(top = 8.dp))
                }
                NexusCard {
                    Text("Kept in", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
                    LocationPicker(state.value.locations, form.locationId) { id -> viewModel.edit { it.copy(locationId = id) } }
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("How many", style = MaterialTheme.typography.titleSmall)
                            Text("Each package is its own item, with the same date.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        CountStepper(form.count) { count -> viewModel.edit { it.copy(count = count) } }
                    }
                }
            }
        }
    }
}

/** Says where the form's values came from — a suggestion is never mistaken for a known product. */
@Composable
private fun LookupNotice(lookup: Lookup, onRetry: () -> Unit) {
    val (icon, text) = when (lookup) {
        Lookup.None -> return
        Lookup.Running -> null to "Asking Open Food Facts about this barcode…"
        is Lookup.Found -> Icons.Rounded.AutoAwesome to "Filled in from Open Food Facts, nutrition included. Check it before saving."
        Lookup.Unknown -> Icons.Rounded.HelpOutline to "Open Food Facts doesn't know this barcode. Describe the product once; next time it is known."
        is Lookup.Failed -> Icons.Rounded.CloudOff to "Couldn't ask Open Food Facts: ${lookup.reason}"
    }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shape = MaterialTheme.shapes.large) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (icon == null) LoadingIndicator(Modifier.size(24.dp)) else Icon(icon, contentDescription = null)
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (lookup is Lookup.Failed) TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}
