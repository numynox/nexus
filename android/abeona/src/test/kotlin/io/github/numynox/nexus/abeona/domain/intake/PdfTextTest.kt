package io.github.numynox.nexus.abeona.domain.intake

import android.app.Application
import org.robolectric.RuntimeEnvironment
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** PdfBox on the synthetic ticket PDF reads to text that parses like pdftotext's (intake.fixtures.json, "pdf"). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PdfTextTest {
    @Serializable private data class PdfCase(val name: String, val file: String, @SerialName("same_as") val sameAs: String)
    @Serializable private data class TicketCase(val file: String, val expect: ParsedTicket)
    @Serializable private data class Fixtures(val pdf: List<PdfCase>, val ticket: List<TicketCase>)

    @Test
    fun syntheticTicket() {
        val fixtures: Fixtures = IntakeJson.decodeFromString(resourceText("intake.fixtures.json"))
        val context = RuntimeEnvironment.getApplication()
        fixtures.pdf.forEach { c ->
            val bytes = checkNotNull(javaClass.getResource("/${c.file}")).readBytes()
            val text = PdfText.extract(context, bytes)
            val expected = fixtures.ticket.first { it.file == c.sameAs }.expect
            assertEquals(c.name, expected, parseDbTicket(text))
        }
    }
}
