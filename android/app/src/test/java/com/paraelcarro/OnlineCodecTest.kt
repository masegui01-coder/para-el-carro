package com.paraelcarro

import com.paraelcarro.data.online.OnlineState
import com.paraelcarro.data.online.RoomPlayer
import com.paraelcarro.data.online.enginePlayers
import com.paraelcarro.data.online.parseState
import com.paraelcarro.data.online.toEngine
import com.paraelcarro.data.online.toMap
import com.paraelcarro.data.online.toOnline
import com.paraelcarro.domain.GameEngine
import com.paraelcarro.domain.model.Question
import com.paraelcarro.domain.model.QuestionType
import com.paraelcarro.domain.model.RoundState
import com.paraelcarro.domain.model.TurnAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** El formato de rooms/CODIGO/state tiene que ser el mismo que publica js/online.js. */
class OnlineCodecTest {
    private val players = listOf(RoomPlayer("aa", "Manu", "🤠"), RoomPlayer("bb", "Lucía", "🧔"), RoomPlayer("cc", "Pepe", "🧝‍♀️"))
    private val pids = players.map { it.id }
    private val q = Question("n045", QuestionType.NUMERO, "¿Cuántos?", 85000.0, "habitantes", "nota")

    @Test
    fun `lee el estado tal como lo escribe la web`() {
        // Lo que devuelve Firebase para un estado publicado por un navegador (números como Long)
        val web = mapOf(
            "phase" to "turn", "seq" to 7L, "round" to 1L, "rounds" to 2L,
            "players" to players.map { mapOf("id" to it.id, "name" to it.name, "avatar" to it.avatar) },
            "q" to mapOf("id" to "n045", "tipo" to "numero", "q" to "¿Cuántos?", "u" to "habitantes"),
            "R" to mapOf(
                "bid" to 50000L, "driver" to "aa",
                "status" to mapOf("aa" to "conductor", "bb" to "carro", "cc" to "carro"),
                "queue" to listOf("cc"),
                "log" to listOf(mapOf("id" to "aa", "action" to "abre", "value" to 50000L), mapOf("id" to "bb", "action" to "tiempo")),
            ),
            "opener" to "aa", "deadline" to 1_800_000_000_000L,
            "scores" to mapOf("aa" to 0L, "bb" to 0L, "cc" to 0L),
        )
        val st = parseState(web)!!
        assertEquals("turn", st.phase)
        assertEquals(50000.0, st.r!!.bid, 0.0)
        assertEquals(listOf("cc"), st.r!!.queue)
        assertEquals("tiempo", st.r!!.log[1].action)
        assertNull(st.r!!.log[1].value)
        assertNull(st.result)
        assertEquals(0.0, st.question.answer, 0.0) // la respuesta no viaja hasta resolver
    }

    @Test
    fun `ida y vuelta entre el motor y Firebase`() {
        val engine = enginePlayers(players)
        var rs = GameEngine.open(engine, RoundState(q, 0), 0, 90000.0)
        rs = GameEngine.act(engine, rs, 1, TurnAction.GetOff)
        rs = GameEngine.act(engine, rs, 2, TurnAction.Stay)
        val res = GameEngine.score(engine, rs).toOnline(pids, q)
        assertEquals(mapOf("aa" to -4, "bb" to 3, "cc" to -2), res.delta)

        val st = OnlineState("bridge", 3, 1, 2, players, q, rs.toOnline(pids), "aa", 0, mapOf("aa" to -4, "bb" to 3, "cc" to -2), res)
        val back = parseState(st.toMap())!!
        assertEquals(mapOf("aa" to "conductor", "bb" to "bajado", "cc" to "carro"), back.r!!.status)
        assertEquals(listOf("abre", "bajar", "quedar"), back.r!!.log.map { it.action })
        assertEquals(85000.0, back.result!!.answer, 0.0)
        assertEquals(listOf("bb"), back.result!!.off)

        // El jefe puede reconstruir la ronda tras reiniciar la app
        assertEquals(rs.status, back.r!!.toEngine(pids, q, 0).status)
    }
}
