package io.github.numynox.nexus.abeona.ui.wallet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.cos.COSString
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
                    .sortedByDescending { it.width.toLong() * it.height }
                    .firstNotNullOfOrNull { image -> bitmapOf(image)?.takeIf(::looksLikeACode) }
            }
        }.getOrNull()
    }

    /**
     * The image as a bitmap. A barcode is usually 1 bit per pixel, often with
     * an indexed colour space ("[/Indexed /DeviceGray 1 <00FF>]" on DB's
     * tickets), which PdfBox-Android turns into a blank bitmap: those are
     * decoded here, bit by bit through the colour space. Anything else goes
     * through PdfBox.
     */
    private fun bitmapOf(image: PDImageXObject): Bitmap? {
        if (image.bitsPerComponent != 1 || image.isStencil) return runCatching { image.image }.getOrNull()
        val width = image.width
        val height = image.height
        val bytes = image.stream.createInputStream().use { it.readBytes() }
        val rowBytes = (width + 7) / 8
        if (bytes.size < rowBytes * height) return null
        // The two colours a bit can be: through the palette, or grey with its
        // Decode array. Read from the dictionary: PdfBox-Android has no
        // indexed colour space at all.
        val inverted = image.decode?.let { it.size() >= 2 && it.toFloatArray()[0] > it.toFloatArray()[1] } == true
        val colours = paletteOf(image) ?: intArrayOf(Color.BLACK, Color.WHITE)
        if (inverted) colours.reverse()
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val bit = (bytes[y * rowBytes + (x shr 3)].toInt() shr (7 - (x and 7))) and 1
                pixels[y * width + x] = colours[bit]
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * The two colours of a 1-bit "[/Indexed base 1 <lookup>]" image, for a grey
     * or RGB base; null for any other colour space, which reads as grey.
     */
    private fun paletteOf(image: PDImageXObject): IntArray? {
        val space = image.cosObject.getDictionaryObject(COSName.COLORSPACE) as? COSArray ?: return null
        if ((space.getObject(0) as? COSName)?.name != "Indexed" || space.size() < 4) return null
        val channels = when ((space.getObject(1) as? COSName)?.name) {
            "DeviceGray", "G" -> 1
            "DeviceRGB", "RGB" -> 3
            else -> return null
        }
        val lookup = when (val table = space.getObject(3)) {
            is COSString -> table.bytes
            is COSStream -> table.createInputStream().use { it.readBytes() }
            else -> return null
        }
        if (lookup.size < 2 * channels) return null
        return IntArray(2) { i ->
            val v = IntArray(channels) { c -> lookup[i * channels + c].toInt() and 0xFF }
            if (channels == 1) Color.rgb(v[0], v[0], v[0]) else Color.rgb(v[0], v[1], v[2])
        }
    }

    /** Dark and light modules both, in plenty: not a blank or a filled square. */
    private fun looksLikeACode(bitmap: Bitmap): Boolean {
        val step = maxOf(1, minOf(bitmap.width, bitmap.height) / 64)
        var dark = 0
        var total = 0
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val c = bitmap.getPixel(x, y)
                val opaque = Color.alpha(c) > 128
                if (opaque && Color.red(c) + Color.green(c) + Color.blue(c) < 3 * 128) dark++
                total++
            }
        }
        val share = dark.toDouble() / total
        return share in 0.2..0.8
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
