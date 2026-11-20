package com.ankiduel.shared.engine

import kotlin.math.min

/**
 * Valide le temps d'utilisation d'AnkiDroid par rapport aux cartes réellement révisées
 * afin d'éviter qu'un joueur laisse l'écran d'AnkiDroid allumé sans réviser.
 *
 * Si l'API AnkiDroid n'est pas connectée, le validateur accepte le temps au premier plan brut.
 * Si elle est connectée, au-delà d'une fenêtre de grâce de [graceSecondsPerWindow] (5 min par défaut),
 * le temps supplémentaire sans nouvelle activité de révision est plafonné jusqu'à la prochaine révision.
 */
class AntiIdleValidator(
    private val graceSecondsPerWindow: Long = 300L
) {
    data class State(
        val lastRawAnkiSeconds: Long = 0L,
        val lastCardsReviewed: Int = 0,
        val secondsSinceLastReview: Long = 0L,
        val accumulatedEffectiveSeconds: Long = 0L,
        val bankedCards: Int = 0
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
        ankiApiConnected: Boolean,
        extraReviewActivity: Boolean = false,
        resetOnRawDrop: Boolean = false
    ): Result {
        val safeRawInput = rawAnkiSeconds.coerceAtLeast(0L)
        val safeCards = cardsReviewed.coerceAtLeast(0)

        // Réinitialisation explicite uniquement si demandée (ex: nouveau cycle hors gestion par dayKey).
        // Dans une même journée, une baisse transitoire de rawAnkiSeconds (éviction du buffer UsageEvents)
        // ne doit jamais effacer le temps déjà validé.
        if (resetOnRawDrop && safeRawInput < previous.lastRawAnkiSeconds) {
            return evaluate(
                previous = State(),
                rawAnkiSeconds = safeRawInput,
                cardsReviewed = safeCards,
                ankiApiConnected = ankiApiConnected,
                extraReviewActivity = extraReviewActivity,
                resetOnRawDrop = false
            )
        }

        val safeRaw = maxOf(safeRawInput, previous.lastRawAnkiSeconds)
        val rawDelta = safeRaw - previous.lastRawAnkiSeconds

        // Si le compteur de cartes diminue en cours de journée (ex: changement de jour Anki à 4h du matin ou synchro),
        // ne pas effacer le temps effectif déjà validé aujourd'hui.
        val directCardsDelta = if (safeCards < previous.lastCardsReviewed) {
            safeCards
        } else {
            safeCards - previous.lastCardsReviewed
        }
        val activityCardsDelta = when {
            directCardsDelta > 0 -> directCardsDelta
            extraReviewActivity -> 1
            else -> 0
        }

        if (!ankiApiConnected) {
            val newEffective = (previous.accumulatedEffectiveSeconds + rawDelta).coerceAtMost(safeRaw)
            val next = State(
                lastRawAnkiSeconds = safeRaw,
                lastCardsReviewed = safeCards,
                secondsSinceLastReview = 0L,
                accumulatedEffectiveSeconds = newEffective,
                bankedCards = 0
            )
            return Result(
                effectiveSeconds = newEffective,
                idleCappedSeconds = (safeRaw - newEffective).coerceAtLeast(0L),
                isIdleFlagged = false,
                nextState = next
            )
        }

        // Si des révisions sont détectées pendant que UsageStats n'a pas encore flushé le temps au premier plan
        // (rawDelta == 0), on met ces révisions en réserve pour créditer le flush lorsqu'il arrivera.
        if (rawDelta == 0L && activityCardsDelta > 0) {
            val next = State(
                lastRawAnkiSeconds = safeRaw,
                lastCardsReviewed = safeCards,
                secondsSinceLastReview = 0L,
                accumulatedEffectiveSeconds = previous.accumulatedEffectiveSeconds,
                bankedCards = (previous.bankedCards + activityCardsDelta).coerceAtMost(10_000)
            )
            return Result(
                effectiveSeconds = previous.accumulatedEffectiveSeconds,
                idleCappedSeconds = (safeRaw - previous.accumulatedEffectiveSeconds).coerceAtLeast(0L),
                isIdleFlagged = false,
                nextState = next
            )
        }

        val effectiveCardsDelta = activityCardsDelta + if (rawDelta > 0L) previous.bankedCards else 0
        val remainingBanked = if (rawDelta > 0L) 0 else previous.bankedCards

        val remainingGrace = if (effectiveCardsDelta > 0) {
            effectiveCardsDelta.toLong() * graceSecondsPerWindow
        } else {
            (graceSecondsPerWindow - previous.secondsSinceLastReview).coerceAtLeast(0L)
        }
        val creditedDelta = min(rawDelta, remainingGrace)
        val newEffective = previous.accumulatedEffectiveSeconds + creditedDelta
        val exceededGrace = rawDelta > remainingGrace
        val newWindowSeconds = when {
            effectiveCardsDelta <= 0 -> previous.secondsSinceLastReview + rawDelta
            exceededGrace -> graceSecondsPerWindow
            rawDelta <= graceSecondsPerWindow -> rawDelta
            else -> (rawDelta / effectiveCardsDelta).coerceAtMost(graceSecondsPerWindow)
        }
        val isIdle = exceededGrace || (effectiveCardsDelta <= 0 && newWindowSeconds > graceSecondsPerWindow)

        val next = State(
            lastRawAnkiSeconds = safeRaw,
            lastCardsReviewed = safeCards,
            secondsSinceLastReview = newWindowSeconds,
            accumulatedEffectiveSeconds = newEffective,
            bankedCards = remainingBanked
        )

        return Result(
            effectiveSeconds = newEffective,
            idleCappedSeconds = (safeRaw - newEffective).coerceAtLeast(0L),
            isIdleFlagged = isIdle,
            nextState = next
        )
    }
}
