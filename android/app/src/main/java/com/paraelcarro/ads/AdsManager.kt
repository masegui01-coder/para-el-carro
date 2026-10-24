package com.paraelcarro.ads

import com.paraelcarro.analytics.Metrics
import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.paraelcarro.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Anuncios de AdMob (IDs en app/build.gradle.kts; por defecto, los de prueba de Google):
 * - Banner adaptable abajo, solo en pantallas sin temporizador ([com.paraelcarro.ui.components.AdBanner]).
 * - Intersticial cada 2 rondas y antes del podio.
 * Antes de pedir anuncios se gestiona el consentimiento (UMP), obligatorio en la UE.
 */
class AdsManager(private val context: Context) {
    private val started = AtomicBoolean(false)
    private var interstitial: InterstitialAd? = null

    private val _ready = MutableStateFlow(false)
    /** true cuando hay consentimiento y el SDK está iniciado: ya se pueden mostrar banners. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** Llamar desde la Activity al arrancar. */
    fun init(activity: Activity) {
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    if (consent.canRequestAds()) start()
                }
            },
            { if (consent.canRequestAds()) start() },
        )
        if (consent.canRequestAds()) start()
    }

    private fun start() {
        Metrics.setAdConsent(true)
        if (!started.compareAndSet(false, true)) return
        MobileAds.initialize(context) {
            _ready.value = true
            loadInterstitial()
        }
    }

    private fun loadInterstitial() {
        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                }
            },
        )
    }

    /** Muestra el intersticial si hay uno cargado y llama a [onClosed] al cerrarlo; si no hay, sigue sin esperar. */
    fun showInterstitial(activity: Activity, onClosed: () -> Unit) {
        val ad = interstitial ?: run {
            if (started.get()) loadInterstitial()
            onClosed()
            return
        }
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onClosed()
                loadInterstitial()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                onClosed()
                loadInterstitial()
            }
        }
        ad.show(activity)
    }
}
