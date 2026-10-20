# WorkManager (lo trae play-services-ads) crea su base de datos Room por reflexión:
# sin esto R8 elimina el constructor y la app se cierra al arrancar.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }

# Seguridad: en release se eliminan los registros de depuración (no filtran datos al logcat).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}
