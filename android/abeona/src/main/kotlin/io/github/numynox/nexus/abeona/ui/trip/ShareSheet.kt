package io.github.numynox.nexus.abeona.ui.trip

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.numynox.nexus.abeona.BuildConfig
import io.github.numynox.nexus.abeona.data.AbeonaRepository
import io.github.numynox.nexus.abeona.data.ShareLink
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.data.toInstantOrNull
import io.github.numynox.nexus.abeona.ui.common.TripFormat

/** What the share card can do; the screen wires it to the view model. */
class ShareActions(
    val links: List<ShareLink>,
    val busy: String?,
    val onCreate: (String) -> Unit,
    val onShare: (ShareLink) -> Unit,
    val onRevoke: (ShareLink) -> Unit,
)

private data class ShareKind(val kind: String, val title: String, val text: String, val icon: ImageVector)

private val Kinds = listOf(
    ShareKind(
        "status", "Live status",
        "A page with the current train, the expected arrival and the delay — for whoever picks you up. It stops working six hours after the trip.",
        Icons.Rounded.Sensors,
    ),
    ShareKind(
        "ics", "Calendar",
        "Every ride as an event at its live times. Subscribed to, the calendar follows delays.",
        Icons.Rounded.CalendarMonth,
    ),
)

/** The link's address: the status page on the website, or the calendar file from the Edge Function. */
fun shareUrl(link: ShareLink): String = AbeonaRepository.shareUrl(link, BuildConfig.SUPABASE_URL)

/** Hands a link to Android's share sheet. */
fun shareLinkIntent(context: Context, trip: Trip, link: ShareLink) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "${trip.originName} → ${trip.destinationName}")
        putExtra(Intent.EXTRA_TEXT, shareUrl(link))
    }
    context.startActivity(Intent.createChooser(send, if (link.kind == "ics") "Share the calendar" else "Share the live status"))
}

/**
 * Share links for someone outside the household: make, share, revoke. A
 * sheet opened from the trip's ⋮ menu; [actions] is null while the links
 * load or without a connection.
 */
@Composable
fun ShareSheet(actions: ShareActions?, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Share", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Anyone with a link sees the route and the times, nothing else.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (actions == null) {
                Text(
                    "Share links need a connection. Try again when you are online.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Kinds.forEach { kind ->
                    val link = actions.links.firstOrNull { it.kind == kind.kind }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(kind.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(kind.title, style = MaterialTheme.typography.titleSmall)
                            if (link == null) {
                                Text(kind.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text(
                                    shareUrl(link),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                link.expiresAt.toInstantOrNull()?.let {
                                    Text(
                                        "Works until ${TripFormat.day(it)}, ${TripFormat.time(it)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                val busy = actions.busy == kind.kind
                                if (link == null) {
                                    OutlinedButton(onClick = { actions.onCreate(kind.kind) }, enabled = actions.busy == null) {
                                        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        else Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Text("Create link", modifier = Modifier.padding(start = 8.dp))
                                    }
                                } else {
                                    FilledTonalButton(onClick = { actions.onShare(link) }, enabled = actions.busy == null) {
                                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Text("Share", modifier = Modifier.padding(start = 8.dp))
                                    }
                                    TextButton(onClick = { actions.onRevoke(link) }, enabled = actions.busy == null) {
                                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                        Text("Revoke", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
