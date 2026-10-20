package com.paraelcarro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.paraelcarro.BuildConfig

/**
 * Banner adaptable fijo abajo. Solo se muestra en pantallas sin temporizador y con un margen
 * arriba para no quedar pegado a ningún botón (política de AdMob contra clics accidentales).
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xFF0B0C10))
            .padding(top = 8.dp)
            .navigationBarsPadding(),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val widthDp = maxWidth.value.toInt()
            val adSize = remember(widthDp) { AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp) }
            val adView = remember(adSize) {
                AdView(context).apply {
                    setAdSize(adSize)
                    adUnitId = BuildConfig.ADMOB_BANNER_ID
                    loadAd(AdRequest.Builder().build())
                }
            }
            DisposableEffect(adView) { onDispose { adView.destroy() } }
            Box(Modifier.fillMaxWidth().height(adSize.height.dp), contentAlignment = Alignment.Center) {
                AndroidView(factory = { adView })
            }
        }
    }
}
