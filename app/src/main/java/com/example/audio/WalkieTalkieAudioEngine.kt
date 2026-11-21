package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Real-time Cloud Walkie-Talkie Audio Engine.
 * Captures 8kHz 16-bit mono PCM via AudioRecord in ~250ms chunks for low-latency cloud streaming,
 * and plays incoming Base64 PCM chunks live through AudioTrack.
 */
class WalkieTalkieAudioEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private val _isReceiving = MutableStateFlow(false)
    val isReceiving: StateFlow<Boolean> = _isReceiving.asStateFlow()

    private var recordJob: Job? = null
    private var receiveDecayJob: Job? = null
    private var playbackTrack: AudioTrack? = null
    private val playbackLock = Any()

    @Volatile
    private var isMutedCompletely: Boolean = false

    @Volatile
    var rogerBeepStyle: String = "CLASSIC"

    @Volatile
    var radioFxEnabled: Boolean = false

    @Volatile
    var noiseSuppressionEnabled: Boolean = true

    @Volatile
    var squelchEnabled: Boolean = true

    fun setMuted(muted: Boolean) {
        isMutedCompletely = muted
        if (muted) {
            stopTransmitting(playTone = false)
            _isReceiving.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun startTransmitting(
        playTone: Boolean,
        onChunkEncoded: (String) -> Unit
    ) {
        if (isMutedCompletely) return
        if (recordJob?.isActive == true) return

        if (squelchEnabled) {
            playSquelchBurst()
        }
        if (playTone) {
            playChirp(start = true)
        }

        recordJob = scope.launch {
            var recorder: AudioRecord? = null
            var noiseSuppressor: NoiseSuppressor? = null
            var echoCanceler: AcousticEchoCanceler? = null
            var autoGain: AutomaticGainControl? = null
            try {
                val minBuf = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(CLOUD_CHUNK_BYTES * 2)

                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf
                )

                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    Log.w(TAG, "AudioRecord failed to initialize")
                    return@launch
                }

                if (noiseSuppressionEnabled) {
                    val sessionId = recorder.audioSessionId
                    runCatching {
                        if (NoiseSuppressor.isAvailable()) {
                            noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
                        }
                    }
                    runCatching {
                        if (AcousticEchoCanceler.isAvailable()) {
                            echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
                        }
                    }
                    runCatching {
                        if (AutomaticGainControl.isAvailable()) {
                            autoGain = AutomaticGainControl.create(sessionId)?.apply { enabled = true }
                        }
                    }
                }

                recorder.startRecording()

                val chunkBuffer = ByteArray(CLOUD_CHUNK_BYTES)
                while (isActive) {
                    var offset = 0
                    while (offset < CLOUD_CHUNK_BYTES && isActive) {
                        val read = recorder.read(
                            chunkBuffer,
                            offset,
                            CLOUD_CHUNK_BYTES - offset
                        )
                        if (read > 0) {
                            offset += read
                            _micLevel.value = calculateRmsLevel(chunkBuffer, offset)
                        } else {
                            break
                        }
                    }
                    if (offset > 0 && isActive) {
                        val encoded = Base64.encodeToString(
                            chunkBuffer,
                            0,
                            offset,
                            Base64.NO_WRAP
                        )
                        onChunkEncoded(encoded)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cloud transmission loop ended: ${e.message}")
            } finally {
                _micLevel.value = 0f
                runCatching { noiseSuppressor?.release() }
                runCatching { echoCanceler?.release() }
                runCatching { autoGain?.release() }
                try {
                    recorder?.stop()
                    recorder?.release()
                } catch (_: Exception) {}
            }
        }
    }

    fun stopTransmitting(playTone: Boolean = false) {
        val wasActive = recordJob?.isActive == true
        recordJob?.cancel()
        recordJob = null
        _micLevel.value = 0f
        if (wasActive && !isMutedCompletely) {
            if (squelchEnabled) {
                playSquelchBurst()
            }
            if (playTone) {
                playChirp(start = false)
            }
        }
    }

    fun playIncomingAudio(audioBase64: String) {
        if (isMutedCompletely || audioBase64.isBlank()) return
        scope.launch {
            try {
                val pcm = Base64.decode(audioBase64, Base64.NO_WRAP)
                if (pcm.isEmpty()) return@launch
                val processed = if (radioFxEnabled) applyRadioFx(pcm) else pcm
                synchronized(playbackLock) {
                    val track = ensurePlaybackTrack()
                    val written = track?.write(processed, 0, processed.size) ?: -1
                    if (written < 0) {
                        runCatching {
                            playbackTrack?.stop()
                            playbackTrack?.release()
                        }
                        playbackTrack = null
                    }
                }
                _isReceiving.value = true
                receiveDecayJob?.cancel()
                receiveDecayJob = scope.launch {
                    delay(500L)
                    _isReceiving.value = false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to play incoming audio chunk: ${e.message}")
            }
        }
    }

    private fun ensurePlaybackTrack(): AudioTrack? {
        playbackTrack?.let {
            if (it.state == AudioTrack.STATE_INITIALIZED) {
                if (it.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    it.play()
                }
                return it
            }
        }
        return try {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(CLOUD_CHUNK_BYTES * 2)

            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
                .also {
                    it.play()
                    playbackTrack = it
                }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to create AudioTrack: ${e.message}")
            null
        }
    }

    fun playSquelchBurst() {
        if (isMutedCompletely) return
        scope.launch {
            try {
                val durationMs = 55
                val sampleCount = (SAMPLE_RATE * durationMs) / 1000
                val pcm = ShortArray(sampleCount)
                var prev = 0
                for (i in 0 until sampleCount) {
                    val progress = i.toDouble() / sampleCount
                    val envelope = sin(PI * progress)
                    val white = Random.nextInt(-3600, 3600)
                    val filtered = (white - (prev * 0.4f).toInt())
                    prev = white
                    pcm[i] = (filtered * envelope).toInt().coerceIn(-6000, 6000).toShort()
                }
                playStaticTone(pcm, durationMs)
            } catch (_: Exception) {}
        }
    }

    fun playChirp(start: Boolean) {
        scope.launch {
            try {
                val style = rogerBeepStyle
                val durationMs = when (style) {
                    "POLICE" -> if (start) 95 else 150
                    "MODERN" -> if (start) 65 else 90
                    else -> if (start) 75 else 110
                }
                val sampleCount = (SAMPLE_RATE * durationMs) / 1000
                val pcm = ShortArray(sampleCount)
                val (freq1, freq2) = when (style) {
                    "POLICE" -> if (start) 1050.0 to 1580.0 else 1580.0 to 920.0
                    "MODERN" -> if (start) 660.0 to 990.0 else 990.0 to 660.0
                    else -> if (start) 880.0 to 1240.0 else 1180.0 to 760.0
                }

                for (i in 0 until sampleCount) {
                    val progress = i.toDouble() / sampleCount
                    val freq = if (progress < 0.5) freq1 else freq2
                    val envelope = sin(PI * progress)
                    val sample = sin(2.0 * PI * freq * i / SAMPLE_RATE) * envelope * 9000.0
                    pcm[i] = sample.toInt().toShort()
                }
                playStaticTone(pcm, durationMs)
            } catch (_: Exception) {}
        }
    }

    fun playPingSiren() {
        if (isMutedCompletely) return
        scope.launch {
            try {
                val durationMs = 360
                val sampleCount = (SAMPLE_RATE * durationMs) / 1000
                val pcm = ShortArray(sampleCount)
                for (i in 0 until sampleCount) {
                    val progress = i.toDouble() / sampleCount
                    val freq = if (progress < 0.33 || progress > 0.66) 1420.0 else 980.0
                    val envelope = sin(PI * ((progress * 3.0) % 1.0))
                    val sample = sin(2.0 * PI * freq * i / SAMPLE_RATE) * envelope * 11500.0
                    pcm[i] = sample.toInt().toShort()
                }
                playStaticTone(pcm, durationMs)
            } catch (_: Exception) {}
        }
    }

    private suspend fun playStaticTone(pcm: ShortArray, durationMs: Int) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            runCatching { track.release() }
            return
        }
        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            delay(durationMs.toLong() + 45L)
            track.stop()
        } catch (_: Exception) {
        } finally {
            runCatching { track.release() }
        }
    }

    private fun applyRadioFx(pcm: ByteArray): ByteArray {
        val out = ByteArray(pcm.size)
        var prev = 0
        var i = 0
        while (i + 1 < pcm.size) {
            val low = pcm[i].toInt() and 0xFF
            val high = pcm[i + 1].toInt() shl 8
            val sample = (high or low).toShort().toInt()
            // Crisp walkie-talkie high-pass + soft-clip saturation
            val filtered = ((sample - (prev * 0.45f).toInt()) * 1.18f).toInt().coerceIn(-22000, 22000)
            prev = sample
            out[i] = (filtered and 0xFF).toByte()
            out[i + 1] = ((filtered shr 8) and 0xFF).toByte()
            i += 2
        }
        return out
    }

    fun release() {
        stopTransmitting(playTone = false)
        receiveDecayJob?.cancel()
        synchronized(playbackLock) {
            try {
                playbackTrack?.stop()
                playbackTrack?.release()
            } catch (_: Exception) {}
            playbackTrack = null
        }
        scope.cancel()
    }

    private fun calculateRmsLevel(bytes: ByteArray, length: Int): Float {
        var sum = 0.0
        var samples = 0
        var i = 0
        while (i + 1 < length) {
            val low = bytes[i].toInt() and 0xFF
            val high = bytes[i + 1].toInt() shl 8
            val sample = (high or low).toShort().toDouble()
            sum += sample * sample
            samples++
            i += 2
        }
        if (samples == 0) return 0f
        val rms = sqrt(sum / samples)
        return min(1f, (rms / 8500.0).toFloat())
    }

    companion object {
        private const val TAG = "WalkieTalkieAudioEngine"
        private const val SAMPLE_RATE = 8000
        private const val CLOUD_CHUNK_BYTES = 4000 // 250ms @ 8kHz 16-bit mono
    }
}
