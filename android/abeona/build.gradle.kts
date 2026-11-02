plugins {
    alias(libs.plugins.nexus.android.application)
}

// SDK levels, version, signing and the Supabase settings come from the
// nexus.android.application convention (build-logic/). Abeona adds the map
// key the websites use; it has no settings of its own in config.yaml.
android {
    defaultConfig {
        buildConfigField("String", "CARTO_API_KEY", nexus.setting("PUBLIC_CARTO_API_KEY").asBuildConfigString())
    }
}

dependencies {
    // Ticket upload and download: Storage's REST API over a plain Ktor client
    // (core's Supabase client has no Storage plugin installed).
    implementation(libs.ktor.client.okhttp)
    // The live companion's auto-start and the widget's periodic refresh.
    implementation(libs.androidx.work.runtime)
    // The home screen widget.
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    // The trip map.
    implementation(libs.maplibre.android)

    testImplementation(libs.androidx.work.testing)
}
