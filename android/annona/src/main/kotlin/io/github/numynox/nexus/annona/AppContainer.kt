package io.github.numynox.nexus.annona

import android.content.Context
import io.github.numynox.nexus.annona.data.AnnonaPreferences
import io.github.numynox.nexus.annona.data.AnnonaRepository
import io.github.numynox.nexus.core.data.NexusConfig
import io.github.numynox.nexus.core.data.NexusCore
import io.ktor.client.engine.HttpClientEngine

/** Every long-lived object, wired by hand like the other apps'. [httpEngine] replaces the network in screenshot tests. */
class AppContainer(
    context: Context,
    httpEngine: HttpClientEngine? = null,
    configOverride: NexusConfig? = null,
) {
    val core = NexusCore(
        context,
        configOverride ?: NexusConfig(
            appName = "Annona",
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            releaseRepository = BuildConfig.RELEASE_REPOSITORY,
            releaseTagPrefix = BuildConfig.RELEASE_TAG_PREFIX,
        ),
        httpEngine,
    )
    val preferences = AnnonaPreferences(context)
    val repository = AnnonaRepository(core.supabase)
}
