package com.paraelcarro.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.paraelcarro.ui.theme.Rustic

/** Opción del jefe de sala: −1 a quien diga un disparate. Sin [onChange] se muestra solo el valor. */
@Composable
fun BarbaridadesSwitch(on: Boolean, onChange: ((Boolean) -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("😱 Modo barbaridades")
            Text("−1 a quien diga un disparate (10 veces más o menos, o más de 100 años)", color = Rustic.TextoSuave, fontSize = 13.sp)
        }
        if (onChange != null) {
            Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Rustic.Ambar, checkedThumbColor = Rustic.MaderaOscura))
        } else {
            Text(if (on) "Sí" else "No", color = Rustic.AmbarClaro)
        }
    }
}
