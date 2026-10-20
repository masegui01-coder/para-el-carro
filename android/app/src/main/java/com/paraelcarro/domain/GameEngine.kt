package com.paraelcarro.domain

import com.paraelcarro.domain.model.LogEntry
import com.paraelcarro.domain.model.LogKind
import com.paraelcarro.domain.model.Player
import com.paraelcarro.domain.model.PlayerStatus
import com.paraelcarro.domain.model.Question
import com.paraelcarro.domain.model.QuestionType
import com.paraelcarro.domain.model.RoundResult
import com.paraelcarro.domain.model.RoundState
import com.paraelcarro.domain.model.TurnAction

/**
 * Reglas de una ronda, sin dependencias de Android (las mismas que js/game.js).
 *
 * - El que abre dice una cifra y se monta en la burra (conductor).
 * - Por turnos, los que siguen en el carro suben la cifra (pasan a conductor), se quedan o se bajan.
 *   Quien se baja no vuelve a jugar en la ronda.
 * - La ronda acaba cuando todos los del carro han pasado desde la última subida.
 * - El carro cruza si la respuesta real es IGUAL O MAYOR que la cifra del conductor.
 */
object GameEngine {

    private fun queueAfter(players: List<Player>, status: Map<Int, PlayerStatus>, fromId: Int): List<Int> {
        val i = players.indexOfFirst { it.id == fromId }
        return (1 until players.size)
            .map { players[(i + it) % players.size].id }
            .filter { status[it] == PlayerStatus.EN_CARRO }
    }

    fun open(players: List<Player>, round: RoundState, playerId: Int, value: Double): RoundState {
        val status = players.associate { it.id to if (it.id == playerId) PlayerStatus.CONDUCTOR else PlayerStatus.EN_CARRO }
        return round.copy(
            bid = value,
            driverId = playerId,
            status = status,
            queue = queueAfter(players, status, playerId),
            log = round.log + LogEntry(playerId, LogKind.ABRE, value),
        )
    }

    fun act(players: List<Player>, round: RoundState, playerId: Int, action: TurnAction, timedOut: Boolean = false): RoundState {
        require(round.queue.firstOrNull() == playerId) { "No es el turno de $playerId" }
        return when (action) {
            is TurnAction.Raise -> {
                require(action.value > (round.bid ?: Double.NEGATIVE_INFINITY)) { "La cifra debe ser mayor" }
                val status = round.status.toMutableMap().apply {
                    round.driverId?.let { put(it, PlayerStatus.EN_CARRO) }
                    put(playerId, PlayerStatus.CONDUCTOR)
                }
                round.copy(
                    bid = action.value,
                    driverId = playerId,
                    status = status,
                    queue = queueAfter(players, status, playerId),
                    log = round.log + LogEntry(playerId, LogKind.SUBE, action.value),
                )
            }
            TurnAction.Stay -> round.copy(
                queue = round.queue.drop(1),
                log = round.log + LogEntry(playerId, if (timedOut) LogKind.TIEMPO else LogKind.QUEDA),
            )
            TurnAction.GetOff -> round.copy(
                status = round.status + (playerId to PlayerStatus.BAJADO),
                queue = round.queue.drop(1),
                log = round.log + LogEntry(playerId, LogKind.BAJA),
            )
        }
    }

    fun isFinished(round: RoundState): Boolean = round.driverId != null && round.queue.isEmpty()

    /** ¡Clavada! La cifra está a menos de un 10 % (o a 5 años) por debajo de la respuesta. */
    fun isBullseye(question: Question, bid: Double): Boolean {
        if (bid > question.answer) return false
        return if (question.type == QuestionType.FECHA) question.answer - bid <= 5 else bid >= question.answer * 0.9
    }

    /**
     * Puntuación:
     * - Conductor: cruza +3 +1 por pasajero (+2 si es ¡Clavada!); cae −3 y paga 1 a cada uno que se bajó.
     * - En el carro: cruza +3; cae −2.
     * - Bajados: cruza 0; cae +2 (+1 que paga el conductor).
     */
    fun score(players: List<Player>, round: RoundState): RoundResult {
        val bid = requireNotNull(round.bid)
        val driver = requireNotNull(round.driverId)
        val success = round.question.answer >= bid
        val passengers = players.filter { round.status[it.id] == PlayerStatus.EN_CARRO }.map { it.id }
        val off = players.filter { round.status[it.id] == PlayerStatus.BAJADO }.map { it.id }
        val bullseye = success && isBullseye(round.question, bid)
        val delta = players.associate { it.id to 0 }.toMutableMap()
        if (success) {
            delta[driver] = 3 + passengers.size + if (bullseye) 2 else 0
            passengers.forEach { delta[it] = 3 }
        } else {
            delta[driver] = -3 - off.size
            passengers.forEach { delta[it] = -2 }
            off.forEach { delta[it] = 2 + 1 }
        }
        return RoundResult(round.question, bid, driver, success, bullseye, passengers, off, delta)
    }
}
