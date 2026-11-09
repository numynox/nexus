plugins {
    alias(libs.plugins.nexus.android.library)
}

// The in-app update: the daily check, the download, the check of the
// downloaded APK and its install through PackageInstaller. Its manifest adds
// the permissions, the receiver and WorkManager's foreground service type.
dependencies {
    api(project(":core:data"))
    api(libs.androidx.work.runtime)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}

android {
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}
