package com.paraelcarro

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Los "¿Sabías que…?" de assets/aprende.json corresponden a preguntas que existen y tienen texto. */
class AprendeDataTest {
    private val assets = File("src/main/assets")
    private val aprende = JSONObject(File(assets, "aprende.json").readText())
    private val ids = JSONArray(File(assets, "preguntas.json").readText()).let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("id") }.toSet() }

    @Test
    fun `cada explicacion es de una pregunta que existe y tiene texto`() {
        aprende.keys().forEach { id ->
            assertTrue("no existe la pregunta $id", id in ids)
            val e = aprende.getJSONArray(id)
            assertTrue("$id sin texto", e.getString(0).length > 20)
        }
    }

    @Test
    fun `casi todas las preguntas tienen explicacion`() {
        assertTrue("solo ${aprende.length()} de ${ids.size}", aprende.length() >= ids.size * 0.85)
    }
}
