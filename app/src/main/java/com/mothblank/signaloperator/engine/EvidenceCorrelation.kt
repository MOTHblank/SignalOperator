package com.mothblank.signaloperator.engine

import kotlin.math.abs

/** Intercepts are observations, not verified facts or proof of a sender's identity. */
enum class EvidenceReliability { CLEAR, PARTIAL, DEGRADED, UNVERIFIED }
enum class EvidenceDisposition { UNREVIEWED, TRUSTED, SUSPICIOUS }
enum class EvidenceRelation {
    REPEATED_CALLSIGN,
    SHARED_FREQUENCY,
    SHARED_SECTOR,
    MATCHING_CARRIER,
    CARRIER_MISMATCH,
    CARRIER_REUSE,
    CONTRADICTION,
    HISTORICAL_UPDATE,
    UNRESOLVED_CLAIM
}

enum class EvidenceClaimCategory { IDENTITY, LOCATION, STATE, DIRECTIVE, CONTEXT }

/** Provenance describes what a transmission reported, never what actually happened. */
data class ClaimObservation(
    val category: EvidenceClaimCategory,
    val observedSequence: Long,
    val reportedBy: String? = null,
    val transmissionId: String? = null
)

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
    val suspectedDeception: Boolean = false,
    val operatorDisposition: EvidenceDisposition = EvidenceDisposition.UNREVIEWED,
    val claimProvenance: Map<String, ClaimObservation> = emptyMap()
)

data class EvidenceLink(
    val earlierId: String,
    val laterId: String,
    val relation: EvidenceRelation,
    val detail: String,
    val claimKey: String? = null
)

data class EvidenceAssessment(
    val evidence: InterceptEvidence,
    val links: List<EvidenceLink>,
    val warnings: List<String>
)

/**
 * The last 256 intercepts form the live correlation window, NOT the save limit.
 * Earlier observations remain in durable history with stable IDs and can be compared directly.
 */
object EvidenceCorrelation {
    const val MAX_INTERCEPTS = 256
    private const val FREQUENCY_TOLERANCE_MHZ = 0.025f

    fun retain(items: List<InterceptEvidence>): List<InterceptEvidence> =
        items.asReversed().distinctBy { it.id }.asReversed()
            .sortedWith(compareBy<InterceptEvidence> { it.sequence }.thenBy { it.id })

    fun activeWindow(items: List<InterceptEvidence>): List<InterceptEvidence> =
        retain(items).takeLast(MAX_INTERCEPTS)

    fun archive(items: List<InterceptEvidence>): List<InterceptEvidence> =
        retain(items).dropLast(MAX_INTERCEPTS)

    fun categoryFor(key: String): EvidenceClaimCategory = when (key) {
        "actorId" -> EvidenceClaimCategory.IDENTITY
        "actorLocation", "reportedLocation", "subjectLocation" -> EvidenceClaimCategory.LOCATION
        "relayStatus", "anchorStatus", "fieldTeamStatus", "voiceprintStatus",
        "evacuationRequired" -> EvidenceClaimCategory.STATE
        "containmentAdvice", "isolationPolicy", "routeRequest" -> EvidenceClaimCategory.DIRECTIVE
        else -> EvidenceClaimCategory.CONTEXT
    }

    fun observation(item: InterceptEvidence, key: String): ClaimObservation =
        item.claimProvenance[key] ?: ClaimObservation(
            category = categoryFor(key),
            observedSequence = item.sequence,
            reportedBy = item.callsign,
            transmissionId = item.id
        )

    /**
     * A changed state or location at a later observation is a report of a possible
     * development, not proof of deception. Conflicting sources remain unresolved.
     */
    private fun classifyClaim(
        older: InterceptEvidence,
        incoming: InterceptEvidence,
        key: String,
        sameCallsign: Boolean
    ): EvidenceRelation? {
        val earlier = observation(older, key)
        val later = observation(incoming, key)
        if (earlier.category == EvidenceClaimCategory.CONTEXT ||
            later.category == EvidenceClaimCategory.CONTEXT) return null
        val sameReporter = sameCallsign &&
            earlier.reportedBy.equals(later.reportedBy, ignoreCase = true)
        return when {
            earlier.category != later.category -> EvidenceRelation.UNRESOLVED_CLAIM
            earlier.category == EvidenceClaimCategory.IDENTITY ->
                if (sameReporter) EvidenceRelation.CONTRADICTION
                else EvidenceRelation.UNRESOLVED_CLAIM
            earlier.observedSequence == later.observedSequence && sameReporter ->
                EvidenceRelation.CONTRADICTION
            earlier.category == EvidenceClaimCategory.LOCATION ||
                earlier.category == EvidenceClaimCategory.STATE ->
                if (sameReporter && later.observedSequence > earlier.observedSequence)
                    EvidenceRelation.HISTORICAL_UPDATE
                else EvidenceRelation.UNRESOLVED_CLAIM
            else -> EvidenceRelation.UNRESOLVED_CLAIM
        }
    }

    fun assess(existing: List<InterceptEvidence>, incoming: InterceptEvidence): EvidenceAssessment {
        val links = mutableListOf<EvidenceLink>()
        val warnings = mutableListOf<String>()
        for (older in activeWindow(existing).filter { it.id != incoming.id }) {
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
                    val oldValue = older.claims[key] ?: return@forEach
                    if (oldValue.equals(value, ignoreCase = true)) return@forEach
                    // A field about a different reported actor is not the same subject.
                    if (key == "actorLocation" &&
                        older.claims["actorId"] != null && incoming.claims["actorId"] != null &&
                        older.claims["actorId"] != incoming.claims["actorId"]) return@forEach
                    val relation = classifyClaim(older, incoming, key, sameCallsign) ?: return@forEach
                    links += EvidenceLink(
                        older.id, incoming.id, relation,
                        key + ": '" + oldValue + "' versus '" + value + "'",
                        claimKey = key
                    )
                }
            }
        }
        if (incoming.reliability != EvidenceReliability.CLEAR)
            warnings += "Intercept quality is uncertain; claims are not confirmed."
        if (incoming.missingFields.isNotEmpty())
            warnings += "Missing fields: " + incoming.missingFields.sorted().joinToString(", ")
        if (incoming.suspectedDeception)
            warnings += "Possible impersonation; independently verify source."
        if (links.any { it.relation == EvidenceRelation.CONTRADICTION })
            warnings += "Mutually exclusive assertions; neither source is automatically authoritative."
        if (links.any { it.relation == EvidenceRelation.UNRESOLVED_CLAIM })
            warnings += "Different reports are unresolved; timing and provenance may explain the difference."
        if (links.any { it.relation == EvidenceRelation.CARRIER_MISMATCH })
            warnings += "Claimed identity does not match the prior RF fingerprint."
        if (links.any { it.relation == EvidenceRelation.CARRIER_REUSE })
            warnings += "One RF carrier is presenting multiple claimed identities."
        return EvidenceAssessment(incoming, links.distinct(), warnings.distinct())
    }

    fun append(existing: List<InterceptEvidence>, incoming: InterceptEvidence): List<InterceptEvidence> =
        retain(existing.filterNot { it.id == incoming.id } + incoming)
}
