package io.github.numynox.nexus.abeona

import android.content.Context
import io.github.numynox.nexus.abeona.data.AbeonaPreferences
import io.github.numynox.nexus.abeona.data.AbeonaRepository
import io.github.numynox.nexus.abeona.data.OfflinePack
import io.github.numynox.nexus.abeona.data.TripStore
import io.github.numynox.nexus.core.data.NexusConfig
import io.github.numynox.nexus.core.data.NexusCore
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

/** Every long-lived object, wired by hand like the other apps'. [httpEngine] replaces the network in screenshot tests. */
class AppContainer(
    context: Context,
    httpEngine: HttpClientEngine? = null,
    configOverride: NexusConfig? = null,
) {
    val core = NexusCore(
        context,
        configOverride ?: NexusConfig(
            appName = "Abeona",
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            releaseRepository = BuildConfig.RELEASE_REPOSITORY,
            releaseTagPrefix = BuildConfig.RELEASE_TAG_PREFIX,
        ),
        httpEngine,
    )
    val preferences = AbeonaPreferences(context)
    val repository = AbeonaRepository(
        core.supabase,
        core.config,
        httpEngine?.let { HttpClient(it) } ?: HttpClient(OkHttp),
    )
    val pack = OfflinePack(context)
    val trips = TripStore(repository, pack, core.scope)
}
