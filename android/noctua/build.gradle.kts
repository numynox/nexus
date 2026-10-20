plugins {
    alias(libs.plugins.nexus.android.application)
}

// SDK levels, version, signing and the Supabase settings come from the
// nexus.android.application convention (build-logic/). Only Noctua's own
// configuration is here: its block of config.yaml, read the same way the
// website reads it.
android {
    defaultConfig {
        buildConfigField("int", "ARTICLE_FETCH_LIMIT", nexus.configInt("noctua", "article_fetch_limit", 300))
        buildConfigField("int", "STATISTICS_WEEKS", nexus.configInt("noctua", "statistics_weeks", 8))
    }
}

dependencies {
    // Articles open in a Custom Tab: the user's browser, inside the app.
    implementation(libs.androidx.browser)
    // Article thumbnails and feed favicons.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.coil.test)
}
