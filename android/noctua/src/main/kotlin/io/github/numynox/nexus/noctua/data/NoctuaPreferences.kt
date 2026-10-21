package io.github.numynox.nexus.noctua.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.numynox.nexus.core.data.NexusJson
import io.github.numynox.nexus.core.ui.theme.ThemeMode
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.noctuaStore by preferencesDataStore(name = "noctua")

/**
 * How the feed draws an article: the web's full-width image cards (Stories),
 * cards with a thumbnail (Comfortable), or a title-and-time list for scanning a
 * few hundred (Compact). The stored names are the enum's, so never rename one.
 */
enum class Density { Stories, Comfortable, Compact }

/** Newest first for triage, oldest first for reading through a backlog. */
enum class SortOrder { Newest, Oldest }

data class NoctuaSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    /** Seen articles are left out of the feed on its next load. */
    val hideSeen: Boolean = true,
    /** Articles scrolled past count as seen. */
    val autoMarkSeen: Boolean = true,
    val density: Density = Density.Stories,
    val sortOrder: SortOrder = SortOrder.Newest,
    /** Articles open in a Custom Tab rather than switching to the browser app. */
    val openInApp: Boolean = true,
    /** The section the feed shows; null is every section at once. */
    val selectedSectionId: Long? = null,
)

/**
 * Device-local preferences — the counterpart of the web app's lib/storage.ts
 * (decisions/2026-04-06-device-local-preferences-in-localstorage.md),
 * including "seen", which is per device and disposable, unlike "read"
 * (glossary.md). Every value is validated on read.
 */
class NoctuaPreferences(private val context: Context) {

    private object Keys {
        val ThemeMode = stringPreferencesKey("theme_mode")
        val DynamicColor = booleanPreferencesKey("dynamic_color")
        val HideSeen = booleanPreferencesKey("hide_seen")
        val AutoMarkSeen = booleanPreferencesKey("auto_mark_seen")
        val Density = stringPreferencesKey("density")
        val SortOrder = stringPreferencesKey("sort_order")
        val OpenInApp = booleanPreferencesKey("open_in_app")
        val SelectedSection = longPreferencesKey("selected_section_id")
        val Seen = stringSetPreferencesKey("seen_articles")
        val CachedSections = stringPreferencesKey("cached_sections")
    }

    val settings: Flow<NoctuaSettings> = context.noctuaStore.data.map { it.toSettings() }

    /** Article ids seen on this phone. */
    val seen: Flow<Set<Long>> = context.noctuaStore.data.map { prefs ->
        prefs[Keys.Seen].orEmpty().mapNotNullTo(HashSet()) { it.toLongOrNull() }
    }

    private fun Preferences.toSettings() = NoctuaSettings(
        themeMode = ThemeMode.entries.firstOrNull { it.name == this[Keys.ThemeMode] } ?: ThemeMode.System,
        dynamicColor = this[Keys.DynamicColor] ?: false,
        hideSeen = this[Keys.HideSeen] ?: true,
        autoMarkSeen = this[Keys.AutoMarkSeen] ?: true,
        density = Density.entries.firstOrNull { it.name == this[Keys.Density] } ?: Density.Stories,
        sortOrder = SortOrder.entries.firstOrNull { it.name == this[Keys.SortOrder] } ?: SortOrder.Newest,
        openInApp = this[Keys.OpenInApp] ?: true,
        selectedSectionId = this[Keys.SelectedSection]?.takeIf { it > 0 },
    )

    suspend fun current(): NoctuaSettings = settings.first()

    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.ThemeMode] = value.name }
    suspend fun setDynamicColor(value: Boolean) = edit { it[Keys.DynamicColor] = value }
    suspend fun setHideSeen(value: Boolean) = edit { it[Keys.HideSeen] = value }
    suspend fun setAutoMarkSeen(value: Boolean) = edit { it[Keys.AutoMarkSeen] = value }
    suspend fun setDensity(value: Density) = edit { it[Keys.Density] = value.name }
    suspend fun setSortOrder(value: SortOrder) = edit { it[Keys.SortOrder] = value.name }
    suspend fun setOpenInApp(value: Boolean) = edit { it[Keys.OpenInApp] = value }
    suspend fun setSelectedSection(id: Long?) = edit { if (id == null) it.remove(Keys.SelectedSection) else it[Keys.SelectedSection] = id }

    /**
     * Adds to the seen set in one write. The set is capped: ids only grow, so
     * the oldest are dropped first, and the server prunes unread articles after
     * 90 days anyway.
     */
    suspend fun markSeen(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        edit { prefs ->
            val current = prefs[Keys.Seen].orEmpty().mapNotNullTo(HashSet()) { it.toLongOrNull() }
            if (current.containsAll(ids)) return@edit
            current += ids
            prefs[Keys.Seen] = current.sortedDescending().take(MAX_SEEN).mapTo(HashSet()) { it.toString() }
        }
    }

    suspend fun clearSeen() = edit { it.remove(Keys.Seen) }

    /**
     * The sections as last loaded, for [userId] only — so the feed's header and
     * chips are there at once on the next start, before the network answers.
     * A cache, not a source: the next load always replaces it, and a damaged
     * or another user's entry reads as nothing.
     */
    suspend fun cachedSections(userId: String): List<Section>? {
        val raw = context.noctuaStore.data.first()[Keys.CachedSections] ?: return null
        return runCatching { NexusJson.decodeFromString<CachedSections>(raw) }.getOrNull()
            ?.takeIf { it.userId == userId }
            ?.sections?.map { it.toSection() }
    }

    suspend fun cacheSections(userId: String, sections: List<Section>) = edit { prefs ->
        prefs[Keys.CachedSections] = NexusJson.encodeToString(CachedSections(userId, sections.map(CachedSection::of)))
    }

    suspend fun clearCachedSections() = edit { it.remove(Keys.CachedSections) }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.noctuaStore.edit(block)
    }

    private companion object {
        const val MAX_SEEN = 5_000
    }
}

@Serializable
private data class CachedSections(val userId: String, val sections: List<CachedSection>)

@Serializable
private data class CachedSection(val id: Long, val name: String, val icon: String, val sortOrder: Int, val feeds: List<Feed>) {
    fun toSection() = Section(id, name, icon, sortOrder, feeds)

    companion object {
        fun of(section: Section) = CachedSection(section.id, section.name, section.icon, section.sortOrder, section.feeds)
    }
}
