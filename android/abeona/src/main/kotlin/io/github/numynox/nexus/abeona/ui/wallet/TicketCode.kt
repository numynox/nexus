package io.github.numynox.nexus.abeona.ui.wallet

import android.content.Context
import android.graphics.Bitmap
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File
import kotlin.math.abs

/**
 * The ticket's barcode (a DB ticket's Aztec code), taken out of the PDF as the
 * image it is embedded as, so it can be shown alone and large. On the device
 * only. The web does the same with pdf.js (lib/ticketCode.ts).
 *
 * The code is the largest roughly square raster image on the first pages; a
 * logo or a photo is not square, and a small icon is too small. A ticket that
 * draws its code as vector shapes yields null, and the PDF is shown instead.
 */
object TicketCode {
    private const val MIN_SIDE = 80
    private const val PAGES = 2

    /** Blocking: call off the main thread. */
    fun extract(context: Context, file: File): Bitmap? {
        if (!file.name.endsWith(".pdf", ignoreCase = true)) return null
        PDFBoxResourceLoader.init(context.applicationContext)
        return runCatching {
            PDDocument.load(file).use { doc ->
                val images = mutableListOf<PDImageXObject>()
                doc.pages.take(PAGES).forEach { page -> page.resources?.let { collect(it, images, depth = 0) } }
                images
                    .filter { it.width >= MIN_SIDE && it.height >= MIN_SIDE && squareEnough(it.width, it.height) }
                    .maxByOrNull { it.width.toLong() * it.height }
                    ?.image
            }
        }.getOrNull()
    }

    private fun squareEnough(width: Int, height: Int) = abs(width - height) <= maxOf(width, height) * 0.15

    /** Images on the page and inside its forms, which some generators wrap them in. */
    private fun collect(resources: PDResources, into: MutableList<PDImageXObject>, depth: Int) {
        for (name in resources.xObjectNames) {
            when (val x = runCatching { resources.getXObject(name) }.getOrNull()) {
                is PDImageXObject -> into += x
                is PDFormXObject -> if (depth < 3) x.resources?.let { collect(it, into, depth + 1) }
            }
        }
    }
}
