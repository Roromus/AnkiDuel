package com.ankiduel.android.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiDroidTrackerTest {

    @Test
    fun `queries rated_1 on notes URI and collects rating buckets and deck counts`() {
        val gateway = FakeAnkiContentGateway(
            installedPackages = setOf("com.ichi2.anki"),
            grantedPermissions = setOf(AnkiDroidTracker.ANKI_READ_WRITE_PERMISSION),
            noteCountsBySelection = mutableMapOf(
                "rated:1" to 14,
                "rated:1:1" to 5,
                "rated:1:2" to 2,
                "rated:1:3" to 12,
                "rated:1:4" to 1
            ),
            deckCounts = listOf(2L to "[0,4,1]", 1L to "[2,10,5]")
        )

        val snapshot = AnkiDroidTracker(gateway).queryTodayCardsReviewed()

        assertTrue(snapshot.apiConnected)
        assertEquals(AnkiApiStatus.CONNECTED, snapshot.status)
        assertEquals("com.ichi2.anki", snapshot.detectedPackage)
        assertEquals(14, snapshot.cardsReviewedToday)
        assertEquals(20, snapshot.ratingBucketsTotal)
        assertEquals("1:[2,10,5]|2:[0,4,1]", snapshot.deckCountsSignature)
        assertEquals("rated:1", gateway.queriedSelections.firstOrNull())
        assertEquals("com.ichi2.anki.flashcards", gateway.lastAuthority)
    }

    @Test
    fun `detects activity fingerprint change when sibling cloze or learning card changes deck counts or ease`() {
        val gateway = FakeAnkiContentGateway(
            installedPackages = setOf("com.ichi2.anki"),
            grantedPermissions = setOf(AnkiDroidTracker.ANKI_READ_WRITE_PERMISSION),
            noteCountsBySelection = mutableMapOf(
                "rated:1" to 15,
                "rated:1:1" to 10,
                "rated:1:3" to 5
            ),
            deckCounts = listOf(1L to "[10,0,5]")
        )
        val tracker = AnkiDroidTracker(gateway)

        val first = tracker.queryTodayCardsReviewed()

        // Same 15 unique notes rated today, but user graduated a learning card (Again -> Good)
        gateway.noteCountsBySelection["rated:1:3"] = 6
        gateway.deckCounts = listOf(1L to "[9,0,5]")

        val second = tracker.queryTodayCardsReviewed()

        assertEquals(15, first.cardsReviewedToday)
        assertEquals(15, second.cardsReviewedToday)
        assertTrue(first.activityFingerprint != second.activityFingerprint)
    }

    @Test
    fun `reports apiConnected false when AnkiDroid provider returns null instead of capping at 0 cards`() {
        val gateway = FakeAnkiContentGateway(
            installedPackages = setOf("com.ichi2.anki"),
            grantedPermissions = setOf(AnkiDroidTracker.ANKI_READ_WRITE_PERMISSION),
            providerAvailable = false
        )

        val snapshot = AnkiDroidTracker(gateway).queryTodayCardsReviewed()

        assertFalse(snapshot.apiConnected)
        assertEquals(AnkiApiStatus.PROVIDER_UNAVAILABLE, snapshot.status)
        assertEquals(0, snapshot.cardsReviewedToday)
    }

    @Test
    fun `reports permission missing when AnkiDroid database permission is denied`() {
        val gateway = FakeAnkiContentGateway(
            installedPackages = setOf("com.ichi2.anki"),
            grantedPermissions = emptySet()
        )

        val snapshot = AnkiDroidTracker(gateway).queryTodayCardsReviewed()

        assertFalse(snapshot.apiConnected)
        assertEquals(AnkiApiStatus.PERMISSION_MISSING, snapshot.status)
        assertEquals(0, snapshot.cardsReviewedToday)
    }

    @Test
    fun `reports not installed when no AnkiDroid package is present`() {
        val gateway = FakeAnkiContentGateway(
            installedPackages = emptySet(),
            grantedPermissions = emptySet()
        )

        val snapshot = AnkiDroidTracker(gateway).queryTodayCardsReviewed()

        assertFalse(snapshot.apiConnected)
        assertEquals(AnkiApiStatus.NOT_INSTALLED, snapshot.status)
        assertEquals(0, snapshot.cardsReviewedToday)
    }

    @Test
    fun `supports parallel AnkiDroid build package authority and permission`() {
        val gateway = FakeAnkiContentGateway(
            installedPackages = setOf("com.ichi2.anki.a"),
            grantedPermissions = setOf("com.ichi2.anki.a.permission.READ_WRITE_DATABASE"),
            noteCountsBySelection = mutableMapOf("rated:1" to 7)
        )

        val snapshot = AnkiDroidTracker(gateway).queryTodayCardsReviewed()

        assertTrue(snapshot.apiConnected)
        assertEquals(AnkiApiStatus.CONNECTED, snapshot.status)
        assertEquals("com.ichi2.anki.a", snapshot.detectedPackage)
        assertEquals("com.ichi2.anki.a.flashcards", gateway.lastAuthority)
        assertEquals(7, snapshot.cardsReviewedToday)
    }

    private class FakeAnkiContentGateway(
        private val installedPackages: Set<String>,
        private val grantedPermissions: Set<String>,
        val noteCountsBySelection: MutableMap<String, Int> = mutableMapOf(),
        var deckCounts: List<Pair<Long, String>> = emptyList(),
        private val providerAvailable: Boolean = true
    ) : AnkiContentGateway {
        val queriedSelections = mutableListOf<String>()
        var lastAuthority: String? = null

        override fun isPackageInstalled(packageName: String): Boolean = packageName in installedPackages

        override fun hasPermission(permission: String): Boolean = permission in grantedPermissions

        override fun queryNoteCount(authority: String, selection: String): Int? {
            lastAuthority = authority
            queriedSelections.add(selection)
            if (!providerAvailable) return null
            return noteCountsBySelection[selection] ?: 0
        }

        override fun queryDeckCounts(authority: String): List<Pair<Long, String>> {
            lastAuthority = authority
            if (!providerAvailable) return emptyList()
            return deckCounts
        }
    }
}
