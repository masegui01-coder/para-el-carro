package com.paraelcarro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paraelcarro.domain.model.AVATARS
import com.paraelcarro.ui.theme.Rustic

/** Rejilla de avatares; los que ya tiene otro jugador salen desactivados. */
@Composable
fun AvatarPickerDialog(current: String, taken: Set<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Cerrar") } },
        title = { Text("Elige avatar") },
        text = {
            LazyVerticalGrid(GridCells.Fixed(6), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(AVATARS) { a ->
                    val free = a !in taken
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (a == current) Rustic.Ambar.copy(alpha = .3f) else Color.Black.copy(alpha = .3f))
                            .alpha(if (free) 1f else .25f)
                            .clickable(enabled = free) { onPick(a) },
                        contentAlignment = Alignment.Center,
                    ) { Avatar(a, 44.dp, background = false) }
                }
            }
        },
    )
}
