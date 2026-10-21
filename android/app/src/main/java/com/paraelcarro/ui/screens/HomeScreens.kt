package com.paraelcarro.ui.screens

import com.paraelcarro.ui.components.Ico
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.paraelcarro.BuildConfig
import com.paraelcarro.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paraelcarro.domain.model.OPEN_SECONDS
import com.paraelcarro.domain.model.TURN_SECONDS
import com.paraelcarro.ui.components.Panel
import com.paraelcarro.ui.components.Title
import com.paraelcarro.ui.components.Tone
import com.paraelcarro.ui.components.WoodButton
import com.paraelcarro.ui.theme.Rustic

@Composable
fun HomeScreen(onCreateRoom: () -> Unit, onJoinRoom: () -> Unit, onPlay: () -> Unit, onRules: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painterResource(R.drawable.portada),
            contentDescription = "Un campesino salta del carro mientras el caballo frena al borde del barranco",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1376f / 768f)
                .clip(RoundedCornerShape(14.dp))
                .border(2.dp, Rustic.MaderaClara, RoundedCornerShape(14.dp)),
        )
        // Solo en la versión de pruebas: 7 toques en el título fuerzan un error para comprobar CrashGuard
        var taps by remember { mutableIntStateOf(0) }
        Text(
            "Para el Carro", color = Rustic.Ambar, fontSize = 38.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
            modifier = if (BuildConfig.DEBUG) Modifier.clickable(indication = null, interactionSource = null) {
                if (++taps >= 7) { taps = 0; error("Fallo de prueba forzado") }
            } else Modifier,
        )
        Text(
            "Sube la cifra, carga el carro… y reza para que el puente aguante.",
            color = Rustic.TextoSuave, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        WoodButton("🌐 Crear sala privada", onCreateRoom, Modifier.fillMaxWidth(), tone = Tone.AMBAR)
        WoodButton("🔑 Unirme con un código", onJoinRoom, Modifier.fillMaxWidth(), tone = Tone.MUSGO)
        WoodButton("📱 Jugar en un solo móvil", onPlay, Modifier.fillMaxWidth())
        WoodButton("Cómo se juega", onRules, Modifier.fillMaxWidth(), tone = Tone.FANTASMA)
    }
}

@Composable
fun RulesScreen(onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title("Cómo se juega")
        Panel {
            Text("1. Sale una pregunta cuya respuesta es un año o un número.")
            Text("2. El primer jugador tiene $OPEN_SECONDS s para decir una cifra. Se monta en la burra: es el conductor.")
            Text("3. Por turnos, cada jugador tiene $TURN_SECONDS s para:")
            RuleOption(R.drawable.ic_subir, "Subir la cifra (pasa a conductor y el anterior se sube al carro)")
            RuleOption(R.drawable.ic_carreta, "Quedarse en el carro")
            RuleOption(R.drawable.ic_bajar, "Bajarse (ya no puede volver ni pujar en la ronda)")
            Text("4. Se dan vueltas hasta que nadie sube la cifra.")
            Text("5. El carro cruza si la respuesta real es IGUAL O MAYOR que la cifra del conductor. Si no… ¡al río!")
        }
        Title("Puntos")
        Panel {
            RuleRow("", "Cruza", "Al río")
            RuleRow("Conductor", "+3 +1/pasajero", "−3 −1/bajado", R.drawable.ic_burra)
            RuleRow("En el carro", "+3", "−2", R.drawable.ic_carreta)
            RuleRow("Bajados", "0", "+3", R.drawable.ic_bajar)
            Text("🎯 ¡Clavada! +2 al conductor si cruza y su cifra está a menos del 10 % (o a 5 años) de la respuesta.", color = Rustic.TextoSuave)
        }
        WoodButton("Volver", onBack, Modifier.fillMaxWidth())
    }
}

@Composable
private fun RuleRow(who: String, ok: String, ko: String, icon: Int? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1.2f), verticalAlignment = Alignment.CenterVertically) { if (icon != null) { Ico(icon, 24.dp); Text(" ") }; Text(who) }
        Text(ok, Modifier.weight(1f), color = Rustic.Verde, textAlign = TextAlign.End)
        Text(ko, Modifier.weight(1f), color = Rustic.Rojo, textAlign = TextAlign.End)
    }
}

@Composable
private fun RuleOption(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
        Ico(icon, 28.dp)
        Text("  $text")
    }
}
