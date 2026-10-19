package io.github.numynox.nexus.vibilia

import android.app.Application
import org.maplibre.android.MapLibre

open class VibiliaApplication : Application() {
    lateinit var container: AppContainer
        private set

    /** False where MapLibre's native library cannot load — the JVM screenshot tests. */
    open val mapsAvailable: Boolean = true

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
        if (mapsAvailable) MapLibre.getInstance(this)
    }

    protected open fun createContainer(): AppContainer = AppContainer(this)
}
