package com.paraelcarro.data.online

import com.paraelcarro.domain.model.LogEntry
import com.paraelcarro.domain.model.LogKind
import com.paraelcarro.domain.model.Player
import com.paraelcarro.domain.model.PlayerStatus
import com.paraelcarro.domain.model.Question
import com.paraelcarro.domain.model.QuestionType
import com.paraelcarro.domain.model.RoundResult
import com.paraelcarro.domain.model.RoundState

/*
 * Salas online en Firebase Realtime Database (rooms/CODIGO), con el MISMO formato que js/online.js
 * para que en una sala puedan jugar a la vez móviles con la app y navegadores:
 *   meta     { host, created, status: lobby|playing|final, rounds, mode: fecha|numero|mezcla }
 *   players  { pid: { name, avatar, order, online } }
 *   state    { phase: open|turn|bridge|score|final, seq, round, rounds, players:[{id,name,avatar}],
 *              q:{id,tipo,q,u}, R:{bid,driver,status:{pid:conductor|carro|bajado},queue:[pid],log:[{id,action,value}]},
 *              opener, deadline, scores:{pid:n}, result:{success,bullseye,delta,passengers,off,a,n} }
 *   actions  { pushId: { pid, a: abre|subir|quedar|bajar, v, seq } }
 */

const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
val CODE_REGEX = Regex("^[A-HJ-NP-Z2-9]{5}$")

data class RoomMeta(val host: String, val status: String, val rounds: Int, val mode: String, val barbaridades: Boolean = false)

data class RoomPlayer(val id: String, val name: String, val avatar: String, val order: Long = 0, val online: Boolean = true)

data class OnlineLog(val id: String, val action: String, val value: Double?)

data class OnlineRound(
    val bid: Double,
    val driver: String,
    val status: Map<String, String>,
    val queue: List<String>,
    val log: List<OnlineLog>,
)

data class OnlineResult(
    val success: Boolean,
    val bullseye: Boolean,
    val delta: Map<String, Int>,
    val passengers: List<String>,
    val off: List<String>,
    val answer: Double,
    val note: String,
    val barbaridades: List<OnlineBarbaridad> = emptyList(),
)

/** La grabación de la abuela para esta ronda (la misma en todos los móviles). */
fun OnlineResult.vozAbuela(): com.paraelcarro.domain.model.VozAbuela =
    com.paraelcarro.domain.model.VozAbuela.para(barbaridades.map { it.value }, barbaridades.any { it.high })

/**
 * Un disparate: quién, qué cifra y si se pasó (tipo "alto") o se quedó muy corto ("bajo").
 * [from] ("de") es el conductor de la cifra si el jugador solo se quedó en el carro con ella.
 */
data class OnlineBarbaridad(val id: String, val value: Double, val high: Boolean, val from: String? = null)

data class OnlineState(
    val phase: String,
    val seq: Long,
    val round: Int,
    val rounds: Int,
    val players: List<RoomPlayer>,
    /** Sin la respuesta (answer = 0) hasta que se resuelve la ronda: va en [result]. */
    val question: Question,
    val r: OnlineRound?,
    val opener: String?,
    val deadline: Long,
    val scores: Map<String, Int>,
    val result: OnlineResult?,
)

// ------------------------------------------------------------------ Lectura (lo que devuelve Firebase)

private fun Any?.asMap(): Map<String, Any?> = (this as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: emptyMap()

/** Firebase devuelve las listas como List o, si tienen huecos, como Map con claves "0", "1"… */
private fun Any?.asList(): List<Any?> = when (this) {
    is List<*> -> this
    is Map<*, *> -> entries.sortedBy { it.key.toString().toIntOrNull() ?: 0 }.map { it.value }
    else -> emptyList()
}

private fun Any?.num(): Double? = (this as? Number)?.toDouble()
private fun Any?.str(): String? = this as? String

fun parseMeta(v: Any?): RoomMeta? {
    val m = v.asMap()
    val host = m["host"].str() ?: return null
    return RoomMeta(host, m["status"].str() ?: "lobby", m["rounds"].num()?.toInt() ?: 6, m["mode"].str() ?: "mezcla", m["barbaridades"] as? Boolean ?: false)
}

fun parsePlayers(v: Any?): List<RoomPlayer> = v.asMap().mapNotNull { (id, raw) ->
    val m = raw.asMap()
    val name = m["name"].str() ?: return@mapNotNull null
    RoomPlayer(id, name, m["avatar"].str() ?: "🙂", m["order"].num()?.toLong() ?: 0, m["online"] as? Boolean ?: true)
}.sortedBy { it.order }

fun parseState(v: Any?): OnlineState? {
    val m = v.asMap()
    val phase = m["phase"].str() ?: return null
    val q = m["q"].asMap()
    val question = Question(
        id = q["id"].str() ?: "",
        type = if (q["tipo"] == "fecha") QuestionType.FECHA else QuestionType.NUMERO,
        text = q["q"].str() ?: "",
        answer = 0.0,
        unit = q["u"].str() ?: "",
    )
    val r = m["R"].asMap().takeIf { it.isNotEmpty() }?.let { rm ->
        OnlineRound(
            bid = rm["bid"].num() ?: 0.0,
            driver = rm["driver"].str() ?: "",
            status = rm["status"].asMap().mapValues { it.value.str() ?: "carro" },
            queue = rm["queue"].asList().mapNotNull { it.str() },
            log = rm["log"].asList().mapNotNull { e ->
                val em = e.asMap()
                OnlineLog(em["id"].str() ?: return@mapNotNull null, em["action"].str() ?: "", em["value"].num())
            },
        )
    }
    val res = m["result"].asMap().takeIf { it.isNotEmpty() }?.let { rm ->
        OnlineResult(
            success = rm["success"] as? Boolean ?: false,
            bullseye = rm["bullseye"] as? Boolean ?: false,
            delta = rm["delta"].asMap().mapValues { it.value.num()?.toInt() ?: 0 },
            passengers = rm["passengers"].asList().mapNotNull { it.str() },
            off = rm["off"].asList().mapNotNull { it.str() },
            answer = rm["a"].num() ?: 0.0,
            note = rm["n"].str() ?: "",
            barbaridades = rm["barbaridades"].asList().mapNotNull { b ->
                val bm = b.asMap()
                OnlineBarbaridad(bm["id"].str() ?: return@mapNotNull null, bm["v"].num() ?: return@mapNotNull null, bm["tipo"] == "alto", bm["de"].str())
            },
        )
    }
    return OnlineState(
        phase = phase,
        seq = m["seq"].num()?.toLong() ?: 0,
        round = m["round"].num()?.toInt() ?: 0,
        rounds = m["rounds"].num()?.toInt() ?: 0,
        players = m["players"].asList().mapNotNull { p ->
            val pm = p.asMap()
            RoomPlayer(pm["id"].str() ?: return@mapNotNull null, pm["name"].str() ?: "", pm["avatar"].str() ?: "🙂")
        },
        question = question,
        r = r,
        opener = m["opener"].str(),
        deadline = m["deadline"].num()?.toLong() ?: 0,
        scores = m["scores"].asMap().mapValues { it.value.num()?.toInt() ?: 0 },
        result = res,
    )
}

// ------------------------------------------------------------------ Conversión con el motor (ids Int = posición)

private val STATUS_TO = mapOf(PlayerStatus.CONDUCTOR to "conductor", PlayerStatus.EN_CARRO to "carro", PlayerStatus.BAJADO to "bajado")
private val STATUS_FROM = STATUS_TO.entries.associate { it.value to it.key }
private val LOG_TO = mapOf(LogKind.ABRE to "abre", LogKind.SUBE to "subir", LogKind.QUEDA to "quedar", LogKind.BAJA to "bajar", LogKind.TIEMPO to "tiempo")
private val LOG_FROM = LOG_TO.entries.associate { it.value to it.key }

/** Jugadores de la partida para el motor: el id es la posición en la lista. */
fun enginePlayers(players: List<RoomPlayer>) = players.mapIndexed { i, p -> Player(i, p.name, p.avatar) }

fun RoundState.toOnline(pids: List<String>) = OnlineRound(
    bid = bid ?: 0.0,
    driver = driverId?.let { pids[it] } ?: "",
    status = status.entries.associate { pids[it.key] to STATUS_TO.getValue(it.value) },
    queue = queue.map { pids[it] },
    log = log.map { OnlineLog(pids[it.playerId], LOG_TO.getValue(it.kind), it.value) },
)

/** Para que el jefe recupere la ronda si la app se reinicia a mitad de partida. */
fun OnlineRound.toEngine(pids: List<String>, question: Question, openerIndex: Int) = RoundState(
    question = question,
    openerIndex = openerIndex,
    bid = bid,
    driverId = pids.indexOf(driver).takeIf { it >= 0 },
    status = status.entries.mapNotNull { (pid, s) -> pids.indexOf(pid).takeIf { it >= 0 }?.let { it to (STATUS_FROM[s] ?: PlayerStatus.EN_CARRO) } }.toMap(),
    queue = queue.map { pids.indexOf(it) }.filter { it >= 0 },
    log = log.mapNotNull { e -> pids.indexOf(e.id).takeIf { it >= 0 }?.let { LogEntry(it, LOG_FROM[e.action] ?: LogKind.QUEDA, e.value) } },
)

fun RoundResult.toOnline(pids: List<String>, question: Question) = OnlineResult(
    success = success,
    bullseye = bullseye,
    delta = delta.entries.associate { pids[it.key] to it.value },
    passengers = passengers.map { pids[it] },
    off = gotOff.map { pids[it] },
    answer = question.answer,
    note = question.note,
    barbaridades = barbaridades.map { OnlineBarbaridad(pids[it.playerId], it.value, it.high, it.from?.let { f -> pids[f] }) },
)

// ------------------------------------------------------------------ Escritura

fun OnlineState.toMap(): Map<String, Any?> = buildMap {
    put("phase", phase)
    put("seq", seq)
    put("round", round)
    put("rounds", rounds)
    put("players", players.map { mapOf("id" to it.id, "name" to it.name, "avatar" to it.avatar) })
    put("q", buildMap {
        put("id", question.id)
        put("tipo", if (question.type == QuestionType.FECHA) "fecha" else "numero")
        put("q", question.text)
        if (question.unit.isNotEmpty()) put("u", question.unit)
    })
    r?.let { r ->
        put("R", buildMap {
            put("bid", r.bid)
            put("driver", r.driver)
            put("status", r.status)
            put("queue", r.queue)
            put("log", r.log.map { e -> buildMap { put("id", e.id); put("action", e.action); e.value?.let { put("value", it) } } })
        })
    }
    opener?.let { put("opener", it) }
    put("deadline", deadline)
    put("scores", scores)
    result?.let { res ->
        put("result", mapOf(
            "success" to res.success, "bullseye" to res.bullseye, "delta" to res.delta,
            "passengers" to res.passengers, "off" to res.off, "a" to res.answer, "n" to res.note,
            "barbaridades" to res.barbaridades.map { b ->
                buildMap { put("id", b.id); put("v", b.value); put("tipo", if (b.high) "alto" else "bajo"); b.from?.let { put("de", it) } }
            },
        ))
    }
}
