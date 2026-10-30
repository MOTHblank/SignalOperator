package com.mothblank.signaloperator.engine

import com.mothblank.signaloperator.models.SignalData

enum class InterceptQuality {
    CLEAR,
    PARTIAL,
    DEGRADED,
    UNUSABLE
}

data class RecoveredIntercept(
    val quality: InterceptQuality,
    val sender: String,
    val message: String,
    val briefing: String?,
    val sectorId: String?,
    val missingFields: Set<String>,
    val actionable: Boolean
)

/**
 * Produces a deterministic view of what the operator actually recovered at the
 * current calibration. Improving stability reveals information rather than
 * rerolling corruption.
 */
object InterceptRecovery {
    const val MIN_ACTIONABLE_STABILITY = 88f
    const val PARTIAL_STABILITY = 94f
    const val CLEAR_STABILITY = 98f

    fun quality(stability: Float): InterceptQuality = when {
        stability >= CLEAR_STABILITY -> InterceptQuality.CLEAR
        stability >= PARTIAL_STABILITY -> InterceptQuality.PARTIAL
        stability >= MIN_ACTIONABLE_STABILITY -> InterceptQuality.DEGRADED
        else -> InterceptQuality.UNUSABLE
    }

    fun recover(signal: SignalData, stability: Float): RecoveredIntercept {
        val quality = quality(stability)
        val missing = linkedSetOf<String>()

        val sender = when (quality) {
            InterceptQuality.CLEAR,
            InterceptQuality.PARTIAL -> signal.sender
            InterceptQuality.DEGRADED -> {
                missing += "authentication detail"
                damageIdentifier(signal.sender)
            }
            InterceptQuality.UNUSABLE -> {
                missing += "source identity"
                "[SOURCE UNRESOLVED]"
            }
        }

        val sector = when (quality) {
            InterceptQuality.CLEAR,
            InterceptQuality.PARTIAL -> signal.outcome?.locationId
            InterceptQuality.DEGRADED,
            InterceptQuality.UNUSABLE -> {
                if (signal.outcome?.locationId != null) missing += "sector reference"
                null
            }
        }

        val message = when (quality) {
            InterceptQuality.CLEAR -> signal.encodedMessage
            InterceptQuality.PARTIAL -> mask(signal.encodedMessage, signal.id, 0.10f)
            InterceptQuality.DEGRADED -> {
                missing += "message fragments"
                mask(signal.encodedMessage, signal.id, 0.30f)
            }
            InterceptQuality.UNUSABLE -> {
                missing += "message body"
                "[CARRIER PRESENT // PAYLOAD UNRECOVERABLE]"
            }
        }

        val briefing = signal.outcome?.briefing?.let { text ->
            when (quality) {
                InterceptQuality.CLEAR -> text
                InterceptQuality.PARTIAL -> mask(text, signal.id + "-briefing", 0.12f)
                InterceptQuality.DEGRADED -> {
                    missing += "secondary context"
                    mask(text, signal.id + "-briefing", 0.34f)
                }
                InterceptQuality.UNUSABLE -> null
            }
        }

        return RecoveredIntercept(
            quality = quality,
            sender = sender,
            message = message,
            briefing = briefing,
            sectorId = sector,
            missingFields = missing,
            actionable = stability >= MIN_ACTIONABLE_STABILITY
        )
    }

    private fun damageIdentifier(value: String): String {
        if (value.isBlank()) return "[SOURCE UNRESOLVED]"
        val visible = value.take((value.length * 0.55f).toInt().coerceAtLeast(1))
        return visible + "?"
    }

    private fun mask(text: String, seed: String, fraction: Float): String {
        if (text.isBlank()) return text
        val tokens = Regex("""\S+""").findAll(text).toList()
        if (tokens.isEmpty()) return text
        val target = (tokens.size * fraction).toInt().coerceAtLeast(1)
        val ranked = tokens.indices.sortedBy { index ->
            (seed.hashCode() * 31 + index * 1103515245).ushr(1)
        }.take(target).toSet()

        var tokenIndex = 0
        return Regex("""\S+""").replace(text) { match ->
            val current = tokenIndex++
            if (current in ranked) "▒▒▒" else match.value
        }
    }
}
