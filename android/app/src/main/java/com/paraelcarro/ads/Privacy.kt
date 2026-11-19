package com.paraelcarro.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform

/** Política de privacidad publicada (privacidad.html en Firebase Hosting); es la misma URL que se da en Google Play. */
const val PRIVACY_URL = "https://paraelcarroyomebajo.com/privacidad.html"

/**
 * Opciones de privacidad para cambiar el consentimiento de anuncios (UMP). AdMob exige ofrecerlas
 * cuando el usuario está en la UE, Reino Unido o Suiza; fuera de ahí el botón no aparece.
 */
object Privacy {
    fun optionsRequired(context: Context): Boolean = runCatching {
        UserMessagingPlatform.getConsentInformation(context).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }.getOrDefault(false)

    fun showOptions(context: Context) {
        val activity = context.findActivity() ?: return
        runCatching { UserMessagingPlatform.showPrivacyOptionsForm(activity) { } }
    }

    fun openPolicy(context: Context) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))) }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
