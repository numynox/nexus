package io.github.numynox.nexus.core.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val userId: String, val email: String?) : AuthState
}

/**
 * Sign-in only, like the websites: accounts are created in the Supabase
 * dashboard, so there is no sign-up.
 */
class AuthRepository(
    private val supabase: SupabaseClient,
    scope: CoroutineScope,
) {
    val state: StateFlow<AuthState> = supabase.auth.sessionStatus
        .map { status ->
            when (status) {
                is SessionStatus.Initializing -> AuthState.Loading
                is SessionStatus.Authenticated -> signedIn() ?: AuthState.SignedOut
                // A refresh that failed because the phone is offline must not
                // sign anybody out: the stored session is still theirs.
                is SessionStatus.RefreshFailure -> signedIn() ?: AuthState.SignedOut
                is SessionStatus.NotAuthenticated -> AuthState.SignedOut
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, AuthState.Loading)

    val currentUserId: String?
        get() = supabase.auth.currentUserOrNull()?.id

    /** For background work: waits for the stored session to load first. */
    suspend fun awaitUserId(): String? {
        supabase.auth.awaitInitialization()
        return currentUserId
    }

    suspend fun signIn(email: String, password: String) {
        supabase.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signOut() {
        supabase.auth.signOut()
    }

    private fun signedIn(): AuthState.SignedIn? =
        supabase.auth.currentUserOrNull()?.let { AuthState.SignedIn(it.id, it.email) }
}
