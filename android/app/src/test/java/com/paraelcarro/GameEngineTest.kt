package com.paraelcarro

import com.paraelcarro.data.QuestionRepository
import com.paraelcarro.domain.GameEngine
import com.paraelcarro.domain.formatCifra
import com.paraelcarro.domain.model.Player
import com.paraelcarro.domain.model.PlayerStatus
import com.paraelcarro.domain.model.Question
import com.paraelcarro.domain.model.QuestionType
import com.paraelcarro.domain.model.RoundState
import com.paraelcarro.domain.model.TurnAction
import com.paraelcarro.domain.parseCifra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GameEngineTest {
    private val players = (1..4).map { Player(it, "J$it", "🙂") }
    private val q = Question("n1", QuestionType.NUMERO, "¿Cuántos?", 1000.0)

    private fun round(question: Question = q) = RoundState(question, openerIndex = 0)

    @Test
    fun `subir la cifra cambia de conductor y reinicia la cola`() {
        var r = GameEngine.open(players, round(), 1, 100.0)
        assertEquals(listOf(2, 3, 4), r.queue)
        r = GameEngine.act(players, r, 2, TurnAction.Raise(200.0))
        assertEquals(2, r.driverId)
        assertEquals(PlayerStatus.EN_CARRO, r.status[1])
        assertEquals(listOf(3, 4, 1), r.queue)
        r = GameEngine.act(players, r, 3, TurnAction.GetOff)
        r = GameEngine.act(players, r, 4, TurnAction.Stay)
        assertFalse(GameEngine.isFinished(r))
        r = GameEngine.act(players, r, 1, TurnAction.Raise(300.0))
        // El que se bajó ya no vuelve a la cola
        assertEquals(listOf(2, 4), r.queue)
        r = GameEngine.act(players, r, 2, TurnAction.Stay)
        r = GameEngine.act(players, r, 4, TurnAction.Stay)
        assertTrue(GameEngine.isFinished(r))
    }

    @Test
    fun `puntos cuando el carro cruza`() {
        var r = GameEngine.open(players, round(), 1, 500.0)
        r = GameEngine.act(players, r, 2, TurnAction.Stay)
        r = GameEngine.act(players, r, 3, TurnAction.Stay)
        r = GameEngine.act(players, r, 4, TurnAction.GetOff)
        val res = GameEngine.score(players, r)
        assertTrue(res.success)
        assertFalse(res.bullseye)
        assertEquals(mapOf(1 to 5, 2 to 3, 3 to 3, 4 to 0), res.delta)
    }

    @Test
    fun `puntos cuando cae al rio`() {
        var r = GameEngine.open(players, round(), 1, 5000.0)
        r = GameEngine.act(players, r, 2, TurnAction.GetOff)
        r = GameEngine.act(players, r, 3, TurnAction.GetOff)
        r = GameEngine.act(players, r, 4, TurnAction.Stay)
        val res = GameEngine.score(players, r)
        assertFalse(res.success)
        assertEquals(mapOf(1 to -5, 2 to 3, 3 to 3, 4 to -2), res.delta)
    }

    @Test
    fun `clavada da bonus`() {
        var r = GameEngine.open(players, round(), 1, 950.0)
        repeat(3) { r = GameEngine.act(players, r, r.queue.first(), TurnAction.GetOff) }
        val res = GameEngine.score(players, r)
        assertTrue(res.bullseye)
        assertEquals(3 + 0 + 2, res.delta[1])
    }

    @Test
    fun `fechas antes de Cristo`() {
        assertEquals(-2560.0, parseCifra("2560", QuestionType.FECHA, beforeChrist = true)!!, 0.0)
        assertEquals("2560 a. C.", formatCifra(-2560.0, QuestionType.FECHA))
        assertEquals(7_400_000_000_000.0, parseCifra("7.400.000.000.000", QuestionType.NUMERO, false)!!, 0.0)
        assertEquals("13,7", formatCifra(13.7, QuestionType.NUMERO))
    }

    @Test
    fun `el banco de preguntas carga entero`() {
        val json = File("src/main/assets/preguntas.json").readText()
        val all = QuestionRepository.parse(json)
        assertTrue(all.count { it.type == QuestionType.FECHA } >= 150)
        assertTrue(all.count { it.type == QuestionType.NUMERO } >= 150)
    }
}
