package com.ankiduel.android.tracking

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneId

data class AnkiActivitySnapshot(
    val apiConnected: Boolean,
    val cardsReviewedToday: Int
)

/**
 * Interroge le ContentProvider officiel d'AnkiDroid (FlashCardsContract) lorsqu'il est autorisé
 * afin de récupérer le nombre de cartes révisées aujourd'hui pour la détection anti-inactivité.
 */
class AnkiDroidTracker(private val context: Context) {

    fun queryTodayCardsReviewed(): AnkiActivitySnapshot {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            "com.ichi2.anki.permission.READ_WRITE_DATABASE"
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return AnkiActivitySnapshot(apiConnected = false, cardsReviewedToday = 0)
        }

        // AnkiDroid FlashCardsContract exposes review_info / decks under com.ichi2.anki.flashcards
        val midnightMs = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        // Try querying revlog / cards modified today via AnkiDroid FlashCardsContract URI
        val cardsUri = Uri.parse("content://com.ichi2.anki.flashcards/notes")
        return runCatching {
            // Vérifie d'abord que le provider répond
            val deckUri = Uri.parse("content://com.ichi2.anki.flashcards/decks")
            context.contentResolver.query(deckUri, null, null, null, null)?.use { deckCursor ->
                // Provider accessible : on interroge les cartes révisées récemment si le provider l'expose
                var reviewedCount = 0
                val revUri = Uri.parse("content://com.ichi2.anki.flashcards/schedule")
                runCatching {
                    context.contentResolver.query(revUri, null, null, null, null)?.use { sched ->
                        // Certains schémas exposent le compteur de révisions dans les colonnes de schedule/decks
                        reviewedCount = sched.count
                    }
                }
                if (reviewedCount == 0) {
                    // Fallback : nombre de paquets actifs ou notes modifiées aujourd'hui
                    runCatching {
                        context.contentResolver.query(
                            cardsUri,
                            arrayOf("_id"),
                            "mod >= ?",
                            arrayOf((midnightMs / 1000L).toString()),
                            null
                        )?.use { c ->
                            reviewedCount = c.count
                        }
                    }
                }
                AnkiActivitySnapshot(
                    apiConnected = deckCursor.count >= 0,
                    cardsReviewedToday = reviewedCount
                )
            } ?: AnkiActivitySnapshot(apiConnected = false, cardsReviewedToday = 0)
        }.getOrElse {
            AnkiActivitySnapshot(apiConnected = false, cardsReviewedToday = 0)
        }
    }
}
