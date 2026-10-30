package com.mothblank.signaloperator.engine

import com.mothblank.signaloperator.models.SignalData
import com.mothblank.signaloperator.models.SignalKind

/**
 * Converts a processed radio signal into the information the operator actually
 * recovered, preserving uncertainty introduced by imperfect calibration.
 */
object EvidenceCapture {
    fun fromSignal(
        signal: SignalData,
        stability: Float,
        sequence: Long
    ): InterceptEvidence {
        val recovered = InterceptRecovery.recover(signal, stability)
        val reliability = when (recovered.quality) {
            InterceptQuality.CLEAR -> EvidenceReliability.CLEAR
            InterceptQuality.PARTIAL -> EvidenceReliability.PARTIAL
            InterceptQuality.DEGRADED -> EvidenceReliability.DEGRADED
            InterceptQuality.UNUSABLE -> EvidenceReliability.UNVERIFIED
        }

        val claims = buildMap {
            putAll(signal.claims)
            if ("authentication detail" !in recovered.missingFields) {
                signal.outcome?.actorId?.let { put("actorId", it) }
            } else {
                remove("actorId")
                remove("actorLocation")
            }
            put("signalKind", signal.kind.name)
        }

        val missing = recovered.missingFields.toMutableSet()
        if (signal.kind == SignalKind.MISSION && signal.outcome?.locationId == null) {
            missing += "sector reference"
        }

        return InterceptEvidence(
            id = "evidence-" + signal.id,
            sequence = sequence,
            frequencyMhz = signal.frequency,
            callsign = recovered.sender.takeUnless { it.startsWith("[SOURCE") },
            sectorId = recovered.sectorId,
            message = recovered.briefing ?: recovered.message,
            reliability = reliability,
            claims = claims,
            missingFields = missing,
            suspectedSpoof = false
        )
    }
}
