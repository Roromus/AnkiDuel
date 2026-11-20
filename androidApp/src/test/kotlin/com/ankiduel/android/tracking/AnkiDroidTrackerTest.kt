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

        FakeAnkiNotesProvider.reset(rowCount = 14)
        Robolectric.buildContentProvider(FakeAnkiNotesProvider::class.java)
            .create("com.ichi2.anki.flashcards")

        val snapshot = AnkiDroidTracker(app).queryTodayCardsReviewed()

        assertTrue(snapshot.apiConnected)
        assertEquals(AnkiApiStatus.CONNECTED, snapshot.status)
        assertEquals(14, snapshot.cardsReviewedToday)
        assertEquals("rated:1", FakeAnkiNotesProvider.lastSelection)
        assertNull(FakeAnkiNotesProvider.lastSelectionArgs)
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
            if (uri.path != "/notes") return null
            lastSelection = selection
            lastSelectionArgs = selectionArgs?.toList()
            return MatrixCursor(arrayOf("_id")).apply {
                for (i in 1..configuredRowCount) {
                    addRow(arrayOf(i.toLong()))
                }
            }
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

        companion object {
            var configuredRowCount: Int = 0
            var lastSelection: String? = null
            var lastSelectionArgs: List<String>? = null

            fun reset(rowCount: Int) {
                configuredRowCount = rowCount
                lastSelection = null
                lastSelectionArgs = null
            }
        }
    }
}
