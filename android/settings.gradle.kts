pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "nexus-android"

// Shared by every app: the Supabase client and session, and the design system.
include(":core:data")
include(":core:ui")

// One application module per Nexus app. Noctua and Annona join here later.
include(":vibilia")
