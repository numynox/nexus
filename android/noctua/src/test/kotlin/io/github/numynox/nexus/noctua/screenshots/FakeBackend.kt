package io.github.numynox.nexus.noctua.screenshots

import io.github.numynox.nexus.core.testing.FakeSupabase
import io.github.numynox.nexus.core.testing.FakeSupabase.select
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * A Supabase that answers every query Noctua makes with plausible data: three
 * sections over seven feeds, two days of articles, one story told by three
 * feeds, a reading history for the statistics. Times are relative to now.
 */
object FakeBackend {
    val config = FakeSupabase.config("Noctua")

    private val now = Instant.now().truncatedTo(ChronoUnit.MINUTES)

    private data class FakeFeed(val id: Long, val name: String, val host: String)

    private val feeds = listOf(
        FakeFeed(1, "Tagesschau", "tagesschau.de"),
        FakeFeed(2, "ZDF heute", "zdf.de"),
        FakeFeed(3, "The Guardian", "theguardian.com"),
        FakeFeed(4, "Heise", "heise.de"),
        FakeFeed(5, "Ars Technica", "arstechnica.com"),
        FakeFeed(6, "The Verge", "theverge.com"),
        FakeFeed(7, "Quanta Magazine", "quantamagazine.org"),
        FakeFeed(8, "NASA", "nasa.gov"),
    )

    private data class Sec(val id: Long, val title: String, val icon: String, val feeds: List<Long>)

    private val sections = listOf(
        Sec(10, "News", "newspaper", listOf(1, 2, 3)),
        Sec(11, "Tech", "cpu", listOf(4, 5, 6)),
        Sec(12, "Science", "atom", listOf(7, 8)),
    )

    private data class Story(val id: Long, val feed: Long, val minutesAgo: Long, val title: String, val summary: String, val image: Boolean = true)

    private val stories = listOf(
        Story(501, 1, 25, "EU agrees on the first binding rules for general-purpose AI models", "After a night of negotiations, member states and parliament settle on transparency duties for the largest models."),
        Story(502, 2, 40, "Brussels reaches deal on AI rulebook", "The compromise obliges providers of large models to disclose training data summaries."),
        Story(503, 3, 55, "EU strikes landmark deal on regulating artificial intelligence", "Negotiators agreed on rules for foundation models after 36 hours of talks."),
        Story(504, 4, 70, "Linux 7.2 released with a new scheduler for hybrid CPUs", "Linus Torvalds has released the new kernel; the scheduler now knows efficiency cores apart."),
        Story(505, 5, 95, "Inside the factory that recycles a million phone batteries a year", "Lithium, cobalt and nickel come back out — and the economics are finally working."),
        Story(506, 7, 130, "A new proof settles a 90-year-old question about prime gaps", "Mathematicians show that gaps between primes behave as randomly as long suspected.", image = false),
        Story(507, 6, 160, "The best Android phones you can buy right now", "From flagship cameras to week-long batteries, these are our picks this autumn."),
        Story(508, 8, 210, "Webb spots carbon dioxide in the atmosphere of a rocky exoplanet", "The detection is the clearest sign yet of an atmosphere around a planet the size of Earth."),
        Story(509, 1, 260, "Deutsche Bahn reports punctuality up for the third month running", "Long-distance trains arrived on time 71 percent of the time in September.", image = false),
        Story(510, 4, 320, "Router maker patches critical flaw used in botnet attacks", "Owners should install the firmware update now; the vulnerability is being exploited."),
        Story(511, 3, 1_500, "Heatwaves are getting longer across southern Europe, study finds", "Researchers looked at 70 years of temperature records from 600 weather stations."),
        Story(512, 5, 1_560, "Why your next laptop might run on a RISC-V chip", "The open instruction set is moving from microcontrollers to the desktop."),
        Story(513, 7, 1_620, "How birds navigate by sensing Earth's magnetic field", "A protein in their eyes may let them see magnetic north."),
        Story(514, 2, 1_700, "Bundestag debates new rules for rental prices", "The coalition wants to extend the rent cap until 2029."),
        Story(515, 6, 1_800, "Hands-on with the new e-ink tablets for note-taking", "Three devices, one week, and a lot of handwriting.", image = false),
        Story(516, 8, 2_950, "Artemis crew completes final rehearsal before lunar flyby", "The four astronauts spent twelve hours in the capsule on the launch pad."),
    )

    /** One story told by three feeds — collapsed to one card. */
    private val groups = mapOf(501L to 501L, 502L to 501L, 503L to 501L)
    private val reads = mapOf(504L to 50L, 509L to 200L, 512L to 1_400L)
    private val starred = listOf(506L, 511L, 508L)

    fun engine() = FakeSupabase.engine { request, path ->
        val select = request.select()
        when {
            path.endsWith("/rpc/get_similar_article_groups") -> buildJsonArray {
                groups.forEach { (article, key) -> addJsonObject { put("article_id", article); put("group_key", key) } }
            }
            path.endsWith("/rest/v1/sections") -> sectionsJson()
            path.endsWith("/rest/v1/feeds") -> buildJsonArray { feeds.forEach { add(feedJson(it)) } }
            path.endsWith("/rest/v1/articles") && request.method.value == "HEAD" -> FakeSupabase.count(1_284)
            path.endsWith("/rest/v1/articles") && select.contains("feeds(") -> publishedJson()
            path.endsWith("/rest/v1/articles") -> {
                // PostgREST's `feed_id=in.(1,2,3)`, the feeds of the chosen section.
                val wanted = request.url.parameters["feed_id"]?.removePrefix("in.(")?.removeSuffix(")")?.split(",")?.mapNotNull { it.toLongOrNull() }
                buildJsonArray { stories.filter { wanted == null || it.feed in wanted }.forEach { add(articleJson(it, embedFeed = false)) } }
            }
            path.endsWith("/rest/v1/article_reads") && select.contains("articles(feed_id") -> readHistoryJson()
            path.endsWith("/rest/v1/article_reads") && select.contains("articles(") -> buildJsonArray {
                reads.entries.sortedBy { it.value }.forEach { (id, minutes) ->
                    addJsonObject {
                        put("article_id", id); put("read_at", now.minus(minutes, ChronoUnit.MINUTES).toString())
                        put("articles", articleJson(stories.first { it.id == id }, embedFeed = true))
                    }
                }
            }
            path.endsWith("/rest/v1/article_reads") -> buildJsonArray {
                reads.forEach { (id, minutes) -> addJsonObject { put("article_id", id); put("read_at", now.minus(minutes, ChronoUnit.MINUTES).toString()) } }
            }
            path.endsWith("/rest/v1/article_stars") -> buildJsonArray {
                starred.forEachIndexed { index, id ->
                    addJsonObject {
                        put("article_id", id); put("starred_at", now.minus(30L + index * 400, ChronoUnit.MINUTES).toString())
                        if (select.contains("articles(")) put("articles", articleJson(stories.first { it.id == id }, embedFeed = true))
                    }
                }
            }
            else -> null
        }
    }

    private fun feedJson(feed: FakeFeed) = buildJsonObject {
        put("id", feed.id); put("name", feed.name); put("url", "https://${feed.host}/feed"); put("icon", "https://${feed.host}/favicon.ico"); put("enabled", true)
    }

    private fun sectionsJson() = buildJsonArray {
        sections.forEachIndexed { index, section ->
            addJsonObject {
                put("id", section.id); put("title", section.title); put("icon", section.icon); put("sort_order", index)
                putJsonArray("sections_feeds") {
                    section.feeds.forEachIndexed { position, id ->
                        addJsonObject { put("sort_order", position); put("feed_id", id); put("feeds", feedJson(feeds.first { it.id == id })) }
                    }
                }
            }
        }
    }

    private fun articleJson(story: Story, embedFeed: Boolean): JsonObject = buildJsonObject {
        val feed = feeds.first { it.id == story.feed }
        put("id", story.id); put("feed_id", story.feed); put("title", story.title)
        put("url", "https://${feed.host}/articles/${story.id}")
        put("published_at", now.minus(story.minutesAgo, ChronoUnit.MINUTES).toString())
        put("summary", "<p>${story.summary}</p>")
        if (story.image) put("image_url", "https://${feed.host}/images/${story.id}.jpg")
        putJsonArray("tags") {}
        if (embedFeed) putJsonObject("feeds") { put("id", feed.id); put("name", feed.name); put("icon", "https://${feed.host}/favicon.ico") }
    }

    /** Eight weeks of reading: mornings and evenings, more on weekdays, a streak up to today. */
    private fun readHistoryJson() = buildJsonArray {
        var id = 10_000L
        for (day in 0L until 56L) {
            val date = now.minus(day, ChronoUnit.DAYS)
            if (day > 9 && day % 6 == 0L) continue
            val perDay = 2 + ((day * 7) % 5).toInt()
            repeat(perDay) { n ->
                val hour = listOf(7, 8, 12, 19, 21, 22)[(n + day.toInt()) % 6]
                val at = date.truncatedTo(ChronoUnit.DAYS).plus(hour.toLong(), ChronoUnit.HOURS).plus((n * 7).toLong(), ChronoUnit.MINUTES)
                if (at.isAfter(now)) return@repeat
                addJsonObject {
                    put("article_id", id++); put("read_at", at.toString())
                    putJsonObject("articles") {
                        val feed = feeds[abs((day * 3 + n * 5).toInt()) % if (n % 3 == 0) 3 else feeds.size]
                        put("feed_id", feed.id); putJsonObject("feeds") { put("name", feed.name) }
                    }
                }
            }
        }
    }

    private fun publishedJson() = buildJsonArray {
        for (hourAgo in 0L until 56L * 24L) {
            val count = when ((hourAgo + 2) % 24) { in 6..9 -> 3; in 10..18 -> 2; in 19..22 -> 1; else -> 0 }
            repeat(count) { n ->
                addJsonObject {
                    put("published_at", now.minus(hourAgo, ChronoUnit.HOURS).minus((n * 11).toLong(), ChronoUnit.MINUTES).toString())
                    putJsonObject("feeds") { put("name", feeds[((hourAgo + n) % feeds.size).toInt()].name) }
                }
            }
        }
    }
}
