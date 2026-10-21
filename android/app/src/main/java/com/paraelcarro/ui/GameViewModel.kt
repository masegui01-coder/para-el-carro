package com.paraelcarro.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paraelcarro.data.LobbyStore
import com.paraelcarro.data.QuestionRepository
import com.paraelcarro.data.online.QuestionRatings
import com.paraelcarro.domain.GameEngine
import com.paraelcarro.domain.model.AVATARS
import com.paraelcarro.domain.model.Draft
import com.paraelcarro.domain.model.GamePhase
import com.paraelcarro.domain.model.GameState
import com.paraelcarro.domain.model.MAX_PLAYERS
import com.paraelcarro.domain.model.MIN_PLAYERS
import com.paraelcarro.domain.model.OPEN_SECONDS
import com.paraelcarro.domain.model.Player
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

/** Efectos puntuales que la UI convierte en vibración / sonido. */
enum class GameEvent { TICK, BID, RAISE, STAY, GET_OFF }

/** Intersticial cada 2 rondas y antes del podio. */
private const val AD_EVERY_ROUNDS = 2

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val questions = QuestionRepository(app)
    private val lobby = LobbyStore(app)
    private val ratings = QuestionRatings(app)

    init {
        viewModelScope.launch { ratings.refresh() }
    }

    fun isRated(qid: String) = ratings.isRated(qid)
    fun rate(qid: String, kind: String) = ratings.rate(qid, kind)

    private val _state = MutableStateFlow(GameState(players = lobby.loadPlayers(), config = lobby.loadConfig()))
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<GameEvent> = _events

    private var timerJob: Job? = null

    // ------------------------------------------------------------ Navegación

    fun goHome() = setPhase(GamePhase.Home)
    fun showRules() = setPhase(GamePhase.Rules)
    fun openLobby() = setPhase(GamePhase.Lobby)

    private fun setPhase(phase: GamePhase) {
        stopTimer()
        _state.update { it.copy(phase = phase) }
    }

    // ------------------------------------------------------------ Sala (el jefe de sala es el jugador 1)

    private fun editLobby(block: (GameState) -> GameState) {
        _state.update(block)
        _state.value.let { lobby.save(it.players, it.config) }
    }

    fun rename(id: Int, name: String) = editLobby { s ->
        s.copy(players = s.players.map { if (it.id == id) it.copy(name = name.take(14)) else it })
    }

    fun setAvatar(id: Int, avatar: String) = editLobby { s ->
        if (s.players.any { it.id != id && it.avatar == avatar }) s
        else s.copy(players = s.players.map { if (it.id == id) it.copy(avatar = avatar) else it })
    }

    fun addPlayer() = editLobby { s ->
        if (s.players.size >= MAX_PLAYERS) return@editLobby s
        val id = (s.players.maxOfOrNull { it.id } ?: 0) + 1
        val avatar = AVATARS.first { a -> s.players.none { it.avatar == a } }
        s.copy(players = s.players + Player(id, "Jugador ${s.players.size + 1}", avatar))
    }

    fun removePlayer(id: Int) = editLobby { s ->
        if (s.players.size <= MIN_PLAYERS || s.players.first().id == id) s else s.copy(players = s.players.filter { it.id != id })
    }

    fun setRounds(rounds: Int) = editLobby { it.copy(config = it.config.copy(rounds = rounds.coerceIn(1, 30))) }
    fun setMode(mode: QuestionMode) = editLobby { it.copy(config = it.config.copy(mode = mode)) }
    fun setBarbaridades(on: Boolean) = editLobby { it.copy(config = it.config.copy(barbaridades = on)) }

    fun startGame() {
        editLobby { s ->
            s.copy(
                players = s.players.mapIndexed { i, p -> p.copy(name = p.name.trim().ifEmpty { "Jugador ${i + 1}" }, score = 0) },
                roundNumber = 0,
            )
        }
        nextRound()
    }

    fun rematch() {
        _state.update { s -> s.copy(players = s.players.map { it.copy(score = 0) }, roundNumber = 0) }
        nextRound()
    }

    // ------------------------------------------------------------ Ronda

    private fun nextRound() {
        val s = _state.value
        val number = s.roundNumber + 1
        val opener = (number - 1) % s.players.size
        val round = RoundState(question = questions.next(s.config.mode, ratings.excluded), openerIndex = opener)
        _state.update {
            it.copy(
                roundNumber = number, round = round, openAttempts = 0, draft = Draft(),
                phase = GamePhase.Handoff(s.players[opener].id, opening = true, message = "Abre la ronda $number de ${s.config.rounds}"),
            )
        }
        stopTimer()
    }

    /** El jugador ha cogido el móvil: empieza su cuenta atrás. */
    fun startTurn() {
        val phase = _state.value.phase as? GamePhase.Handoff ?: return
        _state.update {
            it.copy(
                draft = Draft(),
                phase = if (phase.opening) GamePhase.OpeningBid(phase.playerId) else GamePhase.Turn(phase.playerId),
            )
        }
        startTimer(if (phase.opening) OPEN_SECONDS else TURN_SECONDS, ::onTimeout)
    }

    fun onDraftText(text: String) = _state.update { it.copy(draft = it.draft.copy(text = text.take(24))) }
    fun onDraftBeforeChrist(bc: Boolean) = _state.update { it.copy(draft = it.draft.copy(beforeChrist = bc)) }
    fun openRaise(suggested: Double) {
        _state.update { it.copy(draft = it.draft.copy(raiseOpen = true)) }
        setDraftValue(suggested)
    }

    fun setDraftValue(value: Double) = _state.update {
        val type = it.round?.question?.type
        val text = when {
            type == QuestionType.FECHA -> Math.abs(Math.round(value)).toString()
            value % 1.0 == 0.0 -> BigDecimal(value).toPlainString()
            else -> value.toString().replace('.', ',')
        }
        it.copy(draft = it.draft.copy(text = text, beforeChrist = value < 0))
    }

    /** Valor que hay escrito ahora mismo, o null si no es una cifra válida. */
    fun draftValue(s: GameState = _state.value): Double? {
        val q = s.round?.question ?: return null
        return parseCifra(s.draft.text, q.type, s.draft.beforeChrist)
    }

    fun submitOpening() {
        val s = _state.value
        val phase = s.phase as? GamePhase.OpeningBid ?: return
        val value = draftValue(s) ?: return
        val round = GameEngine.open(s.players, s.round!!, phase.playerId, value)
        _events.tryEmit(GameEvent.BID)
        advance(round)
    }

    fun submitRaise() {
        val s = _state.value
        val value = draftValue(s) ?: return
        if (value <= (s.round?.bid ?: return)) return
        act(TurnAction.Raise(value))
    }

    fun stay() = act(TurnAction.Stay)
    fun getOff() = act(TurnAction.GetOff)

    private fun act(action: TurnAction, timedOut: Boolean = false) {
        val s = _state.value
        val phase = s.phase as? GamePhase.Turn ?: return
        if (s.animating) return
        stopTimer()
        val round = GameEngine.act(s.players, s.round!!, phase.playerId, action, timedOut)
        _events.tryEmit(
            when (action) {
                is TurnAction.Raise -> GameEvent.RAISE
                TurnAction.Stay -> GameEvent.STAY
                TurnAction.GetOff -> GameEvent.GET_OFF
            },
        )
        // El muñeco salta en la mini escena (se sube a la burra, se baja o bota en el carro) y luego sigue el juego
        _state.update { it.copy(round = round, animating = true, cheerId = if (action == TurnAction.Stay) phase.playerId else null) }
        viewModelScope.launch {
            delay(900)
            _state.update { it.copy(animating = false, cheerId = null) }
            advance(round)
        }
    }

    private fun advance(round: RoundState) {
        stopTimer()
        val s = _state.value
        if (GameEngine.isFinished(round)) {
            _state.update { it.copy(round = round, phase = GamePhase.Bridge(GameEngine.score(s.players, round, s.config.barbaridades))) }
        } else {
            _state.update { it.copy(round = round, draft = Draft(), phase = GamePhase.Handoff(round.queue.first(), opening = false, message = "Pasa el móvil a")) }
        }
    }

    private fun onTimeout() {
        val s = _state.value
        when (val phase = s.phase) {
            is GamePhase.OpeningBid -> {
                if (draftValue(s) != null) return submitOpening()
                // Nadie dijo cifra: abre el siguiente jugador.
                val attempts = s.openAttempts + 1
                val next = s.players[(s.round!!.openerIndex + attempts) % s.players.size]
                val who = s.player(phase.playerId)?.name.orEmpty()
                _state.update {
                    it.copy(openAttempts = attempts, phase = GamePhase.Handoff(next.id, opening = true, message = "¡Tiempo! $who no dijo nada. Abre"))
                }
            }
            is GamePhase.Turn -> {
                val v = draftValue(s)
                if (s.draft.raiseOpen && v != null && v > (s.round?.bid ?: Double.MAX_VALUE)) act(TurnAction.Raise(v))
                else act(TurnAction.Stay, timedOut = true) // si se acaba el tiempo, se queda en el carro
            }
            else -> Unit
        }
    }

    // ------------------------------------------------------------ Resolución y puntos

    fun onBridgeFinished() {
        val phase = _state.value.phase as? GamePhase.Bridge ?: return
        _state.update { s ->
            s.copy(
                players = s.players.map { it.copy(score = it.score + (phase.result.delta[it.id] ?: 0)) },
                phase = GamePhase.RoundScore(phase.result),
            )
        }
    }

    fun shouldShowAdBeforeNext(): Boolean = _state.value.let { it.isLastRound || it.roundNumber % AD_EVERY_ROUNDS == 0 }

    fun nextAfterScore() {
        if (_state.value.isLastRound) setPhase(GamePhase.Final) else nextRound()
    }

    // ------------------------------------------------------------ Temporizador

    private fun startTimer(seconds: Int, onEnd: () -> Unit) {
        stopTimer()
        val totalMs = seconds * 1000L
        val end = System.currentTimeMillis() + totalMs
        timerJob = viewModelScope.launch {
            var lastSec = seconds + 1
            while (true) {
                val left = (end - System.currentTimeMillis()).coerceAtLeast(0)
                val sec = ((left + 999) / 1000).toInt()
                _state.update { it.copy(secondsLeft = sec, timerFraction = left.toFloat() / totalMs) }
                if (sec != lastSec && sec in 1..5) _events.tryEmit(GameEvent.TICK)
                lastSec = sec
                if (left == 0L) break
                delay(100)
            }
            timerJob = null
            onEnd()
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }
}
