plugins {
    alias(libs.plugins.nexus.android.library)
}

// The screenshot harness every app's tests share: a fake Supabase and the
// steps of walking an app with Robolectric and photographing it.
// testImplementation only — never part of an APK.
dependencies {
    implementation(project(":core:data"))
    api(platform(libs.compose.bom))
    api(libs.junit)
    api(libs.robolectric)
    api(libs.roborazzi)
    api(libs.roborazzi.compose)
    api(libs.compose.ui.test.junit4)
    api(libs.ktor.client.mock)
    api(libs.kotlinx.coroutines.test)
}
