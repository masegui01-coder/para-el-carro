package com.paraelcarro.ui.screens

import com.paraelcarro.ui.components.statusIcon
import com.paraelcarro.ui.components.Ico
import com.paraelcarro.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.paraelcarro.domain.model.Question
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paraelcarro.domain.GameEngine
import com.paraelcarro.domain.format
import com.paraelcarro.domain.model.GamePhase
import com.paraelcarro.domain.model.GameState
import com.paraelcarro.domain.model.LogKind
import com.paraelcarro.domain.model.PlayerStatus
import com.paraelcarro.domain.model.QuestionType
import com.paraelcarro.ui.GameViewModel
import com.paraelcarro.ui.components.Avatar
import com.paraelcarro.ui.components.CifraField
import com.paraelcarro.ui.components.PlayerTag
import com.paraelcarro.ui.components.Panel
import com.paraelcarro.ui.components.QuestionCard
import com.paraelcarro.ui.components.RoundHeader
import com.paraelcarro.ui.components.TimerBar
import com.paraelcarro.ui.components.Tone
import com.paraelcarro.ui.components.WoodButton
import com.paraelcarro.ui.theme.Rustic

/** "Pasa el móvil a…": el temporizador no empieza hasta que el jugador lo coge. */
@Composable
fun HandoffScreen(state: GameState, phase: GamePhase.Handoff, vm: GameViewModel) {
    val p = state.player(phase.playerId) ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.round?.let { RoundHeader(state.roundNumber, state.config.rounds, it.question) }
        Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(p.avatar, 120.dp, highlight = true)
            Text(phase.message, color = Rustic.TextoSuave, textAlign = TextAlign.Center)
            Text(p.name, color = Rustic.Ambar, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            WoodButton("¡Soy yo, empezar!", vm::startTurn, Modifier.fillMaxWidth(), tone = Tone.AMBAR)
        }
    }
}

@Composable
fun OpeningBidScreen(state: GameState, phase: GamePhase.OpeningBid, vm: GameViewModel) {
    val p = state.player(phase.playerId) ?: return
    val q = state.round!!.question
    val value = vm.draftValue(state)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RoundHeader(state.roundNumber, state.config.rounds, q)
        QuestionCard(q)
        CenteredTag(p.avatar, "${p.name}, di una cifra para arrancar.")
        Text("Te montarás en la burra como conductor.", color = Rustic.TextoSuave, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        TimerBar(state.secondsLeft, state.timerFraction)
        Panel {
            CifraField(q, state.draft, if (q.type == QuestionType.FECHA) "Año, p. ej. 1890" else "Tu cifra", vm::onDraftText, vm::onDraftBeforeChrist)
            if (q.type == QuestionType.NUMERO) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    WoodButton("×1.000", { value?.let { vm.setDraftValue(it * 1_000) } }, small = true)
                    WoodButton("×1 millón", { value?.let { vm.setDraftValue(it * 1_000_000) } }, small = true)
                }
            }
            WoodButton("¡Me subo a la burra!", vm::submitOpening, Modifier.fillMaxWidth(), tone = Tone.AMBAR, enabled = value != null, icon = R.drawable.ic_burra)
        }
    }
}

@Composable
fun TurnScreen(state: GameState, phase: GamePhase.Turn, vm: GameViewModel) {
    val p = state.player(phase.playerId) ?: return
    val round = state.round!!
    val q = round.question
    val bid = round.bid!!
    val driver = state.player(round.driverId)!!
    val value = vm.draftValue(state)
    val steps = GameEngine.pasosSubida(q, bid)
    val minimo = GameEngine.minSubida(q, bid)
    val quedan = GameEngine.MAX_SUBIDAS - GameEngine.subidas(round, p.id)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RoundHeader(state.roundNumber, state.config.rounds, q)
        QuestionCard(q)
        CenteredTag(driver.avatar, "${driver.name} va en la burra con", Rustic.TextoSuave)
        Text(q.format(bid), color = Rustic.AmbarClaro, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        TimerBar(state.secondsLeft, state.timerFraction)
        CenteredTag(p.avatar, "${p.name}, ¿qué haces?")
        TurnRig(
            players = state.players.map { RigPlayer(it.id.toString(), it.avatar) },
            status = round.status.entries.associate { (id, s) -> id.toString() to when (s) { PlayerStatus.CONDUCTOR -> "conductor"; PlayerStatus.BAJADO -> "bajado"; else -> "carro" } },
            highlightId = p.id.toString(),
            cheerId = state.cheerId?.toString(),
            cheerKey = state.cheerId?.let { round.log.size },
        )
        val free = !state.animating
        if (quedan <= 0) {
            WoodButton("Ya has subido ${GameEngine.MAX_SUBIDAS} veces", {}, Modifier.fillMaxWidth(), tone = Tone.AMBAR, enabled = false, icon = R.drawable.ic_subir)
        } else if (!state.draft.raiseOpen) {
            WoodButton(textoSubir(quedan), { vm.openRaise(minimo) }, Modifier.fillMaxWidth(), tone = Tone.AMBAR, enabled = free, icon = R.drawable.ic_subir)
        } else {
            Panel {
                Text("Mínimo: ${q.format(minimo)} · desliza para subir más", color = Rustic.TextoSuave, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                BarraSubida(q, bid, vm::setDraftValue)
                PasosSubida(steps, vm::setDraftValue)
                CifraField(q, state.draft, "Nueva cifra (mayor)", vm::onDraftText, vm::onDraftBeforeChrist)
                WoodButton("Me monto en la burra", vm::submitRaise, Modifier.fillMaxWidth(), tone = Tone.AMBAR, enabled = free && value != null && vm.puedeSubirCon(state, value), icon = R.drawable.ic_burra)
            }
        }
        WoodButton("Me quedo en el carro", vm::stay, Modifier.fillMaxWidth(), tone = Tone.MUSGO, enabled = free, icon = R.drawable.ic_carreta)
        WoodButton("Me bajo del carro", vm::getOff, Modifier.fillMaxWidth(), tone = Tone.SANGRE, enabled = free, icon = R.drawable.ic_bajar)
        CartPanel(state)
    }
}

/** "Subir la cifra (te quedan 2)" / "(última vez)". */
fun textoSubir(quedan: Int): String = "Subir la cifra (" + (if (quedan == 1) "última vez" else "te quedan $quedan") + ")"

/** Barra para subir deprisa: del mínimo permitido a ×10 (o +200 años), sin tener que escribir. */
@Composable
fun BarraSubida(question: Question, bid: Double, onPick: (Double) -> Unit) {
    var f by remember(question.id, bid) { mutableFloatStateOf(0f) }
    Slider(
        value = f,
        onValueChange = { f = it; onPick(GameEngine.cifraBarra(question, bid, it.toDouble())) },
        colors = SliderDefaults.colors(thumbColor = Rustic.Ambar, activeTrackColor = Rustic.Ambar),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Botones rápidos para subir (en dos filas si no caben: "Mínimo (+10 %)" es largo). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PasosSubida(steps: List<Pair<String, Double>>, onPick: (Double) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        steps.forEach { (label, v) -> WoodButton(label, { onPick(v) }, small = true) }
    }
}

/** Quién va en la burra, quién en el carro y quién se bajó, más las últimas jugadas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CartPanel(state: GameState) {
    val round = state.round ?: return
    Panel {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.players.forEach { p ->
                val st = round.status[p.id]
                val icon = statusIcon(when (st) { PlayerStatus.CONDUCTOR -> "conductor"; PlayerStatus.BAJADO -> "bajado"; else -> "carro" })
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .alpha(if (st == PlayerStatus.BAJADO) .55f else 1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (st == PlayerStatus.CONDUCTOR) Rustic.Ambar.copy(alpha = .25f) else Rustic.Chip)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Ico(icon, 20.dp)
                    Avatar(p.avatar, 20.dp)
                    Text(p.name, fontSize = 14.sp, textDecoration = if (st == PlayerStatus.BAJADO) TextDecoration.LineThrough else null)
                }
            }
        }
        round.log.takeLast(6).reversed().forEach { e ->
            val who = state.player(e.playerId) ?: return@forEach
            val what = when (e.kind) {
                LogKind.ABRE -> "abre con ${round.question.format(e.value!!)}"
                LogKind.SUBE -> "sube a ${round.question.format(e.value!!)}"
                LogKind.QUEDA -> "se queda en el carro"
                LogKind.BAJA -> "se baja del carro"
                LogKind.TIEMPO -> "se queda (tiempo)"
            }
            PlayerTag(who.avatar, "${who.name} $what", size = 18.dp, style = TextStyle(fontSize = 13.sp), color = Rustic.TextoSuave)
        }
    }
}

/** Avatar + frase, centrados ("🐸 Lucía, ¿qué haces?"). */
@Composable
private fun CenteredTag(avatar: String, text: String, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Avatar(avatar, 26.dp)
        Text("  $text", color = color, textAlign = TextAlign.Center)
    }
}