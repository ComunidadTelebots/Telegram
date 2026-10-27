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
