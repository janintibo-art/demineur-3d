package com.demineur3d.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Une note : fréquence en Hz (0 = silence), durée en secondes, vélocité 0..1. */
data class Note(val freq: Double, val dur: Double, val vel: Double = 0.8)

/** Pistes musicales — transcrites à l'identique de la version web. */
enum class MusicTrack(val label: String, val icon: String, val tempo: Int, val notes: List<Note>) {
    OFF("", "🔇", 0, emptyList()),

    TECHNO("TECHNO", "⚡", 160, listOf(
        Note(65.0, 0.09, 0.85), Note(0.0, 0.04), Note(65.0, 0.09, 0.85),
        Note(82.0, 0.09, 0.75), Note(0.0, 0.04), Note(82.0, 0.09, 0.75),
        Note(65.0, 0.09, 0.85), Note(0.0, 0.04), Note(65.0, 0.09, 0.85),
        Note(98.0, 0.13, 0.95), Note(0.0, 0.08),
        Note(65.0, 0.09, 0.85), Note(0.0, 0.04), Note(65.0, 0.09, 0.85),
        Note(82.0, 0.09, 0.75), Note(0.0, 0.04), Note(82.0, 0.09, 0.75),
        Note(65.0, 0.09, 0.85), Note(0.0, 0.04), Note(65.0, 0.09, 0.85),
        Note(110.0, 0.18, 1.0)
    )),

    METAL("METAL", "🤘", 200, listOf(
        Note(82.0, 0.1, 1.0), Note(98.0, 0.1, 0.95), Note(110.0, 0.15, 1.0),
        Note(0.0, 0.05), Note(82.0, 0.1, 1.0), Note(98.0, 0.1, 0.95),
        Note(123.0, 0.15, 1.0), Note(0.0, 0.05),
        Note(82.0, 0.08, 0.95), Note(98.0, 0.08, 0.85), Note(110.0, 0.08, 0.95),
        Note(98.0, 0.08, 0.85), Note(82.0, 0.12, 0.95), Note(0.0, 0.08),
        Note(110.0, 0.1, 1.0), Note(123.0, 0.1, 1.0), Note(147.0, 0.22, 1.0)
    )),

    DNB("D&B", "🔊", 190, listOf(
        Note(87.0, 0.06, 0.95), Note(0.0, 0.03), Note(87.0, 0.06, 0.95),
        Note(110.0, 0.06, 0.85), Note(0.0, 0.03), Note(110.0, 0.06, 0.85),
        Note(87.0, 0.06, 0.95), Note(0.0, 0.03), Note(87.0, 0.06, 0.95),
        Note(131.0, 0.1, 1.0), Note(0.0, 0.06),
        Note(87.0, 0.06, 0.95), Note(0.0, 0.03), Note(87.0, 0.06, 0.95),
        Note(110.0, 0.06, 0.85), Note(0.0, 0.03), Note(110.0, 0.06, 0.85),
        Note(87.0, 0.06, 0.95), Note(0.0, 0.03), Note(87.0, 0.06, 0.95),
        Note(147.0, 0.13, 1.0)
    )),

    CHOPIN("CHOPIN", "🎹", 95, listOf(
        Note(392.0, 0.35, 0.65), Note(440.0, 0.35, 0.65),
        Note(494.0, 0.35, 0.75), Note(587.0, 0.7, 0.85),
        Note(494.0, 0.35, 0.65), Note(440.0, 0.35, 0.65),
        Note(392.0, 0.7, 0.75), Note(0.0, 0.18),
        Note(330.0, 0.35, 0.55), Note(392.0, 0.35, 0.65),
        Note(440.0, 0.35, 0.65), Note(494.0, 0.7, 0.75),
        Note(440.0, 0.35, 0.65), Note(392.0, 0.35, 0.65),
        Note(330.0, 0.7, 0.75), Note(0.0, 0.18),
        Note(523.0, 0.5, 0.75), Note(587.0, 0.25, 0.65),
        Note(659.0, 0.5, 0.85), Note(587.0, 0.25, 0.65),
        Note(523.0, 0.7, 0.75), Note(440.0, 0.7, 0.75),
        Note(392.0, 1.0, 0.85)
    ))
}

enum class Sfx { REVEAL, FLAG, QUESTION, CHORD, EXPLOSION, WIN }

/**
 * Synthétiseur maison qui reproduit la WebAudio API de la version web :
 * oscillateurs carrés désaccordés + sub-basse + harmonique, enveloppe ADSR.
 * Tout est généré à la volée, aucun fichier audio dans l'APK.
 */
class AudioEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var musicJob: Job? = null
    private var musicTrack: AudioTrack? = null

    var currentTrack: MusicTrack = MusicTrack.CHOPIN
        private set

    // ---------- Musique ----------

    fun playMusic(track: MusicTrack) {
        currentTrack = track
        stopMusic()
        if (track == MusicTrack.OFF || track.notes.isEmpty()) return

        musicJob = scope.launch {
            val at = createTrack()
            musicTrack = at
            at.play()
            val gap = 60.0 / track.tempo * 0.25 // silence entre les notes, comme sur le web
            while (isActive) {
                for (note in track.notes) {
                    if (!isActive) break
                    val samples = if (note.freq <= 0.0) {
                        ShortArray((SAMPLE_RATE * note.dur).toInt())
                    } else {
                        synthNote(note.freq, note.dur, note.vel, MUSIC_VOLUME)
                    }
                    at.write(samples, 0, samples.size)
                    val silence = ShortArray((SAMPLE_RATE * gap).toInt())
                    at.write(silence, 0, silence.size)
                }
            }
            runCatching { at.stop(); at.release() }
        }
    }

    fun stopMusic() {
        musicJob?.cancel()
        musicJob = null
        runCatching { musicTrack?.pause(); musicTrack?.flush() }
        musicTrack = null
    }

    // ---------- Effets sonores ----------

    fun playSfx(sfx: Sfx) {
        scope.launch {
            val samples = when (sfx) {
                Sfx.REVEAL -> sweep(400.0, 900.0, 0.06, 0.25, square = true)
                Sfx.FLAG -> sweep(700.0, 1200.0, 0.07, 0.3, square = true)
                Sfx.QUESTION -> sweep(500.0, 350.0, 0.08, 0.25, square = true)
                Sfx.CHORD -> sweep(600.0, 1400.0, 0.12, 0.35, square = true)
                Sfx.EXPLOSION -> explosion()
                Sfx.WIN -> fanfare()
            }
            playOnce(samples)
        }
    }

    // ---------- Synthèse ----------

    /** Note "HD" : 3 carrés désaccordés + sub sinus + harmonique, comme playHDNote() du web. */
    private fun synthNote(freq: Double, dur: Double, vel: Double, volume: Double): ShortArray {
        val n = (SAMPLE_RATE * dur).toInt()
        val out = ShortArray(n)
        val attack = (0.005 * SAMPLE_RATE).toInt().coerceAtLeast(1)
        val release = (0.08 * SAMPLE_RATE).toInt().coerceAtLeast(1)

        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            var s = square(freq, t) * 0.5
            s += square(freq * 1.007, t) * 0.3   // détune +
            s += square(freq * 0.993, t) * 0.3   // détune −
            s += sin(2 * PI * (freq / 2) * t) * 0.35   // sub-basse
            s += sin(2 * PI * (freq * 2) * t) * 0.12   // harmonique

            // Enveloppe : attaque rapide, decay exponentiel, release
            var env = 1.0
            if (i < attack) env = i.toDouble() / attack
            if (i > n - release) env *= (n - i).toDouble() / release
            env *= exp(-2.0 * t / dur.coerceAtLeast(0.05))

            out[i] = clamp(s * env * vel * volume)
        }
        return out
    }

    private fun sweep(from: Double, to: Double, dur: Double, vol: Double, square: Boolean): ShortArray {
        val n = (SAMPLE_RATE * dur).toInt()
        val out = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val p = i.toDouble() / n
            val f = from + (to - from) * p
            phase += 2 * PI * f / SAMPLE_RATE
            val s = if (square) (if (sin(phase) >= 0) 1.0 else -1.0) * 0.6 else sin(phase)
            val env = (1.0 - p) * (1.0 - p)
            out[i] = clamp(s * env * vol)
        }
        return out
    }

    /** Explosion : bruit blanc filtré + descente de fréquence. */
    private fun explosion(): ShortArray {
        val dur = 0.6
        val n = (SAMPLE_RATE * dur).toInt()
        val out = ShortArray(n)
        var phase = 0.0
        var lowpass = 0.0
        for (i in 0 until n) {
            val p = i.toDouble() / n
            val f = 120.0 * (1.0 - p * 0.8)
            phase += 2 * PI * f / SAMPLE_RATE
            val noise = Random.nextDouble(-1.0, 1.0)
            lowpass += (noise - lowpass) * 0.25   // filtre passe-bas simple
            val s = sin(phase) * 0.6 + lowpass * 0.7
            val env = exp(-4.0 * p)
            out[i] = clamp(s * env * 0.85)
        }
        return out
    }

    /** Victoire : arpège ascendant do–mi–sol–do. */
    private fun fanfare(): ShortArray {
        val freqs = listOf(523.0, 659.0, 784.0, 1047.0)
        val noteDur = 0.16
        val n = (SAMPLE_RATE * noteDur * freqs.size).toInt()
        val out = ShortArray(n)
        val per = n / freqs.size
        for (k in freqs.indices) {
            for (i in 0 until per) {
                val t = i.toDouble() / SAMPLE_RATE
                val p = i.toDouble() / per
                val s = square(freqs[k], t) * 0.4 + sin(2 * PI * freqs[k] * t) * 0.5
                val env = (1.0 - p) * 0.9
                val idx = k * per + i
                if (idx < n) out[idx] = clamp(s * env * 0.5)
            }
        }
        return out
    }

    private fun square(freq: Double, t: Double): Double =
        if (sin(2 * PI * freq * t) >= 0) 1.0 else -1.0

    private fun clamp(v: Double): Short =
        (v.coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.85).toInt().toShort()

    // ---------- Plomberie AudioTrack ----------

    private fun createTrack(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun playOnce(samples: ShortArray) {
        runCatching {
            val at = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            at.write(samples, 0, samples.size)
            at.setNotificationMarkerPosition(samples.size)
            at.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack?) { runCatching { t?.release() } }
                override fun onPeriodicNotification(t: AudioTrack?) {}
            })
            at.play()
        }
    }

    fun release() {
        stopMusic()
        scope.cancel()
    }

    private companion object {
        const val SAMPLE_RATE = 44100
        const val MUSIC_VOLUME = 0.35
    }
}
