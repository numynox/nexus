package io.github.numynox.nexus.abeona.domain.intake

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

/**
 * A PDF's text layer on the phone, with PdfBox-Android: lines in reading
 * order, sorted by position, pages separated by a form feed. Columns come out
 * one space apart, which the ticket parser allows for. The web does the same
 * with pdf.js (app/abeona/src/lib/intake/pdfText.ts). Never on a server.
 */
object PdfText {
    @Volatile private var ready = false

    /** Blocking and not cheap: call off the main thread. */
    fun extract(context: Context, bytes: ByteArray): String {
        if (!ready) {
            PDFBoxResourceLoader.init(context.applicationContext)
            ready = true
        }
        return PDDocument.load(bytes).use { doc ->
            PDFTextStripper().apply {
                sortByPosition = true
                lineSeparator = "\n"
                pageEnd = "\n\u000c"
            }.getText(doc)
        }
    }
}
