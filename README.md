# 🐴🛒 Para el Carro

Juego social *pass-and-play* (3 a 8 jugadores en un solo móvil). Sale una pregunta cuya respuesta es
un **año** o un **número** raro ("¿cuántos litros tiene el lago Ness?"), los jugadores van subiendo la
cifra y el carro intenta cruzar el puente colgante. Si la respuesta real es **igual o mayor** que la cifra
del conductor, cruza; si no… ¡al río!

- **Versión web** (para probar ya): `index.html`, `css/`, `js/` → se publica en GitHub Pages.
- **App Android** (Kotlin + Jetpack Compose + AdMob): carpeta `android/`.
- **Preguntas**: `shared/fechas.json` (158) y `shared/numeros.json` (155).

## Salas privadas online

Cada jugador juega desde su propio móvil:

1. Uno pulsa **🌐 Crear sala privada**: es el **jefe de sala** (elige rondas y tipo de preguntas, y empieza).
2. Invita con **📤 Invitar amigos** (WhatsApp, Telegram… con el menú de compartir del móvil) o pasando el
   código de 5 letras. El enlace tiene la forma `https://masegui01-coder.github.io/para-el-carro/?sala=ABCDE`.
3. Los amigos abren el enlace (o pulsan **🔑 Unirme con un código**), ponen su nombre y eligen avatar.
4. Con 3 o más conectados, el jefe empieza. Si alguien recarga la página, vuelve a entrar como él mismo.

Funciona con **Firebase Realtime Database** (proyecto `para-el-carro-91ke5`, separado de Cifras y Letras):

- `js/online.js` — salas, invitaciones y partida online. El móvil del jefe es el árbitro: aplica las
  jugadas, los plazos de 50 s / 15 s y la puntuación con la misma lógica que el modo de un solo móvil.
  La respuesta no se publica hasta que se resuelve la ronda.
- `firebase/database.rules.json` — reglas de seguridad: no se pueden listar las salas (solo entra quien
  conoce el código) y se valida la forma de los datos. Para desplegarlas: `firebase deploy --only database`.
- Si el jefe cierra la app a mitad de partida, la partida se queda en pausa hasta que vuelva.
- **La app Android usa el mismo Firebase y el mismo formato**: en una sala pueden jugar a la vez
  móviles con la app y navegadores (cualquiera puede ser el jefe). En la app, el enlace de invitación
  abre directamente la pantalla de unirse con el código puesto (`data/online/`, `ui/online/`).

## Reglas

1. El jugador que abre tiene **50 s** para decir una cifra y se monta en la burra (conductor).
2. Por turnos, cada uno tiene **15 s** para: ⬆️ **subir la cifra** (pasa a conductor), 🛒 **quedarse en el carro**
   o 🚶 **bajarse** (ya no puede volver a subir ni pujar en esa ronda). Si se acaba el tiempo, se queda.
3. Se dan vueltas hasta que nadie sube. El jefe de sala (jugador 1) elige el número de rondas y el tipo de preguntas.

| | Cruza | Al río |
|---|---|---|
| 🐴 Conductor | +3 +1 por pasajero (+2 si es ¡Clavada!) | −3 −1 por cada bajado |
| 🛒 En el carro | +3 | −2 |
| 🚶 Bajados | 0 | +3 (2 + 1 que paga el conductor) |

¡Clavada! = la cifra está a menos de un 10 % por debajo de la respuesta (o a 5 años en las fechas).

## Probar la web en local

Abre `index.html` con doble clic, o con un servidor:

```bash
python -m http.server 8765
```

## Añadir o cambiar preguntas

Edita `shared/fechas.json` o `shared/numeros.json` (`q` pregunta, `a` respuesta, `u` unidad, `n` curiosidad;
años antes de Cristo en negativo) y regenera los ficheros de la web y de Android:

```bash
node tools/build-preguntas.js
```

## App Android

Abre la carpeta `android/` en Android Studio. Tests y APK de depuración:

```bash
cd android && ./gradlew testDebugUnitTest assembleDebug
```

Arquitectura (MVVM + capas):

- `domain/` — modelos (`Player`, `Question`, `RoundState`, `RoundResult`, `GameState`, `GamePhase`) y `GameEngine` (reglas puras, con tests).
- `data/` — `QuestionRepository` (lee `assets/preguntas.json`, no repite preguntas) y `LobbyStore` (guarda la sala).
- `ui/GameViewModel.kt` — máquina de estados con `StateFlow`: Inicio → Sala → (Pasa el móvil → Apertura → Turnos)… → Puente → Puntos → Podio.
- `ui/screens/BridgeCrossingScreen.kt` — animación en Compose Canvas (`Walking`, `Suspense`, `CrossedSuccessfully`, `FellIntoRiver`) con vibración.

### Monetización (AdMob)

- **Banner** adaptable abajo, solo en pantallas sin temporizador (portada, sala, puntos, podio).
- **Intersticial** cada 2 rondas y antes del podio.
- Consentimiento RGPD con **UMP** (obligatorio en la UE).

Ahora mismo usa los **IDs de prueba de Google**. Para cobrar:

1. Crea la app en [AdMob](https://admob.google.com) y dos bloques de anuncios (banner e intersticial).
2. Pon tus IDs en `%USERPROFILE%\.gradle\gradle.properties` (así no se suben a GitHub):
   ```
   admobAppId=ca-app-pub-XXXXXXXX~YYYYYYYY
   admobBannerId=ca-app-pub-XXXXXXXX/ZZZZZZZZ
   admobInterstitialId=ca-app-pub-XXXXXXXX/WWWWWWWW
   ```
3. Configura el mensaje de consentimiento RGPD en AdMob → *Privacidad y mensajes*.
4. Antes de subir a Google Play, crea tu clave de firma (en `app/build.gradle.kts` el release se firma con la de depuración).

En la web, el hueco `#ad-banner` y el intersticial simulado de `js/game.js` son donde irían los anuncios de
**Google AdSense** (requiere dominio propio aprobado; GitHub Pages no suele aceptarse).
