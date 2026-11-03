package com.paraelcarro.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.paraelcarro.R
import com.paraelcarro.domain.model.AVATARS
import com.paraelcarro.ui.theme.Rustic

/** Color de fondo de cada personaje (de shared/personajes.json). */
fun avatarColor(avatar: String): Color = AvatarAssets.colors[avatar]?.let { Color(it) } ?: Rustic.Chip

private const val MAX_NOMBRE = 14

/**
 * Selector de personaje a pantalla completa: ficha con nombre y frase del personaje elegido, rejilla con los
 * que ya tienen otros jugadores (con su nombre debajo), botón de "al azar" y, si se pasa [name], el campo
 * para el nombre del jugador.
 */
@Composable
fun AvatarPickerDialog(
    current: String,
    taken: Set<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    /** Quién tiene cada personaje ocupado ("av:gato" → "Lucía"). */
    owners: Map<String, String> = emptyMap(),
    /** Nombre del jugador; null si no se edita aquí. */
    name: String? = null,
    onName: (String) -> Unit = {},
) {
    val ocupados = taken + owners.keys
    var elegido by remember { mutableStateOf(current.takeIf { it in AVATARS } ?: AVATARS.firstOrNull { it !in ocupados } ?: current) }
    var nombre by remember { mutableStateOf(name.orEmpty()) }
    val confirmar = {
        if (name != null) onName(nombre.trim())
        onPick(elegido)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Rustic.Noche).safeDrawingPadding().padding(16.dp)) {
            val rejilla: @Composable (Modifier) -> Unit = { mod ->
                LazyVerticalGrid(
                    GridCells.Adaptive(76.dp),
                    mod.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(AVATARS) { a -> Casilla(a, a == elegido, owners[a] ?: if (a in taken) "Ocupado" else null) { elegido = a } }
                }
            }
            val cabecera: @Composable () -> Unit = {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Title("¿Quién eres?", Modifier.weight(1f))
                    WoodButton("Cerrar", onDismiss, small = true, description = "Cerrar sin cambiar")
                }
            }
            val campoNombre: @Composable () -> Unit = {
                if (name != null) {
                    OutlinedTextField(
                        value = nombre,
                        onValueChange = { nombre = it.take(MAX_NOMBRE) },
                        singleLine = true,
                        label = { Text("Tu nombre") },
                        shape = RoundedCornerShape(14.dp),
                        colors = tebeoFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            val botones: @Composable () -> Unit = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WoodButton("Al azar", {
                        AVATARS.filter { it !in ocupados && it != elegido }.randomOrNull()?.let { elegido = it }
                    }, Modifier.weight(1f), description = "Elegir un personaje al azar")
                    WoodButton("¡Me subo al carro!", confirmar, Modifier.weight(1.6f), tone = Tone.AMBAR)
                }
            }
            if (maxWidth > maxHeight && maxHeight < 520.dp) {
                // Apaisado: ficha, nombre y botones a la izquierda; la rejilla a la derecha
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) { cabecera(); Ficha(elegido); campoNombre(); botones() }
                    rejilla(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(
                    Modifier.widthIn(max = 640.dp).fillMaxSize().align(Alignment.TopCenter),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    cabecera(); Ficha(elegido); campoNombre()
                    rejilla(Modifier.weight(1f))
                    botones()
                }
            }
        }
    }
}

/** Ficha del personaje elegido: dibujo sobre su color, nombre y frase. */
@Composable
private fun Ficha(avatar: String) {
    // Sobre el color del personaje, con el nombre ladeado (igual que en la web). Los colores son pasteles:
    // el texto va siempre en tinta oscura.
    Panel(Modifier.widthIn(max = 520.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, color = avatarColor(avatar)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(104.dp).clip(CircleShape).background(Color.White).border(3.dp, Rustic.TintaFija, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Avatar(avatar, 92.dp, background = false) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    AvatarAssets.names[avatar] ?: "", color = Rustic.TintaFija, fontFamily = Rustic.Titulos, fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp, lineHeight = 28.sp, modifier = Modifier.rotate(-2f).semantics { heading() },
                )
                Text("“${AvatarAssets.quotes[avatar].orEmpty()}”", color = Rustic.TintaFija, fontSize = 15.sp, lineHeight = 19.sp)
            }
        }
    }
}

/** Casilla de la rejilla; si [owner] no es null el personaje está ocupado y no se puede elegir. */
@Composable
private fun Casilla(avatar: String, selected: Boolean, owner: String?, onClick: () -> Unit) {
    val free = owner == null
    val shape = RoundedCornerShape(16.dp)
    val nombre = AvatarAssets.names[avatar].orEmpty()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .then(if (selected) Modifier.comicShadow(16.dp, 4.dp, Rustic.Sombra) else Modifier)
                .clip(shape)
                .background(avatarColor(avatar))
                .border(if (selected) 5.dp else 3.dp, if (selected) Rustic.Foco else Rustic.Tinta, shape)
                .alpha(if (free) 1f else .35f)
                .focusRing(shape)
                .clickable(enabled = free, role = Role.RadioButton, onClick = onClick)
                .semantics {
                    this.selected = selected
                    contentDescription = if (free) nombre else "$nombre, lo tiene $owner"
                },
            contentAlignment = Alignment.Center,
        ) {
            Avatar(avatar, 60.dp, background = false)
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).clip(CircleShape)
                        .background(Rustic.Amarillo).border(2.dp, Rustic.TintaFija, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Image(painterResource(R.drawable.ic_t_check), contentDescription = null, modifier = Modifier.size(16.dp), colorFilter = ColorFilter.tint(Rustic.TintaFija)) }
            }
        }
        Text(
            owner ?: nombre, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (free) Rustic.Texto else Rustic.TextoSuave,
        )
    }
}
