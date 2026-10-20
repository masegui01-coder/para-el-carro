package com.paraelcarro.data

import android.content.Context
import com.paraelcarro.domain.model.AVATARS
import com.paraelcarro.domain.model.GameConfig
import com.paraelcarro.domain.model.Player
import com.paraelcarro.domain.model.QuestionMode
import org.json.JSONArray
import org.json.JSONObject

/** Guarda los jugadores y la configuración de la sala entre partidas. */
class LobbyStore(context: Context) {
    private val prefs = context.getSharedPreferences("sala", Context.MODE_PRIVATE)

    fun loadPlayers(): List<Player> {
        val json = prefs.getString("jugadores", null)
            ?: return (1..3).map { Player(it, "Jugador $it", AVATARS[it - 1]) }
        val arr = JSONArray(json)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Player(o.getInt("id"), o.getString("name"), o.getString("avatar"))
        }
    }

    fun loadConfig(): GameConfig = GameConfig(
        rounds = prefs.getInt("rondas", 6),
        mode = runCatching { QuestionMode.valueOf(prefs.getString("modo", null)!!) }.getOrDefault(QuestionMode.MEZCLA),
    )

    fun save(players: List<Player>, config: GameConfig) {
        val arr = JSONArray()
        players.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("avatar", it.avatar)) }
        prefs.edit()
            .putString("jugadores", arr.toString())
            .putInt("rondas", config.rounds)
            .putString("modo", config.mode.name)
            .apply()
    }
}
