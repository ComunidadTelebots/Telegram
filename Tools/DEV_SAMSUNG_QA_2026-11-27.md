# DEV: compilación e integración verificadas · 27/11/2026

## Código y GitHub

Se conserva el commit local de seguridad `871611d60`. DEV local partía de `871611d60`, frente a `origin/dev=b6d8e69e0`; divergían en 49/9 commits. Se integraron ambos historiales sin reset ni force push.

- `949acb337`: descubrimiento AMP continúa tras un primer enlace inválido; ignora href vacío y espacios exteriores. Ocho casos adicionales; fallo reproducido antes del arreglo y suite aprobada después.
- `a9fe2d7f6`: integración del historial remoto, conserva mensajería, compatibilidad, editor de interfaces y selectores locales de diseño.
- `6bdf2675e`: PR #18 (stickers favoritos) integrada; traducciones trasladadas a strings.xml, que consume el generador de recursos.
- `b450bf2f1`: PR #17 (mostrar historias) integrada, sin duplicar selectores históricos; misma corrección de traducciones.
- `f3becfd0b`: referencia explícita a la extensión Android de Gradle para recursos de marca.
- `d4d9075b1`: CI sin secretos de lector, seguridad y políticas de dispositivo.
- `0e982d9de`: corrige contexto no admitido runner.temp en la definición del entorno del workflow APK.

DEV subida sin forzar tras autorización explícita. GitHub confirma #17 y #18 como MERGED. No se fusionaron el resto de PR: #14/#15 tienen un build fallido y #15 conflictos; #16 apunta a la rama de #14, no a DEV; #11–#13 necesitan reconciliarse con el transporte autenticado y el cierre de sockets ya presentes, no aplicar literalmente su antiguo transporte en claro. Las restantes requieren revisión específica; no se consideran validadas por esta sesión. Inventario vivo de las 18 PR iniciales: output/dev-amp-20261127/prs.json.

CI aprobada: https://github.com/ComunidadTelebots/Telegram/actions/runs/46032275219 (también 46031900878). Es regresión JVM/Python, no compilación de APK en GitHub. No se despachó el workflow de publicación de artefactos ni Play.

## Compilaciones locales y APK

Java 21, Gradle 8.13; :TMessagesProj_App:assembleAfatDebug, canal dev, SDK 36. Resultado correcto para x86_64 y arm64-v8a. Paquete org.comunidadtelebots.dev, versión 13.1.1, código 72379, mínimo API23. Telegram oficial 13.2.2 continúa pendiente; no se simula cambiando la versión.

APK ARM64 de pruebas: output/dev-amp-20261127/telebots-dev-samsung-arm64.apk, 74819320 bytes.
SHA-256: ce076ea5583779904c6328a07b444f7f0c8c589351d416c4a54d2677acce12f5.
Firma verificada, certificado público de desarrollo a08d7dc323ddf71ef3201944397e0d3cce7d40847263e11f328b68bbe19229ab; no es clave estable de producción. zipalign -c -P 16 4 correcto y las 10 bibliotecas ARM64 tienen alineación ELF de 16 KiB. Esto no prueba ejecución ARM64 en Samsung.

Las compilaciones incluyen dos modificaciones previas y sin commit del usuario en el submódulo media (WakeLockManager.java y WifiLockManager.java), conservadas sin modificación. No se cambió el gitlink ni se publicaron commits inaccesibles de ese submódulo. La reproducción exacta desde un checkout limpio aún requiere revisar esas diferencias.

## Pruebas ejecutadas

- run_amp_tests.py: URLs, descubrimiento, enmarcado HTTP, direcciones públicas y DNS Tor simulado; paleta 19 comprobaciones. Sin circuito Tor real.
- test_security_boundaries.py: permiso SMS, cifrado autenticado y plazos/cierre de sockets, incluidos casos de bucle local.
- test_p2p_frame.py: entrega TCP local, límite, trama incompleta y timeout.
- test_community_compatibility.py: 68 límites API21–37; no acredita soporte Android21 completo.
- test_low_memory_policy.py: 10 límites y reintento sin contexto.
- test_community_performance.py: aplicar/deshacer, preservación de otras preferencias y precarga.

Instrumentación Android: nueve resultados correctos (tres bloques por tamaño). Lector: idioma alternativo y desactivación. Reproductor: métricas reales de texto y creación/reutilización de vídeo y audio, sin afirmar FPS medidos. Herramientas: navegación a cancelar envío, historial de borradores y respuestas pendientes; callback cancelado y ejecutado por plazo, sin enviar mensajes.

| Tamaño de prueba | Resolución/densidad | Idioma | Reproductor | Herramientas |
|---|---|---|---|---|
| Pixel 9 | 1080×2424 / 420 dpi | PASS | PASS | PASS |
| A56 aproximado | 1080×2340 / 420 dpi | PASS | PASS | PASS |
| Tab A9+ aproximada | 1200×1920 / 240 dpi | PASS | PASS | PASS |

Los tres tamaños se ejecutaron secuencialmente en emulator-5580, API37 x86_64 con páginas 16 KiB. No son tres sistemas Samsung: no reproducen One UI, chipset, módem ni API13/15. Los overrides de pantalla se restauraron. Arranque final: PID 11320, búfer de crash para ese proceso vacío. Evidencia: emulator-results.json, nueve logs, tres capturas y final-startup.log en output/dev-amp-20261127/.

El test antiguo quedaba bloqueado por el arranque síncrono/alias. Se usa LaunchActivity explícita y ActivityMonitor con plazo de 15 segundos. La comprobación de navegación tablet ahora consulta la pila del fragmento de ajustes; antes leía la pila principal y fallaba pese a la presentación en capas. Primera ejecución fallida registrada; la matriz final se repitió completa y pasó.

## Diseño y límites

Se consultaron Tools/DESIGN_BASELINE.md y Canva DAHaJwE0B-A. Continúan siendo propuestas rechazadas, no una referencia aprobada del lector. No se identificó un archivo Figma específico vinculado al lector. No se crearon pantallas ni reglas de movimiento nuevas. Las PR añaden un interruptor y un gesto usando los componentes existentes; las capturas verifican solo las herramientas indicadas. No se certifican los 16 estilos ni toda la interfaz.

El AVD A56/API35 no arrancó: necesita 7372,8 MB para userdata frente a unos 3175 MB libres. La descarga de API33 falló al extraerse por falta de espacio. Se eliminaron únicamente las dos copias comprimidas descargadas en esta sesión y se recuperaron aproximadamente 3,4 GB; no se eliminaron datos personales ni AVD existentes. La imagen API35 queda instalada, sin prueba de arranque.

Pendientes: dispositivo Samsung físico/One UI; llamadas, descarga real, chat autenticado y gesto de favoritos; toggle de historias con historias reales; lectura offline real y AMP completo en esta candidata; UI oscura/TalkBack/RTL y todas las variantes. La APK ARM64 mantiene el control de acceso existente en dispositivos físicos. La previsualización de acceso usada en x86_64 está limitada por el código a emuladores DEV depurables. No se modificaron credenciales, datos de cuentas ni Play.
