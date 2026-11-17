package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.game.GameAudioEvents
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class HorrorAudioEngine : GameAudioEvents {
    private val sampleRate = 22050
    @Volatile var enabled: Boolean = true
    @Volatile var playing: Boolean = false
    @Volatile var tensionLevel: Float = 0f // 0..1 (scales with books & stare)
    @Volatile var stareLevel: Float = 0f   // 0..1

    private var audioThread: Thread? = null
    private var audioTrack: AudioTrack? = null

    private val sfxLock = Any()
    private var footstepEnv = 0f
    private var chimeEnv = 0f
    private var chimeFreq = 523.25f
    private var keyEnv = 0f
    private var doorEnv = 0f
    private var lockEnv = 0f
    private var whisperEnv = 0f
    private var jumpscareEnv = 0f
    private var victoryEnv = 0f

    fun start() {
        if (audioThread != null) return
        playing = true
        audioThread = thread(start = true, isDaemon = true, name = "SlendrinaAudio") {
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(2048)

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack = track
                track.play()

                val chunk = ShortArray(512)
                val rng = Random(42)
                var t = 0.0
                var heartbeatTimer = 0.0
                var heartbeatPulse = 0.0
                val dt = 1.0 / sampleRate

                while (playing) {
                    val isEnabled = enabled
                    val tension = tensionLevel
                    val stare = stareLevel

                    for (i in chunk.indices) {
                        if (!isEnabled) {
                            chunk[i] = 0
                            continue
                        }

                        t += dt
                        // 1. Eerie sub-bass drone + dissonant tritone shimmer
                        val drone = sin(2.0 * PI * 48.0 * t) * 0.14 +
                            sin(2.0 * PI * (48.0 * 1.414) * t) * (0.04 + 0.05 * tension) +
                            sin(2.0 * PI * 110.0 * t) * (0.025 * sin(t * 0.6))

                        // 2. Heartbeat pulse when tension or stare is active
                        val bpm = 58.0 + tension * 48.0 + stare * 65.0
                        val beatPeriod = 60.0 / bpm
                        heartbeatTimer += dt
                        if (heartbeatTimer >= beatPeriod) {
                            heartbeatTimer -= beatPeriod
                            if (tension > 0.15f || stare > 0.05f) {
                                heartbeatPulse = 1.0
                            }
                        }
                        val beat = sin(2.0 * PI * 54.0 * t) * heartbeatPulse * 0.34
                        heartbeatPulse *= 0.9992

                        // 3. Stare static noise
                        val white = (rng.nextFloat() * 2f - 1f)
                        val staticNoise = white * stare * 0.28

                        // 4. One-shot procedural SFX envelopes
                        var sfx = 0.0
                        synchronized(sfxLock) {
                            if (footstepEnv > 0.005f) {
                                sfx += (white * 0.4f + sin(2.0 * PI * 95.0 * t) * 0.6) * footstepEnv * 0.25
                                footstepEnv *= 0.9965f
                            }
                            if (chimeEnv > 0.005f) {
                                sfx += (sin(2.0 * PI * chimeFreq * t) + 0.5 * sin(2.0 * PI * chimeFreq * 1.5 * t)) * chimeEnv * 0.30
                                chimeEnv *= 0.9994f
                            }
                            if (keyEnv > 0.005f) {
                                sfx += (sin(2.0 * PI * 2100.0 * t) + sin(2.0 * PI * 2780.0 * t)) * keyEnv * 0.20
                                keyEnv *= 0.9985f
                            }
                            if (doorEnv > 0.005f) {
                                val creakFreq = 160.0 + 90.0 * sin(t * 14.0)
                                sfx += sin(2.0 * PI * creakFreq * t) * doorEnv * 0.22
                                doorEnv *= 0.9988f
                            }
                            if (lockEnv > 0.005f) {
                                sfx += white * lockEnv * 0.26 * sin(2.0 * PI * 320.0 * t)
                                lockEnv *= 0.9975f
                            }
                            if (whisperEnv > 0.005f) {
                                val snarl = sin(2.0 * PI * 72.0 * t) * (0.45 + 0.55 * sin(t * 28.0))
                                sfx += (white * 0.48 + snarl * 0.34 + sin(2.0 * PI * 640.0 * t) * 0.18) * whisperEnv * 0.27
                                whisperEnv *= 0.9991f
                            }
                            if (jumpscareEnv > 0.005f) {
                                val snarl = sin(2.0 * PI * 86.0 * t) * 0.45
                                val screech = sin(2.0 * PI * (420.0 + 280.0 * sin(t * 45.0)) * t) +
                                    sin(2.0 * PI * 890.0 * t) + snarl + white * 0.7
                                sfx += screech * jumpscareEnv * 0.45
                                jumpscareEnv *= 0.99955f
                            }
                            if (victoryEnv > 0.005f) {
                                val chord = sin(2.0 * PI * 392.0 * t) +
                                    sin(2.0 * PI * 493.88 * t) +
                                    sin(2.0 * PI * 587.33 * t)
                                sfx += chord * victoryEnv * 0.16
                                victoryEnv *= 0.9996f
                            }
                        }

                        val mixed = (drone + beat + staticNoise + sfx).coerceIn(-0.95, 0.95)
                        chunk[i] = (mixed * 32767.0).toInt().toShort()
                    }
                    track.write(chunk, 0, chunk.size)
                }
                track.stop()
                track.release()
            } catch (_: Throwable) {
                // Safe no-op on environments without audio hardware (e.g., headless tests)
            } finally {
                audioTrack = null
            }
        }
    }

    fun stop() {
        playing = false
        audioThread?.interrupt()
        audioThread = null
    }

    override fun onFootstep() {
        synchronized(sfxLock) { footstepEnv = 1.0f }
    }

    override fun onBookCollected(collected: Int, total: Int) {
        synchronized(sfxLock) {
            chimeFreq = 440f + collected * 55f
            chimeEnv = 1.0f
        }
    }

    override fun onKeyCollected() {
        synchronized(sfxLock) { keyEnv = 1.0f }
    }

    override fun onDoorOpened() {
        synchronized(sfxLock) { doorEnv = 1.0f }
    }

    override fun onDoorLocked() {
        synchronized(sfxLock) { lockEnv = 1.0f }
    }

    override fun onSlendrinaAppear(closeRange: Boolean) {
        synchronized(sfxLock) { whisperEnv = if (closeRange) 1.0f else 0.55f }
    }

    override fun onJumpscare() {
        synchronized(sfxLock) { jumpscareEnv = 1.0f }
    }

    override fun onVictory() {
        synchronized(sfxLock) { victoryEnv = 1.0f }
    }
}
