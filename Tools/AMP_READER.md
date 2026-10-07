# Lector AMP

La integración del navegador interno está en `ArticleViewer`. Junto al control IV
aparece AMP cuando la página abierta declara una versión AMP y su documento se
ha comprobado. En ventanas estrechas, ambas acciones están en el menú.

La detección del navegador consulta la página que el usuario ya abrió y su enlace `rel=amphtml`.
Al pulsar AMP se abre el lector nativo local. Si el documento declara restricciones
de acceso, se muestra un aviso local con un enlace opcional al medio: no se extrae
el contenido protegido ni se abre automáticamente el navegador. Para documentos
sin esas restricciones se intentan las cachés Google y Bing;
si no responden con un documento utilizable, se conserva el documento del editor.
El título indica la fuente utilizada. Que una caché figure en el código no garantiza
que tenga una copia de todos los artículos.

El contenido se convierte con el lector Readability incluido en el proyecto a los
bloques nativos de Vista rápida. No se ejecutan los scripts remotos del artículo.
La extracción puede omitir componentes interactivos y contenido que requiere sesión;
no pretende eludir suscripciones ni reproducir toda la página original.

Las descargas usan HTTPS, límites de tamaño, redirecciones y tiempo, y validación de
direcciones públicas. Si Tor está activo se usa su proxy y se rechaza el cambio de
ruta durante la operación. Las imágenes del lector AMP utilizan el mismo transporte.
La caché de descubrimiento es temporal y está en memoria.

### Tarjetas de mensajes

`MessageLinkPreview` mantiene los metadatos aparte del mensaje original: no sustituye
una foto por una página web ni envía mensajes. Para fotos con enlace en la descripción,
consulta a Telegram por el primer enlace HTTPS válido, sin enviar el resto del texto.
Las tarjetas muestran Vista rápida si hay `cached_page` y AMP si se ha detectado una
versión AMP. La detección automática consulta al editor y puede comunicarle la IP
de la conexión (la salida de Tor cuando está activo); Google/Bing se consultan al pulsar.

Las solicitudes de tarjetas se limitan a contenido visible; se cancelan sus callbacks
al reciclar la celda. No se consulta AMP automáticamente para chats secretos. Las
previsualizaciones secretas respetan el ajuste existente de Telegram. Se excluyen
mensajes borrados, contenido temporal y texto con spoilers sin revelar.

Cloudflare no se configura como proveedor: su servicio AMP/SXG fue retirado.

Referencias consultadas el 7 de octubre de 2026:

- [Cachés AMP](https://amp.dev/documentation/guides-and-tutorials/learn/amp-caches-and-cors/how_amp_pages_are_cached)
- [Registro de proveedores](https://github.com/ampproject/amphtml/blob/main/build-system/global-configs/caches.json)
- [Retirada de Cloudflare AMP/SXG](https://developers.cloudflare.com/changelog/post/2026-06-23-amp-sxg-end-of-life/)

## Verificación

Las pruebas de `Tools/tests` cubren URLs, descubrimiento, lectura y límites del
transporte. `amp_reader_fixture.cjs` ejecuta el extractor real con un artículo de
prueba y verifica párrafos e imagen. Estas pruebas no sustituyen la comprobación
visual del lector en Android ni la disponibilidad de cada proveedor en una red real.

Validación de red del 7 de octubre: el ejemplo `basic_markup` de amp.dev respondió
con AMP utilizable tanto desde el editor como desde Google. El dominio de Bing
probado no resolvió por DNS; no se afirma que Bing esté operativo para ese artículo.
La resolución seleccionó Google correctamente.

El transporte limita cada respuesta a 1 MiB. Los sockets tienen un timeout de diez
segundos y la operación comprueba un presupuesto de 45 segundos; la resolución DNS
del sistema, cuando Tor está desactivado, no es cancelable con ese presupuesto.
El protocolo de resolución DNS de Tor se probó con un servidor SOCKS simulado;
queda pendiente comprobarlo con un circuito Tor real. La comprobación de AMP detecta
el documento y su runtime, pero no equivale al validador completo de la especificación.

## Otras tecnologías revisadas (7 de octubre de 2026)

Esta lista es una evaluación, no una declaración de funciones nuevas implementadas.

| Tecnología | Aplicación propuesta | Situación / prioridad |
| --- | --- | --- |
| [Open Graph](https://ogp.me/) | Título, imagen y URL canónica de tarjetas de enlaces | Priorizar las tarjetas; aprovechar primero los metadatos que entrega Telegram. |
| [Schema.org / JSON-LD](https://schema.org/Article) | Autor, fecha y tipo de artículo | Readability ya analiza parte de estos datos; mejorar su aprovechamiento antes de introducir otro parser. |
| [Readability](https://github.com/mozilla/readability) | Lectura de artículos que no tienen AMP | Ya incluido en `instant.js` y usado por Vista rápida local; ampliar cobertura y pruebas. |
| [oEmbed](https://oembed.com/) | Ampliar vistas previas de vídeos y otros contenidos | Hay reproductores específicos existentes; evaluar proveedores concretos, carga al tocar y aislamiento de HTML. |
| [RSS](https://www.rssboard.org/rss-specification) | Suscripciones voluntarias a noticias dentro de un lector | Función adicional posible; requiere pantalla, actualización limitada y almacenamiento propio. |
| [Modo oscuro WebView](https://developer.android.com/develop/ui/views/layout/webapps/dark-theme) | Adaptar páginas al tema de la app | Evaluar `prefers-color-scheme` y oscurecimiento compatible, comprobando capacidades del WebView. |

`BotWebViewContainer` ya activa Safe Browsing para navegación ordinaria desde API 26.
No se propone como función ausente. Para Android Go, mantener tarjetas nativas,
descargas acotadas y multimedia por acción explícita; no crear un WebView por mensaje.

## Inventario de conexiones

La pantalla agrupa en desplegables los dominios HTTPS, DC, CDN de Telegram,
proxies guardados y proxies de Alltech. Los dominios se subdividen en configurados,
CDN AMP y observados. Las secciones conservan su estado durante las actualizaciones.

El catálogo configurado procede de los destinos explícitos de esta compilación:

- `ReleaseAccess`: chat.todosobreall.tech.
- `AlltechProxySource`: todosobreall.tech.
- `ConnectionsManager`: www.google.com, dns.google.com y mozilla.cloudflare-dns.com
  (resolución DNS y mecanismos de respaldo).
- `AndroidUtilities`: static-maps.yandex.ru y maps.googleapis.com (mapas opcionales).
- `LocationController`: ss3.4sqi.net (iconos de lugares).
- `Browser`, enlaces y actualizaciones: t.me, telegram.org y play.google.com.
- `AmpResolver`: cdn.ampproject.org y www.bing-amp.com; los subdominios de caché
  utilizados se añaden al observarlos.

Esto no es una lista exhaustiva publicada por Telegram ni una captura de todos los
SDK, bots, llamadas o sitios que el usuario pueda abrir. Configurado no significa
conexión activa. Los DC y CDN MTProto proceden de la configuración de Telegram y
no se convierten artificialmente en dominios HTTPS. La prueba de dominios, manual,
mide DNS, conexión y TLS en 443, respeta Tor y no evita un proxy no compatible.
Se comprueban secuencialmente hasta 64 entradas; también se puede tocar una sola.

## Recomendaciones y fiabilidad de proxies

Los fallos repetidos (dos pruebas en diez minutos) de Alltech o de los resolutores
configurados muestran una recomendación de probar Tor o comprobar un proxy.
Un DC que no responde también muestra una recomendación, sin afirmar censura.
La conectividad global sin red tiene prioridad. Las comprobaciones de dominios y
DC siguen siendo manuales; no se incorpora una sonda permanente en segundo plano.
Una prueba TLS del resolutor mide su accesibilidad HTTPS, no la corrección de sus
respuestas DNS. No se usa una página de control ni una base de bloqueos confirmados.

La disponibilidad de proxies se calcula a partir del comprobador nativo para todos
los orígenes, incluido Alltech. Tres fallos consecutivos impiden elegir ese proxy
en la rotación automática hasta una prueba correcta o la caducidad del historial
(30 minutos sin pruebas). La evidencia permanece en memoria, limitada a 256 proxies;
la exclusión manual se guarda localmente con una huella de la configuración.
Excluir no corta una conexión activa ni impide una selección manual en Ajustes.
No se infieren baneos ni manipulación a partir del ping: el comprobador disponible
solo ofrece tiempo de respuesta o fallo. No se afirma que un proxy que responde
sea seguro o de confianza. Las garantías criptográficas siguen siendo las de MTProto.

## Catálogo DNS sobre HTTPS

En Dominios y conexión → Servidores DNS se muestran Cloudflare, Google Public DNS
y Quad9. Las banderas US, US y CH indican el país del proveedor, no la ubicación
física del nodo anycast. En entradas personalizadas el país ISO es opcional y se
identifica como declarado por el usuario. Se guardan hasta 20 entradas, con nombre
y URL HTTPS pública sin credenciales, parámetros ni fragmentos; pueden eliminarse.
No se incluyen servidores UDP. «Usar este DNS» aplica la selección a las
resoluciones controladas por la app; no cambia el DNS de Android, WebView o SDKs.
Quad9 se mide mediante DoT (puerto 853), porque retiró HTTP/1.1 para DoH.
Los servidores personalizados admiten actualmente URL DoH.

La medición manual usa una consulta A para example.com en formato DNS wire sobre
HTTPS (RFC 8484) o TLS (RFC 7858) según el protocolo indicado. Incluye resolución inicial del host DoH, conexión, TLS y respuesta.
Valida identificador, pregunta, estado, límites de registros y presencia de una
respuesta A. No verifica DNSSEC ni certifica la honestidad del proveedor. Conserva
las restricciones de red pública, TLS y ruta del transporte AMP. Rechaza redirecciones.
Con Tor, resolución y transporte pasan por Tor; con un proxy sin transporte HTTPS
compatible, no se realiza una conexión directa alternativa. El catálogo se guarda
localmente; los tiempos no se persisten y no se hacen pruebas en segundo plano.

Fuentes oficiales del catálogo (consultadas el 7 de octubre de 2026):

- [Cloudflare DoH](https://developers.cloudflare.com/1.1.1.1/infrastructure/network-operators/)
  y [sede del proveedor](https://www.cloudflare.com/fr-fr/about-overview/).
- [Google Public DNS DoH](https://developers.google.com/speed/public-dns/docs/doh/)
  y [ubicaciones](https://about.google/company-info/locations/).
- [Quad9 DoH](https://www.quad9.net/support/faq)
  y [sede en Suiza](https://www.quad9.net/news/blog/quad9-public-domain-name-service-moves-to-switzerland-for-maximum-internet-privacy-protection).

`Tools/tests/test_dns_probe.py` comprueba respuestas correctas e inválidas,
truncamiento, identificadores, países y URL de servidores.

- [Retirada de HTTP/1.1 en Quad9](https://quad9.net/news/blog/doh-http-1-1-retirement/).

### Selección activa

«Usar este DNS» guarda el servidor para futuras resoluciones de nombres del
transporte AMP/Alltech, los nombres solicitados por tgnet y las consultas TXT de
configuración de respaldo de Telegram. «Automático» restaura el comportamiento
previo. No modifica conexiones ya abiertas ni los DC que usan IP directamente.
Al eliminar una entrada personalizada seleccionada se vuelve a Automático.

El nombre del servidor DNS elegido se resuelve inicialmente por el sistema para
establecer la conexión cifrada; las consultas de destino usan el proveedor elegido.
Tor conserva prioridad y no utiliza el DNS personalizado. Un proxy sin transporte
HTTPS compatible provoca un fallo explícito, no una salida directa alternativa.
No hay fallback silencioso al resolver del sistema si falla la consulta seleccionada.
La caché de tgnet se separa por política de resolución; se descartan respuestas de
una política anterior. Los registros A/AAAA y TXT se validan contra la pregunta y
la cadena de alias CNAME. No se aceptan direcciones privadas en estas rutas públicas.

Pruebas: `test_dns_selection.py`, `test_dns_probe.py` y `run_amp_tests.py`.

## Ubicación de ajustes propios

Los ajustes de la personalización se agrupan en Ajustes → Ajustes Comunidad Telebots:

- Diseños y animaciones: selector de cliente, variantes, versiones de diseño y movimiento.
- Lectura y AMP: ahorro de datos en la detección automática.
- Conexiones y red: DNS activo, dominios/DC/CDN, herramientas de proxies Alltech y Tor.
- Actualizaciones: actualizador de la rama de Comunidad Telebots.
- Privacidad, acceso y condiciones: panel Moonbot y documentos de Comunidad Telebots.
- Canal de Comunidad Telebots.

Los accesos a diseños se retiran de Tema de Telegram y los controles Tor/diagnóstico
se retiran de la lista oficial de proxies. Se conserva la protección de la ruta
cuando Tor está activo. El enlace de privacidad de los ajustes originales vuelve a
PrivacyPolicyUrl; la política propia está en el nuevo apartado. No se cambian las
claves de preferencias ni se restablecen diseños, DNS, proxies o permisos.
Los botones AMP en mensajes y la validación de acceso siguen funcionando donde corresponden.
