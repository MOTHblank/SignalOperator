package com.mothblank.signaloperator.engine

import kotlin.math.abs

/** Observations are not verified facts; matching metadata is not proof of identity. */
enum class EvidenceReliability { CLEAR, PARTIAL, DEGRADED, UNVERIFIED }
enum class EvidenceDisposition { UNREVIEWED, TRUSTED, SUSPICIOUS }
enum class EvidenceRelation {
    REPEATED_CALLSIGN,
    SHARED_FREQUENCY,
    SHARED_SECTOR,
    MATCHING_CARRIER,
    CARRIER_MISMATCH,
    CARRIER_REUSE,
    CONTRADICTION
}

data class InterceptEvidence(
    val id: String,
    val sequence: Long,
    val frequencyMhz: Float,
    val callsign: String?,
    val sectorId: String?,
    val message: String,
    val reliability: EvidenceReliability,
    val claims: Map<String, String> = emptyMap(),
    val carrierFingerprint: String? = null,
    val missingFields: Set<String> = emptySet(),
    val suspectedSpoof: Boolean = false,
    val operatorDisposition: EvidenceDisposition = EvidenceDisposition.UNREVIEWED
)

data class EvidenceLink(
    val earlierId: String,
    val laterId: String,
    val relation: EvidenceRelation,
    val detail: String
)

data class EvidenceAssessment(
    val evidence: InterceptEvidence,
    val links: List<EvidenceLink>,
    val warnings: List<String>
)

/** Deterministic, bounded correlation model for a persistent investigation notebook. */
object EvidenceCorrelation {
    const val MAX_INTERCEPTS = 256
    private const val FREQUENCY_TOLERANCE_MHZ = 0.025f

    fun retain(items: List<InterceptEvidence>): List<InterceptEvidence> =
        items.distinctBy { it.id }
            .sortedWith(compareBy<InterceptEvidence> { it.sequence }.thenBy { it.id })
            .takeLast(MAX_INTERCEPTS)

    fun assess(existing: List<InterceptEvidence>, incoming: InterceptEvidence): EvidenceAssessment {
        val links = mutableListOf<EvidenceLink>()
        val warnings = mutableListOf<String>()
        for (older in retain(existing).filter { it.id != incoming.id }) {
            val sameCallsign = !incoming.callsign.isNullOrBlank() &&
                incoming.callsign.equals(older.callsign, ignoreCase = true)
            val sameSector = !incoming.sectorId.isNullOrBlank() &&
                incoming.sectorId.equals(older.sectorId, ignoreCase = true)
            if (sameCallsign) links += EvidenceLink(
                older.id, incoming.id, EvidenceRelation.REPEATED_CALLSIGN,
                "Repeated callsign: " + incoming.callsign
            )
            if (abs(incoming.frequencyMhz - older.frequencyMhz) <= FREQUENCY_TOLERANCE_MHZ)
                links += EvidenceLink(
                    older.id, incoming.id, EvidenceRelation.SHARED_FREQUENCY,
                    "Similar frequency: " + older.frequencyMhz + " MHz"
                )
            if (sameSector) links += EvidenceLink(
                older.id, incoming.id, EvidenceRelation.SHARED_SECTOR,
                "Shared sector: " + incoming.sectorId
            )

            val incomingCarrier = incoming.carrierFingerprint
            val olderCarrier = older.carrierFingerprint
            if (!incomingCarrier.isNullOrBlank() && !olderCarrier.isNullOrBlank()) {
                if (sameCallsign && incomingCarrier == olderCarrier) {
                    links += EvidenceLink(
                        older.id, incoming.id, EvidenceRelation.MATCHING_CARRIER,
                        "Carrier fingerprint matches prior " + incoming.callsign + " traffic"
                    )
                } else if (sameCallsign && incomingCarrier != olderCarrier) {
                    links += EvidenceLink(
                        older.id, incoming.id, EvidenceRelation.CARRIER_MISMATCH,
                        "Callsign " + incoming.callsign + " moved from " + olderCarrier + " to " + incomingCarrier
                    )
                } else if (!sameCallsign && incomingCarrier == olderCarrier) {
                    links += EvidenceLink(
                        older.id, incoming.id, EvidenceRelation.CARRIER_REUSE,
                        "Carrier " + incomingCarrier + " appeared under multiple callsigns"
                    )
                }
            }

            if (sameCallsign || sameSector) {
                incoming.claims.forEach { (key, value) ->
                    val earlier = older.claims[key]
                    if (earlier != null && !earlier.equals(value, ignoreCase = true))
                        links += EvidenceLink(
                            older.id, incoming.id, EvidenceRelation.CONTRADICTION,
                            key + ": '" + earlier + "' versus '" + value + "'"
                        )
                }
            }
        }
        if (incoming.reliability != EvidenceReliability.CLEAR)
            warnings += "Intercept quality is uncertain; claims are not confirmed."
        if (incoming.missingFields.isNotEmpty())
            warnings += "Missing fields: " + incoming.missingFields.sorted().joinToString(", ")
        if (incoming.suspectedSpoof)
            warnings += "Possible impersonation; independently verify source."
        if (links.any { it.relation == EvidenceRelation.CONTRADICTION })
            warnings += "Contradictory reports; neither is automatically authoritative."
        if (links.any { it.relation == EvidenceRelation.CARRIER_MISMATCH })
            warnings += "Claimed identity does not match the prior RF fingerprint."
        if (links.any { it.relation == EvidenceRelation.CARRIER_REUSE })
            warnings += "One RF carrier is presenting multiple claimed identities."
        return EvidenceAssessment(incoming, links.distinct(), warnings)
    }

    fun append(existing: List<InterceptEvidence>, incoming: InterceptEvidence): List<InterceptEvidence> =
        retain(existing.filterNot { it.id == incoming.id } + incoming)
}
