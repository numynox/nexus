pluginManagement {
    // Conventions shared by every module: SDK levels, versioning, signing, build config.
    includeBuild("build-logic")
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

// Shared by every app, like the websites' packages/: the Supabase client and
// session, the design system and app shell, the in-app update, and the
// screenshot test harness.
include(":core:data")
include(":core:ui")
include(":core:update")
include(":core:testing")

// One application module per Nexus app, named after it.
include(":noctua")
include(":vibilia")
include(":annona")
include(":abeona")
