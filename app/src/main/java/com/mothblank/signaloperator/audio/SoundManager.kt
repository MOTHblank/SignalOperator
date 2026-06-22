package com.mothblank.signaloperator.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.mothblank.signaloperator.R
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.sin
import java.io.File
import java.io.FileOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SoundManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    
    // UI Sounds
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(5)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private var clickSoundId = -1
    private var alertSoundId = -1

    // Static Noise System (Procedural)
    private var staticTrack: AudioTrack? = null
    private var isStaticRunning = false
    private var staticVolume = 0.5f
    private var staticPitch = 1.0f
    private var stability = 0f

    // Heterodyne & Drone Synthesizer parameters
    private var heterodyneVol = 0f
    private var heterodyneFreq = 1000f
    private var droneVol = 0f

    // Voice System
    private var voiceTrack: AudioTrack? = null

    init {
        setupStaticTrack()
        loadProceduralSounds()
    }

    private fun writeWavFile(file: File, samples: FloatArray, sampleRate: Int) {
        val numSamples = samples.size
        val bitsPerSample = 16
        val byteRate = sampleRate * 1 * bitsPerSample / 8
        val blockAlign = 1 * bitsPerSample / 8
        val subChunk2Size = numSamples * bitsPerSample / 8
        val chunkSize = 36 + subChunk2Size

        FileOutputStream(file).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(chunkSize)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16) // Subchunk1Size
            header.putShort(1.toShort()) // AudioFormat (PCM = 1)
            header.putShort(1.toShort()) // NumChannels (Mono = 1)
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign.toShort())
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(subChunk2Size)
            
            fos.write(header.array())

            // Write PCM data as 16-bit signed shorts
            val buffer = ByteBuffer.allocate(numSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in samples) {
                val shortVal = (sample * 32767).toInt().coerceIn(-32768, 32767).toShort()
                buffer.putShort(shortVal)
            }
            fos.write(buffer.array())
            fos.flush()
        }
    }

    private fun loadProceduralSounds() {
        try {
            val clickFile = File(context.cacheDir, "click.wav")
            val alertFile = File(context.cacheDir, "alert.wav")
            
            // Generate Click
            val clickSampleRate = 22050
            val clickDuration = 0.03
            val clickNumSamples = (clickSampleRate * clickDuration).toInt()
            val clickSamples = FloatArray(clickNumSamples)
            for (i in 0 until clickNumSamples) {
                val t = i.toDouble() / clickSampleRate
                val freq = 150.0 + 1050.0 * Math.exp(-t * 150.0)
                val phase = 2.0 * Math.PI * freq * t
                val env = Math.exp(-t * 120.0)
                clickSamples[i] = (sin(phase) * env * 0.4).toFloat()
            }
            writeWavFile(clickFile, clickSamples, clickSampleRate)
            clickSoundId = soundPool.load(clickFile.absolutePath, 1)
            
            // Generate Alert
            val alertSampleRate = 22050
            val alertPulseDuration = 0.1
            val alertGapDuration = 0.05
            val alertNumSamples = (alertSampleRate * (alertPulseDuration * 2 + alertGapDuration)).toInt()
            val alertSamples = FloatArray(alertNumSamples)
            val alertPulseLen = (alertSampleRate * alertPulseDuration).toInt()
            val alertGapLen = (alertSampleRate * alertGapDuration).toInt()
            
            for (i in 0 until alertNumSamples) {
                val t = i.toDouble() / alertSampleRate
                if (i in 0 until alertPulseLen) {
                    val phase = 2.0 * Math.PI * 880.0 * t
                    alertSamples[i] = (sin(phase) * 0.35).toFloat()
                } else if (i in (alertPulseLen + alertGapLen) until (alertPulseLen * 2 + alertGapLen)) {
                    val phase = 2.0 * Math.PI * 740.0 * (t - (alertPulseDuration + alertGapDuration))
                    alertSamples[i] = (sin(phase) * 0.35).toFloat()
                } else {
                    alertSamples[i] = 0f
                }
            }
            writeWavFile(alertFile, alertSamples, alertSampleRate)
            alertSoundId = soundPool.load(alertFile.absolutePath, 1)
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    private fun setupStaticTrack() {
        val minBufferSize = AudioTrack.getMinBufferSize(
            44100,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        staticTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(44100)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    fun startStatic() {
        if (isStaticRunning) return
        isStaticRunning = true
        staticTrack?.play()
        
        scope.launch {
            val sampleRate = 44100
            val bufferSize = 4096
            val buffer = ShortArray(bufferSize)
            var lastSample = 0.0
            var heterodynePhase = 0.0
            var dronePhase1 = 0.0
            var dronePhase2 = 0.0
            var lfoPhase = 0.0

            while (isStaticRunning) {
                // Dynamic parameters derived from stability
                // High stability (90+) -> Subtle crackle
                // Low stability -> Heavy roaring white noise
                val noiseIntensity = ((100f - stability) / 100f).coerceIn(0.02f, 1f)
                val currentHeterodyneVol = heterodyneVol
                val currentHeterodyneFreq = heterodyneFreq
                val currentDroneVol = droneVol
                
                for (i in buffer.indices) {
                    // White noise
                    val white = (Math.random() * 2.0 - 1.0)
                    
                    // Brown noise (integral of white noise) - gives that heavy low-end roar
                    val brown = (lastSample + (0.02 * white)) / 1.02
                    lastSample = brown
                    
                    // Mix white and brown based on pitch (tuning)
                    // Lower pitch -> More brown (low frequency roar)
                    val noiseMix = (white * (staticPitch - 0.5f) + brown * (2.0f - staticPitch)).coerceIn(-1.0, 1.0)
                    
                    // Heterodyne Whistle
                    val whistle = sin(heterodynePhase) * currentHeterodyneVol
                    heterodynePhase += 2.0 * Math.PI * currentHeterodyneFreq / sampleRate
                    if (heterodynePhase > 2.0 * Math.PI) {
                        heterodynePhase -= 2.0 * Math.PI
                    }

                    // Low-Frequency Drone with LFO pitch modulation
                    val lfo = sin(lfoPhase)
                    val f1 = 55.0 + lfo * 1.5
                    val f2 = 82.4 - lfo * 2.0
                    val drone1 = sin(dronePhase1) * 0.5
                    val drone2 = sin(dronePhase2) * 0.5
                    val droneMix = (drone1 + drone2) * currentDroneVol

                    dronePhase1 += 2.0 * Math.PI * f1 / sampleRate
                    dronePhase2 += 2.0 * Math.PI * f2 / sampleRate
                    lfoPhase += 2.0 * Math.PI * 0.15 / sampleRate

                    if (dronePhase1 > 2.0 * Math.PI) dronePhase1 -= 2.0 * Math.PI
                    if (dronePhase2 > 2.0 * Math.PI) dronePhase2 -= 2.0 * Math.PI
                    if (lfoPhase > 2.0 * Math.PI) lfoPhase -= 2.0 * Math.PI
                    
                    val combined = (noiseMix * noiseIntensity * staticVolume) + whistle + droneMix
                    buffer[i] = (combined * 32767).toInt().coerceIn(-32768, 32767).toShort()
                }
                staticTrack?.write(buffer, 0, buffer.size)
                yield()
            }
        }
    }

    fun stopStatic() {
        isStaticRunning = false
        staticTrack?.stop()
    }

    fun updateStaticParameters(volume: Float, pitch: Float, stability: Float) {
        staticVolume = volume.coerceIn(0f, 1f)
        staticPitch = pitch.coerceIn(0.5f, 2.0f)
        this.stability = stability.coerceIn(0f, 100f)
    }

    fun updateHeterodyne(volume: Float, frequency: Float) {
        heterodyneVol = volume.coerceIn(0f, 0.2f)
        heterodyneFreq = frequency.coerceIn(50f, 3000f)
    }

    fun updateDroneVolume(vol: Float) {
        droneVol = vol.coerceIn(0f, 0.35f)
    }

    fun playVoice(samples: FloatArray, sampleRate: Int, isAnomalous: Boolean = true) {
        voiceTrack?.release()
        
        voiceTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(samples.size * 4)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        // Apply "distortion" based on stability and anomalous status
        val processedSamples = FloatArray(samples.size)
        
        // Anomalous signals get more garbled/noisy
        // Mundane signals are clearer but still have radio crunch
        val baselineNoise = if (isAnomalous) 0.05f else 0.02f
        val noiseLevel = if (isAnomalous) {
            ((100f - stability) / 80f).coerceIn(0f, 1.2f)
        } else {
            ((100f - stability) / 150f).coerceIn(0f, 0.4f)
        }
        
        // Bit depth: Anomalous signals crunch down to 3 bits. Mundane stay at 6+ bits.
        val minBitDepth = if (isAnomalous) 3.0 else 6.0
        val bitDepth = (12.0 * (stability / 100f)).coerceAtLeast(minBitDepth)
        val levels = Math.pow(2.0, bitDepth).toInt()

        // Radio Bandpass Filter Constants (Sharper for mundane, wider for anomalous)
        val fcLp = if (isAnomalous) 2200.0f else 3500.0f
        val fcHp = if (isAnomalous) 500.0f else 300.0f
        val dt = 1.0f / sampleRate
        val rcLp = 1.0f / (2.0f * Math.PI.toFloat() * fcLp)
        val alphaLp = dt / (rcLp + dt)
        val rcHp = 1.0f / (2.0f * Math.PI.toFloat() * fcHp)
        val alphaHp = rcHp / (rcHp + dt)

        var yLp = 0.0f
        var yHp = 0.0f
        var lastLp = 0.0f

        val voiceVolume = (stability / 100f).coerceIn(0.05f, 1.0f)
        val carrierFreq = 1400.0 - (stability.toDouble() * 10.0)
        val whistleIntensity = (0.05f * ((100f - stability) / 100f)).coerceIn(0f, 0.06f)
        val rmFreq = 75.0
        var whistlePhase = 0.0
        var rmPhase = 0.0
        val sampleDt = 1.0 / sampleRate

        for (i in samples.indices) {
            // 1. Bit crush
            var sample = (samples[i] * levels).toInt().toFloat() / levels
            
            // 2. Ring modulation (only for anomalous signals)
            if (isAnomalous) {
                val rmMod = (sin(rmPhase) * 0.6 + 0.4).toFloat()
                sample *= rmMod
                rmPhase += 2.0 * Math.PI * rmFreq * sampleDt
            }
            
            // 3. Overdrive with analog soft saturation: x / (1 + |x|)
            val overdriveBase = if (isAnomalous) 1.5f else 1.1f
            val overdriveFactor = overdriveBase + ((100f - stability) / 60f)
            val saturated = sample * overdriveFactor
            sample = saturated / (1.0f + abs(saturated))
            
            // 4. Bandpass Filter
            yLp += alphaLp * (sample - yLp)
            yHp = alphaHp * (yHp + yLp - lastLp)
            lastLp = yLp
            sample = yHp
            
            // 5. Heterodyne whistle (sweeping carrier frequency)
            val whistle = (sin(whistlePhase) * whistleIntensity).toFloat()
            whistlePhase += 2.0 * Math.PI * carrierFreq * sampleDt
            
            // 6. Add noise
            val totalNoise = noiseLevel * 0.6f + baselineNoise
            val noise = (Math.random() * 2.0 - 1.0).toFloat() * totalNoise
            
            // 7. Combine & Gate
            processedSamples[i] = ((sample * 2.2f + whistle) * voiceVolume + noise).coerceIn(-1.0f, 1.0f)
        }

        voiceTrack?.write(processedSamples, 0, processedSamples.size, AudioTrack.WRITE_BLOCKING)
        voiceTrack?.play()
    }

    fun triggerHaptic(type: String) {
        val duration = when(type) {
            "SCAN_NOTCH" -> 10L
            "BUTTON_CLICK" -> 30L
            "ALARM" -> 60L
            else -> 0L
        }
        if (duration == 0L) return
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = when(type) {
                    "SCAN_NOTCH" -> 60
                    "BUTTON_CLICK" -> 120
                    "ALARM" -> 200
                    else -> VibrationEffect.DEFAULT_AMPLITUDE
                }
                if (type == "ALARM") {
                    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 30, 40, 30), intArrayOf(0, 180, 0, 180), -1))
                } else {
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
                }
            } else {
                @Suppress("DEPRECATION")
                if (type == "ALARM") {
                    vibrator.vibrate(longArrayOf(0, 30, 40, 30), -1)
                } else {
                    vibrator.vibrate(duration)
                }
            }
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    fun playClick() {
        triggerHaptic("BUTTON_CLICK")
        if (clickSoundId != -1) {
            soundPool.play(clickSoundId, 0.4f, 0.4f, 1, 0, 1.0f)
        }
    }

    fun playAlert() {
        triggerHaptic("ALARM")
        if (alertSoundId != -1) {
            soundPool.play(alertSoundId, 0.35f, 0.35f, 1, 0, 1.0f)
        }
    }

    fun pause() {
        if (isStaticRunning) {
            staticTrack?.pause()
        }
        voiceTrack?.pause()
    }

    fun resume() {
        if (isStaticRunning) {
            staticTrack?.play()
        }
        // Voice track usually doesn't need resuming as it's static/short
    }

    fun release() {
        isStaticRunning = false
        staticTrack?.release()
        soundPool.release()
        scope.cancel()
    }
}
