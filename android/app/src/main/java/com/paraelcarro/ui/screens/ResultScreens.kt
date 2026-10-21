package com.paraelcarro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paraelcarro.domain.format
import com.paraelcarro.domain.model.GameState
import com.paraelcarro.domain.model.Player
import com.paraelcarro.domain.model.RoundResult
import com.paraelcarro.ui.components.Avatar
import com.paraelcarro.ui.components.Panel
import com.paraelcarro.ui.components.PlayerTag
import com.paraelcarro.ui.components.RatingRow
import com.paraelcarro.ui.components.RoundHeader
import com.paraelcarro.ui.components.Tone
import com.paraelcarro.ui.components.WoodButton
import com.paraelcarro.ui.theme.Rustic

@Composable
fun RoundScoreScreen(state: GameState, result: RoundResult, alreadyRated: Boolean, onRate: (String) -> Unit, onNext: () -> Unit) {
    val q = result.question
    val driver = state.player(result.driverId)!!
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RoundHeader(state.roundNumber, state.config.rounds, q)
        Text(
            if (result.success) "¡El carro cruzó el puente!" else "¡Al río con la burra!",
            color = if (result.success) Rustic.Verde else Rustic.Rojo,
            fontSize = 26.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        if (result.bullseye) {
            Text("🎯 ¡Clavada! +2 para el conductor", color = Rustic.AmbarClaro, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Panel {
            Text(q.text, fontFamily = FontFamily.Serif)
            Text("Respuesta: ${q.format(q.answer)} · ${driver.name} dijo ${q.format(result.bid)}", fontWeight = FontWeight.Bold, color = Rustic.AmbarClaro)
            if (q.note.isNotEmpty()) Text("💡 ${q.note}", color = Rustic.TextoSuave)
            RatingRow(q.id, alreadyRated, onRate)
        }
        Panel {
            Row { Text("Jugador", Modifier.weight(1f), color = Rustic.TextoSuave); Text("Ronda", Modifier.weight(.4f), color = Rustic.TextoSuave, textAlign = TextAlign.End); Text("Total", Modifier.weight(.4f), color = Rustic.TextoSuave, textAlign = TextAlign.End) }
            state.players.sortedByDescending { it.score }.forEach { p ->
                val d = result.delta[p.id] ?: 0
                val icon = when (p.id) { result.driverId -> "🐴"; in result.gotOff -> "🚶"; else -> "🛒" }
                HorizontalDivider(color = Rustic.MaderaClara.copy(alpha = .25f))
                Row {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { Text("$icon "); PlayerTag(p.avatar, p.name) }
                    Text(if (d > 0) "+$d" else "$d", Modifier.weight(.4f), textAlign = TextAlign.End, fontWeight = FontWeight.ExtraBold,
                        color = when { d > 0 -> Rustic.Verde; d < 0 -> Rustic.Rojo; else -> Rustic.Texto })
                    Text("${p.score}", Modifier.weight(.4f), textAlign = TextAlign.End, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        WoodButton(if (state.isLastRound) "🏆 Ver el podio" else "Siguiente ronda ➜", onNext, Modifier.fillMaxWidth(), tone = Tone.AMBAR)
    }
}

@Composable
fun FinalScreen(state: GameState, onRematch: () -> Unit, onLobby: () -> Unit) {
    val ranking = state.players.sortedByDescending { it.score }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("🏆 ${ranking.first().name} gana", color = Rustic.Ambar, fontSize = 32.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            Podium(ranking.getOrNull(1), 70, "2º", Modifier.weight(1f))
            Podium(ranking.getOrNull(0), 100, "1º", Modifier.weight(1f))
            Podium(ranking.getOrNull(2), 50, "3º", Modifier.weight(1f))
        }
        Panel {
            ranking.forEachIndexed { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically) { Text("${i + 1}. "); PlayerTag(p.avatar, p.name, Modifier.weight(1f)); Text("${p.score}", fontWeight = FontWeight.ExtraBold) }
            }
        }
        WoodButton("Revancha (mismos jugadores)", onRematch, Modifier.fillMaxWidth(), tone = Tone.AMBAR)
        WoodButton("Cambiar jugadores o rondas", onLobby, Modifier.fillMaxWidth(), tone = Tone.FANTASMA)
    }
}

@Composable
private fun Podium(p: Player?, height: Int, pos: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (p == null) return@Column
        Avatar(p.avatar, 56.dp, highlight = true)
        Text(p.name, fontSize = 13.sp, textAlign = TextAlign.Center)
        Box(
            Modifier
                .fillMaxWidth()
                .height(height.dp)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(Brush.verticalGradient(listOf(Rustic.MaderaClara, Rustic.MaderaOscura))),
            contentAlignment = Alignment.TopCenter,
        ) { Text("$pos\n${p.score}", color = Color.White, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)) }
    }
}
