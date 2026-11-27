# Perfiles y mediciones de Comunidad Telebots

El módulo opcional `TMessagesProj_Benchmark` contiene generación desde recorridos reales de arranque y desplazamiento por Ajustes Comunidad Telebots, más comparación de 10 arranques en frío con métricas de inicio y frames. No recopila telemetría de los usuarios ni envía datos a Alltech.

Activación: `-PENABLE_BENCHMARKS=true -PAPP_CHANNEL=dev`. Los builds normales no resuelven las dependencias del módulo de pruebas. El target `afatBenchmark` es no depurable y sin ofuscación para generar reglas legibles. Mantiene el control de acceso de producción; no incorpora la excepción de emulador de debug. La cuenta de pruebas debe iniciar sesión y tener autorización de Moonbot.

## Generar

1. Compilar `:TMessagesProj_App:assembleAfatBenchmark :TMessagesProj_Benchmark:assembleAfatBenchmark`.
2. Instalar APK target y APK de pruebas en dispositivo de pruebas API 33+ (o emulador con root). Usar misma firma y paquete que los datos de pruebas que se deseen conservar.
3. Ejecutar `:TMessagesProj_Benchmark:connectedAfatBenchmarkAndroidTest` con `-Pandroid.testInstrumentationRunnerArguments.class=org.comunidadtelebots.benchmark.CommunityBaselineGenerator`. Requiere acceso previo al hub: el test falla si no puede entrar, evitando producir un perfil engañoso de la pantalla de acceso.
4. Recoger los archivos generados `*-baseline-prof.txt` y `*-startup-prof.txt` de `build/outputs/connected_android_test_additional_output/` del módulo. Fusionar las reglas generadas en `TMessagesProj_App/src/main/baseline-prof.txt` y `startup-prof.txt`, respectivamente. No escribir reglas inventadas ni publicar perfiles obtenidos con errores.
5. Recompilar la APK; AGP consume estas rutas estándar. En la APK debe existir `assets/dexopt/baseline.prof`. El proceso de publicación puede conservar los TXT generados por revisión de código.

## Comparar

Ejecutar la clase `org.comunidadtelebots.benchmark.CommunityBenchmarks` en un dispositivo físico, incluyendo Android Go. `withoutProfile` usa `CompilationMode.None`; `withProfile` exige el perfil empaquetado (`BaselineProfileMode.Require`), para que la ausencia no pase desapercibida. Comparar medianas y distribución de StartupTimingMetric/FrameTimingMetric entre recorridos iguales, con la misma cuenta, contenido, temperatura, red y ajustes. Un emulador permite comprobar el funcionamiento de los recorridos, pero no demostrar mejoras en Android Go. No extrapolar los porcentajes generales de Android al fork.

Al ser un módulo optativo manual, no descarga ni ejecuta dispositivos en CI automáticamente. Tampoco hace falta servidor nuevo.

Referencias: https://developer.android.com/topic/performance/baselineprofiles/overview y https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile


## Revisión general de rendimiento — 2026-10-27

Cambios concretos de esta tanda:

- Arranque: una reconciliación de cuentas Android en vez de una por hueco.
- Chats: caché acotada de medición/elipsis de las vistas previas, invalidada al
  cambiar contenido, ancho o tipografía y liberada al enlazar otro mensaje.
- Multimedia: reutilizar el reproductor ya creado no construye un allocator ni
  un load-control que después se descartaban. Los buffers conservan sus valores.
- Segundo plano: una consulta de actualización obligatoria en cola o en curso,
  sin acumular consultas si el servidor tarda; se mantienen controles y periodo.
- Lectura: retirar cargas AMP pendientes de tarjetas cerradas; precalcular datos
  de gráficos; agrupar durante 150 ms la búsqueda de la biblioteca.

La revisión también examinó inicialización de controladores por cuenta,
DownloadController/perfiles de precarga, ImageReceiver, servicios de diagnóstico,
catálogo de temas y almacenamiento propio. No se modifican cuotas de descarga,
DNS/proxy/Tor, protocolos, calidad multimedia ni garantías de acceso para obtener
una velocidad aparente. La inicialización de cuentas vacías y la E/S de algunos
paneles necesitan trazas adicionales antes de cambiarse.

Pruebas: test_startup_accounts.py, test_security_update_coalescing.py,
test_single_line_text_cache.py y test_amp_performance.py. Las operaciones evitadas
se verifican con pruebas controladas; no equivalen a un porcentaje de FPS o
latencia real. `measure_debug_startup.py` captura arranque con `am start -W` y PSS
sin borrar datos. Es una comprobación local de DEV, no sustituye la comparación
release en dispositivo físico descrita arriba. Tampoco mide el tiempo de carga
de mensajes desde Telegram ni permite extrapolar el resultado a toda la app.


Validación local de esta tanda: las pruebas deterministas pasan, y la DEV
completa tres arranques con memoria disponible en Pixel API37 y Android5 API21.
Medianas orientativas antes/después: API21 2745/2657 ms; Pixel 4613/4930 ms.
Sólo tres muestras por caso, emuladores compartiendo host: no hay evidencia de
una mejora global consistente del arranque, ni estos números miden la carga de
chats o descargas. Resultados crudos en `output/performance` (local, ignorado).

La prueba funcional `-e performanceOnly true` usa TextPaint real, comprueba el
eje porcentual y la creación/reutilización de ambos reproductores. Descubrió
un fallo anterior: se compartía TrackSelector entre el reproductor principal y
el de audio separado, rechazado por Media3. El segundo usa ahora su propio
selector con los mismos parámetros. Esta prueba no sustituye reproducción
prolongada de vídeo/audio ni pruebas de streaming con distintas redes.


Resultado final: BUILD SUCCESSFUL; DEV instalada conservando datos en API21 y
API37. `performanceOnly` pasa en ambos: métricas de texto a tres tamaños/ancho,
eje porcentual, creación y reutilización del reproductor principal y audio
separado. La prueba detectó además una llamada API23 checkSelfPermission en
WakeLockManager/WifiLockManager. Se sustituye por checkPermission con PID/UID
propios, manteniendo la comprobación de WAKE_LOCK en Android5.

Hallazgo pendiente de correlación: putChat/loadFullChat precargan administradores
de múltiples comunidades al arrancar. Se observaron COMMUNITY_FILTER_INVALID y
FLOOD_WAIT durante las pruebas; no se modificó el protocolo ni se atribuye aún
cada respuesta a una comunidad concreta. También merece revisar el backoff de
miembros en ProfileActivity tras errores. Se detuvieron rondas adicionales de
arranque repetidas; las pruebas finales fueron sólo las necesarias para validar
las correcciones multimedia.
