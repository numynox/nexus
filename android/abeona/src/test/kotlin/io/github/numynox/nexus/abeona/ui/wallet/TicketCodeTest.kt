package io.github.numynox.nexus.abeona.ui.wallet

import android.app.Application
import android.graphics.Color
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The synthetic ticket's barcode is embedded the way DB's tickets embed their
 * Aztec code, a 1-bit "[/Indexed /DeviceGray 1 <00FF>]" image, which PdfBox
 * alone draws blank (fixtures/make-synthetic-pdf.mjs).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TicketCodeTest {
    @Test
    fun indexedOneBitCodeIsDecoded() {
        val bytes = checkNotNull(javaClass.getResource("/synthetic-ticket.pdf")).readBytes()
        val file = File.createTempFile("ticket", ".pdf").apply { writeBytes(bytes); deleteOnExit() }
        val code = TicketCode.extract(RuntimeEnvironment.getApplication(), file)
        assertNotNull("the code is found", code)
        code!!
        assertEquals(120, code.width)
        // The bull's eye: dark in the very middle, light on the first ring.
        assertEquals(Color.BLACK, code.getPixel(60, 60))
        assertEquals(Color.WHITE, code.getPixel(60, 65))
        var dark = 0
        for (y in 0 until 120) for (x in 0 until 120) if (code.getPixel(x, y) == Color.BLACK) dark++
        assertTrue("dark and light both, not a blank", dark in 2000..12000)
    }
}
