package com.paraelcarro

import com.paraelcarro.data.online.OnlineState
import com.paraelcarro.data.online.RoomPlayer
import com.paraelcarro.data.online.enginePlayers
import com.paraelcarro.data.online.parseState
import com.paraelcarro.data.online.toMap
import com.paraelcarro.data.online.toOnline
import com.paraelcarro.domain.GameEngine
import com.paraelcarro.domain.model.Barbaridad
import com.paraelcarro.domain.model.Player
import com.paraelcarro.domain.model.Question
import com.paraelcarro.domain.model.QuestionType
import com.paraelcarro.domain.model.RoundState
import com.paraelcarro.domain.model.TurnAction
import com.paraelcarro.domain.model.VozAbuela
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mismos casos que se comprobaron en la web (Logic.barbaridad). */
class BarbaridadTest {
    private val kanjis = Question("n1", QuestionType.NUMERO, "¿Cuántos kanjis?", 2136.0, "kanjis")
    private val velcro = Question("f1", QuestionType.FECHA, "¿Velcro?", 1955.0)
    private val stonehenge = Question("f2", QuestionType.FECHA, "¿Stonehenge?", -3000.0)
    private val vanGogh = Question("n2", QuestionType.NUMERO, "¿Cuadros vendidos?", 1.0)

    private fun b(q: Question, v: Double) = GameEngine.barbaridad(q, v, currentYear = 2026)

    @Test
    fun `umbrales de barbaridad`() {
        assertEquals(listOf(Barbaridad.BAJO, Barbaridad.BAJO, null, null, null, Barbaridad.ALTO, Barbaridad.ALTO),
            listOf(150.0, 213.0, 214.0, 1000.0, 5000.0, 21360.0, 25000.0).map { b(kanjis, it) })
        assertEquals(listOf(Barbaridad.BAJO, Barbaridad.BAJO, null, null, Barbaridad.ALTO),
            listOf(1700.0, 1854.0, 1855.0, 2050.0, 2056.0).map { b(velcro, it) })
        assertEquals(listOf(Barbaridad.BAJO, Barbaridad.BAJO, null), listOf(-4500.0, -4300.0, -2000.0).map { b(stonehenge, it) })
        assertEquals(listOf(null, Barbaridad.ALTO, Barbaridad.ALTO), listOf(10.0, 11.0, 20.0).map { b(vanGogh, it) })
    }

    @Test
    fun `menos uno una sola vez por jugador y nada si el modo esta apagado`() {
        val players = (1..3).map { Player(it, "J$it", "av:gato") }
        var r = GameEngine.open(players, RoundState(kanjis, 0), 1, 1.0) // abre con 1: barbaridad por lo bajo
        r = GameEngine.act(players, r, 2, TurnAction.Raise(5.0))       // otra barbaridad del jugador 2
        r = GameEngine.act(players, r, 3, TurnAction.Raise(2000.0))    // razonable
        r = GameEngine.act(players, r, 1, TurnAction.Stay)
        r = GameEngine.act(players, r, 2, TurnAction.Stay)
        val sin = GameEngine.score(players, r, barbaridades = false)
        assertTrue(sin.barbaridades.isEmpty())
        val con = GameEngine.score(players, r, barbaridades = true)
        assertEquals(setOf(1, 2), con.barbaridades.map { it.playerId }.toSet())
        con.barbaridades.forEach { assertEquals(sin.delta[it.playerId]!! - 1, con.delta[it.playerId]) }
        assertEquals(sin.delta[3], con.delta[3])
    }

    @Test
    fun `viaja igual que en la web`() {
        val room = listOf(RoomPlayer("aa", "Ana", "av:gato"), RoomPlayer("bb", "Luis", "av:rana"))
        val engine = enginePlayers(room)
        var r = GameEngine.open(engine, RoundState(kanjis, 0), 0, 30000.0)
        r = GameEngine.act(engine, r, 1, TurnAction.GetOff)
        val res = GameEngine.score(engine, r, barbaridades = true).toOnline(room.map { it.id }, kanjis)
        val st = OnlineState("bridge", 1, 1, 1, room, kanjis, r.toOnline(room.map { it.id }), "aa", 0, mapOf(), res)
        val back = parseState(st.toMap())!!.result!!
        assertEquals(1, back.barbaridades.size)
        assertEquals("aa", back.barbaridades[0].id)
        assertTrue(back.barbaridades[0].high)
        @Suppress("UNCHECKED_CAST")
        val raw = (st.toMap()["result"] as Map<String, Any?>)["barbaridades"] as List<Map<String, Any?>>
        assertEquals(mapOf("id" to "aa", "v" to 30000.0, "tipo" to "alto"), raw[0])
    }

    @Test
    fun `quien se queda en el carro con una barbaridad tambien pierde un punto`() {
        val players = (1..4).map { Player(it, "J$it", "av:gato") }
        var r = GameEngine.open(players, RoundState(kanjis, 0), 1, 150.0) // J1 se queda cortísimo
        r = GameEngine.act(players, r, 2, TurnAction.Stay)             // J2 lo da por bueno → −1
        r = GameEngine.act(players, r, 3, TurnAction.Raise(2000.0))    // J3 sube a algo razonable
        r = GameEngine.act(players, r, 4, TurnAction.Stay)             // J4 se queda con 2000 → nada
        r = GameEngine.act(players, r, 1, TurnAction.Stay)
        r = GameEngine.act(players, r, 2, TurnAction.Stay)
        val sin = GameEngine.score(players, r, barbaridades = false)
        val con = GameEngine.score(players, r, barbaridades = true)
        assertEquals(listOf(1, 2), con.barbaridades.map { it.playerId })
        assertNull(con.barbaridades[0].from)
        assertEquals(1, con.barbaridades[1].from)
        assertEquals(150.0, con.barbaridades[1].value, 0.0)
        assertEquals(sin.delta.mapValues { (id, d) -> if (id <= 2) d - 1 else d }, con.delta)
    }

    @Test
    fun `lo que dijo uno mismo cuenta antes que lo que apoyo`() {
        val players = (1..3).map { Player(it, "J$it", "av:gato") }
        var r = GameEngine.open(players, RoundState(kanjis, 0), 1, 30000.0)
        r = GameEngine.act(players, r, 2, TurnAction.Raise(90000.0))
        r = GameEngine.act(players, r, 3, TurnAction.Stay)
        r = GameEngine.act(players, r, 1, TurnAction.Stay)              // J1 apoya 90000, pero dijo 30000
        val b = GameEngine.score(players, r, barbaridades = true).barbaridades.associateBy { it.playerId }
        assertEquals(30000.0, b.getValue(1).value, 0.0)
        assertNull(b.getValue(1).from)
        assertEquals(2, b.getValue(3).from)
    }

    @Test
    fun `la voz se elige igual que en la web`() {
        assertEquals(VozAbuela.SANTO, VozAbuela.para(listOf(150.0, 150.0), anyHigh = false))
        assertEquals(VozAbuela.CORTO, VozAbuela.para(listOf(30001.0, 30001.0), anyHigh = true))
        assertEquals(VozAbuela.TRILLONES, VozAbuela.para(listOf(30001.0, 30000.0), anyHigh = true))
        assertEquals(12500L, VozAbuela.TRILLONES.escenaMs)
        assertEquals(4500L, VozAbuela.CORTO.escenaMs)
    }

    @Test
    fun `el apoyo viaja por la sala con el campo de`() {
        val room = listOf(RoomPlayer("aa", "Ana", "av:gato"), RoomPlayer("bb", "Luis", "av:rana"))
        val engine = enginePlayers(room)
        var r = GameEngine.open(engine, RoundState(kanjis, 0), 0, 30000.0)
        r = GameEngine.act(engine, r, 1, TurnAction.Stay)
        val res = GameEngine.score(engine, r, barbaridades = true).toOnline(room.map { it.id }, kanjis)
        val st = OnlineState("bridge", 1, 1, 1, room, kanjis, r.toOnline(room.map { it.id }), "aa", 0, mapOf(), res)
        val back = parseState(st.toMap())!!.result!!.barbaridades.associateBy { it.id }
        assertNull(back.getValue("aa").from)
        assertEquals("aa", back.getValue("bb").from)
    }
}
