package com.paraelcarro

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** Versión de tienda: Play Integrity comprueba que la app es la auténtica instalada desde Google Play. */
internal fun appCheckFactory(): AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance()
