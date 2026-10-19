package io.github.numynox.nexus.vibilia

import android.content.Context
import io.github.numynox.nexus.core.data.AuthRepository
import io.github.numynox.nexus.core.data.NexusConfig
import io.github.numynox.nexus.core.data.UpdateChecker
import io.github.numynox.nexus.core.data.createNexusSupabaseClient
import io.github.numynox.nexus.vibilia.data.CarRepository
import io.github.numynox.nexus.vibilia.data.DriveRepository
import io.github.numynox.nexus.vibilia.data.FuelPriceRepository
import io.github.numynox.nexus.vibilia.data.LogbookRepository
import io.github.numynox.nexus.vibilia.data.StatisticsRepository
import io.github.numynox.nexus.vibilia.data.VibiliaPreferences
import io.github.numynox.nexus.vibilia.location.LocationProvider
import io.github.numynox.nexus.vibilia.tracking.DriveTracker
import io.github.numynox.nexus.vibilia.tracking.DriveTracking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Every long-lived object, created once with the application. Plain
 * constructor wiring — the app is small enough that a DI framework would add
 * more than it saves.
 */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val config = NexusConfig(
        appName = "Vibilia",
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY,
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        releaseRepository = BuildConfig.RELEASE_REPOSITORY,
        releaseTagPrefix = "vibilia-android-v",
    )

    val supabase = createNexusSupabaseClient(config)
    val auth = AuthRepository(supabase, scope)
    val preferences = VibiliaPreferences(context)
    val prices = FuelPriceRepository(supabase)
    val cars = CarRepository(supabase)
    val logbook = LogbookRepository(supabase)
    val statistics = StatisticsRepository(supabase)
    val drives = DriveRepository(supabase)
    val locations = LocationProvider(context)
    val updates = UpdateChecker(config)
    val driveTracking = DriveTracking(context, DriveTracker(context), preferences, cars)
}
