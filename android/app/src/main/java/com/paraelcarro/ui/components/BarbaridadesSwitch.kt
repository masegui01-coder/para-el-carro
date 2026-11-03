package com.paraelcarro.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.paraelcarro.ui.theme.Rustic

/** Opción del jefe de sala: −1 a quien diga un disparate. Sin [onChange] se muestra solo el valor. */
@Composable
fun BarbaridadesSwitch(on: Boolean, onChange: ((Boolean) -> Unit)?) {
    val texto: @Composable RowScope.() -> Unit = {
        Column(Modifier.weight(1f)) {
            Text("Modo barbaridades", fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold)
            Text("−1 a quien diga un disparate (10 veces más o menos, o más de 100 años)", color = Rustic.TextoSuave, fontSize = 13.sp)
        }
    }
    if (onChange != null) {
        SwitchRow(on, onChange, texto)
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            texto()
            Text(if (on) "Sí" else "No", color = Rustic.AmbarClaro, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold)
        }
    }
}
