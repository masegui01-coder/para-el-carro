package com.paraelcarro.ui.online

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paraelcarro.R
import com.paraelcarro.data.online.OnlineBarbaridad
import com.paraelcarro.data.online.OnlineState
import com.paraelcarro.data.online.RoomPlayer
import com.paraelcarro.data.online.vozAbuela
import com.paraelcarro.domain.GameEngine
import com.paraelcarro.domain.format
import com.paraelcarro.domain.model.MAX_PLAYERS
import com.paraelcarro.domain.model.MIN_PLAYERS
import com.paraelcarro.domain.model.Question
import com.paraelcarro.domain.model.QuestionType
import com.paraelcarro.ui.Feedback
import com.paraelcarro.ui.components.AbuelaOverlay
import com.paraelcarro.ui.components.AdaptivePanes
import com.paraelcarro.ui.components.Avatar
import com.paraelcarro.ui.components.AvatarAssets
import com.paraelcarro.ui.components.AvatarPickerDialog
import com.paraelcarro.ui.components.BarbaridadesPanel
import com.paraelcarro.ui.components.BarbaridadesSwitch
import com.paraelcarro.ui.components.Bullseye
import com.paraelcarro.ui.components.CifraField
import com.paraelcarro.ui.components.Etiqueta
import com.paraelcarro.ui.components.Ico
import com.paraelcarro.ui.components.LearnCard
import com.paraelcarro.ui.components.OutlinedText
import com.paraelcarro.ui.components.Panel
import com.paraelcarro.ui.components.PlayerTag
import com.paraelcarro.ui.components.QuestionCard
import com.paraelcarro.ui.components.RatingRow
import com.paraelcarro.ui.components.ResultTitle
import com.paraelcarro.ui.components.RoundHeader
import com.paraelcarro.ui.components.Title
import com.paraelcarro.ui.components.Tone
import com.paraelcarro.ui.components.TurnTitle
import com.paraelcarro.ui.components.WoodButton
import com.paraelcarro.ui.components.avatarColor
import com.paraelcarro.ui.components.comicShadow
import com.paraelcarro.ui.components.culpable as culpableDe
import com.paraelcarro.ui.components.focusRing
import com.paraelcarro.ui.components.reducedMotion
import com.paraelcarro.ui.components.statusIcon
import com.paraelcarro.ui.components.tebeoFieldColors
import com.paraelcarro.ui.screens.BarraSubida
import com.paraelcarro.ui.screens.BidCard
import com.paraelcarro.ui.screens.BridgeAnimState
import com.paraelcarro.ui.screens.BridgeScene
import com.paraelcarro.ui.screens.PasosSubida
import com.paraelcarro.ui.screens.RIG_SCENE_RATIO
import com.paraelcarro.ui.screens.RigPlayer
import com.paraelcarro.ui.screens.StayOrGetOff
import com.paraelcarro.ui.screens.SubirButton
import com.paraelcarro.ui.screens.TurnRig
import com.paraelcarro.ui.screens.sceneLabelColor
import com.paraelcarro.ui.theme.Rustic

private val fieldColors @Composable get() = tebeoFieldColors()

// ------------------------------------------------------------------ Crear / unirse

@Composable
fun OnlineFormScreen(ui: OnlineUi, vm: OnlineViewModel) {
    val creating = ui.screen == OnlineScreen.CREATE
    var picking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WoodButton("Inicio", vm::close, small = true, description = "Volver al inicio")
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
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Rustic.Titulos, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, letterSpacing = 6.sp, color = Rustic.Texto),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AvatarCircle(ui.avatar) { picking = true }
                OutlinedTextField(
                    value = ui.name, onValueChange = vm::onName, singleLine = true, label = { Text("Tu nombre") },
                    colors = fieldColors, modifier = Modifier.weight(1f),
                )
            }
            if (ui.error.isNotEmpty()) Text(ui.error, color = Rustic.Rojo, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
            WoodButton(
                if (creating) "Crear sala" else "¡Entrar en la sala!",
                if (creating) vm::create else vm::join,
                Modifier.fillMaxWidth(), tone = Tone.AMBAR, enabled = !ui.busy, big = true,
            )
        }
    }
    if (picking) {
        AvatarPickerDialog(
            ui.avatar, emptySet(), onPick = { vm.onAvatar(it); picking = false }, onDismiss = { picking = false },
            name = ui.name, onName = vm::onName,
        )
    }
}

@Composable
private fun AvatarCircle(avatar: String, enabled: Boolean = true, onClick: () -> Unit = {}) {
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(avatarColor(avatar))
            .border(3.dp, Rustic.Tinta, CircleShape)
            .focusRing(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Cambiar personaje", onClick = onClick)
            .semantics { contentDescription = AvatarAssets.names[avatar].orEmpty() },
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
        WoodButton("Salir", onExit, small = true, description = "Salir de la sala")
        Panel {
            Text("Código de la sala", color = Rustic.TextoSuave, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            OutlinedText(ui.code, Modifier.fillMaxWidth(), size = 42, letterSpacing = 6)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WoodButton("Invitar amigos", onShare, Modifier.weight(1f), tone = Tone.AMBAR)
                WoodButton("Copiar", onCopy, Modifier.weight(.6f), description = "Copiar enlace de invitación")
            }
            Text(ui.inviteLink, color = Rustic.TextoSuave, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Title("Jugadores (${ui.players.size}/$MAX_PLAYERS)")
        Panel {
            ui.players.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AvatarCircle(p.avatar, enabled = p.id == ui.me) { picking = true }
                    Text(p.name + if (p.id == ui.me) " (tú)" else "", Modifier.weight(1f))
                    if (p.id == meta.host) Etiqueta("Jefe", icon = R.drawable.ic_t_corona)
                    if (!p.online) Text("sin conexión", color = Rustic.Rojo, fontSize = 12.sp)
                    if (host && p.id != ui.me) WoodButton("", { vm.kick(p.id) }, tone = Tone.FANTASMA, small = true, icon = R.drawable.ic_t_cerrar, description = "Echar a ${p.name}")
                }
            }
        }
        Title(if (host) "Tú decides" else "El jefe decide")
        val modes = listOf("fecha" to "Fechas", "numero" to "Números", "mezcla" to "Mezcla")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Número de rondas", Modifier.weight(1f))
                if (host) WoodButton("", { vm.setRounds(meta.rounds - 1) }, small = true, icon = R.drawable.ic_t_menos, description = "Menos rondas")
                Text("${meta.rounds}", Modifier.widthIn(min = 48.dp), color = Rustic.AmbarClaro, fontFamily = Rustic.Titulos, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                if (host) WoodButton("", { vm.setRounds(meta.rounds + 1) }, small = true, icon = R.drawable.ic_t_mas, description = "Más rondas")
            }
            if (host) {
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    modes.forEach { (k, l) ->
                        WoodButton(l, { vm.setMode(k) }, Modifier.weight(1f), small = true, tone = if (meta.mode == k) Tone.AMBAR else Tone.FANTASMA, selected = meta.mode == k)
                    }
                }
            } else {
                Row { Text("Preguntas", Modifier.weight(1f)); Text(modes.firstOrNull { it.first == meta.mode }?.second ?: "", color = Rustic.AmbarClaro, fontWeight = FontWeight.Bold) }
            }
            BarbaridadesSwitch(meta.barbaridades, if (host) vm::setBarbaridades else null)
        }
        if (host) {
            WoodButton("¡Arre, burra! Empezar", vm::startGame, Modifier.fillMaxWidth(), tone = Tone.AMBAR, enabled = online >= MIN_PLAYERS, big = true)
            if (online < MIN_PLAYERS) Hint("Hacen falta al menos $MIN_PLAYERS jugadores conectados.")
        } else {
            Hint("Esperando a que el jefe empiece la partida…")
        }
    }
    if (picking) {
        val owners = ui.players.filter { it.id != ui.me }.associate { it.avatar to it.name }
        val mine = ui.players.firstOrNull { it.id == ui.me }?.avatar ?: ui.avatar
        AvatarPickerDialog(mine, emptySet(), onPick = { vm.changeAvatar(it); picking = false }, onDismiss = { picking = false }, owners = owners)
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
        "El jefe de sala se ha desconectado. La partida sigue cuando vuelva.",
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Rustic.Sangre.copy(alpha = .25f))
            .border(2.dp, Rustic.Tinta, RoundedCornerShape(10.dp))
            .padding(10.dp),
    )
}

@Composable
private fun Header(ui: OnlineUi, st: OnlineState) {
    RoundHeader(st.round, st.rounds, st.question)
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
        TurnTitle(if (mine) "¡Abres tú!" else "Abre ${ui.player(st.opener).tag()}", ui.secondsLeft, ui.timerFraction)
        QuestionCard(q)
        Text(
            if (mine) "Di una cifra: te montarás en la burra como conductor." else "${ui.player(st.opener).tag()} está pensando una cifra…",
            color = Rustic.TextoSuave, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
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
    val steps = GameEngine.pasosSubida(q, bid)
    val minimo = GameEngine.minSubida(q, bid)
    val quedan = vm.subidasQuedan(ui)
    val where = when (r.status[ui.me]) { "conductor" -> "Vas montado en la burra."; "bajado" -> "Te has bajado del carro."; else -> "Vas en el carro." }
    AdaptivePanes(left = {
        Header(ui, st)
        TurnTitle(if (mine) "¡Te toca!" else "Le toca a ${ui.player(turnId).tag()}", ui.secondsLeft, ui.timerFraction)
        QuestionCard(q, small = true)
        BidCard(ui.player(r.driver)?.avatar ?: "", ui.player(r.driver).tag(), q.format(bid))
        TurnRig(
            players = st.players.map { RigPlayer(it.id, it.avatar) },
            status = ui.rigOverride ?: r.status,
            highlightId = ui.me,
            cheerId = ui.cheerId,
            cheerKey = ui.cheerId?.let { ui.cheerKey },
        )
    }, right = {
        if (mine) {
            if (quedan > 0 && ui.draft.raiseOpen) {
                Panel {
                    Text("Mínimo: ${q.format(minimo)} · desliza para subir más", color = Rustic.TextoSuave, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    CifraField(q, ui.draft, "Nueva cifra (mayor)", vm::onDraftText, vm::onDraftBeforeChrist)
                    BarraSubida(q, bid, vm::setDraftValue)
                    PasosSubida(steps, vm::setDraftValue)
                    WoodButton("¡Me monto en la burra!", vm::submitRaise, Modifier.fillMaxWidth(), tone = Tone.AMBAR, enabled = !ui.sent && value != null && vm.puedeSubirCon(value, ui), icon = R.drawable.ic_burra)
                }
            } else {
                SubirButton(quedan, !ui.sent) { vm.openRaise(minimo) }
            }
            StayOrGetOff(!ui.sent, vm::stay, vm::getOff)
        } else {
            Text(where, color = Rustic.TextoSuave, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        CartPanel(ui, st, turnId)
    })
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
                        .clip(RoundedCornerShape(50))
                        .background(if (s == "conductor") Rustic.Amarillo else Rustic.Chip)
                        .border(if (p.id == turnId) 3.dp else 2.dp, if (p.id == turnId) Rustic.Foco else Rustic.Tinta, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Ico(icon, 20.dp)
                    Avatar(p.avatar, 20.dp)
                    Text(p.name + if (!ui.isOnline(p.id)) " (sin conexión)" else "", color = if (s == "conductor") Rustic.TintaFija else Rustic.Texto, fontSize = 14.sp, textDecoration = if (s == "bajado") TextDecoration.LineThrough else null)
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
            PlayerTag(ui.player(e.id)?.avatar.orEmpty(), "${ui.player(e.id).tag()} $what", size = 18.dp, style = TextStyle(fontSize = 13.sp), color = Rustic.TextoSuave)
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
    // Con "quitar animaciones" la escena va acelerada desde el principio (como en la partida local)
    val reduced = reducedMotion()
    var skip by remember(st.seq) { mutableStateOf(reduced) }
    var abuela by remember(st.seq) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Header(ui, st)
        QuestionCard(q, small = true)
        val alto = LocalConfiguration.current.screenHeightDp.dp
        Box(
            Modifier
                .widthIn(max = alto * 0.55f * RIG_SCENE_RATIO)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .padding(end = 4.dp, bottom = 4.dp)
                .comicShadow(18.dp, 4.dp, Rustic.Sombra)
                .clip(RoundedCornerShape(18.dp))
                .border(3.dp, Rustic.Tinta, RoundedCornerShape(18.dp)),
        ) {
            key(st.seq) {
                BridgeScene(
                    driver = driver?.avatar.orEmpty(),
                    passengers = res.passengers.map { ui.player(it)?.avatar.orEmpty() },
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
                        if (res.barbaridades.isNotEmpty()) { abuela = true; feedback.abuela(st.vozAbuela()) }
                    },
                )
            }
            Text(
                if (anim == BridgeAnimState.Walking) "${driver.tag()} apuesta por ${q.format(r.bid)}" else "Respuesta real: ${q.format(res.answer)}",
                color = sceneLabelColor(anim == BridgeAnimState.Walking, res.success),
                fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(Color(0xD91B1B1B)).padding(6.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        WoodButton("Saltar", { skip = true }, Modifier.fillMaxWidth(), small = true, description = "Saltar la animación")
    }
    if (abuela) {
        AbuelaOverlay(
            culpables = res.barbaridades.map { b -> culpable(ui, b, q) },
            voz = st.vozAbuela(),
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
        ResultTitle(res.success)
        if (res.bullseye) Bullseye()
        Panel {
            Text(q.text, fontFamily = Rustic.Titulos)
            Text("Respuesta: ${q.format(res.answer)} · ${ui.player(r.driver).tag()} dijo ${q.format(r.bid)}", fontWeight = FontWeight.Bold, color = Rustic.AmbarClaro)
            if (res.note.isNotEmpty()) Text(res.note, color = Rustic.TextoSuave)
            LearnCard(q.id)
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
                HorizontalDivider(thickness = 2.dp, color = Rustic.Tinta)
                Row {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { Ico(icon, 22.dp); Text(" "); PlayerTag(p.avatar, p.name + if (p.id == ui.me) " (tú)" else "") }
                    Text(if (d > 0) "+$d" else "$d", Modifier.weight(.4f), textAlign = TextAlign.End, fontWeight = FontWeight.ExtraBold,
                        color = when { d > 0 -> Rustic.Verde; d < 0 -> Rustic.Rojo; else -> Rustic.Texto })
                    Text("${st.scores[p.id] ?: 0}", Modifier.weight(.4f), textAlign = TextAlign.End, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        if (ui.isHost) WoodButton(if (last) "Ver el podio" else "Siguiente ronda", vm::nextAfterScore, Modifier.fillMaxWidth(), tone = Tone.AMBAR)
        else Hint("Esperando a que el jefe pase a ${if (last) "el podio" else "la siguiente ronda"}…")
    }
}

@Composable
private fun FinalView(ui: OnlineUi, st: OnlineState, vm: OnlineViewModel, onExit: () -> Unit) {
    val ranking = st.players.sortedByDescending { st.scores[it.id] ?: 0 }
    val winner = ranking.firstOrNull() ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedText("¡${winner.name} gana!", Modifier.fillMaxWidth(), size = 34)
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
            Hint("El jefe decide si hay revancha…")
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
                .heightIn(min = height.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(Rustic.Amarillo)
                .border(3.dp, Rustic.Tinta, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
            contentAlignment = Alignment.TopCenter,
        ) { Text("$pos\n${st.scores[p.id] ?: 0}", color = Rustic.TintaFija, fontFamily = Rustic.Titulos, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)) }
    }
}

private fun culpable(ui: OnlineUi, b: OnlineBarbaridad, q: Question) =
    culpableDe(ui.player(b.id)?.let { it.avatar to it.name }, q.format(b.value), b.from?.let { ui.player(it)?.name ?: "" })