package io.github.numynox.nexus.abeona

import android.content.Context
import io.github.numynox.nexus.abeona.data.AbeonaPreferences
import io.github.numynox.nexus.abeona.data.AbeonaRepository
import io.github.numynox.nexus.abeona.data.OfflinePack
import io.github.numynox.nexus.abeona.data.TripStore
import io.github.numynox.nexus.abeona.live.OnboardSource
import io.github.numynox.nexus.abeona.live.OnboardWifi
import io.github.numynox.nexus.core.data.NexusConfig
import io.github.numynox.nexus.core.data.NexusCore
import io.github.numynox.nexus.core.update.AppUpdates
import io.ktor.client.engine.HttpClientEngine

/**
 * Every long-lived object, wired by hand like the other apps'. [httpEngine]
 * replaces the network in screenshot tests, [onboardSource] the train's Wi-Fi.
 */
class AppContainer(
    context: Context,
    httpEngine: HttpClientEngine? = null,
    configOverride: NexusConfig? = null,
    onboardSource: OnboardSource? = null,
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
    val updates = AppUpdates(context, core.config, core.updates)
    val preferences = AbeonaPreferences(context)
    val repository = AbeonaRepository(core.supabase)
    val pack = OfflinePack(context)
    val trips = TripStore(repository, pack, core.scope)
    /** The train's own Wi-Fi (ICE Portal, ÖBB Railnet), asked during a trip. */
    val onboard: OnboardSource = onboardSource ?: OnboardWifi(context)
}
