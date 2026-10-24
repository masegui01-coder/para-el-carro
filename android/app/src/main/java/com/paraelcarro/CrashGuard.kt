package com.paraelcarro

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.Process
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.paraelcarro.analytics.Metrics
import com.paraelcarro.data.Settings
import kotlin.system.exitProcess

class ParaElCarroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        // Firebase (google-services.json): App Check protege la base de datos; Analytics y Crashlytics
        runCatching {
            FirebaseApp.initializeApp(this)
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckFactory())
        }.onFailure { Log.e("ParaElCarro", "No se pudo iniciar Firebase", it) }
        Metrics.init(this)
        CrashGuard.install(this)
    }
}

/**
 * Control de fallos. Los errores típicos (lo que escribe el jugador, datos raros de una sala…) se
 * validan antes de que ocurran. Si aun así algo falla:
 *  - en un hilo secundario: se registra y el juego sigue;
 *  - en el hilo principal: en vez del aviso de "la app se ha detenido", el juego se vuelve a abrir
 *    al momento en el inicio y avisa al jugador. Si vuelve a fallar nada más abrirse, se deja cerrar
 *    para no entrar en un bucle.
 */
object CrashGuard {
    private const val TAG = "ParaElCarro"
    private const val PREFS = "fallos"
    private const val CRASHLYTICS_FLUSH_MS = 800L
    private val startedAt = System.currentTimeMillis()

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            Log.e(TAG, "Error no controlado en ${thread.name}", e)
            // Crashlytics lo recibe aunque el juego se recupere (si no, no veríamos estos fallos)
            runCatching { FirebaseCrashlytics.getInstance().recordException(e) }
            if (thread != Looper.getMainLooper().thread) return@setDefaultUncaughtExceptionHandler // el juego sigue
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val recentRestart = System.currentTimeMillis() - startedAt < 5000 && prefs.getBoolean("reabierto", false)
            if (recentRestart) {
                prefs.edit().putBoolean("reabierto", false).commit()
                previous?.uncaughtException(thread, e) // falla nada más abrir: cerrar de verdad
                return@setDefaultUncaughtExceptionHandler
            }
            prefs.edit().putBoolean("reabierto", true).commit()
            Thread.sleep(CRASHLYTICS_FLUSH_MS) // da tiempo a Crashlytics a guardar el informe antes de reabrir
            runCatching {
                app.startActivity(
                    Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                )
            }
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    /** true (una sola vez) si el juego se acaba de reabrir tras un fallo. */
    fun consumeRestarted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val restarted = prefs.getBoolean("reabierto", false)
        if (restarted) {
            // Se borra pasados unos segundos: si falla otra vez enseguida, install() lo verá
            android.os.Handler(Looper.getMainLooper()).postDelayed({ prefs.edit().putBoolean("reabierto", false).apply() }, 6000)
        }
        return restarted
    }
}
