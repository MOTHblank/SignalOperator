package com.mothblank.signaloperator.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class AndroidTextToSpeech(private val context: Context, private val soundManager: SoundManager) : TextToSpeechEngine, TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var ready = false
    private var isAnomalousCurrent = true
    private val tempFile = File(context.cacheDir, "tts_capture.wav")

    init {
        setupListener()
    }

    private fun setupListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            private var currentSampleRate = 16000
            private val buffer = mutableListOf<Byte>()

            override fun onStart(utteranceId: String?) {
                buffer.clear()
            }

            override fun onBeginSynthesis(utteranceId: String?, sampleRate: Int, audioFormat: Int, channelCount: Int) {
                currentSampleRate = sampleRate
            }

            override fun onAudioAvailable(utteranceId: String?, audio: ByteArray?) {
                audio?.let { buffer.addAll(it.toList()) }
            }

            override fun onDone(utteranceId: String?) {
                if (buffer.isEmpty()) return

                val rawBytes = buffer.toByteArray()
                val shorts = ShortArray(rawBytes.size / 2)
                ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                val floats = FloatArray(shorts.size) { shorts[it] / 32768f }

                // Route to sound manager for radio distortion
                soundManager.playVoice(floats, currentSampleRate, isAnomalousCurrent)
            }

            override fun onError(utteranceId: String?) {}
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?, errorCode: Int) {}
        })
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                // We keep pitch/rate fairly neutral here as we'll apply DSP in SoundManager
                tts?.setPitch(0.8f)
                tts?.setSpeechRate(0.9f)
                ready = true
            }
        }
    }

    override fun speak(text: String, isAnomalous: Boolean) {
        if (ready) {
            isAnomalousCurrent = isAnomalous
            // Use synthesizeToFile to capture audio silently through the listener
            val params = android.os.Bundle()
            tts?.synthesizeToFile(text, params, tempFile, UUID.randomUUID().toString())
        }
    }

    override fun stop() {
        tts?.stop()
    }

    override fun release() {
        tts?.shutdown()
        if (tempFile.exists()) tempFile.delete()
    }

    override fun isReady(): Boolean = ready

    override fun getAvailableVoices(): List<String> {
        return tts?.voices?.filter {
            it.locale.language.startsWith("en", ignoreCase = true)
        }?.map { it.name } ?: emptyList()
    }

    override fun setVoice(voiceName: String?): Boolean {
        if (!ready || tts == null) return false

        val voices = tts?.voices ?: return false
        val targetVoice = if (voiceName != null) {
            voices.find { it.name == voiceName }
        } else {
            null
        }

        return targetVoice?.let {
            tts?.voice = it
            true
        } ?: false
    }
}
