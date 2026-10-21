package com.paraelcarro.ui.online

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paraelcarro.data.QuestionRepository
import com.paraelcarro.data.online.CODE_CHARS
import com.paraelcarro.data.online.CODE_REGEX
import com.paraelcarro.data.online.OnlineResult
import com.paraelcarro.data.online.QuestionRatings
import com.paraelcarro.data.online.OnlineState
import com.paraelcarro.data.online.RoomMeta
import com.paraelcarro.data.online.RoomPlayer
import com.paraelcarro.data.online.RoomRepository
import com.paraelcarro.data.online.enginePlayers
import com.paraelcarro.data.online.parseMeta
import com.paraelcarro.data.online.parsePlayers
import com.paraelcarro.data.online.parseState
import com.paraelcarro.data.online.toEngine
import com.paraelcarro.data.online.toOnline
import com.paraelcarro.domain.GameEngine
import com.paraelcarro.domain.model.AVATARS
import com.paraelcarro.domain.model.Draft
import com.paraelcarro.domain.model.MAX_PLAYERS
import com.paraelcarro.domain.model.MIN_PLAYERS
import com.paraelcarro.domain.model.OPEN_SECONDS
import com.paraelcarro.domain.model.Question
import com.paraelcarro.domain.model.QuestionMode
import com.paraelcarro.domain.model.QuestionType
import com.paraelcarro.domain.model.RoundState
import com.paraelcarro.domain.model.TURN_SECONDS
import com.paraelcarro.domain.model.TurnAction
import com.paraelcarro.domain.parseCifra
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

enum class OnlineScreen { NONE, CREATE, JOIN, ROOM }

sealed interface OnlineEvent {
    data object Tick : OnlineEvent
    data object Bid : OnlineEvent
    data object Raise : OnlineEvent
    data object YourTurn : OnlineEvent
    data object ShowAd : OnlineEvent
    data class Closed(val message: String) : OnlineEvent
}

data class OnlineUi(
    val screen: OnlineScreen = OnlineScreen.NONE,
    val code: String = "",
    val me: String = "",
    val meta: RoomMeta? = null,
    val players: List<RoomPlayer> = emptyList(),
    val state: OnlineState? = null,
    // Formulario de entrar / crear
    val name: String = "",
    val avatar: String = AVATARS.first(),
    val codeInput: String = "",
    val error: String = "",
    val busy: Boolean = false,
    // Partida
    val draft: Draft = Draft(),
    val sent: Boolean = false,
    val secondsLeft: Int = 0,
    val timerFraction: Float = 1f,
    /** Mini escena: posiciones tras mi jugada mientras salta el muñeco (antes de enviarla). */
    val rigOverride: Map<String, String>? = null,
    /** Jugador que bota de alegría en el carro (se ha quedado) y clave para repetir la animación. */
    val cheerId: String? = null,
    val cheerKey: Long = 0,
) {
    val isHost: Boolean get() = meta?.host == me
    val inGame: Boolean get() = meta != null && meta.status != "lobby" && state != null
    fun player(id: String?): RoomPlayer? = (state?.players ?: players).firstOrNull { it.id == id }
    fun isOnline(id: String): Boolean = players.firstOrNull { it.id == id }?.online ?: false
    val inviteLink: String get() = "$WEB_URL?sala=$code"
}

const val WEB_URL = "https://masegui01-coder.github.io/para-el-carro/"
private const val HOST_GRACE_MS = 1500L   // margen para jugadas enviadas justo al final del plazo
private const val OFFLINE_SKIP_MS = 3000L // si le toca a un desconectado, se le pasa el turno enseguida
private const val BRIDGE_MS = 10500L      // lo que dura la animación del puente
private const val AD_EVERY_ROUNDS = 2

/**
 * Salas privadas online, compatibles con la web (js/online.js): mismo Firebase y mismo formato.
 * El móvil del jefe de sala es la autoridad: aplica jugadas y plazos con [GameEngine] y publica el estado.
 */
class OnlineViewModel(app: Application) : AndroidViewModel(app) {
    private val repo by lazy { RoomRepository(app) }
    private val questions = QuestionRepository(app)
    private val ratings = QuestionRatings(app)
    private val prefs = app.getSharedPreferences("online", Context.MODE_PRIVATE)

    init {
        viewModelScope.launch { ratings.refresh() }
    }

    fun isRated(qid: String) = ratings.isRated(qid)
    fun rate(qid: String, kind: String) = ratings.rate(qid, kind)

    private val _ui = MutableStateFlow(
        OnlineUi(
            name = prefs.getString("nombre", "") ?: "",
            avatar = prefs.getString("avatar", null) ?: AVATARS.take(12).random(),
        ),
    )
    val ui: StateFlow<OnlineUi> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<OnlineEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<OnlineEvent> = _events

    private var offset = 0L
    private fun now() = System.currentTimeMillis() + offset

    private var roomJobs = mutableListOf<Job>()
    private var ticker: Job? = null
    private var lastSeq = -1L
    private var adShownRound = 0

    // ------------------------------------------------------------ Formularios

    fun openCreate() = _ui.update { it.copy(screen = OnlineScreen.CREATE, error = "", busy = false) }

    /** Abrir "unirme", con el código ya puesto si viene de un enlace. Si ya estaba en esa sala, vuelve a entrar. */
    fun openJoin(code: String = "") {
        val c = code.uppercase()
        _ui.update { it.copy(screen = OnlineScreen.JOIN, codeInput = c, error = "", busy = false) }
        val savedCode = prefs.getString("sala", null)
        val savedPid = prefs.getString("pid", null)
        if (c.isNotEmpty() && c == savedCode && savedPid != null) {
            viewModelScope.launch {
                runCatching { repo.read(repo.room(c).child("players/$savedPid")).exists() }
                    .onSuccess { if (it) enterRoom(c, savedPid) }
            }
        }
    }

    fun close() = _ui.update { it.copy(screen = OnlineScreen.NONE) }
    fun onName(v: String) = _ui.update { it.copy(name = v.take(14)) }
    fun onAvatar(v: String) = _ui.update { it.copy(avatar = v) }
    fun onCode(v: String) = _ui.update { it.copy(codeInput = v.uppercase().filter { c -> c.isLetterOrDigit() }.take(5)) }

    private fun saveProfile() {
        prefs.edit().putString("nombre", _ui.value.name.trim()).putString("avatar", _ui.value.avatar).apply()
    }

    private fun fail(msg: String) = _ui.update { it.copy(error = msg, busy = false) }

    fun create() {
        val name = _ui.value.name.trim()
        if (name.isEmpty()) return fail("Escribe tu nombre.")
        saveProfile()
        _ui.update { it.copy(busy = true, error = "") }
        viewModelScope.launch {
            try {
                val pid = newId()
                val rounds = prefs.getInt("rondas", 6)
                val mode = prefs.getString("modo", "mezcla")!!
                var code: String? = null
                repeat(6) {
                    if (code == null) {
                        val c = (1..5).map { CODE_CHARS.random() }.joinToString("")
                        if (repo.createMeta(c, pid, rounds, mode)) code = c
                    }
                }
                val c = code ?: return@launch fail("No se pudo crear la sala.")
                repo.addPlayer(c, pid, name, _ui.value.avatar)
                enterRoom(c, pid)
            } catch (e: Exception) {
                fail("No hay conexión con el servidor. Inténtalo de nuevo.")
            }
        }
    }

    fun join() {
        val s = _ui.value
        val code = s.codeInput
        val name = s.name.trim()
        if (!CODE_REGEX.matches(code)) return fail("El código tiene 5 letras o números.")
        if (name.isEmpty()) return fail("Escribe tu nombre.")
        saveProfile()
        _ui.update { it.copy(busy = true, error = "") }
        viewModelScope.launch {
            try {
                val room = repo.read(repo.room(code))
                val meta = parseMeta(room.child("meta").value) ?: return@launch fail("No existe ninguna sala con ese código.")
                if (meta.status != "lobby") return@launch fail("Esa partida ya ha empezado. Espera a la siguiente.")
                val others = parsePlayers(room.child("players").value)
                if (others.size >= MAX_PLAYERS) return@launch fail("La sala está llena ($MAX_PLAYERS jugadores).")
                val avatar = s.avatar.takeIf { a -> others.none { it.avatar == a } } ?: AVATARS.first { a -> others.none { it.avatar == a } }
                val pid = newId()
                repo.addPlayer(code, pid, name, avatar)
                enterRoom(code, pid)
            } catch (e: Exception) {
                fail("No hay conexión con el servidor. Inténtalo de nuevo.")
            }
        }
    }

    private fun newId() = (1..10).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")

    // ------------------------------------------------------------ Dentro de la sala

    private fun enterRoom(code: String, pid: String) {
        leaveRoom()
        prefs.edit().putString("sala", code).putString("pid", pid).apply()
        lastSeq = -1
        _ui.update { it.copy(screen = OnlineScreen.ROOM, code = code, me = pid, busy = false, error = "", meta = null, state = null, players = emptyList()) }
        val room = repo.room(code)
        roomJobs += viewModelScope.launch { repo.serverOffset().collect { offset = it } }
        roomJobs += viewModelScope.launch { repo.connected().collect { if (it) repo.markOnline(code, pid) } }
        roomJobs += viewModelScope.launch {
            repo.values(room.child("meta")) { parseMeta(it.value) }.collect { meta ->
                if (meta == null) return@collect kicked("La sala se ha cerrado.")
                _ui.update { it.copy(meta = meta) }
                if (meta.host == pid) host.start(code) else host.stop()
            }
        }
        roomJobs += viewModelScope.launch {
            repo.values(room.child("players")) { parsePlayers(it.value) }.collect { players ->
                val s = _ui.value
                if (s.meta != null && players.none { it.id == pid }) return@collect kicked("Ya no estás en la sala.")
                _ui.update { it.copy(players = players) }
                host.onPresence()
            }
        }
        roomJobs += viewModelScope.launch {
            repo.values(room.child("state")) { parseState(it.value) }.collect { st -> onState(st) }
        }
    }

    private fun kicked(msg: String) {
        leaveRoom()
        _ui.update { it.copy(screen = OnlineScreen.NONE) }
        _events.tryEmit(OnlineEvent.Closed(msg))
    }

    private fun leaveRoom() {
        roomJobs.forEach { it.cancel() }
        roomJobs.clear()
        ticker?.cancel()
        host.stop()
        prefs.edit().remove("sala").remove("pid").apply()
        _ui.update { it.copy(code = "", me = "", meta = null, players = emptyList(), state = null) }
    }

    fun exitRoom() {
        val s = _ui.value
        val code = s.code
        val me = s.me
        val wasHost = s.isHost
        leaveRoom()
        _ui.update { it.copy(screen = OnlineScreen.NONE) }
        if (code.isEmpty()) return
        val room = repo.room(code)
        if (wasHost) room.removeValue() else room.child("players/$me").removeValue()
    }

    fun setRounds(n: Int) {
        val r = n.coerceIn(1, 30)
        prefs.edit().putInt("rondas", r).apply()
        repo.room(_ui.value.code).child("meta/rounds").setValue(r)
    }

    fun setMode(mode: String) {
        prefs.edit().putString("modo", mode).apply()
        repo.room(_ui.value.code).child("meta/mode").setValue(mode)
    }

    fun changeAvatar(a: String) = repo.room(_ui.value.code).child("players/${_ui.value.me}/avatar").setValue(a)
    fun kick(pid: String) = repo.room(_ui.value.code).child("players/$pid").removeValue()

    private fun onState(st: OnlineState?) {
        _ui.update { it.copy(state = st) }
        if (st == null) { lastSeq = -1; ticker?.cancel(); return } // de vuelta en la sala
        if (st.seq == lastSeq) return
        lastSeq = st.seq
        val me = _ui.value.me
        val last = st.r?.log?.lastOrNull()
        val cheer = last?.takeIf { st.phase == "turn" && it.id != me && (it.action == "quedar" || it.action == "tiempo") }?.id
        _ui.update { it.copy(draft = Draft(), sent = false, rigOverride = null, cheerId = cheer, cheerKey = st.seq) }
        ticker?.cancel()
        when (st.phase) {
            "open", "turn" -> {
                val mine = if (st.phase == "open") st.opener == me else st.r?.queue?.firstOrNull() == me
                if (mine) _events.tryEmit(OnlineEvent.YourTurn)
                startTicker(st, if (st.phase == "open") OPEN_SECONDS else TURN_SECONDS, mine)
            }
            "score" -> {
                val last = st.round >= st.rounds
                if ((last || st.round % AD_EVERY_ROUNDS == 0) && adShownRound != st.round) {
                    adShownRound = st.round
                    _events.tryEmit(OnlineEvent.ShowAd)
                }
            }
        }
    }

    private fun startTicker(st: OnlineState, seconds: Int, mine: Boolean) {
        val total = seconds * 1000L
        ticker = viewModelScope.launch {
            var lastSec = seconds + 1
            while (true) {
                val left = (st.deadline - now()).coerceIn(0, total)
                val sec = ((left + 999) / 1000).toInt()
                _ui.update { it.copy(secondsLeft = sec, timerFraction = left.toFloat() / total) }
                if (sec != lastSec && sec in 1..5) _events.tryEmit(OnlineEvent.Tick)
                lastSec = sec
                if (left == 0L) break
                delay(100)
            }
            // Se acaba el tiempo con una cifra escrita: se envía sola (si no, el jefe aplica el plazo)
            val s = _ui.value
            if (!mine || s.sent) return@launch
            val v = draftValue() ?: return@launch
            if (st.phase == "open") send("abre", v)
            else if (s.draft.raiseOpen && v > (st.r?.bid ?: Double.MAX_VALUE)) send("subir", v)
        }
    }

    // ------------------------------------------------------------ Jugadas del jugador

    fun onDraftText(t: String) = _ui.update { it.copy(draft = it.draft.copy(text = t)) }
    fun onDraftBeforeChrist(b: Boolean) = _ui.update { it.copy(draft = it.draft.copy(beforeChrist = b)) }
    fun openRaise(v: Double) {
        _ui.update { it.copy(draft = it.draft.copy(raiseOpen = true)) }
        setDraftValue(v)
    }

    fun setDraftValue(value: Double) = _ui.update {
        val type = it.state?.question?.type
        val text = when {
            type == QuestionType.FECHA -> Math.abs(Math.round(value)).toString()
            value % 1.0 == 0.0 -> BigDecimal(value).toPlainString()
            else -> value.toString().replace('.', ',')
        }
        it.copy(draft = it.draft.copy(text = text, beforeChrist = value < 0))
    }

    fun draftValue(s: OnlineUi = _ui.value): Double? {
        val q = s.state?.question ?: return null
        return parseCifra(s.draft.text, q.type, s.draft.beforeChrist)
    }

    fun submitOpening() { draftValue()?.let { _events.tryEmit(OnlineEvent.Bid); send("abre", it) } }
    fun submitRaise() {
        val v = draftValue() ?: return
        if (v > (_ui.value.state?.r?.bid ?: return)) { _events.tryEmit(OnlineEvent.Raise); playThenSend("subir", v) }
    }
    fun stay() = playThenSend("quedar", null)
    fun getOff() = playThenSend("bajar", null)

    /** Primero el muñeco se sube a la burra, se baja o bota en el carro; después se envía la jugada. */
    private fun playThenSend(a: String, v: Double?) {
        val s = _ui.value
        val st = s.state ?: return
        val r = st.r ?: return
        if (s.sent) return
        if (a == "quedar") {
            _ui.update { it.copy(sent = true, cheerId = s.me, cheerKey = System.nanoTime()) }
        } else {
            val after = r.status.toMutableMap()
            if (a == "subir") { after[r.driver] = "carro"; after[s.me] = "conductor" } else after[s.me] = "bajado"
            _ui.update { it.copy(sent = true, rigOverride = after) }
        }
        viewModelScope.launch {
            delay(900)
            repo.sendAction(s.code, s.me, a, v, st.seq)
        }
    }

    private fun send(a: String, v: Double?) {
        val s = _ui.value
        if (s.sent || s.state == null) return
        _ui.update { it.copy(sent = true) }
        repo.sendAction(s.code, s.me, a, v, s.state.seq)
    }

    // ------------------------------------------------------------ Jefe de sala

    fun startGame() = host.startGame()
    fun nextAfterScore() = host.next()
    fun backToLobby() = host.backToLobby()

    private val host = Host()

    /** Partida completa en el móvil del jefe (con la respuesta de la pregunta). */
    private class HostGame(
        val players: List<RoomPlayer>,
        val rounds: Int,
        val mode: String,
        val scores: MutableMap<String, Int>,
        var seq: Long,
    ) {
        val pids = players.map { it.id }
        val engine = enginePlayers(players)
        var round = 0
        var question: Question? = null
        var rs: RoundState? = null
        var openerIdx = 0
        var openAttempts = 0
        var phase = "open"
        var opener: String? = null
        var deadline = 0L
        var result: OnlineResult? = null
    }

    private inner class Host {
        private var code: String? = null
        private var g: HostGame? = null
        private var actionsJob: Job? = null
        private var timer: Job? = null
        private var bridgeTimer: Job? = null

        fun start(roomCode: String) {
            if (code == roomCode) return
            stop()
            code = roomCode
            actionsJob = viewModelScope.launch {
                repo.actions(roomCode).collect { snap ->
                    apply(snap.child("pid").value as? String, snap.child("a").value as? String, (snap.child("v").value as? Number)?.toDouble())
                    snap.ref.removeValue()
                }
            }
            // Si la app del jefe se reinició a mitad de partida, recupera el estado publicado
            if (_ui.value.meta?.status == "playing") viewModelScope.launch { resume(roomCode) }
        }

        fun stop() {
            actionsJob?.cancel(); timer?.cancel(); bridgeTimer?.cancel()
            actionsJob = null
            code = null
            g = null
        }

        private suspend fun resume(roomCode: String) {
            val st = runCatching { parseState(repo.read(repo.room(roomCode).child("state")).value) }.getOrNull() ?: return
            val full = questions.byId(st.question.id) ?: st.question.copy(answer = st.result?.answer ?: 0.0)
            val game = HostGame(st.players, st.rounds, _ui.value.meta?.mode ?: "mezcla", st.scores.toMutableMap(), st.seq)
            game.round = st.round
            game.question = full
            game.openerIdx = game.pids.indexOf(st.opener).coerceAtLeast(0)
            game.rs = st.r?.toEngine(game.pids, full, game.openerIdx) ?: RoundState(full, game.openerIdx)
            game.phase = st.phase
            game.opener = st.opener
            game.deadline = st.deadline
            game.result = st.result
            g = game
            if (st.phase == "bridge") afterBridge() else schedule()
        }

        private fun publish() {
            val game = g ?: return
            val q = game.question ?: return
            val reveal = game.phase == "bridge" || game.phase == "score" || game.phase == "final"
            game.seq++
            repo.publish(
                code ?: return,
                OnlineState(
                    phase = game.phase, seq = game.seq, round = game.round, rounds = game.rounds,
                    players = game.players, question = q, r = game.rs?.toOnline(game.pids),
                    opener = game.opener, deadline = game.deadline, scores = game.scores,
                    result = if (reveal) game.result else null,
                ),
            )
        }

        fun startGame() {
            val s = _ui.value
            val players = s.players.filter { it.online }
            if (players.size < MIN_PLAYERS) return
            // Los que no estén conectados al empezar se quedan fuera de la partida
            s.players.filter { !it.online }.forEach { kick(it.id) }
            // seq empieza en la hora actual: así nunca coincide con el de una partida anterior de la sala
            g = HostGame(players, s.meta?.rounds ?: 6, s.meta?.mode ?: "mezcla", players.associate { it.id to 0 }.toMutableMap(), System.currentTimeMillis())
            adShownRound = 0
            repo.room(code ?: return).child("meta/status").setValue("playing")
            nextRound()
        }

        private fun nextRound() {
            val game = g ?: return
            game.round++
            game.openerIdx = (game.round - 1) % game.players.size
            game.openAttempts = 0
            val mode = when (game.mode) { "fecha" -> QuestionMode.FECHAS; "numero" -> QuestionMode.NUMEROS; else -> QuestionMode.MEZCLA }
            val q = questions.next(mode, ratings.excluded)
            game.question = q
            game.rs = RoundState(q, game.openerIdx)
            game.result = null
            setOpen()
        }

        private fun setOpen() {
            val game = g ?: return
            game.phase = "open"
            game.opener = game.pids[(game.openerIdx + game.openAttempts) % game.pids.size]
            game.deadline = now() + OPEN_SECONDS * 1000L
            publish()
            schedule()
        }

        private fun setTurn() {
            val game = g ?: return
            game.phase = "turn"
            game.deadline = now() + TURN_SECONDS * 1000L
            publish()
            schedule()
        }

        fun onPresence() { if (g != null) schedule() }

        /** Programa el fin del plazo (o lo adelanta si al que le toca está desconectado). */
        private fun schedule() {
            timer?.cancel()
            val game = g ?: return
            if (game.phase != "open" && game.phase != "turn") return
            val who = if (game.phase == "open") game.opener else game.rs?.queue?.firstOrNull()?.let { game.pids[it] }
            val offline = who == null || !_ui.value.isOnline(who)
            val at = if (offline) minOf(game.deadline, now() + OFFLINE_SKIP_MS) else game.deadline + HOST_GRACE_MS
            val seq = game.seq
            timer = viewModelScope.launch {
                delay((at - now()).coerceAtLeast(0))
                if (g === game && game.seq == seq) timeout()
            }
        }

        private fun timeout() {
            val game = g ?: return
            when (game.phase) {
                "open" -> { game.openAttempts++; setOpen() } // nadie dijo cifra: abre el siguiente
                "turn" -> {
                    val rs = game.rs ?: return
                    game.rs = GameEngine.act(game.engine, rs, rs.queue.first(), TurnAction.Stay, timedOut = true)
                    after()
                }
            }
        }

        fun apply(pid: String?, a: String?, v: Double?) {
            val game = g ?: return
            val rs = game.rs ?: return
            val idx = game.pids.indexOf(pid)
            if (idx < 0) return
            runCatching {
                when {
                    game.phase == "open" && a == "abre" && pid == game.opener && v != null && v.isFinite() -> {
                        game.rs = GameEngine.open(game.engine, rs, idx, v)
                        after()
                    }
                    game.phase == "turn" && rs.queue.firstOrNull() == idx -> {
                        val action = when (a) {
                            "subir" -> if (v != null && v > (rs.bid ?: Double.MAX_VALUE)) TurnAction.Raise(v) else return
                            "quedar" -> TurnAction.Stay
                            "bajar" -> TurnAction.GetOff
                            else -> return
                        }
                        game.rs = GameEngine.act(game.engine, rs, idx, action)
                        after()
                    }
                }
            }
        }

        private fun after() {
            val game = g ?: return
            val rs = game.rs ?: return
            timer?.cancel()
            if (!GameEngine.isFinished(rs)) return setTurn()
            val res = GameEngine.score(game.engine, rs)
            res.delta.forEach { (i, d) -> game.scores[game.pids[i]] = (game.scores[game.pids[i]] ?: 0) + d }
            game.result = res.toOnline(game.pids, game.question!!)
            game.phase = "bridge"
            game.deadline = 0
            publish()
            afterBridge()
        }

        private fun afterBridge() {
            bridgeTimer?.cancel()
            val game = g ?: return
            bridgeTimer = viewModelScope.launch {
                delay(BRIDGE_MS)
                if (g === game && game.phase == "bridge") { game.phase = "score"; publish() }
            }
        }

        fun next() {
            val game = g ?: return
            if (game.phase != "score") return
            if (game.round >= game.rounds) {
                game.phase = "final"
                repo.room(code ?: return).child("meta/status").setValue("final")
                publish()
            } else {
                nextRound()
            }
        }

        fun backToLobby() {
            timer?.cancel(); bridgeTimer?.cancel()
            g = null
            val room = repo.room(code ?: return)
            room.child("state").removeValue()
            room.child("meta/status").setValue("lobby")
        }
    }

    override fun onCleared() {
        roomJobs.forEach { it.cancel() }
        host.stop()
    }
}
