package com.ankiduel.shared.engine

import kotlin.math.min

/**
 * Valide le temps d'utilisation d'AnkiDroid par rapport aux cartes réellement révisées
 * afin d'éviter qu'un joueur laisse l'écran d'AnkiDroid allumé sans réviser.
 *
 * Si l'API AnkiDroid n'est pas connectée, le validateur accepte le temps au premier plan brut.
 * Si elle est connectée, au-delà d'une fenêtre de grâce de [graceSecondsPerWindow] (3 min),
 * le temps supplémentaire sans nouvelle carte révisée est plafonné jusqu'à la prochaine révision.
 */
class AntiIdleValidator(
    private val graceSecondsPerWindow: Long = 180L
) {
    data class State(
        val lastRawAnkiSeconds: Long = 0L,
        val lastCardsReviewed: Int = 0,
        val secondsSinceLastReview: Long = 0L,
        val accumulatedEffectiveSeconds: Long = 0L
    )

    data class Result(
        val effectiveSeconds: Long,
        val idleCappedSeconds: Long,
        val isIdleFlagged: Boolean,
        val nextState: State
    )

    fun evaluate(
        previous: State,
        rawAnkiSeconds: Long,
        cardsReviewed: Int,
        ankiApiConnected: Boolean
    ): Result {
        val safeRaw = rawAnkiSeconds.coerceAtLeast(0L)
        val safeCards = cardsReviewed.coerceAtLeast(0)

        // Réinitialisation si nouveau cycle (minuit : le temps brut repart de 0)
        if (safeRaw < previous.lastRawAnkiSeconds) {
            val fresh = State()
            return evaluate(fresh, safeRaw, safeCards, ankiApiConnected)
        }

        val rawDelta = safeRaw - previous.lastRawAnkiSeconds
        // Si le compteur de cartes diminue en cours de journée (ex: changement de jour Anki à 4h du matin ou synchro),
        // ne pas effacer le temps effectif déjà validé aujourd'hui.
        val cardsDelta = if (safeCards < previous.lastCardsReviewed) {
            safeCards
        } else {
            safeCards - previous.lastCardsReviewed
        }

        if (!ankiApiConnected) {
            val next = State(
                lastRawAnkiSeconds = safeRaw,
                lastCardsReviewed = safeCards,
                secondsSinceLastReview = 0L,
                accumulatedEffectiveSeconds = safeRaw
            )
            return Result(
                effectiveSeconds = safeRaw,
                idleCappedSeconds = 0L,
                isIdleFlagged = false,
                nextState = next
            )
        }

        val remainingGrace = if (cardsDelta > 0) {
            cardsDelta.toLong() * graceSecondsPerWindow
        } else {
            (graceSecondsPerWindow - previous.secondsSinceLastReview).coerceAtLeast(0L)
        }
        val creditedDelta = min(rawDelta, remainingGrace)
        val newEffective = previous.accumulatedEffectiveSeconds + creditedDelta
        val isIdle = rawDelta > remainingGrace
        val newWindowSeconds = when {
            cardsDelta <= 0 -> previous.secondsSinceLastReview + rawDelta
            isIdle -> graceSecondsPerWindow
            rawDelta <= graceSecondsPerWindow -> rawDelta
            else -> (rawDelta / cardsDelta).coerceAtMost(graceSecondsPerWindow)
        }

        val next = State(
            lastRawAnkiSeconds = safeRaw,
            lastCardsReviewed = safeCards,
            secondsSinceLastReview = newWindowSeconds,
            accumulatedEffectiveSeconds = newEffective
        )

        return Result(
            effectiveSeconds = newEffective,
            idleCappedSeconds = (safeRaw - newEffective).coerceAtLeast(0L),
            isIdleFlagged = isIdle,
            nextState = next
        )
    }
}
