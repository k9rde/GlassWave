package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
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

        if (playTone) {
            playChirp(start = true)
        }

        recordJob = scope.launch {
            var recorder: AudioRecord? = null
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
        if (wasActive && playTone && !isMutedCompletely) {
            playChirp(start = false)
        }
    }

    fun playIncomingAudio(audioBase64: String) {
        if (isMutedCompletely || audioBase64.isBlank()) return
        scope.launch {
            try {
                val pcm = Base64.decode(audioBase64, Base64.NO_WRAP)
                if (pcm.isEmpty()) return@launch
                synchronized(playbackLock) {
                    val track = ensurePlaybackTrack()
                    track?.write(pcm, 0, pcm.size)
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

    fun playChirp(start: Boolean) {
        scope.launch {
            try {
                val durationMs = if (start) 75 else 110
                val sampleCount = (SAMPLE_RATE * durationMs) / 1000
                val pcm = ShortArray(sampleCount)
                val freq1 = if (start) 880.0 else 1180.0
                val freq2 = if (start) 1240.0 else 760.0

                for (i in 0 until sampleCount) {
                    val progress = i.toDouble() / sampleCount
                    val freq = if (progress < 0.5) freq1 else freq2
                    val envelope = sin(PI * progress)
                    val sample = sin(2.0 * PI * freq * i / SAMPLE_RATE) * envelope * 9000.0
                    pcm[i] = sample.toInt().toShort()
                }

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

                track.write(pcm, 0, pcm.size)
                track.play()
                delay(durationMs.toLong() + 40L)
                track.stop()
                track.release()
            } catch (_: Exception) {}
        }
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
