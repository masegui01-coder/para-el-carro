package com.paraelcarro.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

/** Valoración opcional de la pregunta: buena, normal o mala (una vez por dispositivo). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RatingRow(questionId: String, alreadyRated: Boolean, onRate: (String) -> Unit) {
    var voted by remember(questionId) { mutableStateOf(false) }
    HorizontalDivider(thickness = 2.dp, color = Rustic.Tinta)
    when {
        voted -> Text("¡Gracias! Tu voto ayuda a elegir las mejores preguntas.", color = Rustic.TextoSuave)
        alreadyRated -> Text("Ya has valorado esta pregunta", color = Rustic.TextoSuave)
        else -> FlowRow(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("¿Qué te ha parecido?", Modifier.align(Alignment.CenterVertically).padding(end = 4.dp), color = Rustic.TextoSuave)
            listOf(
                Triple("up", "¡Buena!", Tone.MUSGO), Triple("meh", "Normal", Tone.FANTASMA), Triple("down", "Mala", Tone.SANGRE),
            ).forEach { (kind, label, tone) ->
                WoodButton(label, { voted = true; onRate(kind) }, tone = tone, small = true, description = "Pregunta $label".replace("¡", "").replace("!", ""))
            }
        }
    }
}
