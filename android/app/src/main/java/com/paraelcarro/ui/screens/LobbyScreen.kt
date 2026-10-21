package com.paraelcarro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paraelcarro.domain.model.AVATARS
import com.paraelcarro.domain.model.GameState
import com.paraelcarro.domain.model.MAX_PLAYERS
import com.paraelcarro.domain.model.MIN_PLAYERS
import com.paraelcarro.domain.model.QuestionMode
import com.paraelcarro.ui.GameViewModel
import com.paraelcarro.ui.components.Avatar
import com.paraelcarro.ui.components.AvatarPickerDialog
import com.paraelcarro.ui.components.Panel
import com.paraelcarro.ui.components.Title
import com.paraelcarro.ui.components.Tone
import com.paraelcarro.ui.components.WoodButton
import com.paraelcarro.ui.theme.Rustic

@Composable
fun LobbyScreen(state: GameState, vm: GameViewModel) {
    var pickingFor by remember { mutableStateOf<Int?>(null) }
    val players = state.players

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WoodButton("← Inicio", vm::goHome, tone = Tone.FANTASMA, small = true)
        Title("Jugadores (${players.size}/$MAX_PLAYERS)")
        Panel {
            players.forEachIndexed { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .border(2.dp, Rustic.Ambar, CircleShape)
                            .background(Color.Black.copy(alpha = .35f))
                            .clickable { pickingFor = p.id },
                        contentAlignment = Alignment.Center,
                    ) { Avatar(p.avatar, 52.dp, background = false) }
                    OutlinedTextField(
                        value = p.name,
                        onValueChange = { vm.rename(p.id, it) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Rustic.Ambar, unfocusedBorderColor = Rustic.MaderaClara),
                        modifier = Modifier.weight(1f),
                    )
                    if (i == 0) {
                        Text("👑 Jefe", color = Rustic.Ambar, fontSize = 12.sp)
                    } else {
                        WoodButton("✕", { vm.removePlayer(p.id) }, tone = Tone.FANTASMA, small = true, enabled = players.size > MIN_PLAYERS)
                    }
                }
            }
            WoodButton("+ Añadir jugador", vm::addPlayer, Modifier.fillMaxWidth(), tone = Tone.FANTASMA, small = true, enabled = players.size < MAX_PLAYERS)
        }

        Title("👑 El jefe de sala decide")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Número de rondas", Modifier.weight(1f))
                WoodButton("−", { vm.setRounds(state.config.rounds - 1) }, small = true)
                Text("${state.config.rounds}", Modifier.size(48.dp, 32.dp), color = Rustic.Ambar, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                WoodButton("+", { vm.setRounds(state.config.rounds + 1) }, small = true)
            }
            Text("Tipo de preguntas")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(QuestionMode.FECHAS to "📅 Fechas", QuestionMode.NUMEROS to "🔢 Números", QuestionMode.MEZCLA to "🎲 Mezcla").forEach { (mode, label) ->
                    WoodButton(
                        label, { vm.setMode(mode) }, Modifier.weight(1f), small = true,
                        tone = if (state.config.mode == mode) Tone.AMBAR else Tone.FANTASMA,
                    )
                }
            }
        }
        WoodButton("¡Arre, burra! Empezar", vm::startGame, Modifier.fillMaxWidth(), tone = Tone.AMBAR)
    }

    pickingFor?.let { id ->
        val me = players.first { it.id == id }
        val taken = players.filter { it.id != id }.map { it.avatar }.toSet()
        AvatarPickerDialog(me.avatar, taken, onPick = { vm.setAvatar(id, it); pickingFor = null }, onDismiss = { pickingFor = null })
    }
}
