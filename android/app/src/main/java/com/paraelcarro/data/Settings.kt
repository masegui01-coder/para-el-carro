package com.paraelcarro.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode { OSCURO, CLARO, SISTEMA }

/**
 * Ajustes del jugador (sonido, vibración, ilustraciones y tema), guardados en el móvil.
 * Son estado de Compose: al cambiarlos se repinta lo que dependa de ellos.
 */
object Settings {
    private var prefs: android.content.SharedPreferences? = null

    var sound by mutableStateOf(true)
        private set
    var vibration by mutableStateOf(true)
        private set
    var theme by mutableStateOf(ThemeMode.OSCURO)
        private set
    /** Ilustraciones de Wikipedia en "¿Sabías que…?" (usan datos). */
    var images by mutableStateOf(true)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)
        prefs = p
        sound = p.getBoolean("sonido", true)
        vibration = p.getBoolean("vibracion", true)
        images = p.getBoolean("imagenes", true)
        theme = runCatching { ThemeMode.valueOf(p.getString("tema", null)!!) }.getOrDefault(ThemeMode.OSCURO)
    }

    fun updateSound(on: Boolean) { sound = on; prefs?.edit()?.putBoolean("sonido", on)?.apply() }
    fun updateVibration(on: Boolean) { vibration = on; prefs?.edit()?.putBoolean("vibracion", on)?.apply() }
    fun updateImages(on: Boolean) { images = on; prefs?.edit()?.putBoolean("imagenes", on)?.apply() }
    fun updateTheme(mode: ThemeMode) { theme = mode; prefs?.edit()?.putString("tema", mode.name)?.apply() }
}
