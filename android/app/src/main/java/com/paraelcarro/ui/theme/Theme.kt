package com.paraelcarro.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/** Paleta rústica y tenebrosa: noche, madera, musgo y ámbar de farol. */
object Rustic {
    val Noche = Color(0xFF14161C)
    val Noche2 = Color(0xFF1D212B)
    val Niebla = Color(0xFF9AA5B1)
    val Madera = Color(0xFF6B4423)
    val MaderaClara = Color(0xFFA0703C)
    val MaderaOscura = Color(0xFF3E2714)
    val Ambar = Color(0xFFF2A93B)
    val AmbarClaro = Color(0xFFFFD27A)
    val Musgo = Color(0xFF2F4A32)
    val MusgoClaro = Color(0xFF4F7A4F)
    val Sangre = Color(0xFFC0503A)
    val Texto = Color(0xFFEFE6D6)
    val TextoSuave = Color(0xFFB9AD99)
    val Verde = Color(0xFF8FD18F)
    val Rojo = Color(0xFFFF8A73)
}

private val colors = darkColorScheme(
    primary = Rustic.Ambar,
    onPrimary = Color(0xFF2A1A08),
    secondary = Rustic.MusgoClaro,
    tertiary = Rustic.MaderaClara,
    background = Rustic.Noche,
    onBackground = Rustic.Texto,
    surface = Rustic.Noche2,
    onSurface = Rustic.Texto,
    error = Rustic.Sangre,
)

@Composable
fun ParaElCarroTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors) {
        // Texto claro por defecto: las pantallas no van dentro de un Surface
        CompositionLocalProvider(LocalContentColor provides Rustic.Texto, content = content)
    }
}
