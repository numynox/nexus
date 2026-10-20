package io.github.numynox.nexus.core.testing

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import io.github.numynox.nexus.core.data.NexusConfig
import io.github.numynox.nexus.core.data.NexusJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

/**
 * The pieces of a fake Supabase every app's screenshot tests need: a config
 * pointing nowhere, a signed-in session, and an HTTP engine that answers from
 * the app's own routing. Anything not routed is an empty JSON array, which is
 * what PostgREST says about a table with no matching rows.
 */
object FakeSupabase {
    const val USER_ID = "11111111-1111-4111-8111-111111111111"
    const val EMAIL = "jannis@example.com"

    fun config(appName: String) = NexusConfig(
        appName = appName,
        supabaseUrl = "https://fake.supabase.co",
        supabaseAnonKey = "fake-anon-key",
        versionName = "0.0.42",
        versionCode = 42,
        releaseRepository = "numynox/nexus",
        releaseTagPrefix = "${appName.lowercase()}-android-v",
    )

    /** A session as GoTrue returns it, decoded by supabase-kt's own serializer. */
    val sessionJson = """
        {"access_token": "fake.jwt.token", "refresh_token": "fake-refresh", "expires_in": 31536000, "token_type": "bearer",
         "user": {"id": "$USER_ID", "aud": "authenticated", "email": "$EMAIL", "app_metadata": {}, "user_metadata": {}}}
    """.trimIndent()

    /** Signs [supabase] in without a network round trip. */
    suspend fun signIn(supabase: SupabaseClient) {
        supabase.auth.importSession(NexusJson.decodeFromString<UserSession>(sessionJson), autoRefresh = false)
    }

    /**
     * An engine answering through [route], which returns a JSON body, a
     * [Reply] when headers matter, or null for "not mine". GitHub (the update
     * check) and auth are answered here.
     */
    fun engine(route: (request: HttpRequestData, path: String) -> Any?) = MockEngine { request ->
        val path = request.url.encodedPath
        val reply = when {
            request.url.host == "api.github.com" -> Reply("[]")
            path.startsWith("/auth/") -> Reply("{}")
            else -> when (val answer = route(request, path)) {
                is Reply -> answer
                null -> Reply("[]")
                else -> Reply(answer.toString())
            }
        }
        val headers = headersOf(
            *(reply.headers + (HttpHeaders.ContentType to "application/json")).map { (key, value) -> key to listOf(value) }.toTypedArray(),
        )
        respond(reply.body, HttpStatusCode.OK, headers)
    }

    /** A response with headers — e.g. PostgREST's `Content-Range`, which carries a count. */
    data class Reply(val body: String, val headers: Map<String, String> = emptyMap())

    /** What PostgREST answers to a `head` request with `count=exact`. */
    fun count(n: Int) = Reply("", mapOf("Content-Range" to "*/$n"))

    /** The `select` a request asked for, to tell apart queries on the same table. */
    fun HttpRequestData.select(): String = url.parameters["select"].orEmpty()
}
