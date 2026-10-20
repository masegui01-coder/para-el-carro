package com.paraelcarro.domain

/** Votos 👍 😐 👎 de una pregunta. */
data class RatingCounts(val up: Int = 0, val meh: Int = 0, val down: Int = 0) {
    val votes: Int get() = up + meh + down
    /** De -1 (todo 👎) a 1 (todo 👍). */
    val score: Double get() = if (votes == 0) 0.0 else (up - down).toDouble() / votes
}

/** La misma regla que Ratings.computeExcluded en js/game.js. */
object RatingPolicy {
    const val MIN_VOTES = 5

    /** Preguntas apartadas: al menos [MIN_VOTES] votos, nota negativa y en el 20 % peor de las valoradas. */
    fun excluded(ratings: Map<String, RatingCounts>): Set<String> {
        val rated = ratings.filterValues { it.votes >= MIN_VOTES }
        if (rated.isEmpty()) return emptySet()
        val sorted = rated.values.map { it.score }.sorted()
        val p20 = sorted[(sorted.size * 0.2).toInt()]
        return rated.filterValues { it.score < 0 && it.score <= p20 }.keys
    }
}
