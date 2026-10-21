package com.paraelcarro.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/**
 * Sonidos sintetizados al vuelo (los mismos que la web, sin ficheros de audio):
 * cada sonido es una lista de notas con forma de onda, frecuencia (con deslizamiento), duración y volumen.
 */
class SoundSynth {
    enum class Wave { SINE, SQUARE, SAWTOOTH, TRIANGLE }

    data class Note(val freq: Double, val dur: Double, val wave: Wave, val vol: Double, val delay: Double = 0.0, val to: Double? = null)

    private val rate = 22050
    private val cache = HashMap<String, ShortArray>()

    private fun render(notes: List<Note>): ShortArray {
        val total = notes.maxOf { it.delay + it.dur }
        val out = DoubleArray((total * rate).toInt() + 1)
        for (n in notes) {
            val start = (n.delay * rate).toInt()
            val len = (n.dur * rate).toInt()
            var phase = 0.0
            for (i in 0 until len) {
                val t = i.toDouble() / len
                // Deslizamiento exponencial de frecuencia, como exponentialRampToValueAtTime en la web
                val f = if (n.to != null) n.freq * exp(ln(n.to / n.freq) * t) else n.freq
                phase += f / rate
                val p = phase - kotlin.math.floor(phase)
                val s = when (n.wave) {
                    Wave.SINE -> sin(2 * PI * p)
                    Wave.SQUARE -> if (p < .5) 1.0 else -1.0
                    Wave.SAWTOOTH -> 2 * p - 1
                    Wave.TRIANGLE -> 1 - 4 * kotlin.math.abs(p - .5)
                }
                val env = n.vol * exp(ln(.001 / 1.0) * t) // caída como en la web
                val k = start + i
                if (k < out.size) out[k] += s * env
            }
        }
        return ShortArray(out.size) { (out[it].coerceIn(-1.0, 1.0) * Short.MAX_VALUE * .9).toInt().toShort() }
    }

    fun play(name: String, notes: () -> List<Note>) {
        runCatching {
            val pcm = cache.getOrPut(name) { render(notes()) }
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(pcm, 0, pcm.size)
            track.setNotificationMarkerPosition(pcm.size - 1)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) { t.release() }
                override fun onPeriodicNotification(t: AudioTrack) = Unit
            })
            track.play()
        }
    }
}
