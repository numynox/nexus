package io.github.numynox.nexus.abeona.ui.intake

import android.content.Intent
import android.net.Uri
import android.os.Build

/** Something shared to Abeona from another app: DB Navigator's text, or a ticket PDF. */
data class IncomingShare(val text: String? = null, val uri: Uri? = null) {
    companion object {
        /** The share an ACTION_SEND intent carries; null for any other intent. */
        fun from(intent: Intent?): IncomingShare? {
            if (intent?.action != Intent.ACTION_SEND) return null
            val stream = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
            return when {
                stream != null && (intent.type == "application/pdf" || text == null) -> IncomingShare(uri = stream)
                text != null -> IncomingShare(text = text)
                else -> null
            }
        }
    }
}
