package com.paraelcarro.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paraelcarro.ui.theme.Rustic

/** Valoración opcional de la pregunta: 👍 😐 👎 (una vez por dispositivo). */
@Composable
fun RatingRow(questionId: String, alreadyRated: Boolean, onRate: (String) -> Unit) {
    var voted by remember(questionId) { mutableStateOf(false) }
    HorizontalDivider(color = Rustic.MaderaClara.copy(alpha = .35f))
    when {
        voted -> Text("¡Gracias! Tu voto ayuda a elegir las mejores preguntas.", color = Rustic.TextoSuave)
        alreadyRated -> Text("✅ Ya has valorado esta pregunta", color = Rustic.TextoSuave)
        else -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("¿Qué te ha parecido?", Modifier.weight(1f), color = Rustic.TextoSuave)
            listOf("up" to "👍", "meh" to "😐", "down" to "👎").forEach { (kind, emoji) ->
                WoodButton(emoji, { voted = true; onRate(kind) }, tone = Tone.FANTASMA, small = true)
            }
        }
    }
}
