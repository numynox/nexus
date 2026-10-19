package io.github.numynox.nexus.core.data

/**
 * What an app tells the shared modules about itself. Every value is baked in
 * at build time (the app's BuildConfig), the same way the websites inline
 * `PUBLIC_SUPABASE_*` — there is no runtime configuration.
 */
data class NexusConfig(
    val appName: String,
    val supabaseUrl: String,
    val supabaseAnonKey: String,
    val versionName: String,
    val versionCode: Int,
    /** GitHub `owner/repo` whose releases carry this app's APK. */
    val releaseRepository: String,
    /** This app's release tags start with this, e.g. `vibilia-android-v`. */
    val releaseTagPrefix: String,
) {
    /** False for a build made without Supabase credentials; the app says so instead of failing obscurely. */
    val isConfigured: Boolean
        get() = supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()
}
