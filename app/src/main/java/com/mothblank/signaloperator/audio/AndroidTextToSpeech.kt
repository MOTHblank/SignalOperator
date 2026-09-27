package com.mothblank.signaloperator.audio

import android.content.Context
import android.media.AudioFormat
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
    private var ready = false
    private var isAnomalousCurrent = true
    private val tempFile = File(context.cacheDir, "tts_capture.wav")

    init {
        setupListener()
    }

    private fun setupListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            private var currentSampleRate = 16000
            private var currentAudioFormat = AudioFormat.ENCODING_PCM_16BIT
            private var currentChannelCount = 1
            private val buffer = ByteArrayOutputStream()

            override fun onStart(utteranceId: String?) {
                synchronized(buffer) {
                    buffer.reset()
                }
            }

            override fun onBeginSynthesis(
                utteranceId: String?,
                sampleRate: Int,
                audioFormat: Int,
                channelCount: Int
            ) {
                currentSampleRate = sampleRate
                currentAudioFormat = audioFormat
                currentChannelCount = channelCount.coerceAtLeast(1)
            }

            override fun onAudioAvailable(utteranceId: String?, audio: ByteArray?) {
                if (audio == null) return
                synchronized(buffer) {
                    buffer.write(audio)
                }
            }

            override fun onDone(utteranceId: String?) {
                val rawBytes = synchronized(buffer) {
                    if (buffer.size() == 0) return
                    buffer.toByteArray().also { buffer.reset() }
                }

                val samples = decodePcm(
                    rawBytes = rawBytes,
                    audioFormat = currentAudioFormat,
                    channelCount = currentChannelCount
                )
                if (samples.isNotEmpty()) {
                    soundManager.playVoice(samples, currentSampleRate, isAnomalousCurrent)
                }
            }

            override fun onError(utteranceId: String?) {
                synchronized(buffer) { buffer.reset() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?, errorCode: Int) {
                synchronized(buffer) { buffer.reset() }
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
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setPitch(0.8f)
                tts?.setSpeechRate(0.9f)
                ready = true
            }
        }
    }

    override fun speak(text: String, isAnomalous: Boolean) {
        if (!ready) return

        isAnomalousCurrent = isAnomalous
        val params = android.os.Bundle()
        tts?.synthesizeToFile(text, params, tempFile, UUID.randomUUID().toString())
    }

    override fun stop() {
        tts?.stop()
    }

    override fun release() {
        ready = false
        tts?.shutdown()
        tts = null
        if (tempFile.exists()) tempFile.delete()
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
