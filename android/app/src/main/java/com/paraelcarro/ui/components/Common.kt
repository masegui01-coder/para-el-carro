package com.paraelcarro.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paraelcarro.domain.format
import com.paraelcarro.domain.model.Draft
import com.paraelcarro.domain.model.Question
import com.paraelcarro.domain.model.QuestionType
import com.paraelcarro.domain.model.category
import com.paraelcarro.domain.parseCifra
import com.paraelcarro.ui.theme.Rustic

enum class Tone(val top: Color, val bottom: Color, val text: Color) {
    AMBAR(Rustic.AmbarClaro, Rustic.Ambar, Color(0xFF2A1A08)),
    MADERA(Rustic.MaderaClara, Rustic.Madera, Color(0xFFFFF7E8)),
    MUSGO(Rustic.MusgoClaro, Rustic.Musgo, Color(0xFFFFF7E8)),
    SANGRE(Color(0xFFD9715C), Rustic.Sangre, Color(0xFFFFF7E8)),
    FANTASMA(Color.Transparent, Color.Transparent, Rustic.Texto),
}

@Composable
fun WoodButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.MADERA,
    enabled: Boolean = true,
    small: Boolean = false,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .heightIn(min = if (small) 38.dp else 50.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(tone.top, tone.bottom)), alpha = if (enabled) 1f else .35f)
            .then(if (tone == Tone.FANTASMA) Modifier.border(BorderStroke(1.dp, Rustic.Texto.copy(alpha = .25f)), shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 12.dp else 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = tone.text.copy(alpha = if (enabled) 1f else .5f), fontWeight = FontWeight.Bold, fontSize = if (small) 14.sp else 16.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Rustic.Madera.copy(alpha = .28f), Rustic.MaderaOscura.copy(alpha = .35f))))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun Title(text: String, modifier: Modifier = Modifier, color: Color = Rustic.AmbarClaro, size: Int = 22) {
    Text(text, modifier = modifier, color = color, fontSize = size.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
}

@Composable
fun RoundHeader(round: Int, total: Int, question: Question) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Ronda $round/$total", color = Rustic.TextoSuave, fontSize = 14.sp)
        Text(
            question.category.uppercase(),
            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (question.type == QuestionType.FECHA) Color(0xFF4B3A6B) else Rustic.Musgo)
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun QuestionCard(question: Question, small: Boolean = false) {
    Panel { Text(question.text, color = Rustic.Texto, fontFamily = FontFamily.Serif, fontSize = if (small) 16.sp else 20.sp, lineHeight = if (small) 21.sp else 26.sp) }
}

@Composable
fun TimerBar(secondsLeft: Int, fraction: Float) {
    val urgent = secondsLeft <= 5
    val pulse by rememberInfiniteTransition(label = "pulso").animateFloat(1f, 1.15f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "escala")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$secondsLeft", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold,
            color = if (urgent) Rustic.Sangre else Rustic.Texto,
            modifier = Modifier.scale(if (urgent) pulse else 1f),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = .45f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(Rustic.Sangre, Rustic.Ambar))),
            )
        }
    }
}

/** Campo de cifra con vista previa formateada ("= 7.400.000 litros") y casilla a. C. en las fechas. */
@Composable
fun CifraField(
    question: Question,
    draft: Draft,
    placeholder: String,
    onText: (String) -> Unit,
    onBeforeChrist: (Boolean) -> Unit,
) {
    val value = parseCifra(draft.text, question.type, draft.beforeChrist)
    OutlinedTextField(
        value = draft.text,
        onValueChange = { t -> onText(t.filter { it.isDigit() || it == '.' || it == ',' }) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Rustic.Ambar, unfocusedBorderColor = Rustic.MaderaClara),
        modifier = Modifier.fillMaxWidth(),
    )
    if (question.type == QuestionType.FECHA) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(draft.beforeChrist, onBeforeChrist, colors = CheckboxDefaults.colors(checkedColor = Rustic.Ambar))
            Text("Antes de Cristo (a. C.)", color = Rustic.TextoSuave)
        }
    }
    Text(
        if (value == null) " " else "= ${question.format(value)}",
        color = Rustic.AmbarClaro, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
