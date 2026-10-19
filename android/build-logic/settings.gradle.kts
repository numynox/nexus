// Gradle conventions shared by every module of the Android build: one place
// for SDK levels, versioning, signing and build-time configuration, so the
// three apps cannot drift apart.

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
