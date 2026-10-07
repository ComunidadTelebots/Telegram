# Compilación y publicación por canales

Dos productos, cinco destinos, una web pública que solo consume `stable`.
El servicio es un publicador dedicado a APK (`PRODUCTS=android`). Para publicar
también React, configurar explícitamente `PRODUCTS=android,react`.
No crea una identidad Telegram ni registra por sí mismo un bot en Moonbot.
Moonbot ya admite bots administrados: la identidad hija puede crearse mediante
un enlace `https://t.me/newbot/USUARIO_GESTOR/USUARIO_HIJO?name=NOMBRE` y la
confirmación del propietario en Telegram. El gestor debe tener `can_manage_bots`.

## Destinos facilitados por el propietario

| Canal | ID de Bot API |
|---|---|
| stable | -1004211289901 |
| rc | -1004326004401 |
| beta | -1004377260003 |
| alfa | -1004307525605 |
| dev | -1004315521214 |

Se han interpretado los números positivos como IDs internos de canales y añadido
el prefijo `-100`. `getChat` y `getChatMember` verifican tipo y permisos antes de
compilar. Android y React comparten los destinos; se distinguen en nombre y pie
del archivo. Cambiar `channels.json` permite separarlos.

## Preparación del servidor

1. Crear una identidad hija administrada desde Moonbot (o una independiente en
   BotFather) y agregarla como administradora a
   los cinco canales con permiso de publicar. No reutilizar el token de Moonbot:
   un bot no puede utilizar simultáneamente el Bot API público y uno local.
   Si Moonbot autoconecta el hijo como bot general, detener esa instancia antes
   de migrar su token al publicador local; conservar el vínculo con el gestor.
2. Copiar `.env.example` a `.env` y `channels.configured.json` a `channels.json`.
   Crear `secrets/bot_token`, `secrets/api_id`, `secrets/api_hash`, solo legibles
   por el operador. Los últimos dos son credenciales de my.telegram.org para el
   servidor Bot API, no el token del bot. Si el bot ya utilizó la API pública,
   migrarlo con `logOut` según la documentación oficial antes de arrancar aquí.
3. Crear WORK_ROOT (privado, sin servir por HTTP), SIGNING_DIR y PUBLIC_DIR.
   SIGNING_DIR contiene `release.keystore`, `gradle.properties` con
   RELEASE_STORE_PASSWORD/RELEASE_KEY_ALIAS/RELEASE_KEY_PASSWORD, y el
   `BuildVars.java` real con el API ID/hash de la aplicación. Añadir
   `certificate.sha256` con la huella SHA-256 esperada del certificado de firma;
   la subida se bloquea si no coincide. Mantener la misma
   clave de firma para permitir actualizaciones sobre instalaciones anteriores.
4. Proteger las etiquetas `ready/**` en ambos repositorios de GitHub: solamente
   responsables de releases pueden crearlas; no permitir cambios ni borrados.
   El código de compilación recibe la clave de firma; solo se debe compilar
   código revisado. Usar un host de compilación dedicado: el worker tiene acceso
   al socket Docker y por tanto privilegios sobre ese host.
5. En Linux, desde esta carpeta:

   ```sh
   docker compose --profile images build
   docker compose up -d worker bot-api
   docker compose logs -f worker
   ```

   Esperar los dos mensajes `baseline recorded`. Las etiquetas preexistentes se
   registran sin publicar para evitar enviar el historial al iniciar el servicio.
   El Bot API no publica puertos; se accede solo por la red Docker interna.
   No instalar otro worker sobre el mismo directorio de estado.

## Marcar código preparado en GitHub

En el repositorio de cada producto, después de revisar y validar el commit:

```sh
git tag ready/rc/1.0.0 COMMIT_REVISADO
git push origin ready/rc/1.0.0
```

El worker consulta GitHub cada 60 segundos, fija el commit, compila en un
contenedor temporal y solo envía el archivo si todo termina correctamente.
La versión de la etiqueta debe coincidir con versionName del APK (APP_VERSION_NAME)
o con package.json en React. Actualizar también APP_VERSION_CODE en Android antes
de cada publicación: cambiar únicamente una etiqueta no actualiza el manifiesto.
`ready/stable/1.0.0` exige versión final sin sufijos. Los demás canales admiten
sufijos. Una etiqueta es inmutable y no se vuelve a procesar automáticamente.
React produce un tar.gz del cliente web; Android produce un APK release universal,
verifica su firma y paquete. Este proceso **no despliega** automáticamente el
cliente React sobre `tg.todosobreall.tech`; entrega el paquete al canal indicado.
Los repositorios actuales se leen por HTTPS sin credenciales. Para repositorios
privados hace falta configurar autenticación Git antes de activar la publicación.

Los contenedores compilan de uno en uno, con un límite de 3 GiB de RAM, 1 GiB
adicional de swap y una CPU. Gradle utiliza un único worker. Reservar
espacio suficiente para SDK/NDK, fuentes y compilaciones (decenas de GB); no
activar en el VPS compartido sin comprobar capacidad. Los trabajos se conservan
en WORK_ROOT para diagnóstico y requieren una política de retención del operador.

## Web pública

`site/index.html` sustituye la portada de `apps/chat/index.html` del repositorio
Todosobrealltech. PUBLIC_DIR debe ser `apps/chat/releases` en ese despliegue.
Solo tras una publicación estable confirmada se escribe `android.json` o
`react.json` de forma atómica; no se publican canales de prueba, IDs privados,
tokens ni logs. Una publicación antigua no rebaja la versión visible.
Los enlaces de descarga solo se muestran si Telegram devuelve un username
público del canal stable. Si es privado, queda la ficha sin enlace de descarga.
La web no certifica como estables funciones o versiones que aún no se han publicado.

## Fallos y recuperación

`state.sqlite` registra baseline/building/failed/sending/sent. Si Telegram recibe
un archivo pero su respuesta se pierde, el estado queda `sending`: **no hay
reintento automático** que duplique mensajes. Revisar el canal y conciliar el
estado antes de cualquier reenvío. Un fallo de compilación queda `failed`; corregir
el código y crear una nueva etiqueta. No borrar la base de datos para reintentar:
se perdería el registro de publicaciones. El feed se reconstruye desde los
registros `sent` si falla su escritura.

## Verificación local

```sh
python -m unittest discover -s Tools/release -p 'test_*.py' -v
docker compose -f Tools/release/compose.yaml config --no-interpolate
```

Se necesita Docker Linux en funcionamiento para probar las imágenes completas.
No se considera validada una entrega hasta compilar con credenciales reales,
instalar el APK y comprobar su firma esperada, y probar la subida al canal.

Referencias: https://core.telegram.org/bots/api#using-a-local-bot-api-server
y https://github.com/tdlib/telegram-bot-api.


## Memoria de compilación Android

El build observado en el VPS compartido falló en `minifyAfatReleaseWithR8` con
`Java heap space` y heap de 1800 MiB. Es un límite del proceso Java: cambiar la
firma o repetir exactamente ese build no lo corrige. No se ha aumentado la carga
del VPS ni se han relanzado builds allí para resolverlo.

Los valores predeterminados se conservan: contenedor 3072 MiB, RAM+swap 4096 MiB,
una CPU, heap 1800 MiB, metaspace 512 MiB y un worker. `build_resources.py` valida
enteros acotados, workers <= CPU y que heap + metaspace + reserva nativa quepan
en RAM. La validación se repite dentro del contenedor contra su límite cgroup;
no cuenta swap como RAM disponible. El fallo de validación aborta antes de clonar
código o copiar las claves de firma. Estos límites son una comprobación mínima,
no una garantía de que Gradle, R8 y todos los compiladores nativos quepan.

En una **máquina dedicada**, con al menos 16 GiB de RAM y capacidad suficiente
para el SO y la compilación C++, se puede evaluar este perfil inicial:

```dotenv
ANDROID_BUILD_MEMORY_MB=12288
ANDROID_BUILD_SWAP_MB=12288
ANDROID_BUILD_CPUS=4
ANDROID_GRADLE_HEAP_MB=6144
ANDROID_GRADLE_METASPACE_MB=1024
ANDROID_GRADLE_WORKERS=2
```

`ANDROID_BUILD_SWAP_MB` es el límite total RAM+swap de Docker; igualarlo a RAM
impide usar swap. El perfil dedicado no ha sido compilado y puede requerir ajustes
según mediciones reales. No aplicarlo al servidor compartido de 8 GiB. Un ARM64
primero reduce el trabajo nativo, pero no elimina el consumo de R8. Mantener R8 y
las comprobaciones de firma: no usar debug o desactivar minificación como una
supuesta corrección de release.

Los parámetros se transmiten desde Compose al worker y de éste al builder.
Actualizar parámetros exige reconstruir las imágenes de worker/android para
incluir el validador y recrear el worker en el host elegido. Para ejecutar
`docker run` directamente, pasar las mismas variables `ANDROID_*` y límites
`--memory`, `--memory-swap`, `--cpus`; el constructor verifica inconsistencias
contra el límite real del contenedor.


La selección de APK utiliza `select_apk.py` y el `output-metadata.json` de AGP:
acepta las ubicaciones `build/outputs/apk` y `build/intermediates/apk`, exige
`variantName=afatRelease`, tipo APK, paquete del canal y un único archivo sin
splits. Si AGP conserva copias en ambas ubicaciones, sólo acepta duplicados con paquete,
versionCode, versionName y SHA-256 del APK idénticos, dando prioridad a outputs.
Rechaza rutas externas, resultados diferentes y cualquier fallback a debug.
Después se mantienen todas las comprobaciones criptográficas y de manifiesto.
La ubicación intermedia por sí sola no autoriza una publicación: se requiere
compilación completada correctamente, metadatos release y verificación posterior.

## Mejoras de bajo coste revisadas (7 de octubre de 2026)

Implementadas en Android en esta revisión:

- Caché DNS cifrada en RAM: hasta 128 entradas, separadas por proveedor, ruta,
  nombre y tipo. Respeta el menor TTL de la respuesta y limita la retención a cinco
  minutos. No almacena errores ni respuestas vacías. Se limpia al cambiar selección.
- Detección automática de AMP opcional solo en redes no medidas y respeto al ahorro
  de datos de Android. No cambia las vistas rápidas oficiales ni la apertura manual.

Ambas funcionan sin un servidor adicional. No se han medido mejoras porcentuales
de batería o velocidad en dispositivos reales.

Opciones siguientes, todavía no implementadas:

| Opción | Utilidad | Coste y criterio |
| --- | --- | --- |
| Baseline/Startup Profiles | Optimizar arranque y recorridos frecuentes | Generar perfiles reales y medir una APK release; no inventar reglas ni prometer porcentajes. |
| Lector sin conexión voluntario | Guardar artículos públicos para leer sin red | Solo local, límite de tamaño y borrado; no guardar contenido restringido ni sincronizarlo automáticamente. |
| Metadatos públicos stable con ETag | Reducir consultas a Alltech | Aplicarlo solo al catálogo público; nunca cachear autorización, usuarios o respuestas privadas en una caché compartida. |
| Distribución de APK por canales/GitHub | Evitar servir cada APK desde Alltech | Ya existe infraestructura de compilación/publicación en este directorio; mantener esta vía primero. |
| BitTorrent opcional para stable | Repartir descarga de archivos públicos grandes | Solo con consentimiento, Wi-Fi no medida y límites de batería/subida; verificar hash, firma APK y paquete. Requiere semilla inicial y fallback HTTPS. No distribuir dev/alfa/beta/rc ni contenido de chats. |
| ZeroNet | Publicación de sitios distribuidos | No es un sustituto directo del actualizador Android; añade runtime, mantenimiento y exposición P2P. No se incorpora en esta revisión. |

La lista de peers de BitTorrent intercambia direcciones IP. Un torrent privado no
convierte un APK distribuido en confidencial ni evita que un receptor lo copie.
Por ello no se introduce P2P por defecto ni para ramas con control de acceso.
Si se necesitan espejos públicos, se puede añadir al Docker de publicación existente
un servidor estático limitado a stable, detrás del HTTPS actual, con ETag y Range;
no hace falta desplegar otro backend para las dos mejoras implementadas.

Fuentes de la evaluación:
- [Perfiles de rendimiento Android](https://developer.android.com/topic/performance/baselineprofiles/overview).
- [Consumo de red en segundo plano](https://developer.android.com/topic/performance/issues/bg-network-usage).
- [BitTorrent privado y peers](https://www.bittorrent.org/beps/bep_0027.html).
- [Repositorio oficial de ZeroNet](https://github.com/HelloZeroNet/ZeroNet).
