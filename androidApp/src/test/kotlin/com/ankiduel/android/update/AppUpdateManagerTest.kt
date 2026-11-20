package com.ankiduel.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun `parses version from apk asset name`() {
        val parsed = AppUpdateManager.parseReleaseVersion(
            tagName = "v1.0.1-2.15",
            assetName = "AnkiDuel-v1.0.1-2.apk"
        )
        assertEquals(ParsedReleaseVersion("1.0.1", 2), parsed)
    }

    @Test
    fun `falls back to tag name when asset name does not match`() {
        val parsed = AppUpdateManager.parseReleaseVersion(
            tagName = "v1.2.0-5.42",
            assetName = "app-release.apk"
        )
        assertEquals(ParsedReleaseVersion("1.2.0", 5), parsed)
    }

    @Test
    fun `returns null on invalid tag and asset name`() {
        val parsed = AppUpdateManager.parseReleaseVersion(
            tagName = "latest",
            assetName = "app.apk"
        )
        assertNull(parsed)
    }
}
