# Subida a Google Play — prueba cerrada

Todo lo que hay en esta carpeta:

- `README.md` — estos pasos, en orden.
- `ficha.md` — nombre, descripciones, notas de versión, categoría y contacto (listos para pegar).
- `formularios.md` — respuestas de «Contenido de la aplicación»: seguridad de los datos, clasificación, público, anuncios.
- `testers.md` — cómo apuntar a los 40 testers, mensaje de invitación y calendario de 14 días.
- `graficos/` — icono 512×512 y gráfico destacado 1024×500.

## 1. Crear la clave de subida (una sola vez, en tu PC)

Lo más fácil: abre `tools\configurar-clave.ps1` en VS Code o PowerShell ISE y pulsa ▶. Crea la clave (o comprueba
la que ya tienes), rellena `gradle.properties` (paso 2) y copia el `.jks` a Google Drive. A mano:

En PowerShell:

```powershell
mkdir $env:USERPROFILE\claves
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
  -keystore $env:USERPROFILE\claves\paraelcarro-upload.jks `
  -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

Guarda el `.jks` y las contraseñas en un gestor de contraseñas y una copia fuera del PC.
Con la firma de apps de Google Play, si la pierdes se puede pedir otra, pero tarda días.

## 2. Configurar `%USERPROFILE%\.gradle\gradle.properties`

```
uploadStoreFile=C:/Users/TU_USUARIO/claves/paraelcarro-upload.jks
uploadStorePassword=...
uploadKeyAlias=upload
uploadKeyPassword=...

admobAppId=ca-app-pub-XXXXXXXX~YYYYYYYY
admobBannerId=ca-app-pub-XXXXXXXX/ZZZZZZZZ
admobInterstitialId=ca-app-pub-XXXXXXXX/WWWWWWWW
```

Usa barras `/` en la ruta. Este fichero no está en el repositorio, así que las claves nunca llegan a GitHub.
Si aún no tienes los IDs de AdMob, la prueba cerrada puede salir con los de prueba de Google
(el script te avisa), pero cámbialos antes de producción.

## 3. Publicar la política de privacidad

```powershell
firebase deploy --only hosting
```

Comprueba que abre `https://paraelcarroyomebajo.com/privacidad.html`.

## 4. Generar el AAB

```powershell
powershell -File tools\publicar-play.ps1
```

Pasa los tests, compila `bundleRelease` firmado con tu clave y deja `ParaElCarro-v0.11.0-14.aab` en la raíz
del proyecto (los `.aab` están en `.gitignore`).
Para cada subida posterior, sube `versionCode` en `android/app/build.gradle.kts`.

## 5. Play Console

1. **Crear aplicación**: nombre «Para el Carro», idioma Español (España), **Juego**, **Gratis**. Acepta las declaraciones.
2. **Panel → Configurar la aplicación**: rellena todo con `formularios.md` y la ficha con `ficha.md` + `graficos/`.
3. **Probar y publicar → Pruebas → Prueba cerrada** → «Crear canal» o usa *Alpha* →
   - **Países**: España (y los de tus testers).
   - **Testers**: el Grupo de Google o la lista de correos (ver `testers.md`).
   - **Crear versión** → acepta la **Firma de apps de Google Play** → sube el `.aab` → pega las notas de versión de `ficha.md`.
4. **Revisar versión → Enviar a revisión** (la primera revisión suele tardar de 1 a 7 días).
5. Cuando esté aprobada, copia el **enlace de participación** y manda las invitaciones.

## 6. Después de la primera subida (huellas SHA-256)

Play vuelve a firmar la app con su propia clave, así que hay que dar su huella a dos sitios.
Cópiala de **Play Console → Probar y publicar → Configuración → Integridad de la aplicación → Firma de apps**
(«Certificado de la clave de firma de apps», SHA-256).

- **`.well-known/assetlinks.json`**: añade esa huella a `sha256_cert_fingerprints` (deja la actual, que es la de
  tus APK de depuración) y `firebase deploy --only hosting`. Si no, los enlaces de invitación
  `?sala=` abrirán el navegador en vez de la app instalada desde Play.
- **Firebase Console → Configuración del proyecto → app Android → Huellas digitales**: añade la misma huella, y en
  **App Check** registra la app con Play Integrity (y vincula el proyecto en Play Console → Integridad de la app).
  Si no, App Check puede bloquear las salas online en la versión de Play.

## 7. Paso a producción

Tras 14 días con al menos 12 testers apuntados (ver `testers.md`): **Panel → Solicitar acceso a producción**.
