package com.paraelcarro.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Vibración y pitidos sencillos (sin ficheros de audio). */
class Feedback(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    private val tones = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 60) }.getOrNull()

    private fun vibrate(vararg pattern: Long) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, *pattern), -1))
    }

    fun tick() = tones?.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
    fun bid() = vibrate(30)
    fun raise() = vibrate(20, 40, 20)
    fun creak() = vibrate(80)

    /** El carro cruza: vibración alegre. */
    fun crossed() {
        tones?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
        vibrate(40, 60, 40, 60, 120)
    }

    /** Al río: vibración larga. */
    fun fell() {
        tones?.startTone(ToneGenerator.TONE_SUP_ERROR, 600)
        vibrate(300, 80, 500)
    }

    fun release() = tones?.release()
}
