package com.ankiduel.android.tracking

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Process
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AnkiDroidTrackerTest {

    @Test
    fun `queries rated_1 on notes URI with null selectionArgs and counts reviewed notes`() {
        val app = RuntimeEnvironment.getApplication()
        installFakeAnkiPackage(app, "com.ichi2.anki")
        grantAnkiPermission(app, AnkiDroidTracker.ANKI_READ_WRITE_PERMISSION)

        FakeAnkiNotesProvider.reset(
            rowCountBySelection = mapOf(
                "rated:1" to 14,
                "rated:1:1" to 5,
                "rated:1:2" to 2,
                "rated:1:3" to 12,
                "rated:1:4" to 1
            ),
            deckCounts = mapOf(1L to "[2,10,5]")
        )
        Robolectric.buildContentProvider(FakeAnkiNotesProvider::class.java)
            .create("com.ichi2.anki.flashcards")

        val snapshot = AnkiDroidTracker(app).queryTodayCardsReviewed()

        assertTrue(snapshot.apiConnected)
        assertEquals(AnkiApiStatus.CONNECTED, snapshot.status)
        assertEquals(14, snapshot.cardsReviewedToday)
        assertEquals(20, snapshot.ratingBucketsTotal)
        assertEquals("1:[2,10,5]", snapshot.deckCountsSignature)
        assertEquals("rated:1", FakeAnkiNotesProvider.queriedSelections.firstOrNull())
        assertNull(FakeAnkiNotesProvider.lastSelectionArgs)
    }

    @Test
    fun `detects activity fingerprint change when sibling cloze or learning card changes deck counts or ease`() {
        val app = RuntimeEnvironment.getApplication()
        installFakeAnkiPackage(app, "com.ichi2.anki")
        grantAnkiPermission(app, AnkiDroidTracker.ANKI_READ_WRITE_PERMISSION)

        FakeAnkiNotesProvider.reset(
            rowCountBySelection = mapOf(
                "rated:1" to 15,
                "rated:1:1" to 10,
                "rated:1:3" to 5
            ),
            deckCounts = mapOf(1L to "[10,0,5]")
        )
        Robolectric.buildContentProvider(FakeAnkiNotesProvider::class.java)
            .create("com.ichi2.anki.flashcards")

        val first = AnkiDroidTracker(app).queryTodayCardsReviewed()

        // Same 15 unique notes rated today, but user graduated a learning card (Again -> Good)
        FakeAnkiNotesProvider.rowCountBySelection = mapOf(
            "rated:1" to 15,
            "rated:1:1" to 10,
            "rated:1:3" to 6
        )
        FakeAnkiNotesProvider.deckCounts = mapOf(1L to "[9,0,5]")

        val second = AnkiDroidTracker(app).queryTodayCardsReviewed()

        assertEquals(15, first.cardsReviewedToday)
        assertEquals(15, second.cardsReviewedToday)
        assertTrue(first.activityFingerprint != second.activityFingerprint)
    }

    @Test
    fun `reports apiConnected false when AnkiDroid provider returns null instead of capping at 0 cards`() {
        val app = RuntimeEnvironment.getApplication()
        installFakeAnkiPackage(app, "com.ichi2.anki")
        grantAnkiPermission(app, AnkiDroidTracker.ANKI_READ_WRITE_PERMISSION)

        // No provider registered -> ContentResolver.query returns null
        val snapshot = AnkiDroidTracker(app).queryTodayCardsReviewed()

        assertFalse(snapshot.apiConnected)
        assertEquals(AnkiApiStatus.PROVIDER_UNAVAILABLE, snapshot.status)
        assertEquals(0, snapshot.cardsReviewedToday)
    }

    @Test
    fun `reports permission missing when AnkiDroid database permission is denied`() {
        val app = RuntimeEnvironment.getApplication()
        installFakeAnkiPackage(app, "com.ichi2.anki")

        val snapshot = AnkiDroidTracker(app).queryTodayCardsReviewed()

        assertFalse(snapshot.apiConnected)
        assertEquals(AnkiApiStatus.PERMISSION_MISSING, snapshot.status)
        assertEquals(0, snapshot.cardsReviewedToday)
    }

    private fun installFakeAnkiPackage(app: Application, packageName: String) {
        val pkgInfo = PackageInfo().apply {
            this.packageName = packageName
        }
        shadowOf(app.packageManager).installPackage(pkgInfo)
    }

    private fun grantAnkiPermission(app: Application, permission: String) {
        shadowOf(app).grantPermissions(Process.myPid(), Process.myUid(), permission)
    }

    class FakeAnkiNotesProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? {
            return when (uri.path) {
                "/notes" -> {
                    queriedSelections.add(selection)
                    lastSelectionArgs = selectionArgs?.toList()
                    val count = rowCountBySelection[selection] ?: 0
                    MatrixCursor(arrayOf("_id")).apply {
                        for (i in 1..count) {
                            addRow(arrayOf(i.toLong()))
                        }
                    }
                }
                "/decks" -> {
                    MatrixCursor(arrayOf("deck_id", "deck_count")).apply {
                        for ((id, counts) in deckCounts) {
                            addRow(arrayOf(id, counts))
                        }
                    }
                }
                else -> null
            }
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

        companion object {
            var rowCountBySelection: Map<String, Int> = emptyMap()
            var deckCounts: Map<Long, String> = emptyMap()
            val queriedSelections: MutableList<String?> = mutableListOf()
            var lastSelectionArgs: List<String>? = null

            fun reset(
                rowCountBySelection: Map<String, Int> = emptyMap(),
                deckCounts: Map<Long, String> = emptyMap()
            ) {
                this.rowCountBySelection = rowCountBySelection
                this.deckCounts = deckCounts
                queriedSelections.clear()
                lastSelectionArgs = null
            }
        }
    }
}
