package com.paraelcarro.data

import android.content.Context
import com.paraelcarro.domain.model.Question
import com.paraelcarro.domain.model.QuestionMode
import com.paraelcarro.domain.model.QuestionType
import org.json.JSONArray

/**
 * Banco de preguntas de assets/preguntas.json (generado con tools/build-preguntas.js).
 * Recuerda las ya jugadas para no repetirlas hasta agotar el modo elegido.
 */
class QuestionRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("preguntas", Context.MODE_PRIVATE)
    private val all: List<Question> by lazy {
        parse(context.assets.open("preguntas.json").bufferedReader().use { it.readText() })
    }

    fun byId(id: String): Question? = all.firstOrNull { it.id == id }

    fun next(mode: QuestionMode): Question {
        val pool = all.filter {
            when (mode) {
                QuestionMode.FECHAS -> it.type == QuestionType.FECHA
                QuestionMode.NUMEROS -> it.type == QuestionType.NUMERO
                QuestionMode.MEZCLA -> true
            }
        }
        val used = prefs.getStringSet(KEY_USED, emptySet())!!.toMutableSet()
        var fresh = pool.filter { it.id !in used }
        if (fresh.isEmpty()) {
            used.removeAll(pool.map { it.id }.toSet())
            fresh = pool
        }
        val q = fresh.random()
        used += q.id
        prefs.edit().putStringSet(KEY_USED, used).apply()
        return q
    }

    companion object {
        private const val KEY_USED = "usadas"

        fun parse(json: String): List<Question> {
            val arr = JSONArray(json)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Question(
                    id = o.getString("id"),
                    type = if (o.getString("tipo") == "fecha") QuestionType.FECHA else QuestionType.NUMERO,
                    text = o.getString("q"),
                    answer = o.getDouble("a"),
                    unit = o.optString("u"),
                    note = o.optString("n"),
                )
            }
        }
    }
}
