package io.github.numynox.nexus.core.data

import android.content.SharedPreferences
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SettingsCodeVerifierCache
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
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
): SupabaseClient {
    var created: SupabaseClient? = null
    return createSupabaseClient(
        supabaseUrl = config.supabaseUrl.ifBlank { "https://not-configured.invalid" },
        supabaseKey = config.supabaseAnonKey.ifBlank { "not-configured" },
    ) {
        defaultSerializer = KotlinXSerializer(NexusJson)
        // Named explicitly rather than discovered through ServiceLoader, which
        // R8 can strip from a release build.
        httpEngine = engine ?: OkHttp.create()
        // Marked internal by supabase-kt, but the only way to put a plugin in
        // front of every request; a change there fails this build, not a phone.
        @OptIn(SupabaseInternal::class)
        httpConfig { install(sessionGate { created }) }
        install(Auth) {
            val settings = SharedPreferencesSettings(sessionStore)
            sessionManager = SettingsSessionManager(settings)
            codeVerifierCache = SettingsCodeVerifierCache(settings)
        }
        install(Postgrest)
        install(Functions)
    }.also { created = it }
}

/** How long a data request waits for an expired session to be refreshed. */
private val SessionWait = 15.seconds

/** The session could not be refreshed in time — almost always no network yet. */
class SessionUnavailableException : IOException("Could not reach the server to renew your session.")

/**
 * Holds every data request — PostgREST and Edge Functions, not sign-in —
 * until the session is usable, and sends it with the token current then.
 *
 * While a stored session is being loaded, or an expired one refreshed after a
 * while in the background, supabase-kt has no token to give, and a request
 * built meanwhile goes out with only the anon key. RLS does not refuse that:
 * it answers with nothing, and an app would show "no sections" or "no
 * vehicles" to someone who has plenty. Waiting turns that into either the real
 * answer or, if the refresh does not get through, a network error to retry.
 */
private fun sessionGate(client: () -> SupabaseClient?) = createClientPlugin("NexusSessionGate") {
    on(Send) { request ->
        val supabase = client()
        val api = request.url.pathSegments.firstOrNull { it.isNotEmpty() }
        if (supabase != null && (api == "rest" || api == "functions")) {
            val status = withTimeoutOrNull(SessionWait) {
                supabase.auth.sessionStatus.first { it !is SessionStatus.Initializing && it !is SessionStatus.RefreshFailure }
            } ?: throw SessionUnavailableException()
            if (status is SessionStatus.Authenticated) {
                request.headers[HttpHeaders.Authorization] = "Bearer ${status.session.accessToken}"
            }
        }
        proceed(request)
    }
}
