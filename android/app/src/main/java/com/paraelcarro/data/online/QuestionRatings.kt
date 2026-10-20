package com.paraelcarro.data.online

import android.content.Context
import com.google.firebase.database.ServerValue
import com.paraelcarro.domain.RatingCounts
import com.paraelcarro.domain.RatingPolicy

/**
 * Valoraciones de las preguntas en Firebase (ratings/QID/{up,meh,down}, compartidas con la web).
 * Cada dispositivo vota una vez por pregunta; las peor valoradas dejan de salir ([RatingPolicy]).
 */
class QuestionRatings(context: Context) {
    private val rooms by lazy { RoomRepository(context) }
    private val prefs = context.getSharedPreferences("valoraciones", Context.MODE_PRIVATE)

    /** Preguntas que el juego aparta. Se actualiza con [refresh]. */
    var excluded: Set<String> = emptySet()
        private set

    suspend fun refresh() {
        val ratings = runCatching {
            rooms.read(rooms.db.getReference("ratings")).children.associate { s ->
                fun n(k: String) = (s.child(k).value as? Number)?.toInt() ?: 0
                s.key!! to RatingCounts(n("up"), n("meh"), n("down"))
            }
        }.getOrDefault(emptyMap())
        excluded = RatingPolicy.excluded(ratings)
    }

    fun isRated(qid: String): Boolean = qid in rated()

    /** kind: "up", "meh" o "down". */
    fun rate(qid: String, kind: String) {
        runCatching { rooms.db.getReference("ratings/$qid/$kind").setValue(ServerValue.increment(1)) }
        prefs.edit().putStringSet("ids", rated() + qid).apply()
    }

    private fun rated(): Set<String> = prefs.getStringSet("ids", emptySet()) ?: emptySet()
}
