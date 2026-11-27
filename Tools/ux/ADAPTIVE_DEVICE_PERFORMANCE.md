# Comunidad Telebots Android — diseño UX: rendimiento por dispositivo (MVP)

**Objetivo.** Dejar que una persona adapte los efectos y animaciones de la aplicación a la capacidad de su dispositivo sin cambiar funciones de mensajes, llamadas ni seguridad.

## Pantalla antes de implementación

Ruta: **Ajustes → Ahorro de energía → Rendimiento por dispositivo**.

```text
‹  Rendimiento por dispositivo
   Tu dispositivo
   Samsung / [modelo detectado]      [estimación: medio]
   Android 17 · RAM limitada detectada cuando corresponda
   Elige cómo se adapta Comunidad Telebots

   ◉ Automático (recomendado)
     Usa la capacidad real del dispositivo
   ○ Básico · menos efectos
     Para móviles antiguos o con poca memoria
   ○ Equilibrado · recomendado
     Animaciones suaves y consumo moderado
   ○ Potente · más efectos
     Solo si el hardware lo admite

   [Aplicar perfil]

   Información
   Modifica efectos visuales de Telegram LiteMode.
   El ahorro por batería sigue activo. Cambia el modo cuando quieras.
```

## Casos de uso/hipótesis (no resultados de usuarios)

- Quien tiene un móvil modesto quiere fluidez y menor consumo: **Básico** desactiva funciones visuales costosas.
- Quien no quiere configurar nada escoge **Automático**: clasificación existente de Telegram y límites de RAM.
- Quien tiene un móvil potente puede probar **Potente**; nunca se ignoran restricciones de memoria ni batería.
- Si una persona ya personalizó efectos en LiteMode, solo se sobrescriben al pulsar **Aplicar perfil**. Los controles individuales permanecen disponibles.
- Accesibilidad: controles de radio etiquetados, botones de tamaño táctil cómodo y texto claro sin depender de color.

## Plan de pruebas de UX

1. Prueba de descubrimiento: localizar la función desde Ajustes en menos de tres pulsaciones.
2. Prueba de comprensión: identificar que «Potente» no acelera el hardware ni garantiza más FPS.
3. Prueba de reversibilidad: pasar de Básico a Automático, sin perder chats y sin cerrar sesión.
4. Prueba con lector de pantalla y texto aumentado; comprobación en tema claro y oscuro.
5. Pruebas instrumentadas en teléfono económico (RAM baja), gama media y tablet; observar fotogramas lentos, inicio frío, RAM y consumo antes/después en las mismas tareas.
6. No afirmar preferencias reales ni mejoras porcentuales sin usuarios o métricas.

## Decisiones técnicas

- Reutilizar `SharedConfig.measureDevicePerformanceClass()` y `LiteMode.PRESET_LOW/MEDIUM/HIGH`.
- Aplicación local, voluntaria y persistente; el ahorro por batería de Telegram sigue prevaleciendo.
- El modo «Potente» se limita a la clasificación real del dispositivo.
- No crear una tabla ficticia de modelos Samsung/Xiaomi/etc. ni asignar rendimiento sin mediciones.
- No alterar conexiones Telegram/MTProto, iconos, marcas de agua ni seguridad.

**Estado:** hipótesis y especificación de UX para implementación y QA; no es un estudio de usuarios realizado.
