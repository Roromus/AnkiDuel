package com.ankiduel.android.tracking

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.ankiduel.shared.model.SocialAppCatalog

enum class AnkiApiStatus {
    CONNECTED,
    NOT_INSTALLED,
    PERMISSION_MISSING,
    PROVIDER_UNAVAILABLE,
    QUERY_FAILED
}

data class AnkiActivitySnapshot(
    val apiConnected: Boolean,
    val cardsReviewedToday: Int,
    val status: AnkiApiStatus = if (apiConnected) AnkiApiStatus.CONNECTED else AnkiApiStatus.PROVIDER_UNAVAILABLE,
    val detectedPackage: String? = null,
    val diagnosticDetail: String? = null,
    val ratingBucketsTotal: Int = 0,
    val deckCountsSignature: String = ""
) {
    /**
     * Empreinte d'activité de révision combinant :
     * - les notes uniques révisées aujourd'hui (`rated:1`)
     * - les notations par bouton (`rated:1:1`..`rated:1:4`, détecte les passages À revoir -> Bien)
     * - les compteurs par paquet (`deck_count`, détecte les cartes cloze sœurs et graduations d'apprentissage)
     */
    val activityFingerprint: String
        get() = "$cardsReviewedToday|$ratingBucketsTotal|$deckCountsSignature"
}

internal interface AnkiContentGateway {
    fun isPackageInstalled(packageName: String): Boolean
    fun hasPermission(permission: String): Boolean
    fun queryNoteCount(authority: String, selection: String): Int?
    fun queryDeckCounts(authority: String): List<Pair<Long, String>>
}

private class AndroidAnkiContentGateway(private val context: Context) : AnkiContentGateway {
    override fun isPackageInstalled(packageName: String): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)
    }

    override fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun queryNoteCount(authority: String, selection: String): Int? {
        val notesUri = Uri.parse("content://$authority/notes")
        // FlashCardsContract.Note.CONTENT_URI attend une requête de recherche Anki dans `selection`
        // (et `selectionArgs = null`).
        return context.contentResolver.query(
            notesUri,
            arrayOf("_id"),
            selection,
            null,
            null
        )?.use { cursor ->
            cursor.count.coerceAtLeast(0)
        }
    }

    override fun queryDeckCounts(authority: String): List<Pair<Long, String>> {
        val decksUri = Uri.parse("content://$authority/decks")
        return context.contentResolver.query(
            decksUri,
            arrayOf("deck_id", "deck_count"),
            null,
            null,
            null
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex("deck_id")
            val countIdx = cursor.getColumnIndex("deck_count")
            if (idIdx < 0 || countIdx < 0) return@use emptyList()
            val entries = mutableListOf<Pair<Long, String>>()
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                val counts = cursor.getString(countIdx).orEmpty().trim()
                entries.add(id to counts)
            }
            entries
        }.orEmpty()
    }
}

/**
 * Interroge le ContentProvider officiel d'AnkiDroid (FlashCardsContract) lorsqu'il est autorisé
 * afin de récupérer le nombre de notes/cartes révisées aujourd'hui (`rated:1`) et l'empreinte
 * d'activité pour la détection anti-inactivité.
 */
class AnkiDroidTracker internal constructor(
    private val gateway: AnkiContentGateway
) {
    constructor(context: Context) : this(AndroidAnkiContentGateway(context))

    fun findInstalledAnkiPackage(): String? {
        return SocialAppCatalog.ankiDroidPackages.firstOrNull { gateway.isPackageInstalled(it) }
    }

    fun hasAnkiApiPermission(ankiPackage: String? = findInstalledAnkiPackage()): Boolean {
        val candidates = buildList {
            add(ANKI_READ_WRITE_PERMISSION)
            if (!ankiPackage.isNullOrBlank() && ankiPackage != SocialAppCatalog.ANKIDROID_PACKAGE) {
                add("$ankiPackage.permission.READ_WRITE_DATABASE")
            }
        }
        return candidates.any { perm -> gateway.hasPermission(perm) }
    }

    fun queryTodayCardsReviewed(): AnkiActivitySnapshot {
        val ankiPkg = findInstalledAnkiPackage()
            ?: return AnkiActivitySnapshot(
                apiConnected = false,
                cardsReviewedToday = 0,
                status = AnkiApiStatus.NOT_INSTALLED,
                diagnosticDetail = "AnkiDroid non détecté sur cet appareil."
            )

        if (!hasAnkiApiPermission(ankiPkg)) {
            return AnkiActivitySnapshot(
                apiConnected = false,
                cardsReviewedToday = 0,
                status = AnkiApiStatus.PERMISSION_MISSING,
                detectedPackage = ankiPkg,
                diagnosticDetail = "Permission API AnkiDroid non accordée."
            )
        }

        val authority = "$ankiPkg.flashcards"

        return try {
            val ratedNotes = gateway.queryNoteCount(authority, "rated:1")
                ?: return AnkiActivitySnapshot(
                    apiConnected = false,
                    cardsReviewedToday = 0,
                    status = AnkiApiStatus.PROVIDER_UNAVAILABLE,
                    detectedPackage = ankiPkg,
                    diagnosticDetail = "Le fournisseur AnkiDroid n'a pas répondu (vérifiez Paramètres > Avancé > API AnkiDroid dans AnkiDroid)."
                )

            // Signaux d'activité complémentaires pour ne pas bloquer le compteur lors des révisions
            // de cartes sœurs (cloze/inversées) ou de cartes en apprentissage (À revoir -> Bien) :
            // 1) Somme des notations par bouton aujourd'hui (`rated:1:1`..`rated:1:4`)
            // 2) Signature des compteurs [learn, review, new] par paquet (`decks/deck_count`)
            val ratingBucketsTotal = if (ratedNotes > 0) {
                queryRatingBucketsTotal(authority)
            } else {
                0
            }
            val deckSignature = queryDeckCountsSignature(authority)

            AnkiActivitySnapshot(
                apiConnected = true,
                cardsReviewedToday = ratedNotes,
                status = AnkiApiStatus.CONNECTED,
                detectedPackage = ankiPkg,
                ratingBucketsTotal = ratingBucketsTotal,
                deckCountsSignature = deckSignature
            )
        } catch (sec: SecurityException) {
            AnkiActivitySnapshot(
                apiConnected = false,
                cardsReviewedToday = 0,
                status = AnkiApiStatus.PERMISSION_MISSING,
                detectedPackage = ankiPkg,
                diagnosticDetail = sec.message ?: "Accès refusé par AnkiDroid."
            )
        } catch (err: Exception) {
            AnkiActivitySnapshot(
                apiConnected = false,
                cardsReviewedToday = 0,
                status = AnkiApiStatus.QUERY_FAILED,
                detectedPackage = ankiPkg,
                diagnosticDetail = err.message ?: err.javaClass.simpleName
            )
        }
    }

    private fun queryRatingBucketsTotal(authority: String): Int {
        var total = 0
        for (ease in 1..4) {
            val count = runCatching {
                gateway.queryNoteCount(authority, "rated:1:$ease") ?: 0
            }.getOrDefault(0)
            total += count
        }
        return total
    }

    private fun queryDeckCountsSignature(authority: String): String {
        return runCatching {
            gateway.queryDeckCounts(authority)
                .sortedBy { it.first }
                .joinToString("|") { (id, counts) -> "$id:$counts" }
        }.getOrDefault("")
    }

    companion object {
        const val ANKI_READ_WRITE_PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
    }
}
