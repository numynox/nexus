package io.github.numynox.nexus.noctua

import android.content.Context
import io.github.numynox.nexus.core.data.NexusConfig
import io.github.numynox.nexus.core.data.NexusCore
import io.github.numynox.nexus.noctua.data.NoctuaPreferences
import io.github.numynox.nexus.noctua.data.NoctuaRepository
import io.ktor.client.engine.HttpClientEngine

/**
 * Every long-lived object, created once with the application, wired by hand
 * like Vibilia's. [httpEngine] replaces the network in screenshot tests.
 */
class AppContainer(
    context: Context,
    httpEngine: HttpClientEngine? = null,
    configOverride: NexusConfig? = null,
) {
    val core = NexusCore(
        context,
        configOverride ?: NexusConfig(
            appName = "Noctua",
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            releaseRepository = BuildConfig.RELEASE_REPOSITORY,
            releaseTagPrefix = BuildConfig.RELEASE_TAG_PREFIX,
        ),
        httpEngine,
    )
    val preferences = NoctuaPreferences(context)
    val repository = NoctuaRepository(core.supabase)
}
