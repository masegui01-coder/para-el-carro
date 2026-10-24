package com.paraelcarro.data.online

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Proyecto Firebase "Para el Carro" (el mismo que la web: js/firebase-config.js). Normalmente ya lo inicia google-services.json. */
private const val DATABASE_URL = "https://para-el-carro-91ke5-default-rtdb.europe-west1.firebasedatabase.app"

private val OPTIONS = FirebaseOptions.Builder()
    .setProjectId("para-el-carro-91ke5")
    .setApplicationId("1:757876990914:android:f8f7af1554b0f8d9e1ac5b")
    .setApiKey("AIzaSyCPsNHeUgWRxP5P6cgKQzdr8g0AmLSUSSg")
    .setDatabaseUrl(DATABASE_URL)
    .build()

/** Acceso a rooms/CODIGO en Realtime Database. */
class RoomRepository(context: Context) {
    val db: FirebaseDatabase = run {
        val app = FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context, OPTIONS)
        FirebaseDatabase.getInstance(app, DATABASE_URL)
    }

    fun room(code: String): DatabaseReference = db.getReference("rooms/$code")

    /** Diferencia entre el reloj del servidor y el del móvil (para que todos vean el mismo plazo). */
    fun serverOffset(): Flow<Long> = values(db.getReference(".info/serverTimeOffset")) { (it.value as? Number)?.toLong() ?: 0L }

    fun connected(): Flow<Boolean> = values(db.getReference(".info/connected")) { it.value as? Boolean ?: false }

    fun <T> values(ref: DatabaseReference, map: (DataSnapshot) -> T): Flow<T> = callbackFlow {
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(map(s)) }
            override fun onCancelled(e: DatabaseError) { close(e.toException()) }
        }
        ref.addValueEventListener(l)
        awaitClose { ref.removeEventListener(l) }
    }

    /** Jugadas nuevas (solo las escucha el jefe); cada una se borra después de aplicarla. */
    fun actions(code: String): Flow<DataSnapshot> = callbackFlow {
        val ref = room(code).child("actions")
        val l = object : ChildEventListener {
            override fun onChildAdded(s: DataSnapshot, prev: String?) { trySend(s) }
            override fun onChildChanged(s: DataSnapshot, prev: String?) = Unit
            override fun onChildRemoved(s: DataSnapshot) = Unit
            override fun onChildMoved(s: DataSnapshot, prev: String?) = Unit
            override fun onCancelled(e: DatabaseError) { close(e.toException()) }
        }
        ref.addChildEventListener(l)
        awaitClose { ref.removeEventListener(l) }
    }

    suspend fun read(ref: DatabaseReference): DataSnapshot = ref.get().await()

    /** Crea rooms/CODIGO/meta solo si el código está libre. Devuelve si lo ha conseguido. */
    suspend fun createMeta(code: String, host: String, rounds: Int, mode: String, barbaridades: Boolean): Boolean =
        suspendCancellableCoroutine { cont ->
            room(code).child("meta").runTransaction(object : Transaction.Handler {
                override fun doTransaction(d: MutableData): Transaction.Result {
                    if (d.value != null) return Transaction.abort()
                    d.value = mapOf("host" to host, "created" to ServerValue.TIMESTAMP, "status" to "lobby", "rounds" to rounds, "mode" to mode, "barbaridades" to barbaridades)
                    return Transaction.success(d)
                }

                override fun onComplete(e: DatabaseError?, committed: Boolean, s: DataSnapshot?) {
                    if (e != null) cont.resumeWithException(e.toException()) else cont.resume(committed)
                }
            })
        }

    suspend fun addPlayer(code: String, pid: String, name: String, avatar: String) {
        room(code).child("players/$pid").setValue(
            mapOf("name" to name, "avatar" to avatar, "order" to System.currentTimeMillis(), "online" to true),
        ).await()
    }

    /** Presencia: "online" pasa a false si se cierra la app o se pierde la conexión. */
    fun markOnline(code: String, pid: String) {
        val ref = room(code).child("players/$pid/online")
        ref.onDisconnect().setValue(false)
        ref.setValue(true)
    }

    fun sendAction(code: String, pid: String, a: String, v: Double?, seq: Long) {
        val data = buildMap<String, Any> { put("pid", pid); put("a", a); v?.let { put("v", it) }; put("seq", seq) }
        room(code).child("actions").push().setValue(data)
    }

    fun publish(code: String, state: OnlineState) {
        room(code).child("state").setValue(state.toMap())
    }
}
