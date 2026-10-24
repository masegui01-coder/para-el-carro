package com.paraelcarro

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Versiones de prueba (las de Drive): proveedor de depuración. Al abrir la app escribe en el logcat
 * un "debug token" que se puede dar de alta en la consola (App Check → Gestionar tokens de depuración).
 */
internal fun appCheckFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
