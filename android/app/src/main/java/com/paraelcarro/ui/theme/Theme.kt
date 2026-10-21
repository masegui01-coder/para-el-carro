package com.paraelcarro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.paraelcarro.data.Settings
import com.paraelcarro.data.ThemeMode

/** Tema activo. Es estado de Compose: al cambiarlo se repinta todo lo que use colores de [Rustic]. */
object ThemeState {
    var light by mutableStateOf(false)
}

/**
 * Paleta rústica: noche, madera, musgo y ámbar de farol. Los colores de fondo y de texto cambian
 * con el tema claro ("de día": pergamino y madera); la madera, el ámbar y el musgo son siempre iguales.
 */
object Rustic {
    private val light get() = ThemeState.light

    val Noche: Color get() = if (light) Color(0xFFF3E9D6) else Color(0xFF14161C)
    val Noche2: Color get() = if (light) Color(0xFFE8DCC4) else Color(0xFF1D212B)
    val Cielo: Color get() = if (light) Color(0xFFFFF6E4) else Color(0xFF2B3342)
    val Texto: Color get() = if (light) Color(0xFF2B1D12) else Color(0xFFEFE6D6)
    val TextoSuave: Color get() = if (light) Color(0xFF6B5A45) else Color(0xFFB9AD99)
    val AmbarClaro: Color get() = if (light) Color(0xFFA8650C) else Color(0xFFFFD27A)
    val Verde: Color get() = if (light) Color(0xFF2F7A3A) else Color(0xFF8FD18F)
    val Rojo: Color get() = if (light) Color(0xFFB23A26) else Color(0xFFFF8A73)
    /** Fondo de las etiquetas de jugador. */
    val Chip: Color get() = if (light) Color(0x1F6B4423) else Color(0x59000000)

    val Niebla = Color(0xFF9AA5B1)
    val Madera = Color(0xFF6B4423)
    val MaderaClara = Color(0xFFA0703C)
    val MaderaOscura = Color(0xFF3E2714)
    val Ambar = Color(0xFFF2A93B)
    val Musgo = Color(0xFF2F4A32)
    val MusgoClaro = Color(0xFF4F7A4F)
    val Sangre = Color(0xFFC0503A)
}

@Composable
fun ParaElCarroTheme(content: @Composable () -> Unit) {
    val light = when (Settings.theme) {
        ThemeMode.OSCURO -> false
        ThemeMode.CLARO -> true
        ThemeMode.SISTEMA -> !isSystemInDarkTheme()
    }
    ThemeState.light = light
    val colors = if (light) {
        lightColorScheme(
            primary = Rustic.Ambar, onPrimary = Color(0xFF2A1A08), secondary = Rustic.MusgoClaro, tertiary = Rustic.MaderaClara,
            background = Rustic.Noche, onBackground = Rustic.Texto, surface = Rustic.Noche2, onSurface = Rustic.Texto, error = Rustic.Sangre,
        )
    } else {
        darkColorScheme(
            primary = Rustic.Ambar, onPrimary = Color(0xFF2A1A08), secondary = Rustic.MusgoClaro, tertiary = Rustic.MaderaClara,
            background = Rustic.Noche, onBackground = Rustic.Texto, surface = Rustic.Noche2, onSurface = Rustic.Texto, error = Rustic.Sangre,
        )
    }
    MaterialTheme(colorScheme = colors) {
        // Texto con el color del tema por defecto: las pantallas no van dentro de un Surface
        CompositionLocalProvider(LocalContentColor provides Rustic.Texto, content = content)
    }
}
