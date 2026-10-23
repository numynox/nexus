plugins {
    alias(libs.plugins.nexus.android.application)
}

// SDK levels, version, signing and the Supabase settings come from the
// nexus.android.application convention (build-logic/). Annona has no
// settings of its own in config.yaml.

dependencies {
    // The barcode scanner: CameraX frames, decoded by zxing-cpp — open
    // source and on-device, nothing from Google Play services.
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.cpp)
    // Product photos from Open Food Facts.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // The daily expiry reminder.
    implementation(libs.androidx.work.runtime)
    // The home screen widget.
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    testImplementation(libs.coil.test)
    testImplementation(libs.androidx.work.testing)
}
