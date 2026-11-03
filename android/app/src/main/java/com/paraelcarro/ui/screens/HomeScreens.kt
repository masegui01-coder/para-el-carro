package com.paraelcarro.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paraelcarro.BuildConfig
import com.paraelcarro.R
import com.paraelcarro.domain.model.OPEN_SECONDS
import com.paraelcarro.domain.model.TURN_SECONDS
import com.paraelcarro.ui.components.Ico
import com.paraelcarro.ui.components.OutlinedText
import com.paraelcarro.ui.components.Panel
import com.paraelcarro.ui.components.Title
import com.paraelcarro.ui.components.Tone
import com.paraelcarro.ui.components.WoodButton
import com.paraelcarro.ui.components.comicShadow
import com.paraelcarro.ui.components.tebeoFieldColors
import com.paraelcarro.ui.theme.Rustic

@Composable
fun HomeScreen(onCreateRoom: () -> Unit, onJoinRoom: (String) -> Unit, onPlay: () -> Unit, onRules: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Banda de cielo con la ilustración ladeada, como una viñeta de tebeo
        Box(
            Modifier
                .fillMaxWidth()
                .padding(end = 6.dp, bottom = 6.dp)
                .comicShadow(22.dp, 6.dp, Rustic.Sombra)
                .clip(RoundedCornerShape(22.dp))
                .background(Rustic.Cielo)
                .border(3.dp, Rustic.Tinta, RoundedCornerShape(22.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(R.drawable.portada),
                contentDescription = "Un campesino salta del carro mientras el caballo frena al borde del barranco",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1376f / 768f)
                    .rotate(-2.5f)
                    .clip(RoundedCornerShape(14.dp))
                    .border(3.dp, Rustic.Tinta, RoundedCornerShape(14.dp)),
            )
        }
        // Solo en la versión de pruebas: 7 toques en el título fuerzan un error para comprobar CrashGuard
        var taps by remember { mutableIntStateOf(0) }
        OutlinedText(
            "¡Para el Carro!", size = 40,
            modifier = Modifier.then(
                if (BuildConfig.DEBUG) Modifier.clickable(indication = null, interactionSource = null) {
                    if (++taps >= 7) { taps = 0; error("Fallo de prueba forzado") }
                } else Modifier,
            ),
        )
        Text(
            "Sube la cifra, carga el carro… y reza para que el puente aguante.",
            color = Rustic.TextoSuave, textAlign = TextAlign.Center,
        )
        WoodButton("Crear sala con amigos", onCreateRoom, Modifier.fillMaxWidth(), tone = Tone.AMBAR, big = true, icon = R.drawable.ic_t_amigos)
        Panel {
            Text("¿Te han invitado? Escribe el código", fontWeight = FontWeight.ExtraBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { v -> code = v.uppercase().filter { it.isLetterOrDigit() }.take(CODE_LENGTH) },
                    singleLine = true,
                    placeholder = { Text("ABCDE") },
                    textStyle = TextStyle(fontFamily = Rustic.Titulos, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = 4.sp, color = Rustic.Texto),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onJoinRoom(code) }),
                    shape = RoundedCornerShape(14.dp),
                    colors = tebeoFieldColors(),
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Código de la sala" },
                )
                WoodButton("¡Entrar!", { onJoinRoom(code) }, tone = Tone.AZUL)
            }
        }
        WoodButton("Un solo móvil para todos", onPlay, Modifier.fillMaxWidth(), icon = R.drawable.ic_carreta)
        WoodButton("¿Cómo se juega?", onRules, Modifier.fillMaxWidth(), tone = Tone.FANTASMA, small = true, icon = R.drawable.ic_t_libro)
    }
}

private const val CODE_LENGTH = 5

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
            Text("4. Para subir hay que subir de verdad: en los números, al menos un 10 % (en las fechas, un año). Cada jugador puede subir como mucho ${com.paraelcarro.domain.GameEngine.MAX_SUBIDAS} veces por ronda.")
            Text("5. Se dan vueltas hasta que nadie sube la cifra.")
            Text("6. El carro cruza si la respuesta real es IGUAL O MAYOR que la cifra del conductor. Si no… ¡al río!")
        }
        Title("Puntos")
        Panel {
            RuleRow("", "Cruza", "Al río")
            RuleRow("Conductor", "+3 +1/pasajero", "−3 −1/bajado", R.drawable.ic_burra)
            RuleRow("En el carro", "+3", "−2", R.drawable.ic_carreta)
            RuleRow("Bajados", "0", "+3", R.drawable.ic_bajar)
            Text("¡Clavada! +2 al conductor si cruza y su cifra está a menos del 10 % (o a 5 años) de la respuesta.", color = Rustic.TextoSuave)
        }
        WoodButton("Volver", onBack, Modifier.fillMaxWidth())
    }
}

@Composable
private fun RuleRow(who: String, ok: String, ko: String, icon: Int? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1.2f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { if (icon != null) Ico(icon, 24.dp); Text(who) }
        Text(ok, Modifier.weight(1f), color = Rustic.Verde, textAlign = TextAlign.End)
        Text(ko, Modifier.weight(1f), color = Rustic.Rojo, textAlign = TextAlign.End)
    }
}

@Composable
private fun RuleOption(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 12.dp)) {
        Ico(icon, 28.dp)
        Text(text)
    }
}
