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
    val diagnosticDetail: String? = null
)

/**
 * Interroge le ContentProvider officiel d'AnkiDroid (FlashCardsContract) lorsqu'il est autorisé
 * afin de récupérer le nombre de notes/cartes révisées aujourd'hui (`rated:1`) pour la détection anti-inactivité.
 */
class AnkiDroidTracker(private val context: Context) {

    fun findInstalledAnkiPackage(): String? {
        return SocialAppCatalog.ankiDroidPackages.firstOrNull { isPackageInstalled(it) }
    }

    fun hasAnkiApiPermission(ankiPackage: String? = findInstalledAnkiPackage()): Boolean {
        val candidates = buildList {
            add(ANKI_READ_WRITE_PERMISSION)
            if (!ankiPackage.isNullOrBlank() && ankiPackage != SocialAppCatalog.ANKIDROID_PACKAGE) {
                add("$ankiPackage.permission.READ_WRITE_DATABASE")
            }
        }
        return candidates.any { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
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
        // FlashCardsContract.Note.CONTENT_URI attend une requête de recherche Anki dans `selection`
        // (et `selectionArgs = null`). `rated:1` retourne les notes révisées aujourd'hui.
        val notesUri = Uri.parse("content://$authority/notes")

        return try {
            context.contentResolver.query(
                notesUri,
                arrayOf("_id"),
                "rated:1",
                null,
                null
            )?.use { cursor ->
                AnkiActivitySnapshot(
                    apiConnected = true,
                    cardsReviewedToday = cursor.count.coerceAtLeast(0),
                    status = AnkiApiStatus.CONNECTED,
                    detectedPackage = ankiPkg
                )
            } ?: AnkiActivitySnapshot(
                apiConnected = false,
                cardsReviewedToday = 0,
                status = AnkiApiStatus.PROVIDER_UNAVAILABLE,
                detectedPackage = ankiPkg,
                diagnosticDetail = "Le fournisseur AnkiDroid n'a pas répondu (vérifiez Paramètres > Avancé > API AnkiDroid dans AnkiDroid)."
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

    private fun isPackageInstalled(packageName: String): Boolean {
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

    companion object {
        const val ANKI_READ_WRITE_PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
    }
}
