package com.paraelcarro.ui.online

import com.paraelcarro.ui.components.statusIcon
import com.paraelcarro.ui.components.Ico
import com.paraelcarro.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paraelcarro.data.online.OnlineState
import com.paraelcarro.data.online.RoomPlayer
import com.paraelcarro.domain.format
import com.paraelcarro.domain.model.MAX_PLAYERS
import com.paraelcarro.domain.model.MIN_PLAYERS
import com.paraelcarro.domain.model.QuestionType
import com.paraelcarro.ui.Feedback
import com.paraelcarro.ui.components.AbuelaOverlay
import com.paraelcarro.ui.components.Avatar
import com.paraelcarro.ui.components.BarbaridadesPanel
import com.paraelcarro.ui.components.BarbaridadesSwitch
import com.paraelcarro.ui.components.Culpable
import com.paraelcarro.data.online.OnlineBarbaridad
import com.paraelcarro.domain.model.Question
import com.paraelcarro.ui.components.AvatarPickerDialog
import com.paraelcarro.ui.components.PlayerTag
import com.paraelcarro.ui.components.CifraField
import com.paraelcarro.ui.components.Panel
import com.paraelcarro.ui.components.RatingRow
import com.paraelcarro.ui.components.QuestionCard
import com.paraelcarro.ui.components.RoundHeader
import com.paraelcarro.ui.components.TimerBar
import com.paraelcarro.ui.components.Title
import com.paraelcarro.ui.components.Tone
import com.paraelcarro.ui.components.WoodButton
import com.paraelcarro.ui.screens.BridgeAnimState
import com.paraelcarro.ui.screens.BridgeScene
import com.paraelcarro.ui.screens.RigPlayer
import com.paraelcarro.ui.screens.TurnRig
import com.paraelcarro.ui.theme.Rustic
import kotlin.math.ceil

private val fieldColors @Composable get() = OutlinedTextFieldDefaults.colors(focusedBorderColor = Rustic.Ambar, unfocusedBorderColor = Rustic.MaderaClara)

// ------------------------------------------------------------------ Crear / unirse

@Composable
fun OnlineFormScreen(ui: OnlineUi, vm: OnlineViewModel) {
    val creating = ui.screen == OnlineScreen.CREATE
    var picking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WoodButton("← Inicio", vm::close, tone = Tone.FANTASMA, small = true)
        Title(if (creating) "Crear sala privada" else "Unirme a una sala")
        Text(
            if (creating) "Serás el jefe de sala: eliges las rondas y empiezas la partida. Luego invitas a tus amigos con el código o el enlace."
            else "Escribe el código que te han pasado (o abre su enlace), tu nombre y elige avatar.",
            color = Rustic.TextoSuave,
        )
        Panel {
            if (!creating) {
                OutlinedTextField(
                    value = ui.codeInput, onValueChange = vm::onCode, singleLine = true,
                    label = { Text("Código de la sala") }, placeholder = { Text("ABCDE") },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 24.sp, letterSpacing = 6.sp, color = Rustic.Texto),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AvatarCircle(ui.avatar) { picking = true }
                OutlinedTextField(
                    value = ui.name, onValueChange = vm::onName, singleLine = true, placeholder = { Text("Tu nombre") },
                    colors = fieldColors, modifier = Modifier.weight(1f),
                )
            }
            if (ui.error.isNotEmpty()) Text(ui.error, color = Rustic.Rojo, fontWeight = FontWeight.Bold)
            WoodButton(
                if (creating) "🌐 Crear sala" else "🔑 Entrar en la sala",
                if (creating) vm::create else vm::join,
                Modifier.fillMaxWidth(), tone = Tone.AMBAR, enabled = !ui.busy,
            )
        }
    }
    if (picking) AvatarPickerDialog(ui.avatar, emptySet(), onPick = { vm.onAvatar(it); picking = false }, onDismiss = { picking = false })
}

@Composable
private fun AvatarCircle(avatar: String, enabled: Boolean = true, onClick: () -> Unit = {}) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .border(2.dp, Rustic.Ambar, CircleShape)
            .background(Color.Black.copy(alpha = .35f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Avatar(avatar, 52.dp, background = false) }
}

// ------------------------------------------------------------------ Sala de espera

@Composable
fun OnlineLobbyScreen(ui: OnlineUi, vm: OnlineViewModel, onShare: () -> Unit, onCopy: () -> Unit, onExit: () -> Unit) {
    val meta = ui.meta ?: return
    val host = ui.isHost
    var picking by remember { mutableStateOf(false) }
    val online = ui.players.count { it.online }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WoodButton("← Salir", onExit, tone = Tone.FANTASMA, small = true)
        Panel {
            Text("Código de la sala", color = Rustic.TextoSuave, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(
                ui.code, color = Rustic.AmbarClaro, fontSize = 44.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold,
                letterSpacing = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WoodButton("📤 Invitar amigos", onShare, Modifier.weight(1f), tone = Tone.AMBAR)
                WoodButton("🔗 Copiar", onCopy, Modifier.weight(.6f), tone = Tone.FANTASMA)
            }
            Text(ui.inviteLink, color = Rustic.TextoSuave, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Title("Jugadores (${ui.players.size}/$MAX_PLAYERS)")
        Panel {
            ui.players.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AvatarCircle(p.avatar, enabled = p.id == ui.me) { picking = true }
                    Text(p.name + if (p.id == ui.me) " (tú)" else "", Modifier.weight(1f))
                    if (p.id == meta.host) Text("👑 Jefe", color = Rustic.Ambar, fontSize = 12.sp)
                    if (!p.online) Text("📴")
                    if (host && p.id != ui.me) WoodButton("✕", { vm.kick(p.id) }, tone = Tone.FANTASMA, small = true)
                }
            }
        }
        Title(if (host) "👑 Tú decides" else "👑 El jefe de sala decide")
        val modes = listOf("fecha" to "📅 Fechas", "numero" to "🔢 Números", "mezcla" to "🎲 Mezcla")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Número de rondas", Modifier.weight(1f))
                if (host) WoodButton("−", { vm.setRounds(meta.rounds - 1) }, small = true)
                Text("${meta.rounds}", Modifier.size(48.dp, 32.dp), color = Rustic.AmbarClaro, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (host) WoodButton("+", { vm.setRounds(meta.rounds + 1) }, small = true)
            }
            if (host) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    modes.forEach { (k, l) ->
                        WoodButton(l, { vm.setMode(k) }, Modifier.weight(1f), small = true, tone = if (meta.mode == k) Tone.AMBAR else Tone.FANTASMA)
                    }
                }
            } else {
                Row { Text("Preguntas", Modifier.weight(1f)); Text(modes.firstOrNull { it.first == meta.mode }?.second ?: "", color = Rustic.Ambar, fontWeight = FontWeight.Bold) }
            }
            BarbaridadesSwitch(meta.barbaridades, if (host) vm::setBarbaridades else null)
        }
        if (host) {
            WoodButton("¡Arre, burra! Empezar", vm::startGame, Modifier.fillMaxWidth(), tone = Tone.AMBAR, enabled = online >= MIN_PLAYERS)
            if (online < MIN_PLAYERS) Hint("Hacen falta al menos $MIN_PLAYERS jugadores conectados.")
        } else {
            Hint("⏳ Esperando a que el jefe empiece la partida…")
        }
    }
    if (picking) {
        val taken = ui.players.filter { it.id != ui.me }.map { it.avatar }.toSet()
        val mine = ui.players.firstOrNull { it.id == ui.me }?.avatar ?: ui.avatar
        AvatarPickerDialog(mine, taken, onPick = { vm.changeAvatar(it); picking = false }, onDismiss = { picking = false })
    }
}

@Composable
private fun Hint(text: String) = Text(text, color = Rustic.TextoSuave, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

// ------------------------------------------------------------------ Partida

private fun RoomPlayer?.tag() = this?.name ?: "¿?"

@Composable
private fun HostOfflineBanner(ui: OnlineUi) {
    val hostId = ui.meta?.host ?: return
    if (ui.isOnline(hostId)) return
    Text(
        "📴 El jefe de sala se ha desconectado. La partida sigue cuando vuelva.",
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Rustic.Sangre.copy(alpha = .25f))
            .padding(10.dp),
    )
}

@Composable
private fun Header(ui: OnlineUi, st: OnlineState) {
    RoundHeader(st.round, st.rounds, st.question)
    Text("Sala ${ui.code}", color = Rustic.TextoSuave, fontSize = 12.sp)
    HostOfflineBanner(ui)
}

@Composable
fun OnlineGameScreen(ui: OnlineUi, vm: OnlineViewModel, onExit: () -> Unit) {
    val st = ui.state ?: return
    when (st.phase) {
        "open" -> OpenView(ui, st, vm)
        "turn" -> TurnView(ui, st, vm)
        "score" -> ScoreView(ui, st, vm)
        "final" -> FinalView(ui, st, vm, onExit)
    }
}

@Composable
private fun OpenView(ui: OnlineUi, st: OnlineState, vm: OnlineViewModel) {
    val q = st.question
    val mine = st.opener == ui.me
    val value = vm.draftValue(ui)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Header(ui, st)
        QuestionCard(q)
        Text(
            if (mine) "Te toca abrir: di una cifra.\nTe montarás en la burra como conductor." else "⏳ ${ui.player(st.opener).tag()} está pensando una cifra…",
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        TimerBar(ui.secondsLeft, ui.timerFraction)
        if (mine) {
            Panel {
                CifraField(q, ui.draft, if (q.type == QuestionType.FECHA) "Año, p. ej. 1890" else "Tu cifra", vm::onDraftText, vm::onDraftBeforeChrist)
                if (q.type == QuestionType.NUMERO) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        WoodButton("×1.000", { value?.let { vm.setDraftValue(it * 1_000) } }, small = true)
                        WoodButton("×1 millón", { value?.let { vm.setDraftValue(it * 1_000_000) } }, small = true)
                    }
                }
                WoodButton(
                    if (ui.sent) "Enviado…" else "¡Me subo a la burra!", vm::submitOpening, Modifier.fillMaxWidth(), icon = R.drawable.ic_burra,
                    tone = Tone.AMBAR, enabled = value != null && !ui.sent,
                )
            }
        }
    }
}

@Composable
private fun TurnView(ui: OnlineUi, st: OnlineState, vm: OnlineViewModel) {
    val q = st.question
    val r = st.r ?: return
    val turnId = r.queue.firstOrNull()
    val mine = turnId == ui.me
    val bid = r.bid
    val value = vm.draftValue(ui)
    val steps: List<Pair<String, () -> Double>> = if (q.type == QuestionType.FECHA) {
        listOf(1, 5, 25, 100).map { n -> "+$n" to { bid + n } }
    } else {
        listOf(
            "+1" to { bid + 1 },
            "+10 %" to { maxOf(bid + 1, ceil(bid * 1.1)) },
            "×2" to { if (bid > 0) bid * 2 else bid + 1 },
            "×10" to { if (bid > 0) bid * 10 else bid + 1 },
        )
    }
    val where = when (r.status[ui.me]) { "conductor" -> "Vas montado en la burra."; "bajado" -> "Te has bajado del carro."; else -> "Vas en el carro." }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Header(ui, st)
        QuestionCard(q)
        Text("${ui.player(r.driver).tag()} va en la burra con", color = Rustic.TextoSuave, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(q.format(bid), color = Rustic.AmbarClaro, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        TimerBar(ui.secondsLeft, ui.timerFraction)
        TurnRig(
            players = st.players.map { RigPlayer(it.id, it.avatar) },
            status = ui.rigOverride ?: r.status,
            highlightId = ui.me,
            cheerId = ui.cheerId,
            cheerKey = ui.cheerId?.let { ui.cheerKey },
        )
        if (mine) {
            Text("¡Te toca! ¿Qué haces?", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (!ui.draft.raiseOpen) {
                WoodButton("Subir la cifra", { vm.openRaise(steps.first().second()) }, Modifier.fillMaxWidth(), tone = Tone.AMBAR, enabled = !ui.sent, icon = R.drawable.ic_subir)
            } else {
                Panel {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                        steps.forEach { (label, f) -> WoodButton(label, { vm.setDraftValue(f()) }, small = true) }
                    }
                    CifraField(q, ui.draft, "Nueva cifra (mayor)", vm::onDraftText, vm::onDraftBeforeChrist)
                    WoodButton("Me monto en la burra", vm::submitRaise, Modifier.fillMaxWidth(), tone = Tone.AMBAR, enabled = !ui.sent && value != null && value > bid, icon = R.drawable.ic_burra)
                }
            }
            WoodButton("Me quedo en el carro", vm::stay, Modifier.fillMaxWidth(), tone = Tone.MUSGO, enabled = !ui.sent, icon = R.drawable.ic_carreta)
            WoodButton("Me bajo del carro", vm::getOff, Modifier.fillMaxWidth(), tone = Tone.SANGRE, enabled = !ui.sent, icon = R.drawable.ic_bajar)
        } else {
            Text("⏳ Le toca a ${ui.player(turnId).tag()}\n$where", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        CartPanel(ui, st, turnId)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CartPanel(ui: OnlineUi, st: OnlineState, turnId: String?) {
    val r = st.r ?: return
    Panel {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            st.players.forEach { p ->
                val s = r.status[p.id]
                val icon = statusIcon(s)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .alpha(if (s == "bajado") .55f else 1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (s == "conductor") Rustic.Ambar.copy(alpha = .25f) else Rustic.Chip)
                        .then(if (p.id == turnId) Modifier.border(2.dp, Rustic.AmbarClaro, RoundedCornerShape(50)) else Modifier)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Ico(icon, 20.dp)
                    Avatar(p.avatar, 20.dp)
                    Text(p.name + if (!ui.isOnline(p.id)) " 📴" else "", fontSize = 14.sp, textDecoration = if (s == "bajado") TextDecoration.LineThrough else null)
                }
            }
        }
        r.log.takeLast(6).reversed().forEach { e ->
            val what = when (e.action) {
                "abre" -> "abre con ${st.question.format(e.value ?: 0.0)}"
                "subir" -> "sube a ${st.question.format(e.value ?: 0.0)}"
                "quedar" -> "se queda en el carro"
                "bajar" -> "se baja del carro"
                else -> "se queda (tiempo)"
            }
            PlayerTag(ui.player(e.id)?.avatar ?: "🙂", "${ui.player(e.id).tag()} $what", size = 18.dp, style = TextStyle(fontSize = 13.sp), color = Rustic.TextoSuave)
        }
    }
}

/** Puente: lo ven todos a la vez; el jefe pasa a los puntos al terminar. */
@Composable
fun OnlineBridgeScreen(ui: OnlineUi, st: OnlineState, feedback: Feedback) {
    val r = st.r ?: return
    val res = st.result ?: return
    val q = st.question
    val driver = ui.player(r.driver)
    var anim by remember(st.seq) { mutableStateOf(BridgeAnimState.Walking) }
    var skip by remember(st.seq) { mutableStateOf(false) }
    var abuela by remember(st.seq) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Header(ui, st)
        QuestionCard(q, small = true)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Rustic.MaderaClara.copy(alpha = .45f), RoundedCornerShape(14.dp)),
        ) {
            key(st.seq) {
                BridgeScene(
                    driver = driver?.avatar ?: "🙂",
                    passengers = res.passengers.map { ui.player(it)?.avatar ?: "🙂" },
                    success = res.success,
                    fastForward = skip,
                    onStateChange = { s ->
                        anim = s
                        when (s) {
                            BridgeAnimState.Suspense -> feedback.creak()
                            BridgeAnimState.CrossedSuccessfully -> feedback.crossed()
                            BridgeAnimState.FellIntoRiver -> feedback.fell()
                            BridgeAnimState.Walking -> Unit
                        }
                    },
                    onDone = {
                        if (res.barbaridades.isNotEmpty()) { abuela = true; feedback.abuela(res.barbaridades.any { it.high }) }
                    },
                )
            }
            Text(
                if (anim == BridgeAnimState.Walking) "${driver.tag()} apuesta por ${q.format(r.bid)}" else "Respuesta real: ${q.format(res.answer)}",
                color = if (anim == BridgeAnimState.Walking) Rustic.AmbarClaro else if (res.success) Rustic.Verde else Rustic.Rojo,
                fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .35f)).padding(6.dp),
            )
        }
        WoodButton("Saltar ⏩", { skip = true }, Modifier.fillMaxWidth(), tone = Tone.FANTASMA, small = true)
    }
    if (abuela) {
        AbuelaOverlay(
            culpables = res.barbaridades.map { b -> culpable(ui, b, q) },
            high = res.barbaridades.any { it.high },
            answer = q.format(res.answer),
            onDone = { abuela = false },
        )
    }
}

@Composable
private fun ScoreView(ui: OnlineUi, st: OnlineState, vm: OnlineViewModel) {
    val r = st.r ?: return
    val res = st.result ?: return
    val q = st.question
    val last = st.round >= st.rounds
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Header(ui, st)
        Text(
            if (res.success) "¡El carro cruzó el puente!" else "¡Al río con la burra!",
            color = if (res.success) Rustic.Verde else Rustic.Rojo, fontSize = 26.sp, fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        if (res.bullseye) Text("🎯 ¡Clavada! +2 para el conductor", color = Rustic.AmbarClaro, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Panel {
            Text(q.text, fontFamily = FontFamily.Serif)
            Text("Respuesta: ${q.format(res.answer)} · ${ui.player(r.driver).tag()} dijo ${q.format(r.bid)}", fontWeight = FontWeight.Bold, color = Rustic.AmbarClaro)
            if (res.note.isNotEmpty()) Text("💡 ${res.note}", color = Rustic.TextoSuave)
            RatingRow(q.id, vm.isRated(q.id)) { vm.rate(q.id, it) }
        }
        BarbaridadesPanel(res.barbaridades.map { b -> culpable(ui, b, q) }, res.barbaridades.map { it.high })
        Panel {
            Row {
                Text("Jugador", Modifier.weight(1f), color = Rustic.TextoSuave)
                Text("Ronda", Modifier.weight(.4f), color = Rustic.TextoSuave, textAlign = TextAlign.End)
                Text("Total", Modifier.weight(.4f), color = Rustic.TextoSuave, textAlign = TextAlign.End)
            }
            st.players.sortedByDescending { st.scores[it.id] ?: 0 }.forEach { p ->
                val d = res.delta[p.id] ?: 0
                val icon = statusIcon(r.status[p.id])
                HorizontalDivider(color = Rustic.MaderaClara.copy(alpha = .25f))
                Row {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { Ico(icon, 22.dp); Text(" "); PlayerTag(p.avatar, p.name + if (p.id == ui.me) " (tú)" else "") }
                    Text(if (d > 0) "+$d" else "$d", Modifier.weight(.4f), textAlign = TextAlign.End, fontWeight = FontWeight.ExtraBold,
                        color = when { d > 0 -> Rustic.Verde; d < 0 -> Rustic.Rojo; else -> Rustic.Texto })
                    Text("${st.scores[p.id] ?: 0}", Modifier.weight(.4f), textAlign = TextAlign.End, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        if (ui.isHost) WoodButton(if (last) "🏆 Ver el podio" else "Siguiente ronda ➜", vm::nextAfterScore, Modifier.fillMaxWidth(), tone = Tone.AMBAR)
        else Hint("⏳ Esperando a que el jefe pase a ${if (last) "el podio" else "la siguiente ronda"}…")
    }
}

@Composable
private fun FinalView(ui: OnlineUi, st: OnlineState, vm: OnlineViewModel, onExit: () -> Unit) {
    val ranking = st.players.sortedByDescending { st.scores[it.id] ?: 0 }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("🏆 ${ranking.first().name} gana", color = Rustic.Ambar, fontSize = 32.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            Podium(ranking.getOrNull(1), st, 70, "2º", Modifier.weight(1f))
            Podium(ranking.getOrNull(0), st, 100, "1º", Modifier.weight(1f))
            Podium(ranking.getOrNull(2), st, 50, "3º", Modifier.weight(1f))
        }
        Panel {
            ranking.forEachIndexed { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically) { Text("${i + 1}. "); PlayerTag(p.avatar, p.name + if (p.id == ui.me) " (tú)" else "", Modifier.weight(1f)); Text("${st.scores[p.id] ?: 0}", fontWeight = FontWeight.ExtraBold) }
            }
        }
        if (ui.isHost) {
            WoodButton("Revancha (mismos jugadores)", vm::startGame, Modifier.fillMaxWidth(), tone = Tone.AMBAR)
            WoodButton("Volver a la sala", vm::backToLobby, Modifier.fillMaxWidth(), tone = Tone.FANTASMA)
        } else {
            Hint("⏳ El jefe decide si hay revancha…")
        }
        WoodButton("Salir de la sala", onExit, Modifier.fillMaxWidth(), tone = Tone.FANTASMA)
    }
}

@Composable
private fun Podium(p: RoomPlayer?, st: OnlineState, height: Int, pos: String, modifier: Modifier) {
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
        ) { Text("$pos\n${st.scores[p.id] ?: 0}", color = Color.White, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)) }
    }
}

private fun culpable(ui: OnlineUi, b: OnlineBarbaridad, q: Question) =
    ui.player(b.id).let { Culpable(it?.avatar ?: "🙂", it?.name ?: "¿?", q.format(b.value)) }