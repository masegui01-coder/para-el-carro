package com.paraelcarro.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paraelcarro.ui.theme.Rustic
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/** Un culpable para la escena: avatar, nombre y lo que dijo ya formateado. */
data class Culpable(val avatar: String, val name: String, val said: String)

const val TEXTO_SE_PASA = "¿No te habrás quedado corto, no?"
const val TEXTO_CORTO = "¡Esto no lo arregla ni el Espíritu Santo!"

/**
 * ¡Qué barbaridad! La abuela entra, suelta su frase y señala a los que dijeron un disparate (−1).
 * Se cierra sola a los 4,5 s o al pulsar "Seguir".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AbuelaOverlay(culpables: List<Culpable>, high: Boolean, answer: String, onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(Unit) {
        delay(4500)
        done()
    }
    val entrada = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrada.animateTo(1f, spring(dampingRatio = .45f, stiffness = 300f)) }
    val anim = rememberInfiniteTransition(label = "abuela")
    val dedo by anim.animateFloat(118f, 128f, infiniteRepeatable(tween(350), RepeatMode.Reverse), label = "dedo")
    val tiembla by anim.animateFloat(-1.5f, 1.5f, infiniteRepeatable(tween(120), RepeatMode.Reverse), label = "tiembla")

    // Capa a pantalla completa por encima de todo (también dentro de pantallas con scroll)
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF0050609))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    if (high) TEXTO_SE_PASA else TEXTO_CORTO,
                    color = Color(0xFF2B1D12), fontFamily = FontFamily.Serif, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, lineHeight = 23.sp,
                    modifier = Modifier
                        .scale(entrada.value.coerceIn(0f, 1.2f))
                        .widthIn(max = 190.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFFFFAF0))
                        .border(3.dp, Color(0xFF2B1D12), RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
                Box(Modifier.offset(x = ((1f - entrada.value) * -200).dp)) {
                    Avatar("av:abuela", 130.dp, highlight = true)
                    Text("👉", fontSize = 42.sp, modifier = Modifier.offset(x = (-26).dp, y = 96.dp).rotate(dedo))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                culpables.forEach { c ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(x = tiembla.dp)) {
                        Box(Modifier.clip(androidx.compose.foundation.shape.CircleShape).border(3.dp, Rustic.Sangre, androidx.compose.foundation.shape.CircleShape)) {
                            Avatar(c.avatar, 70.dp)
                        }
                        Text(c.name, color = Color(0xFFFFF3E0), fontWeight = FontWeight.Bold)
                        Text("dijo ${c.said}", color = Color(0xFFCDBFA8), fontSize = 13.sp, textAlign = TextAlign.Center)
                        Text("−1", color = Color(0xFFFF8A73), fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.alpha(entrada.value.coerceIn(0f, 1f)))
                    }
                }
            }
            Text("La respuesta era $answer", color = Color(0xFFCDBFA8))
            WoodButton("Seguir ⏩", onDone, tone = Tone.FANTASMA, small = true)
        }
    }
    }
}

/** Línea de la tabla de puntos con los disparates de la ronda. */
@Composable
fun BarbaridadesPanel(culpables: List<Culpable>, high: List<Boolean>) {
    if (culpables.isEmpty()) return
    Panel {
        Text("😱 ¡Qué barbaridad! (−1)", fontWeight = FontWeight.Bold, color = Rustic.Rojo)
        culpables.forEachIndexed { i, c ->
            PlayerTag(c.avatar, "${c.name} dijo ${c.said} " + if (high.getOrElse(i) { false }) "(¡se pasó!)" else "(¡se quedó cortísimo!)")
        }
    }
}
