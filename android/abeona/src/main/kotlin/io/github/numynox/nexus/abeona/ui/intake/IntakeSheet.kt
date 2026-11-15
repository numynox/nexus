package io.github.numynox.nexus.abeona.ui.intake

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.numynox.nexus.abeona.ui.AbeonaType
import io.github.numynox.nexus.abeona.ui.muted
import io.github.numynox.nexus.abeona.ui.common.Banner
import io.github.numynox.nexus.abeona.ui.common.PrimaryButton
import io.github.numynox.nexus.abeona.ui.common.StatusTone
import io.github.numynox.nexus.abeona.ui.common.WellField

/**
 * "Add from a ticket or share": paste DB Navigator's share text or choose a
 * ticket PDF. Sharing straight from DB Navigator to Abeona does the same
 * without this sheet.
 */
@Composable
fun IntakeSheet(
    state: IntakeState,
    onReadText: (String) -> Unit,
    onReadPdf: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onReadPdf) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 16.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Add from a ticket or share", style = AbeonaType.headline)
                if (state !is IntakeState.Working) {
                    Text(
                        "Share a connection from DB Navigator to Abeona, or paste it here. A ticket PDF is read on this phone and goes into the trip's wallet.",
                        style = AbeonaType.meta,
                        color = muted,
                    )
                }
            }
            if (state is IntakeState.Working) {
                // Reading or looking up: nothing to type or choose until it is done.
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LoadingIndicator(Modifier.size(48.dp))
                    Text(if (state.matching) "Looking up the connection…" else "Reading…", style = AbeonaType.body, color = muted)
                }
                return@Column
            }
            Text("Share text from DB Navigator", style = AbeonaType.meta, color = muted, modifier = Modifier.padding(horizontal = 4.dp))
            WellField(
                value = text,
                onValueChange = { text = it },
                placeholder = "Erlangen → Leuven\nFr. 14.11.2025\n\nRE19 (4901)\nAb 06:29 Erlangen, Gleis 4\n…",
                description = "Share text from DB Navigator",
                singleLine = false,
                minLines = 4,
                maxLines = 10,
                style = AbeonaType.meta,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state is IntakeState.Failed) {
                Banner(StatusTone.Bad, Icons.Rounded.ErrorOutline, state.message)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { pick.launch(arrayOf("application/pdf")) }) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Ticket PDF", modifier = Modifier.padding(start = 8.dp))
                }
                PrimaryButton(
                    "Read the text",
                    onClick = { onReadText(text) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    icon = Icons.Rounded.ContentPaste,
                    enabled = text.isNotBlank(),
                )
            }
        }
    }
}
