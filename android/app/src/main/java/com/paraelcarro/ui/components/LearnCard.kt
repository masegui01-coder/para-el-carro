package com.paraelcarro.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paraelcarro.data.Learn
import com.paraelcarro.data.LearnRepository
import com.paraelcarro.data.Settings
import com.paraelcarro.ui.theme.Rustic

/** "¿Sabías que…?" tras la ronda, con la ilustración de Wikipedia si está activada en Ajustes. */
@Composable
fun LearnCard(questionId: String) {
    val context = LocalContext.current
    var learn by remember(questionId) { mutableStateOf<Learn?>(null) }
    var image by remember(questionId) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(questionId) { learn = LearnRepository.get(context, questionId) }
    val l = learn ?: return
    LaunchedEffect(l.article, Settings.images) {
        image = if (Settings.images) LearnRepository.thumbnail(l.article) else null
    }
    // Franja ámbar a la izquierda, como en la web
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Rustic.Ambar)
            .padding(start = 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().background(Rustic.Noche2).padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("📚 ¿Sabías que…?", color = Rustic.AmbarClaro, fontWeight = FontWeight.ExtraBold)
                Text(l.text, color = Rustic.Texto, lineHeight = 20.sp)
            }
            image?.let { bmp ->
                Column(Modifier.width(110.dp)) {
                    Image(
                        bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    Text("Imagen: Wikipedia", color = Rustic.TextoSuave, fontSize = 10.sp)
                }
            }
        }
    }
}
