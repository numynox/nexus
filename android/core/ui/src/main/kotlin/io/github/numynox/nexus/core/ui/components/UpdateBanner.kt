package io.github.numynox.nexus.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import io.github.numynox.nexus.core.data.AvailableUpdate

/**
 * Sideloaded apps do not update themselves. This points at the GitHub release
 * that has a newer APK; installing it is one tap in the browser.
 */
@Composable
fun UpdateBanner(update: AvailableUpdate?, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    NoticeBanner(
        visible = update != null,
        icon = Icons.Rounded.SystemUpdate,
        text = "Version ${update?.versionName.orEmpty()} is available.",
        modifier = modifier,
        action = {
            TextButton(onClick = { update?.let { uriHandler.openUri(it.apkUrl ?: it.pageUrl) } }) {
                Text("Download")
            }
        },
    )
}
