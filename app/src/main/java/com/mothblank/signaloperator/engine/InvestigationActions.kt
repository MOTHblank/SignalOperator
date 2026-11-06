package com.mothblank.signaloperator.engine

import com.mothblank.signaloperator.models.GameState
import com.mothblank.signaloperator.models.LocationStatus

data class InvestigationResolution(
    val performed: Boolean,
    val state: GameState,
    val evidence: InterceptEvidence,
    val summary: String,
    val logs: List<String> = emptyList(),
    val provokedSectorId: String? = null
)

object InvestigationActions {
    const val MAX_ANALYSIS_CYCLES = 5

    fun cost(action: InvestigationActionType): Int = when (action) {
        InvestigationActionType.REPLAY_CARRIER -> 1
        InvestigationActionType.CROSS_REFERENCE_CALLSIGN -> 1
        InvestigationActionType.AUTHENTICATION_CHALLENGE -> 2
    }

    fun perform(
        current: GameState,
        target: InterceptEvidence,
        allEvidence: List<InterceptEvidence>,
        action: InvestigationActionType
    ): InvestigationResolution {
        if (action in target.investigationActions) {
            return InvestigationResolution(
                performed = false,
                state = current,
                evidence = target,
                summary = "ANALYSIS ALREADY FILED FOR THIS INTERCEPT."
            )
        }

        val cost = cost(action)
        if (current.analysisCycles < cost) {
            return InvestigationResolution(
                performed = false,
                state = current,
                evidence = target,
                summary = "INSUFFICIENT ANALYSIS CYCLES // NEED " + cost
            )
        }

        val history = EvidenceCorrelation.retain(allEvidence).filterNot { it.id == target.id }
        var authStatus = target.authenticationStatus
        var detectedIdentity = false
        val note = when (action) {
            InvestigationActionType.REPLAY_CARRIER -> {
                val carrier = target.carrierFingerprint
                if (carrier.isNullOrBlank()) {
                    "CARRIER REPLAY // RF fingerprint was not recovered; replay is inconclusive."
                } else {
                    val matches = history.filter { it.carrierFingerprint == carrier }
                    val callsigns = (matches.mapNotNull { it.callsign } + listOfNotNull(target.callsign))
                        .distinctBy { it.uppercase() }
                    detectedIdentity = callsigns.size > 1
                    if (matches.isEmpty()) {
                        "CARRIER REPLAY // " + carrier + " has no prior archived match."
                    } else {
                        "CARRIER REPLAY // " + carrier + " matches " + matches.size +
                            " prior intercept(s); presented callsigns: " +
                            callsigns.joinToString(", ").ifBlank { "UNKNOWN" } + "."
                    }
                }
            }

            InvestigationActionType.CROSS_REFERENCE_CALLSIGN -> {
                val callsign = target.callsign
                if (callsign.isNullOrBlank()) {
                    "CALLSIGN XREF // source identity was not recovered."
                } else {
                    val matches = history.filter {
                        it.callsign.equals(callsign, ignoreCase = true)
                    }
                    val carriers = (matches.mapNotNull { it.carrierFingerprint } +
                        listOfNotNull(target.carrierFingerprint)).distinct()
                    val sectors = (matches.mapNotNull { it.sectorId } +
                        listOfNotNull(target.sectorId)).distinct()
                    detectedIdentity = carriers.size > 1
                    "CALLSIGN XREF // " + callsign + " appears in " +
                        (matches.size + 1) + " record(s), " + carriers.size +
                        " carrier profile(s), sectors " +
                        sectors.joinToString(", ").ifBlank { "UNKNOWN" } + "."
                }
            }

            InvestigationActionType.AUTHENTICATION_CHALLENGE -> {
                val callsign = target.callsign
                val observed = target.carrierFingerprint
                val baseline = callsign?.let(CarrierIdentity::baselineFingerprint)
                when {
                    callsign.isNullOrBlank() -> {
                        authStatus = AuthenticationStatus.INCONCLUSIVE
                        "AUTH CHALLENGE // no recovered callsign; challenge cannot be routed."
                    }
                    observed.isNullOrBlank() -> {
                        authStatus = AuthenticationStatus.INCONCLUSIVE
                        "AUTH CHALLENGE // " + callsign +
                            " responded, but the carrier fingerprint is incomplete."
                    }
                    baseline == null -> {
                        authStatus = AuthenticationStatus.INCONCLUSIVE
                        "AUTH CHALLENGE // " + callsign +
                            " has no registered carrier baseline."
                    }
                    observed == baseline -> {
                        authStatus = AuthenticationStatus.VERIFIED_CARRIER
                        "AUTH CHALLENGE // carrier matches the registered " + callsign +
                            " baseline. Message content is still unverified."
                    }
                    else -> {
                        authStatus = AuthenticationStatus.FAILED
                        detectedIdentity = true
                        "AUTH CHALLENGE // FAILED. Claimed " + callsign +
                            " carrier " + observed + " does not match baseline " + baseline + "."
                    }
                }
            }
        }

        val updatedEvidence = target.copy(
            suspectedDeception = target.suspectedDeception || detectedIdentity,
            investigationActions = target.investigationActions + action,
            investigationNotes = (target.investigationNotes + note).takeLast(8),
            authenticationStatus = authStatus
        )
        var nextState = current.copy(
            analysisCycles = current.analysisCycles - cost,
            investigationsPerformed = current.investigationsPerformed + 1
        )

        val reaction = AdversaryDirector.afterInvestigation(
            strategy = nextState.adversaryStrategy,
            campaignSeed = current.seed,
            investigationCount = nextState.investigationsPerformed,
            action = action,
            evidence = updatedEvidence,
            exposedIdentity = detectedIdentity,
            locations = nextState.locations
        )

        var reactionLog: String? = null
        var locations = nextState.locations
        if (reaction.threatenedSectorId != null && reaction.threatDelta > 0) {
            locations = locations.map { location ->
                if (location.id == reaction.threatenedSectorId) {
                    val threat = (location.threat + reaction.threatDelta).coerceIn(0, 100)
                    location.copy(
                        threat = threat,
                        status = if (
                            threat >= location.security &&
                            location.status == LocationStatus.SECURE
                        ) LocationStatus.INVESTIGATING else location.status
                    )
                } else {
                    location
                }
            }
            val targetName = locations.firstOrNull {
                it.id == reaction.threatenedSectorId
            }?.name ?: reaction.threatenedSectorId
            reactionLog = "COUNTERSIGNAL DETECTED // ADVERSARY PRESSURE SHIFTED TO " +
                targetName + " (+" + reaction.threatDelta + " THREAT)."
        }

        nextState = nextState.copy(
            locations = locations,
            exposure = (nextState.exposure + reaction.exposureDelta).coerceIn(0, 100),
            adversaryStrategy = AdversaryDirector.refreshVulnerability(
                reaction.strategy,
                locations
            )
        )

        return InvestigationResolution(
            performed = true,
            state = nextState,
            evidence = updatedEvidence,
            summary = note,
            logs = listOfNotNull(
                "INVESTIGATION // " + action.name.replace('_', ' ') +
                    " // EVIDENCE #" + target.sequence + " // COST " + cost + " CYCLE(S).",
                note,
                reactionLog
            ),
            provokedSectorId = reaction.threatenedSectorId
        )
    }
}
