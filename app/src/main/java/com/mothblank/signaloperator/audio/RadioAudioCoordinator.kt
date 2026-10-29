package com.mothblank.signaloperator.audio

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Owns Android audio resources, per-signal voice selection, and the cancellable
 * transmission loop. The campaign ViewModel supplies immutable audio inputs.
 */
class RadioAudioCoordinator(
    application: Application,
    private val scope: CoroutineScope
) {
    private val sound = SoundManager(application)
    private val speech: TextToSpeechEngine = AndroidTextToSpeech(application, sound)
    private var transmissionJob: Job? = null

    fun setEffectsEnabled(enabled: Boolean) = sound.setEffectsEnabled(enabled)
    fun setHapticsEnabled(enabled: Boolean) = sound.setHapticsEnabled(enabled)
    fun startStatic() = sound.startStatic()
    fun stopStatic() = sound.stopStatic()
    fun playScanNotch() = sound.playScanNotch()
    fun playCarrierNear() = sound.playCarrierNear()
    fun playLockAcquired() = sound.playLockAcquired()
    fun playCalibrationSync() = sound.playCalibrationSync()
    fun playPacketComplete() = sound.playPacketComplete()
    fun playRouterTile(connectedNeighbors: Int) = sound.playRouterTile(connectedNeighbors)
    fun playCircuitComplete() = sound.playCircuitComplete()
    fun playCommit() = sound.playCommit()
    fun playDiscard() = sound.playDiscard()
    fun playClick() = sound.playClick()
    fun playTypewriterTick() = sound.playTypewriterTick()
    fun playAlert() = sound.playAlert()
    fun triggerHaptic(type: String) = sound.triggerHaptic(type)
    fun updateStaticParameters(volume: Float, pitch: Float, stability: Float) =
        sound.updateStaticParameters(volume, pitch, stability)

    fun updateSignalMix(
        soundEnabled: Boolean,
        proximity: Float,
        stability: Float,
        hasActiveSignal: Boolean,
        minHotspotDistance: Float,
        corruptionLevel: Float
    ) {
        val masterVol = if (soundEnabled) 1.0f else 0.0f
        val baseVol = (0.2f + proximity * 0.8f).coerceIn(0f, 1f) * masterVol
        val vol = if (hasActiveSignal) baseVol * 0.10f else baseVol
        val pitch = (0.8f + proximity * 0.4f).coerceIn(0.5f, 2f)
        sound.updateStaticParameters(vol, pitch, stability)

        if (soundEnabled && !hasActiveSignal && minHotspotDistance < 0.6f) {
            val factor = (1f - minHotspotDistance / 0.6f).coerceIn(0f, 1f)
            sound.updateHeterodyne(factor * 0.12f, (minHotspotDistance / 0.6f) * 1800f + 80f)
        } else {
            sound.updateHeterodyne(0f, 1000f)
        }
        val drone = (corruptionLevel * 0.15f).coerceIn(0f, 0.35f) * masterVol
        sound.updateDroneVolume(drone)
    }

    fun startRadioLoop(text: String, isAnomalous: Boolean, signalId: String, enabled: Boolean) {
        stopRadioLoop()
        if (!enabled) return
        transmissionJob = scope.launch {
            while (isActive) {
                if (speech.isReady()) {
                    val voices = speech.getAvailableVoices()
                    if (voices.isNotEmpty()) {
                        val voice = voices[Random(signalId.hashCode().toLong()).nextInt(voices.size)]
                        speech.setVoice(voice)
                    }
                }
                Log.d("RadioAudioCoordinator", "Radio transmission using Android TTS")
                speech.speak(text, isAnomalous)
                delay(8000)
            }
        }
    }

    fun stopRadioLoop() {
        transmissionJob?.cancel()
        transmissionJob = null
        speech.stop()
    }

    fun pause() {
        stopRadioLoop()
        sound.pause()
    }

    fun resume() = sound.resume()

    fun release() {
        stopRadioLoop()
        sound.release()
        speech.release()
    }
}
