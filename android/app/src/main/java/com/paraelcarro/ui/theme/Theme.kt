package com.paraelcarro.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paraelcarro.R
import com.paraelcarro.data.Settings
import com.paraelcarro.data.ThemeMode

/**
 * Tema activo, derivado del ajuste del jugador y del modo del sistema. Es estado de Compose: al cambiar
 * cualquiera de los dos se repinta todo lo que use colores de [Rustic].
 */
object ThemeState {
    /** ¿El sistema está en modo claro? Lo fija la actividad al crearse y al cambiar la configuración. */
    var systemLight by mutableStateOf(true)
    val light: Boolean
        get() = when (Settings.theme) {
            ThemeMode.OSCURO -> false
            ThemeMode.CLARO -> true
            ThemeMode.SISTEMA -> systemLight
        }
}

/** Fuentes del estilo Tebeo (res/font, OFL): Baloo 2 para títulos y botones, Nunito para el texto. */
@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, vararg weights: Int) = FontFamily(
    weights.map { w -> Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) },
)

/**
 * Paleta "Tebeo": crema, contorno negro grueso, amarillo, azul y cielo. Se sigue llamando Rustic para no
 * tocar todas las pantallas (Noche = fondo, Noche2 = paneles, Ambar = amarillo, Musgo = verde).
 * Los colores de fondo y de texto cambian con el tema oscuro; los rellenos de los botones son fijos.
 */
object Rustic {
    private val light get() = ThemeState.light

    val Titulos: FontFamily = variable(R.font.baloo2, 600, 800)
    val Cuerpo: FontFamily = variable(R.font.nunito, 600, 800)

    /** Contornos y sombras de cómic (en oscuro, claros para que se vean: ≥ 3:1 sobre el fondo). */
    val Tinta: Color get() = if (light) Color(0xFF1B1B1B) else Color(0xFFBDB49C)
    /** Selección y foco (el azul no llega a 3:1 sobre el fondo oscuro: allí, amarillo). */
    val Foco: Color get() = if (light) Color(0xFF1F5FD1) else Color(0xFFFFC93C)
    /** Sombra de cómic desplazada (negra en los dos temas). */
    val Sombra: Color get() = if (light) Color(0xFF1B1B1B) else Color(0xFF000000)
    /** Texto sobre rellenos claros (amarillo, verde), igual en los dos temas. */
    val TintaFija = Color(0xFF1B1B1B)
    /** Fondo de la pantalla. */
    val Noche: Color get() = if (light) Color(0xFFFFF4D6) else Color(0xFF1E2033)
    /** Fondo de paneles y tarjetas. */
    val Noche2: Color get() = if (light) Color(0xFFFFFFFF) else Color(0xFF2B2E48)
    val Cielo: Color get() = if (light) Color(0xFF6EC6F0) else Color(0xFF2C5F80)
    val Texto: Color get() = if (light) Color(0xFF1B1B1B) else Color(0xFFFFF4D6)
    val TextoSuave: Color get() = if (light) Color(0xFF4A4438) else Color(0xFFCFC8B4)
    /** Cifras y resaltados de texto (contraste ≥ 4.5:1 sobre el fondo). */
    val AmbarClaro: Color get() = if (light) Color(0xFF1F5FD1) else Color(0xFFFFC93C)
    val Verde: Color get() = if (light) Color(0xFF1E7A4C) else Color(0xFF7BE0A8)
    val Rojo: Color get() = if (light) Color(0xFFB3261E) else Color(0xFFFF8A80)
    /** Fondo de etiquetas y rellenos suaves. */
    val Chip: Color get() = if (light) Color(0xFFFFF0C2) else Color(0xFF363A5A)

    val Amarillo = Color(0xFFFFC93C)
    val Azul = Color(0xFF1F5FD1)
    val Ambar = Amarillo
    val Musgo = Color(0xFF57CC99)
    val Sangre = Color(0xFFC62A36)
}

@Composable
fun ParaElCarroTheme(content: @Composable () -> Unit) {
    val light = ThemeState.light
    val base = if (light) lightColorScheme() else darkColorScheme()
    val colors = base.copy(
        primary = Rustic.Amarillo, onPrimary = Color(0xFF1B1B1B), secondary = Rustic.Azul, onSecondary = Color.White,
        tertiary = Rustic.Musgo, background = Rustic.Noche, onBackground = Rustic.Texto, surface = Rustic.Noche2,
        onSurface = Rustic.Texto, surfaceVariant = Rustic.Chip, onSurfaceVariant = Rustic.TextoSuave, error = Rustic.Sangre,
        outline = Rustic.Tinta, primaryContainer = Rustic.Amarillo, onPrimaryContainer = Rustic.TintaFija,
        surfaceContainerLowest = Rustic.Noche2, surfaceContainerLow = Rustic.Noche2, surfaceContainer = Rustic.Noche2,
        surfaceContainerHigh = Rustic.Noche2, surfaceContainerHighest = Rustic.Chip,
    )
    val cuerpo = TextStyle(fontFamily = Rustic.Cuerpo, fontWeight = FontWeight(600), fontSize = 16.sp, lineHeight = 22.sp)
    val titulo = TextStyle(fontFamily = Rustic.Titulos, fontWeight = FontWeight.ExtraBold)
    val typography = Typography(
        displayLarge = titulo.copy(fontSize = 52.sp, lineHeight = 56.sp), displayMedium = titulo.copy(fontSize = 44.sp, lineHeight = 48.sp),
        displaySmall = titulo.copy(fontSize = 36.sp, lineHeight = 40.sp), headlineLarge = titulo.copy(fontSize = 32.sp, lineHeight = 36.sp),
        headlineMedium = titulo.copy(fontSize = 28.sp, lineHeight = 32.sp), headlineSmall = titulo.copy(fontSize = 24.sp, lineHeight = 28.sp),
        titleLarge = titulo.copy(fontSize = 24.sp), titleMedium = titulo.copy(fontSize = 18.sp), titleSmall = titulo.copy(fontSize = 16.sp),
        bodyLarge = cuerpo, bodyMedium = cuerpo.copy(fontSize = 14.sp, lineHeight = 20.sp), bodySmall = cuerpo.copy(fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = titulo.copy(fontSize = 16.sp), labelMedium = titulo.copy(fontSize = 14.sp), labelSmall = titulo.copy(fontSize = 12.sp),
    )
    // Esquinas de cómic: pequeñas en botones pequeños, grandes en tarjetas y diálogos
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(22.dp),
    )
    // Tirador y selección de texto en el color de foco (el amarillo no se ve sobre el crema)
    val seleccion = TextSelectionColors(handleColor = Rustic.Foco, backgroundColor = Rustic.Foco.copy(alpha = .3f))
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes) {
        // Texto con el color del tema por defecto: las pantallas no van dentro de un Surface
        CompositionLocalProvider(LocalContentColor provides Rustic.Texto, LocalTextSelectionColors provides seleccion, content = content)
    }
}
