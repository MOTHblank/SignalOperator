package com.mothblank.signaloperator.engine

import com.mothblank.signaloperator.models.SignalData
import com.mothblank.signaloperator.models.SignalKind

/**
 * Converts a processed radio signal into only the information the operator
 * actually recovered. Structured claims are degraded alongside visible text.
 */
object EvidenceCapture {
    fun fromSignal(
        signal: SignalData,
        stability: Float,
        sequence: Long,
        gain: Int? = null,
        filter: Int? = null,
        proximity: Float = 1f
    ): InterceptEvidence {
        val recovered = InterceptRecovery.recover(
            signal = signal,
            stability = stability,
            gain = gain,
            filter = filter,
            proximity = proximity
        )
        val reliability = when (recovered.quality) {
            InterceptQuality.CLEAR -> EvidenceReliability.CLEAR
            InterceptQuality.PARTIAL -> EvidenceReliability.PARTIAL
            InterceptQuality.DEGRADED -> EvidenceReliability.DEGRADED
            InterceptQuality.UNUSABLE -> EvidenceReliability.UNVERIFIED
        }

        val claims = signal.claims.toMutableMap()

        if (
            "authentication detail" in recovered.missingFields ||
            "source identity" in recovered.missingFields
        ) {
            claims.remove("actorId")
        }

        if ("sector reference" in recovered.missingFields) {
            claims.remove("reportedLocation")
            claims.remove("actorLocation")
            claims.remove("routeRequest")
        }

        if (
            recovered.quality == InterceptQuality.DEGRADED &&
            claims.size > 1
        ) {
            val removable = claims.keys
                .filterNot { it == "signalKind" }
                .sorted()
            if (removable.isNotEmpty()) {
                val index = (signal.id.hashCode() and Int.MAX_VALUE) % removable.size
                claims.remove(removable[index])
            }
        }

        if (
            "authentication detail" !in recovered.missingFields &&
            "source identity" !in recovered.missingFields
        ) {
            signal.outcome?.actorId?.let { claims["actorId"] = it }
        }
        claims["signalKind"] = signal.kind.name

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
            carrierFingerprint = recovered.carrierFingerprint,
            missingFields = missing,
            suspectedDeception = false
        )
    }
}
