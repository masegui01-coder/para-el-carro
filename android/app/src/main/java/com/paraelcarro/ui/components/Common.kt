package com.paraelcarro.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paraelcarro.R
import com.paraelcarro.domain.format
import com.paraelcarro.domain.model.Draft
import com.paraelcarro.domain.model.Question
import com.paraelcarro.domain.model.QuestionType
import com.paraelcarro.domain.model.category
import com.paraelcarro.domain.parseCifra
import com.paraelcarro.ui.theme.Rustic

/** Relleno y color de texto de cada tipo de botón (estilo Tebeo). */
enum class Tone { AMBAR, MADERA, MUSGO, SANGRE, AZUL, FANTASMA }

private fun Tone.fill(): Color = when (this) {
    Tone.AMBAR -> Rustic.Amarillo
    Tone.MADERA -> Rustic.Noche2
    Tone.MUSGO -> Rustic.Musgo
    Tone.SANGRE -> Rustic.Sangre
    Tone.AZUL -> Rustic.Azul
    Tone.FANTASMA -> Color.Transparent
}

private fun Tone.ink(): Color = when (this) {
    Tone.AMBAR, Tone.MUSGO -> Rustic.TintaFija
    Tone.SANGRE, Tone.AZUL -> Color.White
    Tone.MADERA, Tone.FANTASMA -> Rustic.Texto
}

/** Sombra de cómic desplazada (se dibuja fuera del recorte, detrás del contenido). */
fun Modifier.comicShadow(radius: Dp, offset: Dp = 4.dp, color: Color = Color.Unspecified): Modifier = drawBehind {
    val o = offset.toPx()
    drawRoundRect(
        color = if (color == Color.Unspecified) Rustic.TintaFija else color,
        topLeft = Offset(o, o), size = size, cornerRadius = CornerRadius(radius.toPx()),
    )
}

/**
 * Botón del estilo Tebeo: contorno negro de 3 dp, sombra desplazada y texto en Baloo 2. Al pulsarlo
 * "se hunde" hacia la sombra. Mínimo 48 dp de alto para que sea fácil de tocar.
 */
@Composable
fun WoodButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.MADERA,
    enabled: Boolean = true,
    small: Boolean = false,
    /** Icono ilustrado (R.drawable.ic_…) delante del texto. */
    @DrawableRes icon: Int? = null,
    /** Botón grande (acción principal). */
    big: Boolean = false,
    /** Texto para lectores de pantalla si el visible no basta. */
    description: String? = null,
    /** Si no es null, el botón es una opción de un grupo (p. ej. el tema) y TalkBack dice si está elegida. */
    selected: Boolean? = null,
    /** Contenido extra tras el texto (p. ej. los puntos de subidas que quedan). */
    trailing: (@Composable () -> Unit)? = null,
) {
    val radius = if (small) 12.dp else 16.dp
    val shape = if (small) MaterialTheme.shapes.small else MaterialTheme.shapes.medium
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val shadow = if (tone == Tone.FANTASMA || !enabled) 0.dp else if (pressed) 1.dp else if (big) 6.dp else 4.dp
    val sink = if (pressed && enabled && tone != Tone.FANTASMA) 3.dp else 0.dp
    Box(
        modifier
            .heightIn(min = if (small) 48.dp else if (big) 64.dp else 54.dp)
            .offset(x = sink, y = sink)
            .then(if (shadow > 0.dp) Modifier.comicShadow(radius, shadow, Rustic.Sombra) else Modifier)
            .clip(shape)
            .background(tone.fill().let { if (enabled) it else it.copy(alpha = it.alpha * .45f) })
            .then(if (focused) Modifier.focusOutline(radius) else Modifier)
            .border(BorderStroke(3.dp, Rustic.Tinta.copy(alpha = if (enabled) 1f else .5f)), shape)
            .then(
                if (selected != null) Modifier.selectable(selected, interactionSource = interaction, indication = LocalIndication.current, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                else Modifier.clickable(enabled = enabled, role = Role.Button, interactionSource = interaction, indication = LocalIndication.current, onClick = onClick),
            )
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = if (small) 12.dp else 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) {
                Image(
                    painterResource(icon), contentDescription = null, modifier = Modifier.size(if (small) 24.dp else 32.dp).alpha(if (enabled) 1f else .5f),
                    colorFilter = if (icon in STROKE_ICONS) ColorFilter.tint(tone.ink()) else null,
                )
            }
            if (text.isNotEmpty()) Text(
                text, color = tone.ink().copy(alpha = if (enabled) 1f else .6f), textAlign = TextAlign.Center,
                style = when {
                    small -> MaterialTheme.typography.labelLarge
                    big -> MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, lineHeight = 24.sp)
                    else -> MaterialTheme.typography.titleMedium.copy(lineHeight = 22.sp)
                },
            )
            trailing?.invoke()
        }
    }
}

/** Iconos de trazo (ic_t_*): se tiñen con el color del texto; los ilustrados (ic_burra…) no. */
private val STROKE_ICONS = setOf(
    R.drawable.ic_t_cerrar, R.drawable.ic_t_sonido, R.drawable.ic_t_silencio, R.drawable.ic_t_ajustes, R.drawable.ic_t_amigos,
    R.drawable.ic_t_libro, R.drawable.ic_t_mas, R.drawable.ic_t_menos, R.drawable.ic_t_flecha, R.drawable.ic_t_diana,
)

/** Tarjeta blanca con contorno y sombra de cómic. */
@Composable
fun Panel(modifier: Modifier = Modifier, color: Color = Color.Unspecified, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier
            .fillMaxWidth()
            .padding(end = 6.dp, bottom = 6.dp)
            .comicShadow(20.dp, 6.dp, Rustic.Sombra)
            .clip(shape)
            .background(if (color == Color.Unspecified) Rustic.Noche2 else color)
            .border(3.dp, Rustic.Tinta, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun Title(text: String, modifier: Modifier = Modifier, color: Color = Rustic.Texto, size: Int = 24) {
    Text(text, modifier = modifier.semantics { heading() }, color = color, style = MaterialTheme.typography.headlineSmall.copy(fontSize = size.sp, lineHeight = (size + 2).sp))
}

/** Etiqueta negra con letras amarillas ("RONDA 3/6"), con un icono de trazo opcional delante. */
@Composable
fun Etiqueta(text: String, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    Row(
        modifier.clip(MaterialTheme.shapes.extraSmall).background(Rustic.TintaFija).border(2.dp, Rustic.Tinta, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(16.dp), colorFilter = ColorFilter.tint(Rustic.Amarillo))
        Text(text, color = Rustic.Amarillo, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp))
    }
}

@Composable
fun RoundHeader(round: Int, total: Int, question: Question) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Etiqueta("RONDA $round/$total")
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Rustic.Chip)
                .border(2.dp, Rustic.Tinta, RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Image(
                painterResource(if (question.type == QuestionType.FECHA) R.drawable.ic_t_calendario else R.drawable.ic_t_numero),
                contentDescription = null, modifier = Modifier.size(16.dp), colorFilter = ColorFilter.tint(Rustic.Texto),
            )
            Text(question.category, color = Rustic.Texto, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun QuestionCard(question: Question, small: Boolean = false) {
    Panel {
        Text(
            question.text, color = Rustic.Texto,
            style = if (small) MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 21.sp)
            else MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, lineHeight = 25.sp),
        )
    }
}

/** ¿Ha pedido el usuario quitar las animaciones (Ajustes del sistema > Accesibilidad)? */
@Composable
fun reducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        android.provider.Settings.Global.getFloat(resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

private const val URGENT_SECONDS = 5

/** Segundos en un círculo amarillo (rojo y latiendo en los 5 últimos). Crece con la letra si hace falta. */
@Composable
fun TimerCircle(secondsLeft: Int, modifier: Modifier = Modifier) {
    val urgent = secondsLeft <= URGENT_SECONDS
    val pulse = if (urgent && !reducedMotion()) pulseScale() else null
    Box(
        modifier
            .sizeIn(minWidth = 58.dp, minHeight = 58.dp)
            .aspectRatio(1f)
            .graphicsLayer { val s = pulse?.value ?: 1f; scaleX = s; scaleY = s }
            .comicShadow(40.dp, 3.dp, Rustic.Sombra)
            .clip(CircleShape)
            .background(if (urgent) Rustic.Sangre else Rustic.Amarillo)
            .border(3.dp, Rustic.Tinta, CircleShape)
            .padding(6.dp)
            .semantics { contentDescription = "Quedan $secondsLeft segundos" },
        contentAlignment = Alignment.Center,
    ) {
        Text("$secondsLeft", color = if (urgent) Color.White else Rustic.TintaFija, fontFamily = Rustic.Titulos, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, maxLines = 1)
    }
}

/** Grosor del contorno de [OutlinedText] por cada sp de letra (≈ 3 dp en un título de 40 sp). */
private const val OUTLINE_PER_SP = 0.075f

/** Latido del temporizador: solo se compone (y anima) en los últimos segundos. */
@Composable
private fun pulseScale(): State<Float> =
    rememberInfiniteTransition(label = "pulso").animateFloat(1f, 1.12f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "escala")

/**
 * Texto con contorno de cómic (relleno amarillo por defecto): se lee sobre cualquier fondo porque el
 * contorno oscuro da el contraste. Para títulos y cifras grandes, como en la web.
 */
@Composable
fun OutlinedText(text: String, modifier: Modifier = Modifier, size: Int = 40, fill: Color = Rustic.Amarillo, letterSpacing: Int = 0) {
    val base = TextStyle(
        fontFamily = Rustic.Titulos, fontWeight = FontWeight.ExtraBold, fontSize = size.sp, lineHeight = (size + 2).sp,
        letterSpacing = letterSpacing.sp, textAlign = TextAlign.Center,
    )
    val contorno = with(LocalDensity.current) { (size * OUTLINE_PER_SP).dp.toPx() }
    Box(modifier.semantics(mergeDescendants = true) { heading() }, contentAlignment = Alignment.Center) {
        // Sombra desplazada, contorno y relleno: tres capas del mismo texto
        Text(text, style = base.copy(color = Rustic.TintaFija, drawStyle = Stroke(width = contorno)), modifier = Modifier.offset(3.dp, 3.dp).clearAndSetSemantics {})
        Text(text, style = base.copy(color = Rustic.TintaFija, drawStyle = Stroke(width = contorno)), modifier = Modifier.clearAndSetSemantics {})
        Text(text, style = base.copy(color = fill))
    }
}

/** Barra de tiempo con contorno (sin número: el número va en [TimerCircle]). */
@Composable
fun TimeBar(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(50))
            .background(Rustic.Noche2)
            .border(3.dp, Rustic.Tinta, RoundedCornerShape(50)),
    ) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(Rustic.Amarillo))
    }
}

/** Cabecera de turno: "¡Te toca, Ana!" + segundos, y la barra de tiempo debajo. */
@Composable
fun TurnTitle(title: String, secondsLeft: Int, fraction: Float) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Title(title, Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Assertive }, size = 25)
        TimerCircle(secondsLeft)
    }
    TimeBar(fraction)
}

/** Colores de todos los campos de texto (cursor y etiqueta legibles en los dos temas). */
@Composable
fun tebeoFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Rustic.Foco, unfocusedBorderColor = Rustic.Tinta,
    focusedContainerColor = Rustic.Noche2, unfocusedContainerColor = Rustic.Noche2,
    cursorColor = Rustic.Texto, focusedLabelColor = Rustic.AmbarClaro, unfocusedLabelColor = Rustic.TextoSuave,
    focusedTextColor = Rustic.Texto, unfocusedTextColor = Rustic.Texto,
    focusedPlaceholderColor = Rustic.TextoSuave, unfocusedPlaceholderColor = Rustic.TextoSuave,
)

/** Colores de los interruptores: pista amarilla y bolita negra cuando están activados. */
@Composable
fun tebeoSwitchColors() = SwitchDefaults.colors(
    checkedTrackColor = Rustic.Musgo, checkedThumbColor = Rustic.TintaFija, checkedBorderColor = Rustic.Tinta,
    uncheckedTrackColor = Rustic.Chip, uncheckedThumbColor = Rustic.TextoSuave, uncheckedBorderColor = Rustic.Tinta,
)

/** Fila con texto e interruptor: se puede pulsar en toda la fila (y TalkBack la lee como un interruptor). */
@Composable
fun SwitchRow(checked: Boolean, onChange: (Boolean) -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
        Switch(checked, onCheckedChange = null, colors = tebeoSwitchColors())
    }
}

/** Diálogo de confirmación con el estilo Tebeo (botones de cómic, legibles en los dos temas). */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, dismiss: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Title(title, size = 22) },
        shape = RoundedCornerShape(20.dp),
        text = { Text(text) },
        confirmButton = { WoodButton(confirm, onConfirm, tone = Tone.SANGRE, small = true) },
        dismissButton = { WoodButton(dismiss, onDismiss, tone = Tone.AMBAR, small = true) },
        containerColor = Rustic.Noche2,
        titleContentColor = Rustic.Texto,
        textContentColor = Rustic.Texto,
        modifier = Modifier.comicShadow(20.dp, 6.dp, Rustic.Sombra).border(3.dp, Rustic.Tinta, RoundedCornerShape(20.dp)),
    )
}

/**
 * Dos columnas en pantallas anchas (tablet, plegable abierto o apaisado): [left] con la pregunta y la escena,
 * [right] con las acciones. En el móvil en vertical, una sola columna.
 */
@Composable
fun AdaptivePanes(left: @Composable ColumnScope.() -> Unit, right: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= TWO_PANE_MIN_WIDTH) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), content = left)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), content = right)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { left(); right() }
        }
    }
}

private val TWO_PANE_MIN_WIDTH = 680.dp

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
        colors = tebeoFieldColors(),
        shape = RoundedCornerShape(14.dp),
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Rustic.Titulos, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Rustic.Texto),
        modifier = Modifier.fillMaxWidth(),
    )
    if (question.type == QuestionType.FECHA) {
        SwitchRow(draft.beforeChrist, onBeforeChrist) { Text("Antes de Cristo (a. C.)", Modifier.weight(1f), color = Rustic.TextoSuave) }
    }
    Text(
        if (value == null) " " else "= ${question.format(value)}",
        color = Rustic.AmbarClaro, fontFamily = Rustic.Titulos, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 32.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Entrada corta (fundido y 8 dp hacia arriba) cada vez que cambia [key]; sin animación con "quitar animaciones". */
@Composable
fun ScreenEnter(key: Any, content: @Composable () -> Unit) {
    val reduced = reducedMotion()
    val entrada = remember(key) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(key) { entrada.animateTo(1f, tween(SCREEN_ENTER_MS)) }
    Box(Modifier.graphicsLayer { alpha = entrada.value; translationY = (1f - entrada.value) * 8.dp.toPx() }) { content() }
}

private const val SCREEN_ENTER_MS = 220

/** Anillo de foco (teclado, mando o D-pad) en el color de foco del tema, fuera del borde del elemento. */
@Composable
fun Modifier.focusRing(shape: androidx.compose.ui.graphics.Shape): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(3.dp, Rustic.Foco, shape) else Modifier)
}

/** "¡El carro cruzó el puente!" / "¡Al río con la burra!": título de cómic (verde o rojo con contorno), como en la web. */
@Composable
fun ResultTitle(success: Boolean) {
    OutlinedText(
        if (success) "¡El carro cruzó el puente!" else "¡Al río con la burra!", Modifier.fillMaxWidth(), size = 28,
        fill = if (success) Rustic.Musgo else Rustic.Sangre,
    )
}

/** "¡Clavada! +2 para el conductor", con la diana. */
@Composable
fun Bullseye() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_t_diana), contentDescription = null, modifier = Modifier.size(22.dp), colorFilter = ColorFilter.tint(Rustic.AmbarClaro))
        Text("¡Clavada! +2 para el conductor", color = Rustic.AmbarClaro, fontWeight = FontWeight.ExtraBold)
    }
}

/** Anillo de foco por fuera del botón (no cambia su tamaño ni empuja el contenido). */
private fun Modifier.focusOutline(radius: Dp): Modifier = drawBehind {
    val gap = FOCUS_GAP.toPx()
    drawRoundRect(
        Rustic.Foco, topLeft = Offset(-gap, -gap), size = androidx.compose.ui.geometry.Size(size.width + gap * 2, size.height + gap * 2),
        cornerRadius = CornerRadius(radius.toPx() + gap), style = Stroke(width = 3.dp.toPx()),
    )
}

private val FOCUS_GAP = 5.dp
