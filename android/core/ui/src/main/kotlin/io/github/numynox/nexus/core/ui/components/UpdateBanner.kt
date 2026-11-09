package io.github.numynox.nexus.core.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.core.data.AvailableUpdate
import io.github.numynox.nexus.core.update.AppUpdates
import io.github.numynox.nexus.core.update.DownloadProgress

/** A newer release, installed from here: the app downloads and installs it itself. */
@Composable
fun UpdateBanner(updates: AppUpdates, modifier: Modifier = Modifier) {
    val update by updates.available.collectAsStateWithLifecycle()
    val download by updates.download.collectAsStateWithLifecycle(null)
    UpdateBanner(update, download, onInstall = rememberInstallUpdate(updates), modifier)
}

/**
 * The Install tap. The download's progress and, from the background,
 * Android's install prompt are notifications, so a phone that has not yet
 * answered asks for them first; the install goes ahead whatever the answer.
 */
@Composable
fun rememberInstallUpdate(updates: AppUpdates): () -> Unit {
    val context = LocalContext.current
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { updates.install(context) }
    return remember(updates, context, notifications) {
        {
            val ask = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && updates.selfInstalls &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (ask) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else updates.install(context)
        }
    }
}

@Composable
fun UpdateBanner(
    update: AvailableUpdate?,
    download: DownloadProgress?,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NoticeBanner(
        visible = update != null,
        icon = Icons.Rounded.SystemUpdate,
        text = if (download != null) downloadingLabel(download) else "Version ${update?.versionName.orEmpty()} is available.",
        modifier = modifier,
        action = {
            if (download != null) {
                DownloadIndicator(download, Modifier.padding(horizontal = 12.dp))
            } else {
                // The banner's own text colour: a text button's default, primary,
                // all but vanishes on the tertiary container in every palette.
                TextButton(
                    onClick = onInstall,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
                ) {
                    Text("Install")
                }
            }
        },
    )
}

/** "Downloading… 42%", or without the number while the size is unknown. */
fun downloadingLabel(download: DownloadProgress): String =
    download.percent?.let { "Downloading… $it%" } ?: "Downloading…"

/** A small ring in the surrounding text colour, filling with the download; spinning while its size is unknown. */
@Composable
fun DownloadIndicator(download: DownloadProgress, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    val percent = download.percent
    if (percent == null) {
        CircularProgressIndicator(modifier.size(20.dp), strokeWidth = 2.dp, color = color)
    } else {
        CircularProgressIndicator(
            progress = { percent / 100f },
            modifier = modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = color,
        )
    }
}
