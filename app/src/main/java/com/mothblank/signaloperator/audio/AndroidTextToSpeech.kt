package com.mothblank.signaloperator.audio

import android.content.Context
import android.media.AudioFormat
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID

class AndroidTextToSpeech(
    private val context: Context,
    private val soundManager: SoundManager
) : TextToSpeechEngine, TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    @Volatile private var ready = false
    @Volatile private var released = false

    private val requestsLock = Any()
    private val requests = mutableMapOf<String, SynthesisRequest>()
    private var generation = 0L

    private class SynthesisRequest(
        val file: File,
        val anomalous: Boolean,
        val generation: Long
    ) {
        val audio = ByteArrayOutputStream()
        var sampleRate = 16000
        var audioFormat = AudioFormat.ENCODING_PCM_16BIT
        var channelCount = 1
    }

    private data class CompletedSynthesis(
        val bytes: ByteArray,
        val sampleRate: Int,
        val audioFormat: Int,
        val channelCount: Int,
        val anomalous: Boolean,
        val generation: Long
    )

    init {
        setupListener()
    }

    private fun discardRequest(utteranceId: String?) {
        if (utteranceId == null) return
        synchronized(requestsLock) {
            requests.remove(utteranceId)?.file?.delete()
        }
    }

    private fun setupListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onBeginSynthesis(
                utteranceId: String?,
                sampleRate: Int,
                audioFormat: Int,
                channelCount: Int
            ) {
                synchronized(requestsLock) {
                    val request = requests[utteranceId] ?: return
                    request.sampleRate = sampleRate
                    request.audioFormat = audioFormat
                    request.channelCount = channelCount.coerceAtLeast(1)
                }
            }

            override fun onAudioAvailable(utteranceId: String?, audio: ByteArray?) {
                if (audio == null) return
                synchronized(requestsLock) {
                    requests[utteranceId]?.audio?.write(audio)
                }
            }

            override fun onDone(utteranceId: String?) {
                val completed = synchronized(requestsLock) {
                    val request = requests.remove(utteranceId) ?: return
                    request.file.delete()
                    CompletedSynthesis(
                        bytes = request.audio.toByteArray(),
                        sampleRate = request.sampleRate,
                        audioFormat = request.audioFormat,
                        channelCount = request.channelCount,
                        anomalous = request.anomalous,
                        generation = request.generation
                    )
                }
                if (completed.bytes.isEmpty()) return

                val samples = decodePcm(
                    rawBytes = completed.bytes,
                    audioFormat = completed.audioFormat,
                    channelCount = completed.channelCount
                )
                if (samples.isEmpty()) return

                // A stop/release can arrive while PCM is being decoded.
                synchronized(requestsLock) {
                    if (!released && completed.generation == generation) {
                        soundManager.playVoice(samples, completed.sampleRate, completed.anomalous)
                    }
                }
            }

            override fun onError(utteranceId: String?) {
                discardRequest(utteranceId)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?, errorCode: Int) {
                discardRequest(utteranceId)
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                discardRequest(utteranceId)
            }
        })
    }

    private fun decodePcm(
        rawBytes: ByteArray,
        audioFormat: Int,
        channelCount: Int
    ): FloatArray {
        val interleaved = when (audioFormat) {
            AudioFormat.ENCODING_PCM_8BIT -> FloatArray(rawBytes.size) { index ->
                (((rawBytes[index].toInt() and 0xFF) - 128) / 128f).coerceIn(-1f, 1f)
            }

            AudioFormat.ENCODING_PCM_FLOAT -> {
                val floatCount = rawBytes.size / Float.SIZE_BYTES
                val byteBuffer = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN)
                FloatArray(floatCount) {
                    byteBuffer.getFloat().coerceIn(-1f, 1f)
                }
            }

            else -> {
                val sampleCount = rawBytes.size / Short.SIZE_BYTES
                val shortBuffer = ByteBuffer.wrap(rawBytes)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer()
                FloatArray(sampleCount) {
                    shortBuffer.get() / 32768f
                }
            }
        }

        if (channelCount <= 1 || interleaved.isEmpty()) {
            return interleaved
        }

        val frameCount = interleaved.size / channelCount
        return FloatArray(frameCount) { frame ->
            var sum = 0f
            val base = frame * channelCount
            for (channel in 0 until channelCount) {
                sum += interleaved[base + channel]
            }
            (sum / channelCount).coerceIn(-1f, 1f)
        }
    }


    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS && !released) {
            val result = tts?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setPitch(0.8f)
                tts?.setSpeechRate(0.9f)
                ready = true
            }
        }
    }

    override fun speak(text: String, isAnomalous: Boolean) {
        val engine = tts ?: return
        if (!ready) return

        val utteranceId = UUID.randomUUID().toString()
        val file = File(context.cacheDir, "tts_capture_${utteranceId}.wav")
        synchronized(requestsLock) {
            if (released || !ready) return
            requests[utteranceId] = SynthesisRequest(file, isAnomalous, generation)
        }

        try {
            if (engine.synthesizeToFile(text, android.os.Bundle(), file, utteranceId) != TextToSpeech.SUCCESS) {
                discardRequest(utteranceId)
                Log.e("AndroidTextToSpeech", "Unable to enqueue synthesis")
            }
        } catch (e: Exception) {
            discardRequest(utteranceId)
            Log.e("AndroidTextToSpeech", "Synthesis request failed", e)
        }
    }

    override fun stop() {
        synchronized(requestsLock) {
            generation++
            requests.values.forEach { it.file.delete() }
            requests.clear()
        }
        tts?.stop()
    }

    override fun release() {
        ready = false
        synchronized(requestsLock) {
            released = true
            generation++
            requests.values.forEach { it.file.delete() }
            requests.clear()
        }
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    override fun isReady(): Boolean = ready

    override fun getAvailableVoices(): List<String> {
        return tts?.voices
            ?.filter { it.locale.language.startsWith("en", ignoreCase = true) }
            ?.map { it.name }
            ?: emptyList()
    }

    override fun setVoice(voiceName: String?): Boolean {
        if (!ready || tts == null) return false

        val voices = tts?.voices ?: return false
        val targetVoice = voiceName?.let { name -> voices.find { it.name == name } }

        return targetVoice?.let {
            tts?.voice = it
            true
        } ?: false
    }
}
