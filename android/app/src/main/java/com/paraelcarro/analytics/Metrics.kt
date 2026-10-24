package com.paraelcarro.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Estadísticas de uso con Firebase Analytics (mismos nombres de evento que la web, js/game.js):
 * partida_inicio, ronda_fin, partida_fin, sala_crear, sala_unirse, pregunta_valorada.
 * Nunca debe romper el juego: si Analytics falla, el evento simplemente no se envía.
 */
object Metrics {
    private var analytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        analytics = runCatching { FirebaseAnalytics.getInstance(context) }.getOrNull()
    }

    fun event(name: String, vararg params: Pair<String, Any>) {
        val bundle = Bundle().apply {
            params.forEach { (k, v) ->
                when (v) {
                    is Int -> putLong(k, v.toLong())
                    is Long -> putLong(k, v)
                    is Boolean -> putLong(k, if (v) 1L else 0L)
                    else -> putString(k, v.toString())
                }
            }
        }
        runCatching { analytics?.logEvent(name, bundle) }
    }

    /**
     * Modo de consentimiento: por defecto (AndroidManifest) no se usan datos para anuncios;
     * se activa solo cuando el formulario de privacidad de AdMob (UMP) permite pedir anuncios.
     */
    fun setAdConsent(granted: Boolean) {
        val status = if (granted) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED
        runCatching {
            analytics?.setConsent(
                mapOf(
                    FirebaseAnalytics.ConsentType.AD_STORAGE to status,
                    FirebaseAnalytics.ConsentType.AD_USER_DATA to status,
                    FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to status,
                ),
            )
        }
    }
}
