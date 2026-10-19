plugins {
    `kotlin-dsl`
}

dependencies {
    // compileOnly: the plugins themselves come from the root build's
    // classpath (android/build.gradle.kts), this only compiles against them.
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("nexusApplication") {
            id = "nexus.android.application"
            implementationClass = "NexusApplicationPlugin"
        }
        register("nexusLibrary") {
            id = "nexus.android.library"
            implementationClass = "NexusLibraryPlugin"
        }
    }
}
