package com.mothblank.signaloperator.audio

interface TextToSpeechEngine {
    fun speak(text: String, isAnomalous: Boolean = true)
    fun stop()
    fun release()
    fun isReady(): Boolean
    fun setVoice(voiceName: String?): Boolean
    fun getAvailableVoices(): List<String>
}
