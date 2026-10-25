package com.paraelcarro.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.paraelcarro.BuildConfig

/** Correo del estudio: el alias "+carro" hace que Gmail lo etiquete como Para el Carro. */
const val SUPPORT_EMAIL = "tizaestudiogames+carro@gmail.com"

/** Abre el correo con el asunto y los datos del móvil ya rellenos. */
fun sendFeedback(context: Context) {
    val body = "\n\n—\nv${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE} · ${Build.MODEL}"
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL"))
        .putExtra(Intent.EXTRA_SUBJECT, "[Para el Carro] Sugerencia o error")
        .putExtra(Intent.EXTRA_TEXT, body)
    runCatching { context.startActivity(intent) }
}
