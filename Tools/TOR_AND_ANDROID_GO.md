# Tor integrado, HTTPS y dispositivos de poca RAM

Implementación del 5 de octubre de 2026. Se activa en Ajustes de proxy → Tor
integrado. Android solicita autorización VPN. Requiere Android 7.0/API 24 para
las bibliotecas nativas; el resto de la aplicación conserva su mínimo anterior.

## Qué se enruta

`TorVpnService` establece una VPN **solo para el paquete instalado**, usando
`getPackageName()`: dev, alfa, beta, rc y stable heredan el mismo código. Captura
IPv4, IPv6 y DNS, sin `allowBypass`, aplicaciones excluidas ni DNS externos.
HevSocks5Tunnel convierte TCP a SOCKS5 hacia Tor. El DNS mapeado transmite los
nombres por SOCKS para que Tor los resuelva. ICMP sintético está desactivado.
Tor no transporta UDP: no se proporcionan llamadas ni transporte UDP alternativo.

Tor se conecta a sus nodos mediante un SOCKS5 local autenticado, `TorEgress`.
Su contraseña se genera para cada servicio; no se publica en registros. Este
componente solo acepta direcciones IP numéricas y conexiones TCP. Crea el
descriptor del socket y exige `VpnService.protect()` **antes** de conectar.
Así Tor puede alcanzar sus nodos sin permitir a los otros componentes saltarse
la VPN. Los listeners se vinculan exclusivamente a loopback con puertos dinámicos.

Antes de completar el circuito y si Tor falla, la VPN mantiene su interfaz abierta
y bloqueada. MTProto usa además un destino loopback cerrado hasta que Tor y el
túnel están preparados. Se suspende la rotación y la comprobación de otros proxies.
Desactivar Tor restaura el proxy que había guardado el usuario.

## Límites que deben comunicarse

- La garantía de bloqueo del paquete corresponde al tiempo en que **la VPN está
  establecida**. Revocar su permiso, sustituirla por otra VPN, forzar la detención
  o matar el proceso elimina la interfaz. No se ha implementado una garantía de
  ausencia de tráfico de todos los SDK antes del establecimiento al arrancar.
- No controla paquetes externos. Firebase/Google Play Services, otras aplicaciones
  y navegadores externos tienen sus propios procesos/UID. Los enlaces HTTPS que
  abre `Browser.openUrl` con Tor habilitado se mantienen en el navegador interno.
- Una cuenta de Telegram sigue identificando al usuario ante Telegram. No se
  transforma MTProto en HTTPS ni se promete anonimato de la cuenta.
- La VPN ocupa el servicio VPN de Android: no se combina con otra VPN del usuario.
- No se incluyen puentes ni transportes para redes que bloqueen Tor.
- No se valida una publicación en tiendas por compilar la aplicación.

## Indicador de latencia

La burbuja del menú de conexión/proxy y la pantalla de proxy muestran el RTT
medido por el motor MTProto (`native_getCurrentPingTime`) para la cuenta actual.
No es ICMP, una respuesta local del túnel ni el tiempo de arranque de Tor.
Verde: hasta 200 ms; ámbar: 201–600 ms; rojo: más de 600 ms. Sin conexión o sin
muestra positiva se muestra «Sin medición» en gris. Se actualiza cada cinco
segundos solo mientras la pantalla está visible, sin generar sondeos adicionales.

## HTTPS

Los enlaces HTTP que pasan por la entrada principal `Browser.openUrl` se elevan
a HTTPS. El manifiesto rechaza tráfico web en claro en las APIs que aplican esa
política. Los WebViews de artículos, pagos y contenido incrustado rechazan
contenido mixto HTTP. Los sitios antiguos que no admitan HTTPS pueden dejar de
cargar; no se rebaja automáticamente a HTTP. Esta política de Android no es un
filtro universal de los bytes que escriba una biblioteca nativa en sockets.

## Android Go / poca RAM

Se usa `ActivityManager.isLowRamDevice()` en vez de inferir Android Go por marca
o nombre del dispositivo. Estos equipos reciben la categoría de rendimiento
bajo incluso si conservaban una clasificación alta de una versión anterior.
La caché principal de imágenes se limita a 8 MiB y no se fuerza ARGB8888 por una
clase de memoria alta. Se enmascaran en ejecución efectos de cristal, desenfoque,
fondos animados, partículas y reproducción automática de vídeos/GIF, conservando
las preferencias guardadas. Las señales de memoria baja liberan las cachés ya
creadas sin construir un ImageLoader nuevo durante la presión de memoria.
El túnel limita sesiones a 128, buffers TCP a 4 KiB y DNS mapeado a 512 entradas.
Estas medidas reducen límites de recursos; no equivalen a una medición de fluidez
ni de autonomía en un teléfono Android Go real.

## Reproducir las comprobaciones

Compilar como indica `ANDROID_COMPATIBILITY.md`. La biblioteca Tor y Hev se
descargan de sus distribuciones oficiales con versión y SHA-256 fijados; las
licencias están en `TMessagesProj_Tor`.

Prueba JVM del transporte protegido (no establece conexiones de Internet):

```powershell
javac -d .gradle/tor-tests Tools/tests/stubs/android/net/VpnService.java TMessagesProj/src/main/java/org/telegram/utils/proxy/TorEgress.java Tools/tests/TorEgressTest.java
java -cp .gradle/tor-tests org.telegram.utils.proxy.TorEgressTest
```

El host **solo debug**, protegido por `android.permission.DUMP`, admite
`--ez proxy true` para mostrar `ProxyListActivity` sin iniciar sesión.
`--es torAction probe` solicita `https://check.torproject.org/api/ip` mediante
HTTPS normal, sin configurar proxy en esa petición; registra únicamente IsTor
o el tipo de fallo, sin imprimir la IP. No se incluye en APK release.

## Resultado de verificación (2026-10-05)

- Compilación `:TMessagesProj_App:assembleAfatDebug`, canal dev, ARM64: correcta
  (323 tareas). Firma y alineación ZIP de 16 KiB comprobadas; los segmentos ELF
  de Tor y Hev ARM64 tienen alineación `0x4000`.
- Emulador API 37 x86_64 con traducción ARM64 y páginas de 16 KiB: permiso VPN,
  arranque hasta 100 % y petición HTTPS ordinaria con `IsTor=true`.
- Detención deliberada de `EmbeddedTorService`: la petición HTTPS falla con
  `ConnectionResetException`; `TorVpnService` y las rutas IPv4/IPv6 permanecen
  activos y Android informa `bypassable=false`.
- Desactivación: desaparecen ambos servicios y la VPN, y la misma comprobación
  HTTPS devuelve `IsTor=false`. Se corrigió el cierre explícito del TUN porque
  `stopService()` por sí solo no destruye un servicio enlazado por Android.
- HTTP en claro rechazado con `Cleartext HTTP traffic to example.com not permitted`.
- Pruebas JVM de autenticación y socket protegido, regresiones AMP y diseño,
  y tres pruebas Python del catálogo: correctas.
- No se han probado cuentas reales, las otras cuatro APK de canal, Android 7–16
  en dispositivos físicos ni rendimiento/autonomía en Android Go. El indicador
  se comprobó visualmente en su estado sin medición; falta comprobar valores
  de RTT en una sesión real de Telegram.

La APK de desarrollo ARM64 se conserva en
`.gradle/channel-apks/telebots-dev-12.10.6-tor-debug.apk`. Es una compilación debug
con `testOnly`, no una versión estable firmada para distribución.

Fuentes: [Android Go](https://developer.android.com/guide/topics/androidgo?hl=es-419),
[VPN Android](https://developer.android.com/develop/connectivity/vpn),
[Tor Android](https://github.com/guardianproject/tor-android),
[Hev 2.18.0](https://github.com/heiher/hev-socks5-tunnel/releases/tag/2.18.0).
