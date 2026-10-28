package com.paraelcarro.domain

import com.paraelcarro.domain.model.Barbaridad
import com.paraelcarro.domain.model.LogEntry
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
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
 * - Para subir hay que subir de verdad: en los números al menos un 10 % ([minSubida]); en las fechas, 1 año.
 *   Cada jugador puede subir como mucho [MAX_SUBIDAS] veces por ronda (abrir no cuenta).
 * - La ronda acaba cuando todos los del carro han pasado desde la última subida.
 * - El carro cruza si la respuesta real es IGUAL O MAYOR que la cifra del conductor.
 */
object GameEngine {

    /** Cada jugador puede subir la cifra como mucho estas veces por ronda (igual que MAX_SUBIDAS en js/game.js). */
    const val MAX_SUBIDAS = 3

    /** En los números hay que subir al menos este porcentaje (igual que SUBIDA_MIN en js/game.js). */
    const val SUBIDA_MIN = 0.10

    /**
     * La cifra mínima para subir. Fechas: un año más. Números: al menos un 10 % más, redondeado hacia
     * arriba a 3 cifras significativas (8.848 → 9.740) y, si la cifra es entera, al menos +1.
     */
    fun minSubida(question: Question, bid: Double): Double {
        if (question.type == QuestionType.FECHA) return bid + 1
        if (bid <= 0) return bid + maxOf(1.0, abs(bid) * SUBIDA_MIN)
        val x = bid * (1 + SUBIDA_MIN)
        val paso = 10.0.pow(floor(log10(x)) - 2)
        var min = ceil(x / paso - 1e-9) * paso
        if (paso < 1) min = java.math.BigDecimal(min).setScale(12, java.math.RoundingMode.HALF_UP).toDouble()
        if (bid % 1.0 == 0.0) min = maxOf(min, bid + 1)
        return min
    }

    /** ¿Vale esta cifra para subir? (con un margen mínimo por los redondeos entre la web y la app) */
    fun subidaValida(question: Question, bid: Double, value: Double): Boolean {
        if (!value.isFinite() || value <= bid) return false
        val min = minSubida(question, bid)
        return value >= min - abs(min) * 1e-9
    }

    /** Cuántas veces ha subido ya la cifra este jugador en la ronda. */
    fun subidas(round: RoundState, playerId: Int): Int = round.log.count { it.playerId == playerId && it.kind == LogKind.SUBE }

    fun puedeSubir(round: RoundState, playerId: Int): Boolean = subidas(round, playerId) < MAX_SUBIDAS

    /** La barra para subir llega hasta ×10 la subida mínima en los números… */
    const val BARRA_NUMEROS = 10.0

    /** …o hasta 200 años más en las fechas. */
    const val BARRA_FECHAS = 200.0

    /**
     * La cifra de la barra para subir: [f] = 0 es la subida mínima y 1 el tope. En los números avanza
     * en escala logarítmica (al principio subidas pequeñas, al final saltos grandes). Igual que cifraBarra en js/game.js.
     */
    fun cifraBarra(question: Question, bid: Double, f: Double): Double {
        val min = minSubida(question, bid)
        val t = f.coerceIn(0.0, 1.0)
        if (question.type == QuestionType.FECHA) return Math.round(min + t * BARRA_FECHAS).toDouble()
        if (min <= 0) return min
        return maxOf(min, redondea(min * BARRA_NUMEROS.pow(t)))
    }

    /** Botones rápidos para subir: el mínimo permitido y saltos grandes (los mismos que en la web). */
    fun pasosSubida(question: Question, bid: Double): List<Pair<String, Double>> {
        val min = minSubida(question, bid)
        if (question.type == QuestionType.FECHA) return listOf(1, 5, 25, 100).map { n -> "+$n" to maxOf(min, bid + n) }
        fun salto(f: Double) = if (bid > 0) maxOf(min, redondea(bid * f)) else min
        return listOf("Mínimo (+10 %)" to min, "+50 %" to salto(1.5), "×2" to salto(2.0), "×10" to salto(10.0))
    }

    /** 13.290 → 13.300 (3 cifras significativas), para que los botones den cifras limpias. */
    private fun redondea(v: Double): Double {
        if (v <= 0) return v
        val paso = 10.0.pow(floor(log10(v)) - 2)
        val r = Math.round(v / paso) * paso
        return if (paso < 1) java.math.BigDecimal(r).setScale(12, java.math.RoundingMode.HALF_UP).toDouble() else r
    }

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
                val bid = requireNotNull(round.bid) { "Nadie ha abierto todavía" }
                require(puedeSubir(round, playerId)) { "Ya ha subido $MAX_SUBIDAS veces" }
                require(subidaValida(round.question, bid, action.value)) { "Hay que subir al menos hasta ${minSubida(round.question, bid)}" }
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
     * el que se queda en el carro con él. Cuenta la ÚLTIMA cifra que dijo o apoyó cada jugador: si
     * empezó con una barbaridad pero luego la corrigió subiendo, ya no se le castiga.
     */
    fun barbaridades(players: List<Player>, round: RoundState): List<Barbaridad> {
        data class Apoyo(val value: Double, val from: Int?)
        val ultima = mutableMapOf<Int, Apoyo>()
        var bid: Double? = null
        var driver: Int? = null
        round.log.forEach { l ->
            when (l.kind) {
                LogKind.ABRE, LogKind.SUBE -> l.value?.let { v ->
                    bid = v; driver = l.playerId
                    ultima[l.playerId] = Apoyo(v, null)
                }
                LogKind.QUEDA, LogKind.TIEMPO -> bid?.let { v -> ultima[l.playerId] = Apoyo(v, driver) }
                LogKind.BAJA -> Unit
            }
        }
        return players.mapNotNull { p ->
            val c = ultima[p.id] ?: return@mapNotNull null
            val tipo = barbaridad(round.question, c.value) ?: return@mapNotNull null
            Barbaridad(p.id, c.value, tipo == Barbaridad.ALTO, c.from)
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
}
