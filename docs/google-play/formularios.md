# Respuestas para los formularios de Play Console

Play Console → **Política y programas → Contenido de la aplicación**. Hay que completarlos todos antes de
enviar la primera versión a revisión, también para la prueba cerrada.

## Política de privacidad

```
https://paraelcarroyomebajo.com/privacidad.html
```
Antes de pegarla, publícala: `firebase deploy --only hosting` y comprueba que abre en el navegador.

## Acceso a la aplicación

**Todas las funciones están disponibles sin restricciones de acceso** (no hay inicio de sesión).

## Anuncios

**Sí, mi aplicación contiene anuncios** (AdMob: banner e intersticial).

## Clasificación del contenido (cuestionario IARC)

- Correo de contacto: `tizaestudiogames+carro@gmail.com`
- Categoría: **Juego**
- Violencia, sangre, terror, sexo, lenguaje soez, drogas, alcohol, tabaco: **No** en todo.
- Apuestas simuladas o juegos de azar: **No** (se apuestan cifras para ganar puntos, sin dinero real ni fichas de casino).
- ¿Los usuarios pueden interactuar o intercambiar contenido? **Sí**: en las salas online se ven los apodos que
  eligen los demás jugadores (hasta 14 caracteres). No hay chat.
- ¿Comparte la ubicación? **No**. ¿Compras digitales? **No**.
- Resultado esperado: PEGI 3 / Todos, con la nota «Interacción de usuarios».

## Público objetivo y contenido

- Grupos de edad: **16-17 y 18 o más** (recomendado).
  Si incluyes menores de 13 años, la app entra en la política de Familias y AdMob debe servir solo anuncios aptos
  para niños; no merece la pena para un juego de sobremesa.
- ¿Podría atraer a niños sin querer? **No**.

## Seguridad de los datos

Respuestas generales:

| Pregunta | Respuesta |
|---|---|
| ¿Recoge o comparte alguno de los tipos de datos obligatorios? | **Sí** |
| ¿Todos los datos se cifran en tránsito? | **Sí** |
| ¿Ofreces una forma de solicitar que se eliminen los datos? | **Sí**, por correo (`tizaestudiogames+carro@gmail.com`); no hay cuentas |

Tipos de datos (marca solo estos):

| Tipo | Recogido | Compartido | Obligatorio | Finalidades |
|---|---|---|---|---|
| Información personal → **Nombre** (apodo en salas online) | Sí | No | Opcional (solo salas online) | Funcionalidad de la app |
| Actividad en la app → **Interacciones con la app** | Sí | No | Obligatorio | Analíticas |
| Información y rendimiento de la app → **Registros de fallos** | Sí | No | Obligatorio | Analíticas |
| Información y rendimiento de la app → **Diagnósticos** | Sí | No | Obligatorio | Analíticas |
| Dispositivo u otros identificadores → **ID de dispositivo u otros identificadores** | Sí | **Sí** (AdMob) | Obligatorio | Publicidad o marketing, Analíticas, Prevención de fraudes y seguridad |

¿Los datos se tratan de forma efímera? **No** en todos. Las valoraciones de preguntas son contadores anónimos y no hay
que declararlas.

> «Compartido» se refiere a enviar datos a terceros; Google trata como no compartido lo que procesa un proveedor por
> cuenta tuya (Firebase), pero el ID de publicidad que usa AdMob para personalizar anuncios sí cuenta como compartido.

## ID de publicidad

**Sí, la app usa el ID de publicidad** → finalidades: **Publicidad o marketing** y **Analíticas**.
(El SDK de AdMob añade el permiso `com.google.android.gms.permission.AD_ID` al compilar.)

## Otras declaraciones

- Aplicación de noticias: **No**.
- Aplicaciones gubernamentales: **No**.
- Funciones financieras: **Ninguna**.
- Aplicación de salud: **No**.
- Ley de Servicios Digitales (UE): indica si eres **comerciante** (vendes o tienes anuncios con ánimo de lucro: normalmente **Sí**).
  Google mostrará en la ficha tu dirección y teléfono de contacto; decide esto con calma porque es información pública.
