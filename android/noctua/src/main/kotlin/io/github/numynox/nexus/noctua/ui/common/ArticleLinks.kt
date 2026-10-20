package io.github.numynox.nexus.noctua.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.net.toUri
import io.github.numynox.nexus.noctua.data.Article

/**
 * Opens an article in a Custom Tab — the user's own browser, with its
 * cookies and logins, drawn inside Noctua and themed to it, so Back returns to
 * the feed — or in the browser app when the user prefers that.
 */
fun openArticle(context: Context, article: Article, inApp: Boolean, toolbarColor: Color) {
    val uri = article.url.toUri()
    try {
        if (inApp) {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(toolbarColor.toArgb()).build())
                .build()
                .launchUrl(context, uri)
        } else {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    } catch (_: ActivityNotFoundException) {
        // No browser at all; nothing sensible to do but stay put.
    }
}

/** The system share sheet with the article's title and link. */
fun shareArticle(context: Context, article: Article) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, article.title)
        putExtra(Intent.EXTRA_TEXT, "${article.title}\n${article.url}")
    }
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
