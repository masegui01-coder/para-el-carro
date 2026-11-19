# Prueba cerrada con 40 testers

## Requisito de Google para cuentas personales

Si tu cuenta de desarrollador es **personal** y se creó después del 13-nov-2023, para pedir el acceso a
producción necesitas una prueba cerrada con **al menos 12 testers apuntados durante 14 días seguidos**.
Con 40 vas sobrado, pero cuenta solo quien **acepta la invitación** (opt-in), no a quien invitas:

- Apunta a los 40, porque siempre hay quien no lo hace o se sale.
- No quites testers de la lista ni pauses la prueba durante esos 14 días.
- Pide que abran la app varias veces y manden comentarios: al pedir producción, Google pregunta cómo fue la prueba.

## Cómo añadirlos

**Opción recomendada: un Grupo de Google** (`para-el-carro-testers@googlegroups.com`, por ejemplo).
Añades o quitas gente sin tocar Play Console.

1. Crea el grupo en groups.google.com → «Quién puede unirse»: *Solo invitados* (o «Cualquiera puede pedir unirse»).
2. Invita los 40 correos de **cuentas de Google** (las que usan en su móvil Android).
3. Play Console → Probar y publicar → Pruebas → **Prueba cerrada** → Testers → añade el correo del grupo.
4. Copia el **enlace de participación** (sale en esa misma pestaña tras publicar la versión) y envíalo con el mensaje de abajo.

Alternativa: una lista de correos en Play Console (pegas los 40 separados por comas).

Los testers necesitan Android 8.0 o superior (minSdk 26).

## Mensaje de invitación (WhatsApp / correo)

```
¡Hola! Estoy a punto de sacar «Para el Carro», un juego de preguntas para jugar en grupo 🐴🛒
Necesito gente que lo pruebe en Android antes de publicarlo. Son 2 minutos:

1️⃣ Acepta la invitación al grupo de Google que te ha llegado (o entra aquí: ENLACE_GRUPO)
2️⃣ Abre este enlace con la misma cuenta de Google que usas en el móvil y pulsa «Convertirse en tester»:
ENLACE_PARTICIPACIÓN
3️⃣ Instala la app desde Google Play (tarda unos minutos en aparecer)

🙏 Lo importante: no te salgas de la prueba ni desinstales la app en 2 semanas (Google lo exige para publicarla).
Juega alguna partida cuando puedas y cuéntame fallos o ideas desde ⚙️ Ajustes → «Enviar sugerencia o error».

¡Gracias! 🙌
```

## Calendario de los 14 días

| Día | Qué hacer |
|---|---|
| 0 | Publicar la versión en prueba cerrada y mandar invitaciones |
| 1-2 | Comprobar en Play Console cuántos han aceptado; recordar a quien no |
| 3 | Organizar una partida online con 5-8 testers (prueba de salas) |
| 7 | Recordatorio: «¿Has encontrado algo raro?». Revisar Crashlytics y subir corrección si hace falta (versionCode +1) |
| 10 | Segunda partida en grupo |
| 14+ | Play Console → Panel → **Solicitar acceso a producción** y responder el cuestionario |

## Qué pedir que prueben

- Partida en un solo móvil con 3 jugadores, de principio a fin.
- Crear sala online, invitar por WhatsApp y unirse desde otro móvil (app y navegador).
- Salir y volver a la app a mitad de partida (llamada, cambiar de app).
- Tema claro/oscuro, sonido y vibración.
- Que salgan los anuncios y no tapen botones.
- En la UE: Ajustes → «Privacidad y anuncios» abre el formulario de consentimiento.
