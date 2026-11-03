package com.paraelcarro.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.paraelcarro.ui.theme.Rustic

/** Avatar ilustrado ("av:gato") o, si es un jugador antiguo, su emoji. */
@Composable
fun Avatar(avatar: String, size: Dp, modifier: Modifier = Modifier, highlight: Boolean = false, background: Boolean = true) {
    val res = AvatarAssets.drawables[avatar]
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .then(if (background) Modifier.background(avatarColor(avatar)).border(if (size >= 40.dp) 2.dp else 1.5.dp, Rustic.Tinta, CircleShape) else Modifier)
            .then(if (highlight) Modifier.border(3.dp, Rustic.Foco, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (res != null) {
            Image(painterResource(res), contentDescription = null, modifier = Modifier.size(size))
        } else if (avatar.isNotEmpty()) {
            // Jugadores antiguos con emoji: tamaño fijo a la caja (no escala con la letra del sistema)
            Text(avatar, fontSize = with(LocalDensity.current) { (size * 0.62f).toSp() }, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

/** Icono ilustrado de las opciones (R.drawable.ic_burra, ic_carreta, ic_bajar, ic_subir). */
@Composable
fun Ico(@androidx.annotation.DrawableRes res: Int, size: Dp = 20.dp) {
    Image(painterResource(res), contentDescription = null, modifier = Modifier.size(size))
}

/** Icono según dónde va el jugador: burra (conductor), carreta (en el carro) o saltando (bajado). */
@androidx.annotation.DrawableRes
fun statusIcon(status: String?): Int = when (status) {
    "conductor" -> com.paraelcarro.R.drawable.ic_burra
    "bajado" -> com.paraelcarro.R.drawable.ic_bajar
    else -> com.paraelcarro.R.drawable.ic_carreta
}

/** Avatar pequeño + nombre, para frases y listas ("🐸 Lucía"). */
@Composable
fun PlayerTag(avatar: String, name: String, modifier: Modifier = Modifier, size: Dp = 22.dp, style: TextStyle = TextStyle.Default, color: Color = Color.Unspecified) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Avatar(avatar, size)
        Text(name, style = style, color = color)
    }
}
