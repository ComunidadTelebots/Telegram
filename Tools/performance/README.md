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
