package com.paraelcarro

import com.paraelcarro.data.QuestionRepository
import com.paraelcarro.data.online.parseMeta
import com.paraelcarro.data.online.parsePlayers
import com.paraelcarro.data.online.parseState
import com.paraelcarro.domain.model.QuestionType
import com.paraelcarro.domain.parseCifra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Entradas raras que no deben romper el juego. */
class RobustezTest {
    @Test
    fun `lo que escribe el jugador nunca rompe`() {
        listOf("abc", "1,2,3", "9".repeat(400), "", " ", "-5", "1e9", "∞", "NaN", "12,,5", "١٢٣").forEach {
            assertNull("'$it' debería ser null", parseCifra(it, QuestionType.NUMERO, false))
        }
        assertEquals(12345.6, parseCifra("12.345,6", QuestionType.NUMERO, false)!!, 0.0)
        assertEquals(-218.0, parseCifra("218", QuestionType.FECHA, true)!!, 0.0)
    }

    @Test
    fun `datos de la sala con forma inesperada no rompen`() {
        // Basura en Firebase (alguien con una versión antigua, datos a medias…)
        assertNull(parseState(null))
        assertNull(parseState("texto"))
        assertNull(parseState(mapOf("seq" to 3L)))
        val st = parseState(mapOf("phase" to "turn", "R" to "no es un mapa", "players" to 42, "scores" to listOf(1, 2)))!!
        assertEquals("turn", st.phase)
        assertNull(st.r)
        assertEquals(emptyList<Any>(), st.players)
        assertNull(parseMeta(mapOf("status" to "lobby")))
        assertEquals(emptyList<Any>(), parsePlayers(mapOf("x" to "basura", "y" to mapOf("avatar" to "av:gato"))))
    }

    @Test(expected = Exception::class)
    fun `un banco de preguntas roto se detecta al cargar`() {
        QuestionRepository.parse("[{\"id\": 1}]")
    }
}
