package io.github.numynox.nexus.core.data

import android.content.Context
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * What every app's container starts from: the Supabase client with its stored
 * session, sign-in, and the update check. Each app's `AppContainer` holds one
 * and adds its own repositories. [engine] replaces the network in screenshot
 * tests.
 */
class NexusCore(
    context: Context,
    val config: NexusConfig,
    engine: HttpClientEngine? = null,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val supabase = createNexusSupabaseClient(
        config,
        sessionStore = context.getSharedPreferences("supabase_session", Context.MODE_PRIVATE),
        engine = engine,
    )
    val auth = AuthRepository(supabase, scope)
    val updates = UpdateChecker(config, engine)
}
