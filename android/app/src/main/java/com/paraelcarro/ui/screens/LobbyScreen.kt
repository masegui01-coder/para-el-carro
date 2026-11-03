package com.paraelcarro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paraelcarro.R
import com.paraelcarro.domain.model.GameState
import com.paraelcarro.domain.model.MAX_PLAYERS
import com.paraelcarro.domain.model.MIN_PLAYERS
import com.paraelcarro.domain.model.QuestionMode
import com.paraelcarro.ui.GameViewModel
import com.paraelcarro.ui.components.Avatar
import com.paraelcarro.ui.components.AvatarAssets
import com.paraelcarro.ui.components.AvatarPickerDialog
import com.paraelcarro.ui.components.BarbaridadesSwitch
import com.paraelcarro.ui.components.Etiqueta
import com.paraelcarro.ui.components.Panel
import com.paraelcarro.ui.components.Title
import com.paraelcarro.ui.components.Tone
import com.paraelcarro.ui.components.WoodButton
import com.paraelcarro.ui.components.avatarColor
import com.paraelcarro.ui.components.focusRing
import com.paraelcarro.ui.components.tebeoFieldColors
import com.paraelcarro.ui.theme.Rustic

@Composable
fun LobbyScreen(state: GameState, vm: GameViewModel) {
    var pickingFor by remember { mutableStateOf<Int?>(null) }
    val players = state.players

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WoodButton("Inicio", vm::goHome, small = true, description = "Volver al inicio")
        Title("Jugadores (${players.size}/$MAX_PLAYERS)")
        Panel {
            players.forEachIndexed { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(avatarColor(p.avatar))
                            .border(3.dp, Rustic.Tinta, CircleShape)
                            .focusRing(CircleShape)
                            .clickable(role = Role.Button, onClickLabel = "Cambiar personaje") { pickingFor = p.id }
                            .semantics { contentDescription = "Personaje de ${p.name}: ${AvatarAssets.names[p.avatar].orEmpty()}" },
                        contentAlignment = Alignment.Center,
                    ) { Avatar(p.avatar, 52.dp, background = false) }
                    OutlinedTextField(
                        value = p.name,
                        onValueChange = { vm.rename(p.id, it) },
                        singleLine = true,
                        label = { Text("Jugador ${i + 1}") },
                        shape = RoundedCornerShape(14.dp),
                        colors = tebeoFieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                    if (i == 0) {
                        Etiqueta("Jefe", icon = R.drawable.ic_t_corona)
                    } else {
                        WoodButton("", { vm.removePlayer(p.id) }, tone = Tone.FANTASMA, small = true, enabled = players.size > MIN_PLAYERS, icon = R.drawable.ic_t_cerrar, description = "Quitar a ${p.name}")
                    }
                }
            }
            WoodButton("Añadir jugador", vm::addPlayer, Modifier.fillMaxWidth(), icon = R.drawable.ic_t_mas, tone = Tone.AZUL, small = true, enabled = players.size < MAX_PLAYERS)
        }

        Title("El jefe decide")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Número de rondas", Modifier.weight(1f))
                WoodButton("", { vm.setRounds(state.config.rounds - 1) }, small = true, icon = R.drawable.ic_t_menos, description = "Menos rondas")
                Text("${state.config.rounds}", Modifier.widthIn(min = 48.dp), color = Rustic.AmbarClaro, fontFamily = Rustic.Titulos, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                WoodButton("", { vm.setRounds(state.config.rounds + 1) }, small = true, icon = R.drawable.ic_t_mas, description = "Más rondas")
            }
            Text("Tipo de preguntas")
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(QuestionMode.FECHAS to "Fechas", QuestionMode.NUMEROS to "Números", QuestionMode.MEZCLA to "Mezcla").forEach { (mode, label) ->
                    WoodButton(
                        label, { vm.setMode(mode) }, Modifier.weight(1f), small = true,
                        tone = if (state.config.mode == mode) Tone.AMBAR else Tone.FANTASMA,
                        selected = state.config.mode == mode,
                    )
                }
            }
            BarbaridadesSwitch(state.config.barbaridades, vm::setBarbaridades)
        }
        WoodButton("¡Arre, burra! Empezar", vm::startGame, Modifier.fillMaxWidth(), tone = Tone.AMBAR, big = true)
    }

    pickingFor?.let { id ->
        val me = players.firstOrNull { it.id == id } ?: return
        val owners = players.filter { it.id != id }.associate { it.avatar to it.name }
        AvatarPickerDialog(
            me.avatar, emptySet(), onPick = { vm.setAvatar(id, it); pickingFor = null }, onDismiss = { pickingFor = null },
            owners = owners, name = me.name, onName = { if (it.isNotEmpty()) vm.rename(id, it) },
        )
    }
}
