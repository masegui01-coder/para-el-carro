package com.paraelcarro.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.paraelcarro.data.Settings
import com.paraelcarro.ui.SoundSynth.Note
import com.paraelcarro.ui.SoundSynth.Wave

/**
 * Sonido y vibración de cada momento del juego (los mismos sonidos que la web).
 * Respeta los ajustes de silencio y vibración.
 */
class Feedback(private val context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    private val synth = SoundSynth()

    private fun vibrate(vararg pattern: Long) {
        if (!Settings.vibration) return
        val v = vibrator ?: return
        runCatching { if (v.hasVibrator()) v.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, *pattern), -1)) }
    }

    private fun sound(name: String, notes: () -> List<Note>) {
        if (Settings.sound) synth.play(name, notes)
    }

    fun tick() = sound("tick") { listOf(Note(880.0, .08, Wave.SQUARE, .05)) }
    fun click() = sound("click") { listOf(Note(520.0, .05, Wave.TRIANGLE, .08)) }

    /** Abrir la ronda: un "¡arre!" corto. */
    fun bid() {
        sound("abre") { listOf(Note(330.0, .12, Wave.TRIANGLE, .3), Note(495.0, .18, Wave.TRIANGLE, .3, .1)) }
        vibrate(40)
    }

    /** Subir la cifra: rebuzno de la burra. */
    fun raise() {
        sound("subir") {
            listOf(
                Note(620.0, .22, Wave.SAWTOOTH, .2, 0.0, 900.0), Note(380.0, .3, Wave.SAWTOOTH, .2, .22, 260.0),
                Note(640.0, .2, Wave.SAWTOOTH, .18, .55, 880.0), Note(360.0, .32, Wave.SAWTOOTH, .18, .75, 240.0),
            )
        }
        vibrate(30, 40, 30, 40, 80)
    }

    /** Quedarse en el carro: crujido de madera ("toc-toc"). */
    fun stay() {
        sound("quedar") { listOf(Note(180.0, .07, Wave.SQUARE, .25), Note(140.0, .09, Wave.SQUARE, .25, .11), Note(90.0, .25, Wave.SAWTOOTH, .1, .2, 120.0)) }
        vibrate(20, 60, 20)
    }

    /** Bajarse: silbido de salto y golpe al caer. */
    fun getOff() {
        sound("bajar") { listOf(Note(900.0, .3, Wave.SINE, .3, 0.0, 180.0), Note(70.0, .25, Wave.TRIANGLE, .6, .3)) }
        vibrate(120, 40, 200)
    }

    fun creak() {
        sound("crujido") { listOf(Note(90.0, 1.2, Wave.SAWTOOTH, .12, 0.0, 140.0)) }
        vibrate(80)
    }

    /** El carro cruza: fanfarria. */
    fun crossed() {
        sound("cruza") { listOf(523.0, 659.0, 784.0, 1046.0).mapIndexed { i, f -> Note(f, .25, Wave.TRIANGLE, .3, i * .12) } }
        vibrate(40, 60, 40, 60, 120)
    }

    /** Al río: caída y chapuzón. */
    fun fell() {
        sound("cae") { listOf(Note(400.0, .9, Wave.SAWTOOTH, .2, 0.0, 60.0), Note(140.0, .5, Wave.SINE, .45, .85)) }
        vibrate(300, 80, 500)
    }

    /** La abuela suelta la grabación que toca ([VozAbuela]). Grabaciones en res/raw. */
    fun abuela(voz: com.paraelcarro.domain.model.VozAbuela) {
        vibrate(60, 40, 60, 40, 120)
        if (!Settings.sound) return
        runCatching {
            val player = android.media.MediaPlayer.create(
                context,
                when (voz) {
                    com.paraelcarro.domain.model.VozAbuela.CORTO -> com.paraelcarro.R.raw.abuela_te_pasas
                    com.paraelcarro.domain.model.VozAbuela.SANTO -> com.paraelcarro.R.raw.abuela_espiritu_santo
                    com.paraelcarro.domain.model.VozAbuela.TRILLONES -> com.paraelcarro.R.raw.abuela_trillones
                },
            )
                ?: return
            player.setOnCompletionListener { it.release() }
            player.start()
        }
    }

    fun release() = Unit
}
