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
    fun `subida minima del 10 por ciento en los numeros`() {
        val everest = Question("n2", QuestionType.NUMERO, "¿Cuánto mide el Everest?", 8848.0, "m")
        assertEquals(9740.0, GameEngine.minSubida(everest, 8848.0), 0.0)
        assertEquals(110.0, GameEngine.minSubida(q, 100.0), 0.0)
        assertEquals(6.0, GameEngine.minSubida(q, 5.0), 0.0)        // enteros: al menos +1
        assertEquals(15.1, GameEngine.minSubida(q, 13.7), 1e-12)
        assertEquals(1.0, GameEngine.minSubida(q, 0.0), 0.0)
        assertEquals(1491.0, GameEngine.minSubida(Question("f", QuestionType.FECHA, "?", 1492.0), 1490.0), 0.0)
        assertTrue(GameEngine.subidaValida(everest, 8848.0, 9740.0))
        assertFalse(GameEngine.subidaValida(everest, 8848.0, 9739.0))
        assertFalse(GameEngine.subidaValida(everest, 8848.0, 8849.0))
        var r = GameEngine.open(players, round(everest), 1, 8848.0)
        val antes = r
        assertTrue(runCatching { GameEngine.act(players, antes, 2, TurnAction.Raise(8849.0)) }.isFailure)
        r = GameEngine.act(players, r, 2, TurnAction.Raise(9740.0))
        assertEquals(9740.0, r.bid!!, 0.0)
        // Los botones rápidos nunca proponen menos del mínimo
        GameEngine.pasosSubida(everest, 8848.0).forEach { (_, v) -> assertTrue(v >= 9740.0) }
    }

    @Test
    fun `cada jugador sube como mucho tres veces por ronda`() {
        val dos = players.take(2)
        var r = GameEngine.open(dos, round(), 1, 100.0)
        var v = 100.0
        repeat(2 * GameEngine.MAX_SUBIDAS) {
            v = GameEngine.minSubida(q, v)
            r = GameEngine.act(dos, r, r.queue.first(), TurnAction.Raise(v))
        }
        assertEquals(GameEngine.MAX_SUBIDAS, GameEngine.subidas(r, 1))
        assertEquals(GameEngine.MAX_SUBIDAS, GameEngine.subidas(r, 2))
        val turno = r.queue.first()
        assertFalse(GameEngine.puedeSubir(r, turno))
        val ultimo = r
        assertTrue(runCatching { GameEngine.act(dos, ultimo, turno, TurnAction.Raise(v * 2)) }.isFailure)
        r = GameEngine.act(dos, r, turno, TurnAction.Stay)
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

    @Test
    fun `la barra va del minimo a diez veces el minimo`() {
        val aneto = Question("n3", QuestionType.NUMERO, "¿Cuánto mide el pico más alto de los Pirineos?", 3404.0, "m")
        // Con la puja en 2.000 nadie puede subir de 1 en 1: el mínimo es 2.200 y la barra llega a 22.000
        assertEquals(2200.0, GameEngine.cifraBarra(aneto, 2000.0, 0.0), 0.0)
        assertEquals(22000.0, GameEngine.cifraBarra(aneto, 2000.0, 1.0), 0.0)
        val mitad = GameEngine.cifraBarra(aneto, 2000.0, 0.5)
        assertTrue(mitad in 6900.0..7000.0) // escala logarítmica: a mitad de barra, unas 3 veces el mínimo
        assertTrue(GameEngine.subidaValida(aneto, 2000.0, mitad))
        val fecha = Question("f9", QuestionType.FECHA, "?", 1900.0)
        assertEquals(1801.0, GameEngine.cifraBarra(fecha, 1800.0, 0.0), 0.0)
        assertEquals(2001.0, GameEngine.cifraBarra(fecha, 1800.0, 1.0), 0.0)
    }
}
