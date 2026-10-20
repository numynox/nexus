plugins {
    alias(libs.plugins.nexus.android.application)
}

// SDK levels, version, signing and the Supabase settings come from the
// nexus.android.application convention (build-logic/). Only Vibilia's own
// configuration is here: the map key from the environment or the root .env,
// and its block of config.yaml.
android {
    defaultConfig {
        buildConfigField("String", "CARTO_API_KEY", nexus.setting("PUBLIC_CARTO_API_KEY").asBuildConfigString())
        buildConfigField("int", "PRICE_BUCKET_MINUTES", nexus.configInt("vibilia", "price_bucket_minutes", 10))
        buildConfigField("double", "SEARCH_RADIUS_KM", nexus.configDouble("vibilia", "search_radius", 3.0))
    }
}

dependencies {
    implementation(libs.androidx.work.runtime)
    implementation(libs.maplibre.android)

    testImplementation(libs.androidx.work.testing)
}
