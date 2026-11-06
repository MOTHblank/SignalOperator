package com.mothblank.signaloperator.engine

import com.mothblank.signaloperator.models.AdversaryMethod
import com.mothblank.signaloperator.models.AdversaryStrategyState
import com.mothblank.signaloperator.models.Location
import com.mothblank.signaloperator.models.TransmissionRisk
import com.mothblank.signaloperator.models.WorldEffect
import com.mothblank.signaloperator.models.WorldEffectType
import kotlin.random.Random

data class AdversaryReaction(
    val strategy: AdversaryStrategyState,
    val threatenedSectorId: String? = null,
    val threatDelta: Int = 0,
    val exposureDelta: Int = 0
)

object AdversaryDirector {
    fun initial(seed: Long, locations: List<Location>): AdversaryStrategyState {
        val doctrines = AdversaryMethod.values()
        val doctrine = doctrines[Random(seed xor 0x53A9F11DL).nextInt(doctrines.size)]
        val sectors = locations.sortedBy { it.id }
        val vulnerable = if (sectors.isEmpty()) {
            null
        } else {
            sectors[Random(seed xor 0x19D4C27BL).nextInt(sectors.size)].id
        }
        return AdversaryStrategyState(
            doctrine = doctrine,
            vulnerableSectorId = vulnerable
        )
    }

    fun refreshVulnerability(
        strategy: AdversaryStrategyState,
        locations: List<Location>
    ): AdversaryStrategyState {
        val vulnerable = locations.minWithOrNull(
            compareBy<Location> { it.security - it.threat }.thenBy { it.id }
        )?.id ?: strategy.vulnerableSectorId
        return strategy.copy(vulnerableSectorId = vulnerable)
    }

    fun afterSignalResolution(
        strategy: AdversaryStrategyState,
        risk: TransmissionRisk,
        accepted: Boolean,
        locations: List<Location>
    ): AdversaryStrategyState {
        if (risk == TransmissionRisk.NONE) {
            return refreshVulnerability(strategy, locations)
        }

        val updated = strategy.copy(
            alertLevel = (strategy.alertLevel + if (accepted) 2 else 7).coerceIn(0, 100),
            spoofSuccesses = strategy.spoofSuccesses +
                if (accepted && risk == TransmissionRisk.SPOOF) 1 else 0,
            compromisedSuccesses = strategy.compromisedSuccesses +
                if (accepted && risk == TransmissionRisk.COMPROMISED_SOURCE) 1 else 0,
            deceptionAttempts = strategy.deceptionAttempts + 1
        )
        return refreshVulnerability(updated, locations)
    }

    fun afterInvestigation(
        strategy: AdversaryStrategyState,
        campaignSeed: Long,
        investigationCount: Int,
        action: InvestigationActionType,
        evidence: InterceptEvidence,
        exposedIdentity: Boolean,
        locations: List<Location>
    ): AdversaryReaction {
        val alertIncrease = when (action) {
            InvestigationActionType.REPLAY_CARRIER -> 6
            InvestigationActionType.CROSS_REFERENCE_CALLSIGN -> 8
            InvestigationActionType.AUTHENTICATION_CHALLENGE -> 14
        }
        val exposedCallsign = evidence.callsign
            ?.takeIf { exposedIdentity }
            ?.uppercase()
        val exposed = if (exposedCallsign == null) {
            strategy.exposedIdentities
        } else {
            strategy.exposedIdentities + exposedCallsign
        }
        val spoofExposure = exposedIdentity &&
            evidence.authenticationStatus == AuthenticationStatus.FAILED
        val compromisedExposure = exposedIdentity &&
            evidence.authenticationStatus == AuthenticationStatus.VERIFIED_CARRIER &&
            evidence.suspectedDeception

        var updated = refreshVulnerability(
            strategy.copy(
                alertLevel = (strategy.alertLevel + alertIncrease).coerceIn(0, 100),
                spoofExposures = strategy.spoofExposures + if (spoofExposure) 1 else 0,
                compromisedExposures = strategy.compromisedExposures +
                    if (compromisedExposure) 1 else 0,
                exposedIdentities = exposed
            ),
            locations
        )

        val rollSeed = campaignSeed xor
            (evidence.id.hashCode().toLong() shl 1) xor
            (investigationCount.toLong() * 0x9E3779B9L) xor
            action.ordinal.toLong()
        val chance = (
            12 + updated.alertLevel / 2 +
                if (action == InvestigationActionType.AUTHENTICATION_CHALLENGE) 14 else 0
            ).coerceAtMost(75)
        val provoked = locations.isNotEmpty() && Random(rollSeed).nextInt(100) < chance
        if (!provoked) return AdversaryReaction(updated)

        val target = updated.vulnerableSectorId
            ?: locations.sortedBy { it.id }.first().id
        val threatDelta = (8 + updated.alertLevel / 12).coerceAtMost(18)
        val exposureDelta = 2 + updated.alertLevel / 40
        updated = updated.copy(alertLevel = (updated.alertLevel + 5).coerceAtMost(100))
        return AdversaryReaction(
            strategy = updated,
            threatenedSectorId = target,
            threatDelta = threatDelta,
            exposureDelta = exposureDelta
        )
    }

    fun selectActiveInvestigationSlot(
        baseSlot: Int,
        strategy: AdversaryStrategyState?
    ): Int {
        if (strategy == null || baseSlot !in setOf(3, 4, 6, 7)) return baseSlot

        val normalOrder = when (strategy.doctrine) {
            AdversaryMethod.CALLSIGN_SPOOFING -> listOf(3, 7, 6, 4)
            AdversaryMethod.RELAY_MANIPULATION -> listOf(6, 3, 7, 4)
            AdversaryMethod.COMPROMISED_GUIDANCE -> listOf(4, 7, 3, 6)
        }.toMutableList()

        if (strategy.spoofExposures > strategy.spoofSuccesses) {
            normalOrder.remove(4)
            normalOrder.add(0, 4)
        } else if (strategy.compromisedExposures > strategy.compromisedSuccesses) {
            normalOrder.remove(4)
            normalOrder.add(4)
        } else if (strategy.compromisedSuccesses > strategy.spoofSuccesses) {
            normalOrder.remove(4)
            normalOrder.add(0, 4)
        }

        return normalOrder[strategy.deceptionAttempts % normalOrder.size]
    }

    fun pressureEffects(strategy: AdversaryStrategyState?): List<WorldEffect> {
        val target = strategy?.vulnerableSectorId ?: return emptyList()
        val pressure = (8 + strategy.alertLevel / 15).coerceAtMost(16)
        return listOf(
            WorldEffect(
                type = WorldEffectType.ADD_LOCATION_THREAT,
                targetId = target,
                amount = pressure
            ),
            WorldEffect(
                type = WorldEffectType.MARK_LOCATION_INVESTIGATING,
                targetId = target
            )
        )
    }
}
