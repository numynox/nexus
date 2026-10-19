package io.github.numynox.nexus.core.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
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
 * refreshed automatically, so an app opens signed in.
 */
fun createNexusSupabaseClient(config: NexusConfig): SupabaseClient =
    createSupabaseClient(
        supabaseUrl = config.supabaseUrl.ifBlank { "https://not-configured.invalid" },
        supabaseKey = config.supabaseAnonKey.ifBlank { "not-configured" },
    ) {
        defaultSerializer = KotlinXSerializer(NexusJson)
        install(Auth)
        install(Postgrest)
        install(Functions)
    }
