package com.paraelcarro.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paraelcarro.data.Settings
import com.paraelcarro.data.ThemeMode
import com.paraelcarro.ui.sendFeedback
import com.paraelcarro.ui.theme.Rustic

/** Botones de arriba a la derecha: silencio rápido y ajustes. */
@Composable
fun SettingsBar(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.statusBarsPadding().padding(top = 8.dp, end = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RoundButton(if (Settings.sound) "🔊" else "🔇", if (Settings.sound) "Silenciar" else "Activar sonido") { Settings.updateSound(!Settings.sound) }
        RoundButton("⚙️", "Ajustes", onOpen)
    }
}

@Composable
private fun RoundButton(emoji: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Rustic.Noche2)
            .border(1.dp, Rustic.TextoSuave.copy(alpha = .35f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = 20.sp) }
}

/** Ajustes: sonido, vibración y tema (oscuro, claro o el del sistema). */
@Composable
fun SettingsDialog(onTest: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val switchColors = SwitchDefaults.colors(checkedTrackColor = Rustic.Ambar, checkedThumbColor = Rustic.MaderaOscura)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Listo") } },
        title = { Text("Ajustes") },
        containerColor = Rustic.Noche2,
        titleContentColor = Rustic.Texto,
        textContentColor = Rustic.Texto,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔊 Sonido", Modifier.weight(1f))
                    Switch(Settings.sound, { Settings.updateSound(it); if (it) onTest() }, colors = switchColors)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📳 Vibración", Modifier.weight(1f))
                    Switch(Settings.vibration, { Settings.updateVibration(it) }, colors = switchColors)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("🖼️ Ilustraciones")
                        Text("En «¿Sabías que…?», desde Wikipedia (usan datos)", color = Rustic.TextoSuave, fontSize = 12.sp)
                    }
                    Switch(Settings.images, { Settings.updateImages(it) }, colors = switchColors)
                }
                Text("🌗 Tema")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(ThemeMode.OSCURO to "🌙 Oscuro", ThemeMode.CLARO to "☀️ Claro", ThemeMode.SISTEMA to "📱 Sistema").forEach { (mode, label) ->
                        WoodButton(label, { Settings.updateTheme(mode) }, Modifier.weight(1f), small = true, tone = if (Settings.theme == mode) Tone.AMBAR else Tone.FANTASMA)
                    }
                }
                WoodButton("✉️ Enviar sugerencia o error", { sendFeedback(context) }, Modifier.fillMaxWidth(), small = true, tone = Tone.FANTASMA)
            }
        },
    )
}
