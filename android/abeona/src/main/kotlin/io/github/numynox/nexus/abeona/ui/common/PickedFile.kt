package io.github.numynox.nexus.abeona.ui.common

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A document the person picked: a ticket or a BahnCard, as PDF or image. */
class PickedFile(val name: String, val mimeType: String, val bytes: ByteArray) {
    companion object {
        /** The MIME types the abeona-tickets bucket accepts, for the picker. */
        val TYPES = arrayOf("application/pdf", "image/png", "image/jpeg")
        const val MAX_BYTES = 10 * 1024 * 1024

        /** Reads [uri] off the main thread; fails with a sentence a person can read. */
        suspend fun read(application: Application, uri: Uri, fallbackName: String): PickedFile = withContext(Dispatchers.IO) {
            val resolver = application.contentResolver
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: fallbackName
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("The file could not be read.")
            require(bytes.size <= MAX_BYTES) { "The file can be at most 10 MB." }
            val mime = resolver.getType(uri) ?: if (name.endsWith(".pdf", true)) "application/pdf" else "image/jpeg"
            PickedFile(name, mime, bytes)
        }
    }
}
