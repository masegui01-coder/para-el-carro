package com.paraelcarro.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** "¿Sabías que…?" de una pregunta: la explicación y el artículo de Wikipedia (en inglés) para la ilustración. */
data class Learn(val text: String, val article: String)

/**
 * Explicaciones de assets/aprende.json (las genera tools/build-preguntas.js, igual que en la web).
 * La ilustración se pide a Wikipedia en el momento, así que no ocupa nada en la app.
 */
object LearnRepository {
    private var data: Map<String, Learn>? = null
    private val thumbs = mutableMapOf<String, Bitmap?>()

    suspend fun get(context: Context, questionId: String): Learn? = withContext(Dispatchers.IO) {
        val map = data ?: runCatching { load(context) }.getOrDefault(emptyMap()).also { data = it }
        map[questionId]
    }

    private fun load(context: Context): Map<String, Learn> {
        val json = JSONObject(context.assets.open("aprende.json").bufferedReader().use { it.readText() })
        return json.keys().asSequence().mapNotNull { id ->
            val a = json.optJSONArray(id) ?: return@mapNotNull null
            id to Learn(a.optString(0), a.optString(1))
        }.toMap()
    }

    /** Miniatura del artículo (null si no hay, o no hay conexión). Se guarda en memoria mientras dura la partida. */
    suspend fun thumbnail(article: String): Bitmap? = withContext(Dispatchers.IO) {
        if (article.isBlank()) return@withContext null
        synchronized(thumbs) { if (thumbs.containsKey(article)) return@withContext thumbs[article] }
        val bmp = runCatching {
            val slug = URLEncoder.encode(article.replace(' ', '_'), "UTF-8").replace("+", "%20")
            val summary = JSONObject(httpGet("https://en.wikipedia.org/api/rest_v1/page/summary/$slug").decodeToString())
            val src = summary.optJSONObject("thumbnail")?.optString("source").orEmpty()
            if (src.isEmpty()) null else httpGet(src).let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }.getOrNull()
        synchronized(thumbs) { thumbs[article] = bmp }
        bmp
    }

    private fun httpGet(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 5000
        c.readTimeout = 8000
        // Wikipedia pide identificar la app
        c.setRequestProperty("User-Agent", "ParaElCarro/0.7 (https://paraelcarroyomebajo.com/)")
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }
}
