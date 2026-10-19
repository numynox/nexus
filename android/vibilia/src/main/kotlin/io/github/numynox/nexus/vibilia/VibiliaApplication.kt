package io.github.numynox.nexus.vibilia

import android.app.Application
import org.maplibre.android.MapLibre

class VibiliaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        MapLibre.getInstance(this)
    }
}
