# Revisión nativa independiente: iOS, Telegram X y menú Android clásico

Estado: implementación parcial nueva, no certificación de fidelidad completa.
Conserva las referencias y capturas anteriores. No sustituye el baseline rechazado.

## Fuentes primarias fijadas

- iOS: TelegramMessenger/Telegram-iOS, `6ad963e5b62d354da79040f388ae2b9132fb17b8`,
  `submodules/TelegramUI/Sources/TelegramRootController.swift:202-242`.
  Define Contacts, Calls (condicional), Chats, Settings y el badge del chat en 206.
  Adaptación: `IosNavigationView` separa la insignia de la etiqueta, crea controles
  propios y conserva ese orden. Las medidas dp, glifos Android, separador y colores
  de tema son adaptación local, no medidas originales verificadas. Aún no reproduce
  el ajuste iOS para ocultar Calls. No implementa Liquid Glass ni SF Pro.
- Telegram X: TGX-Android/Telegram-X, `9312ace35291f68fdd66ddff348d7321bee2a4bd`,
  `app/src/main/java/org/thunderdog/challegram/ui/MainController.java:1801-1808`
  define secciones Chats/Calls en mayúsculas; `:2846-2847` separa indicador y texto
  activo/inactivo. `TelegramXNavigationView` implementa pestañas textuales propias,
  selección subrayada y colores de cabecera. Altura, grosor y menú de desbordamiento
  son adaptación Android propia. El acceso a Contactos/Ajustes/cuentas y a carpetas
  mediante pulsación larga se mantiene. No se ha copiado una animación original.
- Android clásico: DrKLO/Telegram, `f41b228a111e304c2505a86c7cc8b448eaecaf6f`,
  `TMessagesProj/src/main/java/org/telegram/ui/Components/ChatActivityEnterView.java:1855-1935`.
  Menú contextual compacto con programación y envío sin sonido, elementos en
  `:1894-1902`. La adaptación usa ItemOptions actual y el motor de envío actual.
  No afirma que esas funciones existieran en 2013. Se reutiliza la presentación
  compacta para Original/Holo/Material/Classic y se filtra la disponibilidad por
  el año funcional independiente. Con todas las funciones, ofrece una entrada
  explícita a las opciones modernas completas. Su vista previa sigue siendo la
  actual: la recreación visual de esas opciones avanzadas aún está pendiente.

Archivos originales descargados: output/design-source-audit/2026-11-06.
Android clásico puede recuperarse con git show al commit señalado.

## Funciones por año

La política ahora cubre cinco controles de autoría: encuestas (2018), programación
(2019), envío silencioso (2019), historias (2023) y selector de efectos (2024).
Las tres nuevas restricciones cubren el compositor y sus dos menús existentes;
no se anuncia cobertura total de otros compositores multimedia o reenvío.

Fechas verificadas en anuncios originales:
- https://telegram.org/blog/scheduled-reminders-themes (5 septiembre 2019)
- https://telegram.org/blog/silent-messages-slow-mode (9 agosto 2019)
- https://telegram.org/blog/message-effects-and-more (31 mayo 2024)

Los mensajes recibidos, sesiones, seguridad y protocolo conservan comportamiento
actual. Un año representa fin de año. La interfaz sigue indicando cobertura parcial.

## Verificación

180 combinaciones de la política y límites por año pasan en Java 8.
La instrumentación usa pantallas reales y mantiene pulsado el botón de envío.
Introduce un texto temporal sin avisar al delegado de borradores/escritura, nunca
lo envía, y restaura el texto previo, estilo, año, modo y radio anteriores. Las capturas de sesión
son locales, no se publican ni se envían a Canva.

Resultado final: compilación DEV y APK de instrumentación superada con mínimo
API21 y ABI x86_64/arm64-v8a. Instalada conservando datos en Telebots_API21
(Android 5.0.2) y Pixel (API37). Ambas pruebas devuelven PASS.

Se comprueban las barras independientes en Chats, pulsación que abre Llamadas,
consumo de pulsación larga en 2013, recordatorio en 2020, y presencia de Opciones
modernas solo con todas las funciones. Se comprueba que el menú esté cerca del
compositor (mitad inferior de la pantalla). No se ha enviado ningún mensaje ni
realizado una llamada. No se han probado todos los adjuntos, efectos o funciones
de la aplicación. La compatibilidad API21 sigue limitada a DEV experimental.

APK SHA-256: 74ccc3a971cdbf65917a0c9fa2fe2a940af9b976bc4f5916c4d9455662fe5131.
Galería: output/design-independent/index.html. Capturas y huellas: manifest.json.
No se han publicado cambios, APK ni capturas en servicios externos.

## Pendientes de fidelidad

Lista de chats, conversaciones, compositor completo, contactos, llamadas y ajustes
siguen compartiendo componentes Android. Web A/K, Unigram, macOS, Desktop y
Webogram no se han reconstruido en esta iteración. Las barras independientes no
convierten el resto de pantallas en réplicas de esos clientes. La cobertura de
funciones históricas y su adaptación visual completa requiere más iteraciones.

## Correcciones encontradas durante las pruebas

- La prueba debe inicializar las cuentas después de arrancar la aplicación.
- Capturas en caché privada, pues Android 5 puede carecer de almacenamiento externo.
- El ancla del menú es el botón Enviar, nunca su contenedor: dibujar un contenedor
  que incluye la propia capa de oscurecimiento provocaba recursión en la prueba.
- El menú compacto usa el fragmento completo como contenedor para calcular la
  posición junto al compositor, en lugar de quedar pegado a la cabecera.
- La pulsación larga se consume aunque no existan opciones para ese año; se
  muestra un aviso y soltar no puede convertirse en un toque normal de envío.
- Texto de opciones modernas acortado para evitar recorte en Android 5.
- La prueba no espera a que cesen indefinidamente todas las animaciones: sincroniza
  operaciones en el hilo UI y deja un intervalo acotado antes de las capturas.
- Las capturas del menú ocultan temporalmente la lista de mensajes en la prueba,
  sin modificar mensajes ni imágenes guardadas; restauran la vista antes de salir.
