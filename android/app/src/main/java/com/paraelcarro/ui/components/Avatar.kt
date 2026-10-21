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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paraelcarro.ui.theme.Rustic

/** Avatar ilustrado ("av:gato") o, si es un jugador antiguo, su emoji. */
@Composable
fun Avatar(avatar: String, size: Dp, modifier: Modifier = Modifier, highlight: Boolean = false, background: Boolean = true) {
    val res = AvatarAssets.drawables[avatar]
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .then(if (background) Modifier.background(Color(0xFF2A2F3A)) else Modifier)
            .then(if (highlight) Modifier.border(2.dp, Rustic.AmbarClaro, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (res != null) {
            Image(painterResource(res), contentDescription = AvatarAssets.names[avatar], modifier = Modifier.size(size))
        } else {
            Text(avatar.ifEmpty { "🙂" }, fontSize = (size.value * 0.7f).sp)
        }
    }
}

/** Avatar pequeño + nombre, para frases y listas ("🐸 Lucía"). */
@Composable
fun PlayerTag(avatar: String, name: String, modifier: Modifier = Modifier, size: Dp = 22.dp, style: TextStyle = TextStyle.Default, color: Color = Color.Unspecified) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Avatar(avatar, size)
        Text(name, style = style, color = color)
    }
}
