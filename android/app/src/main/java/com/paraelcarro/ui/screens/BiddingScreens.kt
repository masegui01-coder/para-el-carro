package com.paraelcarro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paraelcarro.R
import com.paraelcarro.domain.GameEngine
import com.paraelcarro.domain.format
import com.paraelcarro.domain.model.GamePhase
import com.paraelcarro.domain.model.GameState
import com.paraelcarro.domain.model.LogKind
import com.paraelcarro.domain.model.PlayerStatus
import com.paraelcarro.domain.model.Question
import com.paraelcarro.domain.model.QuestionType
import com.paraelcarro.ui.GameViewModel
import com.paraelcarro.ui.components.AdaptivePanes
import com.paraelcarro.ui.components.Avatar
import com.paraelcarro.ui.components.CifraField
import com.paraelcarro.ui.components.Ico
import com.paraelcarro.ui.components.Panel
import com.paraelcarro.ui.components.PlayerTag
import com.paraelcarro.ui.components.QuestionCard
import com.paraelcarro.ui.components.RoundHeader
import com.paraelcarro.ui.components.Tone
import com.paraelcarro.ui.components.TurnTitle
import com.paraelcarro.ui.components.WoodButton
import com.paraelcarro.ui.components.statusIcon
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
            Text(p.name, color = Rustic.Texto, fontFamily = Rustic.Titulos, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(24.dp))
            WoodButton("¡Soy yo, empezar!", vm::startTurn, Modifier.fillMaxWidth(), tone = Tone.AMBAR, big = true)
        }
    }
}

@Composable
fun OpeningBidScreen(state: GameState, phase: GamePhase.OpeningBid, vm: GameViewModel) {
    val p = state.player(phase.playerId) ?: return
    val q = state.round?.question ?: return
    val value = vm.draftValue(state)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RoundHeader(state.roundNumber, state.config.rounds, q)
        TurnTitle("¡Abres tú, ${p.name}!", state.secondsLeft, state.timerFraction)
        QuestionCard(q)
        Text("Di una cifra: te montarás en la burra como conductor.", color = Rustic.TextoSuave, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
    val round = state.round ?: return
    val q = round.question
    val bid = round.bid ?: return
    val driver = state.player(round.driverId) ?: return
    val value = vm.draftValue(state)
    val steps = GameEngine.pasosSubida(q, bid)
    val minimo = GameEngine.minSubida(q, bid)
    val quedan = GameEngine.MAX_SUBIDAS - GameEngine.subidas(round, p.id)

    AdaptivePanes(left = {
        RoundHeader(state.roundNumber, state.config.rounds, q)
        TurnTitle("¡Te toca, ${p.name}!", state.secondsLeft, state.timerFraction)
        QuestionCard(q, small = true)
        BidCard(driver.avatar, driver.name, q.format(bid))
        TurnRig(
            players = state.players.map { RigPlayer(it.id.toString(), it.avatar) },
            status = round.status.entries.associate { (id, s) -> id.toString() to when (s) { PlayerStatus.CONDUCTOR -> "conductor"; PlayerStatus.BAJADO -> "bajado"; else -> "carro" } },
            highlightId = p.id.toString(),
            cheerId = state.cheerId?.toString(),
            cheerKey = state.cheerId?.let { round.log.size },
        )
    }, right = {
        val free = !state.animating
        if (quedan > 0 && state.draft.raiseOpen) {
            Panel {
                Text("Mínimo: ${q.format(minimo)} · desliza para subir más", color = Rustic.TextoSuave, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                CifraField(q, state.draft, "Nueva cifra (mayor)", vm::onDraftText, vm::onDraftBeforeChrist)
                BarraSubida(q, bid, vm::setDraftValue)
                PasosSubida(steps, vm::setDraftValue)
                WoodButton("¡Me monto en la burra!", vm::submitRaise, Modifier.fillMaxWidth(), tone = Tone.AMBAR, enabled = free && value != null && vm.puedeSubirCon(state, value), icon = R.drawable.ic_burra)
            }
        } else {
            SubirButton(quedan, free) { vm.openRaise(minimo) }
        }
        StayOrGetOff(free, vm::stay, vm::getOff)
        CartPanel(state)
    })
}

/** Tarjeta de la puja actual sobre el cielo: el conductor montado en la burra y su cifra (igual que en la web). */
@Composable
fun BidCard(avatar: String, name: String, value: String) {
    Panel(color = Rustic.Cielo) {
        Row(
            Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(76.dp, 64.dp)) {
                Box(Modifier.align(Alignment.BottomStart)) { Ico(R.drawable.ic_burra, 56.dp) }
                Avatar(avatar, 40.dp, Modifier.align(Alignment.TopEnd).padding(end = 6.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("$name va en la burra con", color = Rustic.Texto, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text(value, color = Rustic.Texto, fontFamily = Rustic.Titulos, fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/** "¡Súbela!" con un punto por cada subida que queda en la ronda. */
@Composable
fun SubirButton(quedan: Int, enabled: Boolean, onClick: () -> Unit) {
    if (quedan <= 0) {
        WoodButton("Ya has subido ${GameEngine.MAX_SUBIDAS} veces", {}, Modifier.fillMaxWidth(), tone = Tone.AMBAR, enabled = false, icon = R.drawable.ic_subir)
        return
    }
    WoodButton(
        "¡Súbela!", onClick, Modifier.fillMaxWidth(), tone = Tone.AMBAR, enabled = enabled, icon = R.drawable.ic_subir, big = true,
        description = "¡Súbela! Te quedan $quedan de ${GameEngine.MAX_SUBIDAS} subidas",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(start = 4.dp)) {
            repeat(GameEngine.MAX_SUBIDAS) { i ->
                Box(
                    Modifier.size(10.dp).clip(CircleShape)
                        .background(if (i < quedan) Color(0xFF1B1B1B) else Color.Transparent)
                        .border(2.dp, Color(0xFF1B1B1B), CircleShape),
                )
            }
        }
    }
}

/** "Me quedo" y "Me bajo", lado a lado. */
@Composable
fun StayOrGetOff(enabled: Boolean, onStay: () -> Unit, onGetOff: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WoodButton("Me quedo", onStay, Modifier.weight(1f), tone = Tone.MUSGO, enabled = enabled, icon = R.drawable.ic_carreta, big = true, description = "Me quedo en el carro")
        WoodButton("Me bajo", onGetOff, Modifier.weight(1f), tone = Tone.SANGRE, enabled = enabled, icon = R.drawable.ic_bajar, big = true, description = "Me bajo del carro")
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
        colors = SliderDefaults.colors(thumbColor = Rustic.Foco, activeTrackColor = Rustic.Foco, inactiveTrackColor = Rustic.TextoSuave.copy(alpha = .8f)),
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "Barra para subir la cifra"
            stateDescription = question.format(GameEngine.cifraBarra(question, bid, f.toDouble()))
        },
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
                        .clip(RoundedCornerShape(50))
                        .background(if (st == PlayerStatus.CONDUCTOR) Rustic.Amarillo else Rustic.Chip)
                        .border(2.dp, Rustic.Tinta, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Ico(icon, 20.dp)
                    Avatar(p.avatar, 20.dp)
                    Text(p.name, color = if (st == PlayerStatus.CONDUCTOR) Rustic.TintaFija else Rustic.Texto, fontSize = 14.sp, textDecoration = if (st == PlayerStatus.BAJADO) TextDecoration.LineThrough else null)
                }
            }
        }
        round.log.takeLast(6).reversed().forEach { e ->
            val who = state.player(e.playerId) ?: return@forEach
            val what = when (e.kind) {
                LogKind.ABRE -> "abre con ${round.question.format(e.value ?: 0.0)}"
                LogKind.SUBE -> "sube a ${round.question.format(e.value ?: 0.0)}"
                LogKind.QUEDA -> "se queda en el carro"
                LogKind.BAJA -> "se baja del carro"
                LogKind.TIEMPO -> "se queda (tiempo)"
            }
            PlayerTag(who.avatar, "${who.name} $what", size = 18.dp, style = TextStyle(fontSize = 13.sp), color = Rustic.TextoSuave)
        }
    }
}
