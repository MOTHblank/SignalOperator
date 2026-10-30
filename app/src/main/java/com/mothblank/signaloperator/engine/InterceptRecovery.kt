package com.mothblank.signaloperator.engine

import com.mothblank.signaloperator.models.SignalData
import kotlin.math.abs

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
    val carrierFingerprint: String?,
    val missingFields: Set<String>,
    val actionable: Boolean
)

/**
 * Produces a deterministic view of what the operator actually recovered.
 *
 * Stability controls payload completeness, while individual hardware errors
 * affect different intelligence channels:
 * - gain error damages claimed source/callsign intelligibility;
 * - filter error damages RF identity and contextual/sector information;
 * - detuning damages spectral identity and location context.
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

    fun recover(
        signal: SignalData,
        stability: Float,
        gain: Int? = null,
        filter: Int? = null,
        proximity: Float = 1f
    ): RecoveredIntercept {
        val quality = quality(stability)
        val missing = linkedSetOf<String>()
        val gainError = gain?.let { abs(signal.targetGain - it) } ?: 0
        val filterError = filter?.let { abs(signal.targetFilter - it) } ?: 0
        val detuned = proximity < 0.94f
        val badlyDetuned = proximity < 0.91f

        val sender = when {
            quality == InterceptQuality.UNUSABLE -> {
                missing += "source identity"
                "[SOURCE UNRESOLVED]"
            }
            gainError >= 8 -> {
                missing += "source identity"
                "[SOURCE UNRESOLVED]"
            }
            gainError >= 4 || quality == InterceptQuality.DEGRADED -> {
                missing += "authentication detail"
                damageIdentifier(signal.sender)
            }
            else -> signal.sender
        }

        val carrierFingerprint = when {
            quality == InterceptQuality.UNUSABLE -> {
                missing += "RF fingerprint"
                null
            }
            filterError >= 5 || badlyDetuned -> {
                missing += "RF fingerprint"
                null
            }
            else -> signal.carrierFingerprint.takeIf { it.isNotBlank() }
        }

        val sector = when {
            signal.outcome?.locationId == null -> null
            quality == InterceptQuality.UNUSABLE -> {
                missing += "sector reference"
                null
            }
            filterError >= 7 || detuned -> {
                missing += "sector reference"
                null
            }
            else -> signal.outcome.locationId
        }

        val baseMask = when (quality) {
            InterceptQuality.CLEAR -> 0f
            InterceptQuality.PARTIAL -> 0.10f
            InterceptQuality.DEGRADED -> 0.28f
            InterceptQuality.UNUSABLE -> 1f
        }
        val filterMask = (filterError * 0.018f).coerceAtMost(0.22f)
        val tuningMask = ((1f - proximity).coerceAtLeast(0f) * 0.75f).coerceAtMost(0.20f)
        val maskFraction = (baseMask + filterMask + tuningMask).coerceIn(0f, 0.72f)

        val message = if (quality == InterceptQuality.UNUSABLE) {
            missing += "message body"
            "[CARRIER PRESENT // PAYLOAD UNRECOVERABLE]"
        } else if (maskFraction <= 0.01f) {
            signal.encodedMessage
        } else {
            missing += "message fragments"
            mask(signal.encodedMessage, signal.id, maskFraction)
        }

        val briefing = signal.outcome?.briefing?.let { text ->
            if (quality == InterceptQuality.UNUSABLE) {
                missing += "secondary context"
                null
            } else {
                val briefingMask = (maskFraction + 0.04f).coerceAtMost(0.78f)
                if (briefingMask <= 0.01f) {
                    text
                } else {
                    missing += "secondary context"
                    mask(text, signal.id + "-briefing", briefingMask)
                }
            }
        }

        if (detuned) missing += "spectral lock context"

        return RecoveredIntercept(
            quality = quality,
            sender = sender,
            message = message,
            briefing = briefing,
            sectorId = sector,
            carrierFingerprint = carrierFingerprint,
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
