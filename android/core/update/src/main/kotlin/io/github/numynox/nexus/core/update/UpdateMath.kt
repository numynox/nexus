package io.github.numynox.nexus.core.update

/**
 * Whether the check on coming to the front is due: always the first time
 * ([lastCheck] null), then again once [interval] milliseconds have passed.
 * Clock values are elapsed-realtime milliseconds.
 */
internal fun checkDue(lastCheck: Long?, now: Long, interval: Long): Boolean =
    lastCheck == null || now - lastCheck >= interval || now < lastCheck

/** How far a download is, 0–100, or null when the server sent no length. */
internal fun percentOf(read: Long, total: Long): Int? =
    if (total <= 0) null else (read * 100 / total).toInt().coerceIn(0, 100)

/** What to do with a downloaded APK. */
internal enum class ApkVerdict {
    /** This app, newer, signed with the same key. */
    Install,

    /**
     * This app and its key, but not newer — usually the worker running again
     * in the new process after the install replaced the old one. Ends quietly.
     */
    NotNewer,

    /** Another package, another key, or not readable: it cannot update this app. */
    Rejected,
}

/**
 * The three checks before an install. Signers are compared as sets of the
 * certificates' bytes, the way Android compares them on an update. Where the
 * platform cannot read an archive's signers, the key is left to Android,
 * which refuses an update signed with another key in any case (and keeps the
 * installed app and its data).
 */
internal fun apkVerdict(
    ownPackage: String,
    installedVersionCode: Long,
    installedSigners: List<ByteArray>?,
    apkPackage: String?,
    apkVersionCode: Long,
    apkSigners: List<ByteArray>?,
): ApkVerdict {
    if (apkPackage != ownPackage) return ApkVerdict.Rejected
    if (!installedSigners.isNullOrEmpty() && !apkSigners.isNullOrEmpty()) {
        val installed = installedSigners.map { it.asList() }.toSet()
        val apk = apkSigners.map { it.asList() }.toSet()
        if (installed != apk) return ApkVerdict.Rejected
    }
    return if (apkVersionCode > installedVersionCode) ApkVerdict.Install else ApkVerdict.NotNewer
}
