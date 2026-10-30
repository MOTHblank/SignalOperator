package com.mothblank.signaloperator.engine

import com.mothblank.signaloperator.models.SignalData
import com.mothblank.signaloperator.models.SignalKind

/**
 * Converts a processed radio signal into an investigative observation.
 * Calibration quality affects how confidently the recovered information can be treated.
 */
object EvidenceCapture {
    fun fromSignal(
        signal: SignalData,
        stability: Float,
        sequence: Long
    ): InterceptEvidence {
        val reliability = when {
            stability >= 98.5f -> EvidenceReliability.CLEAR
            stability >= 96.5f -> EvidenceReliability.PARTIAL
            stability >= 95f -> EvidenceReliability.DEGRADED
            else -> EvidenceReliability.UNVERIFIED
        }

        val claims = buildMap {
            signal.outcome?.actorId?.let { put("actorId", it) }
            put("signalKind", signal.kind.name)
        }

        val missing = buildSet {
            if (reliability == EvidenceReliability.PARTIAL) add("authentication detail")
            if (reliability == EvidenceReliability.DEGRADED) {
                add("authentication detail")
                add("secondary context")
            }
            if (reliability == EvidenceReliability.UNVERIFIED) {
                add("authentication")
                add("context")
            }
            if (signal.kind == SignalKind.MISSION && signal.outcome?.locationId == null) {
                add("sector reference")
            }
        }

        return InterceptEvidence(
            id = "evidence-" + signal.id,
            sequence = sequence,
            frequencyMhz = signal.frequency,
            callsign = signal.sender.takeIf { it.isNotBlank() },
            sectorId = signal.outcome?.locationId,
            message = signal.outcome?.briefing ?: signal.encodedMessage,
            reliability = reliability,
            claims = claims,
            missingFields = missing,
            suspectedSpoof = false
        )
    }
}
