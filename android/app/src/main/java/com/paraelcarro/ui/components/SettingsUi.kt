package com.paraelcarro.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paraelcarro.R
import com.paraelcarro.data.Settings
import com.paraelcarro.data.ThemeMode
import com.paraelcarro.ui.sendFeedback
import com.paraelcarro.ui.theme.Rustic

/** Botones de arriba a la derecha: silencio rápido y ajustes. */
@Composable
fun SettingsBar(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).padding(top = 8.dp, end = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RoundButton(if (Settings.sound) R.drawable.ic_t_sonido else R.drawable.ic_t_silencio, if (Settings.sound) "Silenciar" else "Activar sonido") { Settings.updateSound(!Settings.sound) }
        RoundButton(R.drawable.ic_t_ajustes, "Ajustes", onOpen)
    }
}

@Composable
private fun RoundButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .comicShadow(24.dp, 3.dp, Rustic.Sombra)
            .clip(CircleShape)
            .background(Rustic.Noche2)
            .border(3.dp, Rustic.Tinta, CircleShape)
            .focusRing(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(Rustic.Texto),
        )
    }
}

/** Ajustes: sonido, vibración y tema (oscuro, claro o el del sistema). */
@Composable
fun SettingsDialog(onTest: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { WoodButton("Listo", onDismiss, tone = Tone.AMBAR, small = true) },
        title = { Title("Ajustes", size = 22) },
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.comicShadow(20.dp, 6.dp, Rustic.Sombra).border(3.dp, Rustic.Tinta, RoundedCornerShape(20.dp)),
        containerColor = Rustic.Noche2,
        titleContentColor = Rustic.Texto,
        textContentColor = Rustic.Texto,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SwitchRow(Settings.sound, { Settings.updateSound(it); if (it) onTest() }) { Text("Sonido", Modifier.weight(1f)) }
                SwitchRow(Settings.vibration, { Settings.updateVibration(it) }) { Text("Vibración", Modifier.weight(1f)) }
                SwitchRow(Settings.images, { Settings.updateImages(it) }) {
                    Column(Modifier.weight(1f)) {
                        Text("Ilustraciones")
                        Text("En «¿Sabías que…?», desde Wikipedia (usan datos)", color = Rustic.TextoSuave, fontSize = 12.sp)
                    }
                }
                Text("Tema")
                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(ThemeMode.OSCURO to "Oscuro", ThemeMode.CLARO to "Claro", ThemeMode.SISTEMA to "Sistema").forEach { (mode, label) ->
                        WoodButton(
                            label, { Settings.updateTheme(mode) }, Modifier.weight(1f), small = true,
                            tone = if (Settings.theme == mode) Tone.AMBAR else Tone.FANTASMA,
                            selected = Settings.theme == mode, description = "Tema $label",
                        )
                    }
                }
                WoodButton("Enviar sugerencia o error", { sendFeedback(context) }, Modifier.fillMaxWidth(), small = true, tone = Tone.FANTASMA)
            }
        },
    )
}
