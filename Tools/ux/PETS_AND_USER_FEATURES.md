# Comunidad Telebots — Laboratorio de funciones y mascotas virtuales

## Investigación de diseño (hipótesis pendientes de validar)
No existe todavía una encuesta de usuarios ni pruebas de uso. El objetivo es experimentar con opciones reversibles sin acceso automático a conversaciones.

### Funcionalidad priorizada
| Prioridad | Función | Estado inicial | Hipótesis que validaremos |
|---|---|---|---|
| P0 | Mascota virtual local personalizable | MVP implementado en esta rama | Incrementa la personalización sin molestar |
| P0 | Activar/desactivar mascota y borrar datos | MVP | Las personas quieren control y privacidad |
| P0 | Energía, ánimo, cuidados y progreso | MVP | Las interacciones cortas resultan claras |
| P1 | Recordatorios configurables, siempre opt-in | Diseño | Ayuda sin generar notificaciones molestas |
| P1 | Mascota como ayudante en la búsqueda de ajustes | Diseño | Reduce pasos para funciones ocultas |
| P1 | Mascota con conversaciones generativas IA | Diseño, requiere modelo y consentimiento | Preguntas libres aportan utilidad |
| P1 | Atajos inteligentes de accesibilidad | Diseño | Aumenta utilidad con lectores y texto grande |
| P1 | Perfiles de uso: estudio, viaje y datos reducidos | Diseño | Simplifican ajustes cotidianos |
| P2 | Widgets de Android con mascota | Diseño | Permiten interactuar sin abrir el chat |
| P2 | Personalización por especie, nombre, aspecto y ropa | Diseño | Facilita identidad propia |
| P2 | Mensajes programados, deshacer borrado y resumen de conversaciones | Estudio técnico/privacidad | Evita pérdida de contexto |
| P2 | Traducción de texto elegida por el usuario | Diseño | Ayuda a grupos internacionales |
| P2 | Biblioteca de efectos visuales ligeros | Diseño | Permite decorar sin penalizar rendimiento |
| P2 | Mascota cooperativa en grupos | Estudio de permisos | Requiere consentimiento de todos y control de admins |

### Diseño de la pantalla principal
```text
‹  Mascota de Comunidad Telebots

  [ Activar mascota ] (apagada de forma predeterminada)
  Nombre: [Nube]         Tipo: [Gato ▼]
          (=^･ω･^=)
        ¡Hola! ¿Jugamos?

  Ánimo  70/100   Energía  80/100   Cariño  50/100
  [Dar comida]      [Jugar]      [Descansar]
  [Frase de ánimo]                [Restablecer]

  Datos guardados solo en este dispositivo.
  No lee chats, contactos ni mensajes.
  Conversación libre con IA: disponible en fase posterior.
```

### Reglas de producto
- El motor inicial es **un juego de mascota virtual local**, no un LLM. Nunca debe anunciarse como IA generativa si no hay modelo real conectado.
- Estado y nombre guardados en preferencias locales, aislados por instalación, sin permisos añadidos, sin servicios ni red.
- Los cambios deben ser voluntarios y reversibles.
- Mantener idioma español/inglés, alto contraste del tema, accesibilidad de controles y tamaño táctil.
- Al contrario que un bot, la mascota no entra en chats, no lee conversaciones ni envía mensajes.
- Probar datos corruptos, texto largo, reapertura, modo oscuro, pantalla pequeña y modo sin conexión.
- Medir satisfacción con pruebas voluntarias (descubrimiento, control, utilidad), nunca inventar cifras.

### Diseño de pruebas con usuarios
- Comprensión: preguntar a participantes si creen que la mascota accede a los chats.
- Preferencia: comparar pantalla con mascota y alternativa sin mascota; se elige libremente.
- Usabilidad: cambiar especie, nombre, alimentar, desactivar y borrar datos.
- Rendimiento: presupuesto de cero trabajos en segundo plano y cero red; medir RAM y tiempo de arranque frente a DEV original.
- Accesibilidad: TalkBack, zoom de fuente y navegación con teclado.
