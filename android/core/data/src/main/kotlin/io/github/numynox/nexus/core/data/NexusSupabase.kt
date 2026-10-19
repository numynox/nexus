package io.github.numynox.nexus.core.data

import io.github.jan.supabase.SupabaseClient
import android.content.SharedPreferences
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SettingsCodeVerifierCache
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.Json

/**
 * One JSON configuration for every row the apps read and write. Lenient on
 * reading: rows are hand-written data classes, not generated types
 * (decisions/2026-09-04-permissive-supabase-client-generics.md), so a column
 * added to a table must not break an installed app that does not know it yet.
 * Explicit on writing: nulls and defaults are always sent, so clearing a field
 * in an update clears the column instead of silently leaving it alone.
 */
val NexusJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    encodeDefaults = true
}

/**
 * The Supabase client every app uses — the Android counterpart of each web
 * app's `lib/supabase.ts`. The session is persisted by supabase-kt and
 * refreshed automatically, so an app opens signed in. The session is kept in
 * [sessionStore], handed in explicitly rather than found through supabase-kt's
 * App Startup hook. [engine] replaces the network, for the screenshot tests'
 * fake backend.
 */
fun createNexusSupabaseClient(
    config: NexusConfig,
    sessionStore: SharedPreferences,
    engine: HttpClientEngine? = null,
): SupabaseClient =
    createSupabaseClient(
        supabaseUrl = config.supabaseUrl.ifBlank { "https://not-configured.invalid" },
        supabaseKey = config.supabaseAnonKey.ifBlank { "not-configured" },
    ) {
        defaultSerializer = KotlinXSerializer(NexusJson)
        // Named explicitly rather than discovered through ServiceLoader, which
        // R8 can strip from a release build.
        httpEngine = engine ?: OkHttp.create()
        install(Auth) {
            val settings = SharedPreferencesSettings(sessionStore)
            sessionManager = SettingsSessionManager(settings)
            codeVerifierCache = SettingsCodeVerifierCache(settings)
        }
        install(Postgrest)
        install(Functions)
    }
