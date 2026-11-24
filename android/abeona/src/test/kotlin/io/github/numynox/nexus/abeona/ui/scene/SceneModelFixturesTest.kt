package io.github.numynox.nexus.abeona.ui.scene

import io.github.numynox.nexus.abeona.domain.Confidence
import java.time.Instant
import kotlin.math.abs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scene's model against app/abeona/src/lib/scene.fixtures.json — the same
 * file the web's scene.test.ts runs, copied into the test resources by
 * Gradle. Add cases there, not here.
 */
class SceneModelFixturesTest {
    private val fixtures: JsonObject =
        Json.parseToJsonElement(javaClass.classLoader!!.getResourceAsStream("scene.fixtures.json")!!.bufferedReader().readText()).jsonObject

    private fun cases(name: String) = fixtures.getValue(name).jsonArray.map { it.jsonObject }
    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.number(key: String) = getValue(key).jsonPrimitive.double
    private fun JsonObject.flag(key: String) = getValue(key).jsonPrimitive.boolean
    private fun JsonObject.moment(key: String) = Instant.parse(text(key)).toEpochMilli()

    private fun near(actual: Double, expected: Double, what: String) =
        assertTrue("$what: $actual is not $expected", abs(actual - expected) <= 0.002)

    @Test
    fun theSunRisesAndSetsToTheMinuteAndTheLightFollows() {
        for (c in cases("sky")) {
            val at = c.moment("at")
            val what = "${c.text("place")} ${c.text("at")}"
            val times = SceneModel.sunTimes(at, c.number("lat"), c.number("lon"))!!
            assertTrue("$what sunrise", abs(times.sunrise - c.moment("sunrise")) < 60_000)
            assertTrue("$what sunset", abs(times.sunset - c.moment("sunset")) < 60_000)
            val sky = SceneModel.skyLight(at, c.number("lat"), c.number("lon"))
            near(sky.light, c.number("light"), "$what light")
            near(sky.glow, c.number("glow"), "$what glow")
            near(sky.across, c.number("across"), "$what across")
            assertEquals("$what night", c.flag("night"), sky.night)
        }
    }

    @Test
    fun withoutASunriseTheSkyIsPlainDay() {
        val c = fixtures.getValue("polar").jsonObject
        assertNull(SceneModel.sunTimes(c.moment("at"), c.number("lat"), c.number("lon")))
        assertEquals(
            SceneModel.SkyLight(c.number("light"), c.number("glow"), c.number("across"), c.flag("night")),
            SceneModel.skyLight(c.moment("at"), c.number("lat"), c.number("lon")),
        )
    }

    @Test
    fun theMoonsPhaseItsNameAndItsLitPart() {
        for (c in cases("moon")) {
            near(SceneModel.moonPhase(c.moment("at")), c.number("phase"), c.text("at"))
            assertEquals(c.text("name"), SceneModel.moonPhaseName(SceneModel.moonPhase(c.moment("at"))))
        }
        for (c in cases("moonPath")) {
            assertEquals(c.text("path"), SceneModel.moonLitPath(c.number("phase"), c.number("cx"), c.number("cy"), c.number("r")))
        }
    }

    @Test
    fun theLandTheColoursAndTheRail() {
        for (c in cases("hills")) {
            val kind = if (c.text("kind") == "far") SceneModel.HillKind.Far else SceneModel.HillKind.Near
            near(SceneModel.hillHeight(kind, c.number("x")), c.number("height"), "${c.text("kind")} hill at ${c.number("x")}")
        }
        for (c in cases("mix")) assertEquals(c.text("colour"), SceneModel.mix(c.text("a"), c.text("b"), c.number("t")))
        for (c in cases("palette")) {
            val p = SceneModel.scenePalette(c.flag("dark"), c.number("light"))
            assertEquals(
                listOf(c.text("sky"), c.text("near"), c.text("trainWindow"), c.text("trainRed"), c.text("accent"), c.text("ok"), c.text("watch"), c.text("bad")),
                listOf(p.sky, p.near, p.trainWindow, p.trainRed, p.accent, p.ok, p.watch, p.bad),
            )
        }
        for (c in cases("light")) {
            val at = SceneModel.lightPosition(c.number("across"), c.number("width"), c.number("height"))
            near(at.x, c.number("x"), "light x")
            near(at.up, c.number("up"), "light up")
        }
        for (c in cases("speed")) near(SceneModel.travelSpeed(c.getValue("kmh").jsonPrimitive.doubleOrNull), c.number("units"), "speed at ${c["kmh"]}")
        for (c in cases("rail")) near(SceneModel.railX(c.number("fraction"), c.number("width")), c.number("x"), "rail at ${c.number("fraction")}")
    }

    @Test
    fun theBarIsTheWholeJourneyEveryRideAndChangeOneInFocus() {
        for (c in cases("journey")) {
            val what = c.text("case")
            val journey = BarJourney(
                rides = c.getValue("rides").jsonArray.map { it.jsonObject }.map { r ->
                    BarRide(r.moment("departure"), r.moment("arrival"), r.getValue("stops").jsonArray.map { Instant.parse(it.jsonPrimitive.content).toEpochMilli() })
                },
                current = c.number("current").toInt(),
                state = BarState.entries.first { it.name == snakeToPascal(c.text("state")) },
                confidence = c.getValue("confidence").jsonPrimitive.contentOrNull?.let { name -> Confidence.entries.first { it.name == name.uppercase() } },
            )
            val bar = SceneModel.journeyBar(journey, c.moment("now"))
            assertEquals("$what: parts", c.getValue("parts").jsonArray.map { it.jsonPrimitive.content }, bar.parts.map { it.name.lowercase() })
            assertEquals("$what: focus", c.getValue("focus").jsonPrimitive.doubleOrNull?.toInt(), bar.focus)
            near(bar.fraction, c.number("fraction"), "$what: fraction")
            assertEquals("$what: tone", c.getValue("tone").jsonPrimitive.contentOrNull, bar.tone?.name?.lowercase())
            assertEquals("$what: done", c.number("done").toInt(), bar.done)
            assertEquals("$what: walker at the start", c.flag("walkerAtStart"), bar.walkerAtStart)
            val stops = c.getValue("stops").jsonArray.map { it.jsonPrimitive.double }
            assertEquals("$what: stops", stops.size, bar.stops.size)
            stops.forEachIndexed { i, f -> near(bar.stops[i], f, "$what: stop $i") }
        }
    }

    @Test
    fun theBarsFrameNeverMovesTheFocusTakesTheRoomTheOthersAreStubs() {
        for (c in cases("barLayout")) {
            val what = c.text("case")
            val parts = c.getValue("parts").jsonArray.map { p -> BarPart.entries.first { it.name.lowercase() == p.jsonPrimitive.content } }
            val at = SceneModel.barLayout(parts, c.getValue("focus").jsonPrimitive.doubleOrNull?.toInt(), c.number("width"))
            val expected = c.getValue("at").jsonArray.map { it.jsonArray.map { x -> x.jsonPrimitive.double } }
            assertEquals("$what: parts", expected.size, at.size)
            expected.forEachIndexed { i, (from, to) ->
                near(at[i].from, from, "$what: part $i from")
                near(at[i].to, to, "$what: part $i to")
            }
        }
        for (c in cases("trainOnBar")) {
            val kind = TrainKind.entries.first { it.name.lowercase() == c.text("kind") }
            near(SceneModel.trainOnBar(c.number("x"), kind, c.number("scale")), c.number("nose"), "$kind over ${c.number("x")}")
        }
    }

    /** "before_leaving" → "BeforeLeaving": the fixtures' state names, as the web spells them. */
    private fun snakeToPascal(name: String) = name.split('_').joinToString("") { part -> part.replaceFirstChar { it.uppercase() } }

    @Test
    fun aRideIsDrawnAsTheTrainItIs() {
        for (c in cases("trains")) {
            val kind = SceneModel.trainKind(c.getValue("product").jsonPrimitive.contentOrNull, c.getValue("line").jsonPrimitive.contentOrNull, c.flag("bus"))
            assertEquals("${c["product"]} ${c["line"]}", c.text("kind"), kind.name.lowercase())
            near(SceneModel.trainLength(kind), c.number("length"), "$kind length")
        }
        for (c in cases("nose")) {
            val kind = TrainKind.entries.first { it.name.lowercase() == c.text("kind") }
            near(SceneModel.trainNoseX(kind, c.number("scale"), c.number("width")), c.number("x"), "$kind nose")
        }
    }
}
