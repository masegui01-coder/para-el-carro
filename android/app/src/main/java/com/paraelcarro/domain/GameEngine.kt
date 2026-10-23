package com.paraelcarro.domain

import com.paraelcarro.domain.model.Barbaridad
import com.paraelcarro.domain.model.LogEntry
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.ln
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
     * - Con [barbaridades]: −1 a cada jugador que haya dicho algún disparate o se haya quedado en el
     *   carro con uno (lo da por bueno). Una vez por jugador.
     */
    fun score(players: List<Player>, round: RoundState, barbaridades: Boolean = false): RoundResult {
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
        val barb = if (barbaridades) barbaridades(players, round) else emptyList()
        barb.forEach { delta[it.playerId] = (delta[it.playerId] ?: 0) - 1 }
        return RoundResult(round.question, bid, driver, success, bullseye, passengers, off, delta, barb)
    }

    /**
     * Quién se lleva el −1 (igual que Logic.barbaridades en js/game.js): el que dice el disparate y
     * el que se queda en el carro con él. Una vez por jugador: antes lo que dijo él, y la más disparatada.
     */
    fun barbaridades(players: List<Player>, round: RoundState): List<Barbaridad> {
        data class Apoyo(val value: Double, val from: Int?)
        val apoyos = mutableMapOf<Int, List<Apoyo>>()
        var bid: Double? = null
        var driver: Int? = null
        round.log.forEach { l ->
            when (l.kind) {
                LogKind.ABRE, LogKind.SUBE -> l.value?.let { v ->
                    bid = v; driver = l.playerId
                    apoyos[l.playerId] = apoyos[l.playerId].orEmpty() + Apoyo(v, null)
                }
                LogKind.QUEDA, LogKind.TIEMPO -> bid?.let { v -> apoyos[l.playerId] = apoyos[l.playerId].orEmpty() + Apoyo(v, driver) }
                LogKind.BAJA -> Unit
            }
        }
        return players.mapNotNull { p ->
            val peor = apoyos[p.id].orEmpty()
                .filter { barbaridad(round.question, it.value) != null }
                .sortedWith(compareBy<Apoyo> { if (it.from == null) 0 else 1 }.thenByDescending { distancia(round.question, it.value) })
                .firstOrNull() ?: return@mapNotNull null
            Barbaridad(p.id, peor.value, barbaridad(round.question, peor.value) == Barbaridad.ALTO, peor.from)
        }
    }

    /**
     * ¿Es una barbaridad? (igual que Logic.barbaridad en js/game.js)
     * - Números: 10 veces o más por encima o por debajo, con al menos 10 de diferencia.
     * - Fechas: más de 100 años de error, o más de 1/4 del tiempo transcurrido si es muy antigua.
     * Devuelve [Barbaridad.ALTO] (se ha pasado), [Barbaridad.BAJO] (se ha quedado muy corto) o null.
     */
    fun barbaridad(question: Question, value: Double, currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)): String? {
        val a = question.answer
        if (!value.isFinite()) return null
        if (question.type == QuestionType.FECHA) {
            val margin = maxOf(100.0, 0.25 * (currentYear - a))
            if (abs(value - a) <= margin) return null
            return if (value > a) Barbaridad.ALTO else Barbaridad.BAJO
        }
        if (abs(value - a) < 10) return null
        if (value >= a * 10) return Barbaridad.ALTO
        if (value * 10 <= a) return Barbaridad.BAJO
        return null
    }

    /** Lo lejos que está una cifra de la respuesta (para quedarse con la peor de cada jugador). */
    private fun distancia(question: Question, value: Double): Double =
        if (question.type == QuestionType.FECHA) abs(value - question.answer)
        else abs(ln(maxOf(value, 1e-9) / maxOf(question.answer, 1e-9)))
}
