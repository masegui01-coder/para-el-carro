package com.paraelcarro

import com.paraelcarro.domain.RatingCounts
import com.paraelcarro.domain.RatingPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class RatingPolicyTest {
    @Test
    fun `aparta las preguntas negativas del 20 por ciento peor con votos suficientes`() {
        val ratings = (1..40).associate { "n%03d".format(it) to RatingCounts(up = 6, meh = 2, down = 1) } + mapOf(
            "n050" to RatingCounts(up = 1, meh = 1, down = 8), // muy mala
            "n051" to RatingCounts(up = 2, meh = 2, down = 4), // mala
            "n052" to RatingCounts(down = 3),                  // pocos votos: aún no se juzga
            "n053" to RatingCounts(up = 5, down = 6),          // algo negativa
        )
        // Mismo resultado que Ratings.computeExcluded en la web
        assertEquals(setOf("n050", "n051", "n053"), RatingPolicy.excluded(ratings))
    }

    @Test
    fun `sin votos no aparta nada`() {
        assertEquals(emptySet<String>(), RatingPolicy.excluded(emptyMap()))
    }
}
