package com.mothblank.signaloperator.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns timed gameplay scheduling, cancellation, and foreground gating.
 * Decisions about world state are supplied by GameSession's caller, not stored
 * in the jobs themselves. No Android UI or GameState dependency.
 */
class TimedGameplayCoordinator(private val scope: CoroutineScope) {
    private var breachMonitor: Job? = null
    private var hardwareMonitor: Job? = null
    private var routerCountdown: Job? = null
    private var bootSequence: Job? = null

    fun startBreachMonitor(onTick: () -> Unit) {
        breachMonitor?.cancel()
        breachMonitor = scope.launch {
            while (isActive) {
                delay(25_000)
                onTick()
            }
        }
    }

    fun startHardwareMonitor(isRuntimeActive: () -> Boolean, onTick: () -> Unit) {
        hardwareMonitor?.cancel()
        hardwareMonitor = scope.launch {
            while (isActive) {
                if (isRuntimeActive()) onTick()
                delay(100)
            }
        }
    }

    fun startBootSequence(
        isRuntimeActive: () -> Boolean,
        onStep: (Int?) -> Unit,
        onComplete: () -> Unit
    ) {
        stopBootSequence()
        bootSequence = scope.launch {
            try {
                for (step in 0..3) {
                    onStep(step)
                    delay(if (step == 3) 180L else 210L)
                }
                onStep(null)
                if (isRuntimeActive()) onComplete()
            } finally {
                onStep(null)
            }
        }
    }

    fun stopBootSequence() {
        bootSequence?.cancel()
        bootSequence = null
    }

    /**
     * onTick returns false when the router has closed or timed out.
     * The puzzle rules and state transitions stay in the ViewModel/reducer.
     */
    fun startRouterCountdown(isRuntimeActive: () -> Boolean, onTick: () -> Boolean) {
        stopRouterCountdown()
        routerCountdown = scope.launch {
            while (isActive) {
                if (!isRuntimeActive()) {
                    delay(250)
                    continue
                }
                delay(1000)
                if (!isRuntimeActive()) continue
                if (!onTick()) break
            }
        }
    }

    fun stopRouterCountdown() {
        routerCountdown?.cancel()
        routerCountdown = null
    }

    fun dispose() {
        stopRouterCountdown()
        stopBootSequence()
        breachMonitor?.cancel()
        breachMonitor = null
        hardwareMonitor?.cancel()
        hardwareMonitor = null
    }
}
