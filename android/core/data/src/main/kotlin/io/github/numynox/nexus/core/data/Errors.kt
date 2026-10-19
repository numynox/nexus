package io.github.numynox.nexus.core.data

import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import kotlinx.serialization.json.JsonPrimitive

/**
 * Turns anything thrown into something worth showing a person — the
 * counterpart of `describeError` in `packages/ui/src/errors.js`, with the same
 * rule: Postgres' details and hint are where it says what to do, so they are
 * kept.
 */
fun Throwable.describe(): String = when (this) {
    is PostgrestRestException -> listOfNotNull(
        error.takeIf { it.isNotBlank() },
        (details as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" },
        hint?.takeIf { it.isNotBlank() },
    ).joinToString(" — ").ifBlank { "The database refused the request." }
    is AuthRestException -> errorDescription.ifBlank { error }
    is RestException -> description?.takeIf { it.isNotBlank() } ?: error
    is HttpRequestTimeoutException -> "The server took too long to answer."
    is HttpRequestException, is IOException -> "Could not reach the server. Check your connection."
    else -> message?.takeIf { it.isNotBlank() } ?: "Something went wrong."
}

/** True for failures worth retrying later rather than reporting. */
fun Throwable.isNetworkFailure(): Boolean =
    this is HttpRequestException || this is IOException || this is HttpRequestTimeoutException
