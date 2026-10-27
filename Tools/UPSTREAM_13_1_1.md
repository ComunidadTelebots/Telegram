# Integración de Telegram 13.1.1 en DEV

Fecha de revisión: 27 de octubre de 2026.

## Referencias verificadas

- Fork: `https://github.com/ComunidadTelebots/Telegram.git`.
- DEV local y `origin/dev` coincidían al comenzar en `300884789` (sin commits exclusivos de ninguna de las dos).
- Base oficial común: `f2908b14133bbffbf7ab04f641ecb5bfaf533242`, Telegram 12.10.6.
- Objetivo: `3de37c8cfb039452be7398909461da6288babf15`, Telegram 13.1.1 (7237), del 25 de octubre; también era `upstream/master` al consultar el remoto.
- Las ramas locales alfa/beta/rc eran antecesoras de DEV, 35 commits por detrás. `origin/unstable` contiene un commit exclusivo antiguo y no es la base elegida para esta integración.

## Alcance

El commit final cambia 25 archivos (+904/-323), pero aplicarlo solo mediante cherry-pick omitiría sus dependencias. Desde la base común hay 37 commits oficiales y 842 archivos cambiados. La integración usa una fusión completa, conservando el historial del fork.

El commit final afecta principalmente grabación/codificación de audio, llamadas WebRTC, descarga de archivos, pantalla de código de acceso, QR y empaquetado Docker. Los commits intermedios también incorporan módulos y correcciones de contenido protegido, compartir archivos, teclado de bots y navegación del lector.

## Resolución de conflictos

Siete archivos requirieron intervención:

1. `TMessagesProj/build.gradle`: conservar configuración de paquete, contactos y acceso al emulador; adoptar mínimo oficial API 23.
2. `gradle.properties`: versión 13.1.1 / código base 7237, conservando `org.comunidadtelebots`, rama DEV y Firebase por variante.
3. `FileLog.java`: conservar cola asíncrona oficial y resúmenes sin payloads privados del fork.
4. `ChatActivity.java`: conservar acción oficial de enviar dinero y asignar ID 99032 a Leer después, evitando colisión.
5. `MainTabsActivity.java`: conservar perfiles visuales y añadir las incorporaciones oficiales.
6. `SettingsActivity.java`: conservar Ajustes Comunidad Telebots e integrar ajustes oficiales de grabación.
7. `values/strings.xml`: conservar claves nuevas oficiales y propias, eliminando marcadores sin duplicar nombres.

Se revisaron también las mezclas automáticas en ArticleViewer, ChatMessageCell, LaunchActivity, ProfileActivity, ConnectionsManager, FileLoadOperation y VoIPService.

## Compatibilidad y construcción

- Nueva base mínima: Android 6.0 / API 23. Android 5 requeriría un port específico; cambiar únicamente minSdk no garantiza compatibilidad.
- SDK objetivo/compilación: 36. NDK: 27.2.12479018.
- Actualizados los submódulos oficiales, incluidos tgcalls, wallet-engine, boost-regex, libprisma, tlottie y media, sin descartar modificaciones locales.
- Java 25 de Android Studio no funciona con el Gradle 8.13 de este proyecto. La ejecución de validación usa el JDK 21 de Eclipse Adoptium instalado, sin modificar preferencias globales.
- Se necesitó permitir la descarga de dependencias ausentes en caché; el primer intento offline no era suficiente.

## Validación

Pruebas focalizadas superadas durante la integración: frases/descargas, comparación de artículos, AMP (incluidos sockets de prueba locales), 19 validaciones de paletas, selección DNS, perfiles de consumo/deshacer, diagnóstico de dominios y prevalidación de APK. El runner AMP se actualizó para compilar la dependencia real CommunityThemePalette.

Compilación completa `:TMessagesProj_App:assembleAfatDebug` ARM64 superada en 7 min 13 s (323 tareas). APK verificada con aapt: `org.comunidadtelebots.dev`, versión `13.1.1`, código `72379`, minSdk 23 y targetSdk 36. `apksigner verify` terminó con código 0; conserva advertencias de metadatos META-INF del empaquetado. La build usa `EMULATOR_ACCESS_PREVIEW=true`, pero la comprobación posterior en el emulador reveló que ReleaseAccess fuerza el acceso Moonbot y no utiliza actualmente esa opción. Véase la validación posterior más abajo. No se ha publicado una release ni se han subido cambios a GitHub durante esta integración. El catálogo comunitario conserva su estado de desarrollo previo: esta actualización no equivale a desplegarlo ni completar sus enlaces pendientes.


## Validación posterior en emulador (27 de octubre)

- Instalación con `adb install -r -t`, conservando datos, en Pixel_6 / Android 37, ABI x86_64 con traducción arm64-v8a.
- Detectado cierre antes de la interfaz: FirebaseInitProvider informa de que falta el build ID de Crashlytics. Corregido en `gradle/firebase.gradle`: aplicar el complemento Crashlytics a los módulos de aplicación cuando Firebase está habilitado, no al módulo biblioteca.
- Recompilación ARM64 correcta (324 tareas); ejecutada la tarea `injectCrashlyticsMappingFileIdAfatDebug`. Reinstalación correcta y firma verificada.
- Tras la corrección, el proceso permanece activo y UIAutomator confirma el panel nativo «Tu acceso empieza aquí» con «Continuar con Moonbot». Los errores anteriores de Firebase permanecen en el búfer histórico, no se han reproducido en el nuevo proceso.
- El código actual fuerza Moonbot incluso en el emulador (commit da51d7243). Pendiente resolver el acceso con el usuario antes de probar navegación y funciones dentro de la cuenta. No se han borrado datos ni enviado mensajes.
- Reejecutadas y superadas pruebas locales de frases, comparación y versiones de artículos, selección DNS, verificador APK, AMP, paletas, perfiles aplicar/deshacer y diagnóstico de dominios. Estas pruebas no sustituyen la comprobación visual y funcional en el emulador.

## Prueba DEV con excepción de emulador autorizada

El usuario autorizó restaurar la excepción exclusivamente para DEV de pruebas. `ReleaseAccess` exige simultáneamente `EMULATOR_ACCESS_PREVIEW`, `BuildConfig.DEBUG`, rama dev, APK debuggable, hardware ranchu/goldfish y fingerprint google/sdk_gphone. Sin contexto la protección permanece activa. El monitor no se inicia en esa variante. Se mantiene el bloqueo existente para las demás instalaciones. La prueba `Tools/tests/test_emulator_access.py` compila el predicado real y supera 240 combinaciones.

La APK final se compiló e instaló con reemplazo, sin borrar datos. Se corrigieron además caracteres corruptos de flechas y separadores en NetworkDiagnosticsActivity; prueba de diagnóstico superada y desplegable expandido en pantalla con símbolos correctos.

Resultados en Pixel_6, Android 37, traducción ARM64:
- Con 2 GB: ANR de la app y del controlador de permisos durante el primer arranque/sincronización. El informe muestra alta presión de memoria. No se atribuye exclusivamente a la RAM ni se considera resuelta la compatibilidad con dispositivos de poca memoria.
- Repetición con 4 GB, mediante opción temporal del emulador y sin borrar su disco: arranque final en 4267 ms según am start (una observación, no benchmark), navegación correcta, búfer de crash vacío y ningún ANR desde el reinicio al finalizar la prueba.
- Comprobados: acceso al hub, Lectura/AMP, biblioteca existente, apertura local de Example Domains de IANA y comparación de dos versiones guardadas; pantalla de diseños y sus avisos; centro de descargas; DNS; Tor sin activarlo; categorías de diagnóstico y expansión; pantalla de actualizaciones con versión 13.1.1 (72379) / dev.
- Frases: crear categoría PruebaDEV y frase temporal, guardar, abrir acciones, eliminar con confirmación y comprobar lista vacía. No se enviaron mensajes.
- Rendimiento: abrir Ahorro, revisar explicación de cambios y cancelar. No se cambiaron los perfiles ni DNS/proxy/Tor.
- Pendientes: prueba integral de P2P con otro dispositivo, actualización real descargada e instalada, autenticación Moonbot real, rendimiento con 2 GB, descargas y descubrimiento AMP en vivo, paridad visual de diseños históricos y pruebas completas de todas las funciones. Apertura de una pantalla no demuestra sus operaciones de extremo a extremo.

Captura local del hub: `.gradle/dev-hub-verified.png` (artefacto de prueba ignorado por Git). No se ha publicado el APK ni enviado estos commits a GitHub.

## Correcciones y repetición con 2 GB (27 de octubre)

Cambios:
- SharedConfig considera de poca memoria los equipos con RAM física positiva de hasta 2 GiB, aunque Android no marque isLowRamDevice. Activa las protecciones ya existentes de caché, efectos y rendimiento sin reescribir las preferencias del usuario.
- Primer bloque de diálogos: 30 en poca memoria, 100 en otros equipos; el resto sigue paginado. Se evita precargar los stickers de dados al arrancar en poca memoria.
- Eliminado el arranque P2P desde ApplicationLoader. La pantalla requiere activación explícita, informa de que el transporte LAN experimental no cifra ni autentica y lo detiene al salir. No se activa con Tor/proxy o sin acceso autorizado.
- P2P: pool de 4 trabajadores y cola de 16, timeout de cliente 3 s, tramas TCP/UDP limitadas, historial en memoria hasta 200, mensajes hasta 2000 caracteres, deduplicación sincronizada y conexiones de salida con timeout. Pares limitados y con caducidad; registrar un par no inicializa el hub ni otros radios. La UI muestra 0 pares cuando no hay ninguno. No se contabiliza recepción como tráfico servido.

Evidencia de 2 GB:
- AVD Pixel_6 Android 37, MemTotal 2013496 kB, mismo disco y cuenta.
- La build ARM64 traducida siguió siendo terminada por Android con reason=LOW_MEMORY, incluso tras activar las protecciones; no se declara resuelto ese caso.
- Compilada una APK x86_64 nativa (6 min 11 s, 324 tareas), instalada sin borrar datos. Dos arranques: 5247 ms y 3693 ms, observaciones individuales y no benchmark. Navegación por hub, biblioteca, P2P y actualizaciones sin ANR ni nuevas terminaciones por memoria durante la prueba. La primera sesión permaneció activa hasta el force-stop explícito usado para la segunda.
- PSS observado: aproximadamente 284 MiB durante P2P y 282 MiB en biblioteca tras el segundo arranque. No comparar estas instantáneas como un ahorro universal: cambian carga y arquitectura.
- La build que queda instalada es x86_64 para el emulador. No es un APK ARM64 para teléfonos. Falta validar un móvil físico de 2 GB y uso prolongado.

P2P verificado en la app:
- Emisor controlado en localhost mediante adb forward, no mensajes a contactos ni otros usuarios.
- Recepción visible de PRUEBA LOCAL P2P 2GB, repetida con el mismo ID: una sola burbuja. Se conserva ese registro local de prueba.
- Trama de 9000 bytes cerrada; conexión sin datos cerrada a los 3 s; una trama válida posterior funciona. Puerto 8989 cerrado al salir y ausente tras reiniciar. Túnel ADB retirado.
- Esto verifica recepción TCP local, límites y ciclo de vida. No verifica descubrimiento entre dos móviles, entrega saliente/confirmada, cifrado, autenticación ni retransmisión multisalto. Las funciones conceptuales del hub no equivalen a implementaciones completas de ZeroNet/DHT/BGP.

Actualizaciones:
- 19 pruebas locales del servicio de acceso y actualizaciones superadas: manifiestos, ramas, permisos, sesiones revocadas/caducadas y metadatos. Test del verificador Android superado; usa stubs de PackageManager, no sustituye al motor real de firmas de Android.
- Consulta HTTPS real: stable responde 200/status=unavailable; dev sin token responde 403. Botón de la app muestra error de conexión/permiso sin ofrecer instalación. La excepción de emulador no da acceso al servidor privado.
- Pendiente actualización real: hace falta release publicada y sesión Moonbot real para DEV. No se ha publicado ni instalado una versión ficticia para aparentar éxito.
- En los logs aparecen APP_PUSH_APIKEY_MISSING y COMMUNITY_ID_INVALID/COMMUNITY_FILTER_INVALID de llamadas al servidor. No provocaron cierre durante esta prueba; notificaciones push y esas funciones requieren revisión separada de configuración/compatibilidad de API.

Pruebas añadidas: test_low_memory_policy.py (10 fronteras y contexto nulo) y test_p2p_frame.py (TCP local real, longitud máxima, trama incompleta y timeout). También superadas las de excepción de emulador, perfiles y herramientas propias. Sin push a GitHub ni publicación.

## P2P con autenticación y cifrado de transporte (27 de octubre)

El chat P2P local sustituye TCP plano y descubrimiento UDP por TLS 1.2 con ECDHE/ECDSA y AES-128-GCM. Cada instalación crea una identidad EC P-256 en Android Keystore; su clave privada no se exporta. Ambos dispositivos deben autorizar previamente la huella SHA-256 completa del certificado del otro y su IP privada. No hay confianza automática, fallback a texto plano ni retransmisión multisalto. Se admite un máximo de 16 pares y se conservan los límites anteriores de conexiones, colas y mensajes. El contador muestra autorizaciones, no presencia en línea.

La recepción liga el remitente del JSON al certificado autenticado, comprueba destinatario, fecha y límites, y deduplica contra el historial reciente. Revocar detiene el listener e invalida tareas de sesiones anteriores. El historial previo se etiqueta como no verificado. La salida se muestra como guardada localmente, sin afirmar entrega confirmada. El historial local no tiene cifrado adicional en reposo; esta implementación protege el transporte. La deduplicación del historial no equivale a protección persistente ilimitada contra replay. No se presenta esto como auditoría de seguridad externa ni como cifrado de todos los subsistemas experimentales del hub.

Pruebas en el mismo AVD de 2 GB, APK x86_64 DEV con excepción de acceso exclusiva del emulador:
- Detectado y corregido un fallo real de handshake: Conscrypt usa NONEwithECDSA para firmar un digest ya calculado. La identidad v2 autoriza ese uso en Keystore, manteniendo la clave no exportable y TLS con SHA-256.
- Cliente de prueba local con certificado autorizado: handshake mutuo y recepción visible de TLS_AUTENTICADO_PRUEBA con etiqueta de identidad verificada.
- Cliente anónimo rechazado (handshake failure); certificado ajeno rechazado (certificate unknown).
- Repetir el ID válido deja una sola burbuja; los textos con remitente falsificado y fecha caducada no aparecen.
- Alterar un byte de un registro TLS real provoca BAD_RECORD_MAC; no aparece el texto manipulado. El texto no está presente en el registro cifrado capturado por la fixture.
- Salida desde el emulador a un servidor TLS de prueba: el servidor exige certificado cliente, verifica la huella de la app y recibe el mensaje de prueba. Sustituir el certificado servidor por otro no autorizado hace fallar el handshake antes de recibir datos de aplicación.
- Revocadas las autorizaciones de prueba: contador 0, listener cerrado y activación rechazada sin pares. Retirado el túnel adb. No se han enviado mensajes a usuarios reales ni borrado datos del emulador.
- Test automatizado test_p2p_trust.py compila la clase real y comprueba ambos roles, certificados ajenos/caducados, cadenas nulas/vacías/múltiples, copia inmutable de pins y ausencia de confianza tras revocación. test_p2p_frame.py también superado.

Pendiente: dos móviles físicos, distintas versiones de Conscrypt/Android, cambios de red y pruebas prolongadas. Esta prueba usa una fixture TLS local, no dos teléfonos. No acredita resistencia a todo ataque de denegación de servicio.

### Notificaciones: configuración pendiente del propietario

La build local todavía usa APP_ID=4 de BuildVars.java; la automatización de releases tiene su propia inyección de credenciales. Por tanto, configurar la aplicación propia en my.telegram.org no basta hasta compilar localmente con su api_id/api_hash mediante secretos, sin incorporarlos al repositorio. El módulo TMessagesProj_App usa el proyecto Firebase comunidad-telebots y contiene los cinco paquetes; esto no verifica los otros módulos de distribución.

1. En Firebase Console, abrir comunidad-telebots, configuración del proyecto: comprobar que la aplicación Android DEV corresponde a org.comunidadtelebots.dev. En Cloud Messaging, comprobar que la API HTTP v1 está habilitada.
2. En https://my.telegram.org/apps, abrir la aplicación propia y revisar su configuración de push. Telegram exige configurar las credenciales del servicio emisor: https://core.telegram.org/api/push-updates. El error observado APP_PUSH_APIKEY_MISSING no es un permiso de notificaciones de Android.
3. No confundir api_hash, API key cliente de google-services.json ni Sender ID con una credencial de servidor. Firebase HTTP v1 utiliza autorización de servidor; una clave privada de cuenta de servicio no debe incluirse en el APK: https://firebase.google.com/docs/cloud-messaging/send/v1-api.
4. No se ha inspeccionado el formulario privado actual de Telegram. Si solo ofrece una clave GCM antigua, falta confirmar un mecanismo compatible con HTTP v1; no se afirma que acepte JSON de cuenta de servicio. Se necesitan los nombres de campos, sin valores secretos, para concretar ese paso.
5. Tras alinear credenciales y configuración, recompilar, repetir el registro del dispositivo y probar una notificación con la app en segundo plano y permiso concedido. La entrega push aún no está verificada.

## Inicio de adaptación a Android 5.0 y panel de compatibilidad

Se añade `-PCOMMUNITY_MIN_SDK=21` para compilaciones experimentales DEV. El valor predeterminado continúa en 23 y el modo 21 se rechaza en otras ramas. El mínimo Java/manifest y ANDROID_PLATFORM del código nativo se configuran conjuntamente. Los módulos Media3 reciben el mínimo mediante finalizeDsl, sin modificar el submódulo. No se han añadido excepciones de fusión de manifiestos para ocultar dependencias incompatibles; se conservan las excepciones previas de Tor/segmentación.

La APK x86_64 para API 21 compila (primera compilación nativa: 6 min 11 s; repetición final: 19 s) y se instala en un AVD independiente Telebots_API21, imagen oficial google_apis/x86_64 Android 5.0.2, 2 GB. El arranque llega a ReleaseAccessActivity y muestra el acceso Moonbot, sin excepción AndroidRuntime observada. El AVD no tiene cuenta Telegram; el usuario ha aceptado iniciar sesión posteriormente. Chats, llamadas, reproducción, almacenamiento, actualizaciones y notificaciones en Android 5 siguen pendientes. No se declara soporte completo ni se publica esta build como estable.

Se añade Ajustes Comunidad Telebots > Compatibilidad del dispositivo: versión de Android, RAM y política de poca memoria, requisitos de P2P/Tor, permiso global de notificaciones y acceso a ajustes del sistema. Distingue disponibilidad de APIs de funcionamiento real del servicio. Los datos se consultan localmente. Los requisitos se centralizan en CommunityCompatibility y se prueban en 68 casos de frontera API 21-37.

La identidad P2P actual requiere API 23: en API 21/22 no se intenta generar con KeyGenParameterSpec y se informa de la limitación, sin fallback a texto plano. Tor integrado mantiene su mínimo API 24; el panel lo explica y permite acudir a proxies externos. Estas restricciones no son implementaciones alternativas completas para Android 5.

La prueba de notificaciones con ahorro durante diez minutos quedó pendiente por decisión del usuario de pasar a otras tareas. Se restauraron low_power=0 y bloqueo desactivado, como estaban antes de la prueba. El push con pantalla bloqueada y fuera de recientes sí se había verificado; no confundirlo con Doze prolongado.

Excepción API 21 autorizada posteriormente por el usuario: ReleaseAccess admite la huella exacta de la imagen oficial Android 5.0.2 google_apis/x86_64 usada en Telebots_API21, exclusivamente con SDK 21, hardware ranchu/goldfish, rama DEV, DEBUG, APK debuggable y EMULATOR_ACCESS_PREVIEW explícito. Se conserva la excepción anterior para el emulador moderno. Test de frontera ampliado: 1440 combinaciones superadas. APK recompilada en 1 min 38 s, instalada con reemplazo sin borrar datos en emulator-5556; arranque de 1431 ms y pantalla de acceso de Telegram visible, sin Moonbot ni excepción AndroidRuntime observada. No se completó la autenticación de Telegram ni se introdujeron códigos del usuario.


## Revisión API 21 y gráficos del lector (2026-10-27)

Con la sesión iniciada por el usuario se comprobaron apertura de Mensajes guardados, biblioteca, frases locales (crear/reabrir/eliminar una frase sintética), catálogo DNS, informe de conexión, descargas, perfiles y pantalla de actualizaciones. No se enviaron mensajes ni se cambiaron DNS/proxy/Tor. La actualización privada DEV no pudo validarse sin autorización del servidor. P2P cifrado requiere API 23 y Tor integrado API 24; ahora los controles P2P incompatibles quedan deshabilitados. Se evita sugerir passkeys en API 21 y resolver UserNotAuthenticatedException en sistemas anteriores a API 23. Prueba JVM del error de autenticación: pasa con la clase moderna eliminada del classpath.

El lector conserva figuras con tablas y varios elementos, y reconoce amp-iframe de Datawrapper/Flourish como tarjetas locales. Al tocar se crea un único WebView para el gráfico; se libera al cerrar, perder foco o salir de la celda. No incorpora una interfaz JavaScript nativa, acceso a archivos, ventanas emergentes ni reproducción automática. Solo admite HTTPS de los proveedores explícitos; otros iframes no se ejecutan. Los gráficos se bloquean si Tor, proxy o DNS personalizado están activos, porque este WebView no hereda el transporte propio. Esto no implica soporte de cualquier amp-script, SVG o proveedor de gráficos. La tarjeta requiere conexión; no se guarda el contenido interactivo para uso sin conexión.

Para API 21/22 se incorpora extracción ES5 de un documento separado del DOM visible, sin ejecutar scripts del editor. Usa article/main/body y una lista limitada de atributos; tiene menor precisión editorial que Readability. Las pruebas de ambos parsers conservan texto completo, imágenes, tablas, pies y referencias a gráficos sin ejecutar scripts remotos. Se prueba la política real de proveedores con hosts engañosos, credenciales, puertos y esquemas inválidos. También pasan las pruebas AMP existentes, compilación aislada del almacenamiento y 1440 casos de acceso del emulador.

APK DEV x86_64 API 21 final: assembleAfatDebug correcto (1 min 42 s), instalación con reemplazo correcta, arranque 2525 ms y navegación a la biblioteca. No se observaron excepciones fatales en el registro revisado. El guardado real de example.com falla antes del parser con UnknownHostException; ping del propio emulador también informa unknown host usando DNS 10.0.2.3. Queda pendiente verificar descarga y la interacción real de los gráficos en Android 5 una vez recuperada la resolución DNS. No se afirma compatibilidad de los scripts actuales de proveedores con su WebView antiguo. Se eliminó la entrada sintética de la biblioteca; la sesión del usuario se conserva. No hay publicación en GitHub ni despliegue de esta APK.
