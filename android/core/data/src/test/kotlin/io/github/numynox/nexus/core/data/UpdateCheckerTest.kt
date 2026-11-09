package io.github.numynox.nexus.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckerTest {
    private fun release(tag: String, draft: Boolean = false, prerelease: Boolean = false, apk: Boolean = true) = """
        {"tag_name": "$tag", "html_url": "https://github.com/numynox/nexus/releases/tag/$tag",
         "draft": $draft, "prerelease": $prerelease,
         "assets": [${if (apk) """{"name": "x.apk", "browser_download_url": "https://example.com/$tag.apk"}""" else ""}]}
    """.trimIndent()

    private fun json(vararg releases: String) = releases.joinToString(",", "[", "]")

    @Test
    fun picksTheHighestVersionCodeNotTheNewestOrLexicallyLargest() {
        val newest = UpdateChecker.newest(
            json(release("annona-android-v0.0.9"), release("annona-android-v0.0.12"), release("annona-android-v0.0.10")),
            "annona-android-v",
        )!!
        assertEquals(12L, newest.versionCode)
        assertEquals("0.0.12", newest.versionName)
        assertEquals("https://example.com/annona-android-v0.0.12.apk", newest.apkUrl)
    }

    @Test
    fun skipsDraftsPrereleasesAndOtherApps() {
        val newest = UpdateChecker.newest(
            json(
                release("annona-android-v0.0.5"),
                release("annona-android-v0.0.7", draft = true),
                release("annona-android-v0.0.8", prerelease = true),
                release("noctua-android-v0.0.90"),
                release("annona-android-vbroken"),
            ),
            "annona-android-v",
        )!!
        assertEquals(5L, newest.versionCode)
    }

    @Test
    fun aReleaseWithoutApkHasNoApkUrl() {
        assertNull(UpdateChecker.newest(json(release("annona-android-v0.0.3", apk = false)), "annona-android-v")!!.apkUrl)
    }

    @Test
    fun noReleaseOfThisAppIsNull() {
        assertNull(UpdateChecker.newest(json(release("vibilia-android-v0.0.3")), "annona-android-v"))
        assertNull(UpdateChecker.newest("[]", "annona-android-v"))
    }
}
