# Integración de Aurora en DEV · 27 de noviembre de 2026

## Qué se incorporó

Aurora y DEV local tenían el mismo commit base `87e98351b34160fd99391eb102910144c3a8379c` (Telegram 13.1.1). No había commits exclusivos de Aurora que fusionar. Se trasladó el trabajo local del árbol principal a la rama existente `dev`, sin crear ramas ni publicar cambios remotos. Se conservó una copia recuperable en `output/aurora-to-dev-20261127/source-backup.zip` y un manifiesto SHA-256 de 131 archivos, incluidos dos archivos del submódulo multimedia.

- Estilos Aurora y Aurora Midnight: paletas locales, Manrope y navegación propia; conservados como opciones, sin sustituir los otros diseños ni el tema guardado del usuario. Procedencia exacta en `DESIGN_AURORA_REVISION_2026-11-09.md` y en la licencia/manifiesto de las fuentes. Las referencias fechadas y propuestas rechazadas se conservan con su estado original.
- Perfiles históricos y navegación iOS/Telegram X, selección de año y disponibilidad de funciones.
- Herramientas de mensajería: ventana de cancelación antes de enviar, historial local de borradores y respuestas pendientes, con sus modelos y pruebas.
- Lector: mapas y tablas nativas, proyecciones, Datawrapper, preferencias de idioma, orden de lectura, colas acotadas y transporte TLS público para versiones antiguas.
- Aislamiento de cuentas/backend y tokens, preparación de login, etiquetas de cuenta, caché de texto y agrupación de comprobaciones de seguridad.
- Compilación por ABI, correcciones de Firebase/Crashlytics, comprobación de bibliotecas de 16 KB y herramientas de empaquetado AAB existentes. No se publica en Play ni se modifican claves privadas.

El traslado conserva byte a byte el código existente inventariado; no introduce un nuevo rediseño. La presencia de estas funciones no significa que todas estén verificadas en un dispositivo. El catálogo P2P existente permanece en su estado previo; sus 100 ejecutores de log no se presentan como funciones reales.

## Validación de esta integración

- APK `:TMessagesProj_App:assembleAfatDebug`: **BUILD SUCCESSFUL**, 1 min 44 s, Java 21, ABI x86_64 y previsualización DEV de emulador habilitada.
- Paquete `org.comunidadtelebots.dev`, versión 13.1.1, código 72379, minSdk23 y targetSdk36. La integración a Telegram 13.2.2 continúa pendiente en el plan ampliado; este traslado no la simula.
- Verificación criptográfica de firma correcta. Certificado público de pruebas con SHA-256 `a08d7dc323ddf71ef3201944397e0d3cce7d40847263e11f328b68bbe19229ab`; no es una firma estable de producción. El verificador emite advertencias sobre metadatos META-INF.
- Instalación y arranque correctos en `emulator-5580`, Pixel 9 aproximado, Android API37, x86_64 y páginas de 16 KB. La candidata anterior se sustituyó únicamente en este emulador recién creado porque su firma DEV era distinta. No se tocó ningún dispositivo físico.
- Proceso vivo PID 6032 en la comprobación posterior; búfer de crash vacío para ese proceso. Pantalla de bienvenida inspeccionada en `output/aurora-to-dev-20261127/dev-startup.png`. No se inició sesión ni se enviaron mensajes; no se verificaron las pantallas internas ni todas las variantes de diseño.
- 19 scripts Python pasan, incluidos los de AMP, cuentas/backend, TLS, respuestas pendientes, empaquetado, envío con demora y catálogo histórico. Se ejecutaron además tres pruebas Java: borradores (22 comprobaciones), política de diseño (180 combinaciones/límites) y orden de lectura (11 comprobaciones).
- Prueba JavaScript del parser superada para `instant.js` e `instant_legacy.js`: cuatro descriptores públicos conservados, ocho declaraciones no compatibles rechazadas, scripts inertes.
- No ejecutada la regresión opcional `test_datawrapper_components.py`: faltan sus datasets externos de referencia. Mapas se probaron con los casos internos; no se afirma haber verificado datasets publicados no disponibles.

Los dos cambios preexistentes de WakeLockManager/WifiLockManager permanecen sin commit dentro del submódulo multimedia y se incluyen en la copia de seguridad. No se modifica su gitlink a un commit local inaccesible desde GitHub. La compilación registrada usa esos cambios locales; su objetivo es el acceso a permisos compatible con Android antiguo. No se declara por ello soporte API21 verificado.

## Entrega

APK de pruebas de emulador y evidencias: `output/aurora-to-dev-20261127/`. Esta APK x86_64 no es una compilación para los teléfonos/tablets ARM Samsung. DEV remoto no se sobrescribe: `origin/dev` continúa con su historial propio 12.10.6 hasta una integración posterior revisada. `alfa`, `beta` y `rc` no se modifican.
