# APK firmados descargables desde GitHub

El workflow `.github/workflows/android-apk.yml` compila una APK release de Android bajo demanda y deja la descarga en **Actions → Android APK firmado → ejecución → Artifacts**. Mantiene los APK de los canales Telegram; no los sustituye ni envía publicaciones a esos canales. Tampoco publica en Google Play.

## Preparación del repositorio

1. Incorporar el workflow, `Tools/release/BuildVars.java.template` y el código Android revisado. Para que aparezca el botón **Run workflow**, el workflow debe existir en la rama predeterminada del repositorio. Elegir después la rama que se quiere compilar en ese botón.
2. Crear los entornos GitHub `android-dev`, `android-alfa`, `android-beta`, `android-rc` y `android-stable`. Configurar revisores y restricciones de ramas en cada entorno antes de almacenar los secretos. El YAML referencia estos entornos, pero no puede crear sus reglas de protección.
3. Configurar en cada entorno los secretos de la tabla. Puede usarse la misma firma para los cinco paquetes si ésa es la política elegida. No subir los archivos originales de credenciales al repositorio.
4. Ejecutar manualmente con el canal de paquete y `arm64-v8a` o `universal`. La rama seleccionada y el canal son independientes: revisar ambos antes de lanzar. El checkout usa el SHA exacto del evento, incluidos submódulos.

| Secreto | Contenido |
|---|---|
| `TELEGRAM_API_ID` | API ID propio de Telegram, entero positivo |
| `TELEGRAM_API_HASH` | API hash propio de 32 caracteres hexadecimales |
| `KEYSTORE_BASE64` | Contenido binario del keystore codificado en base64 |
| `STORE_PASSWORD` | Contraseña del almacén |
| `KEY_ALIAS` | Alias de la clave de firma |
| `KEY_PASSWORD` | Contraseña de la clave |
| `CERT_SHA256` | Huella SHA-256 esperada del certificado, con o sin dos puntos |

La preparación comprueba secretos obligatorios y aborta si falta alguno. No sustituye una firma faltante por debug. El archivo Java se genera desde la plantilla sin API ID/hash personales, y las propiedades privadas se escriben en el directorio temporal del runner. No hay caché de Gradle ni archivo de fuentes como artefacto. Un paso `always()` elimina los archivos de firma y credenciales; además el runner alojado es efímero.

## Qué se descarga

El artefacto contiene únicamente:

- `telebots-<canal>-<arquitectura>.apk`.
- `SHA256SUMS.txt` para verificar la descarga.
- `build.json` con commit, canal, arquitectura, versión, paquete y hash.

Antes de subirlo se comprueba la firma contra `CERT_SHA256`, el paquete `org.comunidadtelebots.<canal>`, que no sea debuggable y la alineación ZIP de 16 KB. Esta última prueba no reemplaza la auditoría de todos los segmentos ELF nativos exigida para la compatibilidad con páginas de 16 KB.

Los artefactos caducan a los 14 días, sujetos a la política del repositorio, y su descarga exige los permisos que impone GitHub. Para enlaces permanentes de distribución se puede publicar después una GitHub Release revisada; este workflow sólo tiene permiso `contents: read` y no crea releases. La APK ARM64 requiere un dispositivo ARM64; universal incluye las ABI configuradas por el proyecto y necesita más recursos.

## Recursos y alcance de seguridad

Usa `ubuntu-24.04`, Temurin 21, Android SDK 36/build-tools 36.0.0, NDK 27.2.12479018 y CMake 3.22.1. Las herramientas SDK se instalan mediante el `sdkmanager` proporcionado por la imagen oficial de GitHub. Gradle dispone de 5 GB de heap, dos workers y un límite de ejecución de 180 minutos. El cliente completo contiene compilación C++ pesada: el primer build puede fallar por recursos y debe evaluarse con el log real, especialmente universal o runners de repositorios privados más pequeños.

Se permite `workflow_dispatch` y push exclusivamente a la rama de integración revisada `codex/downloadable-builds`. Ese push genera dev/ARM64 para validar el primer build antes de incorporar el workflow a la rama predeterminada. No ejecuta código de pull requests con secretos. Esto no convierte cualquier rama en confiable: el código Gradle, CMake y scripts de la rama elegida puede leer credenciales durante la compilación. Revisar ese código y usar protecciones de entorno. Las acciones están fijadas por SHA completo comprobado contra los repositorios oficiales: checkout v6.0.2, setup-java v5.5.0 y upload-artifact v6.0.0.

El workflow no garantiza cumplimiento de condiciones de Telegram/Google Play ni elimina la necesidad de revisar privacidad, licencias, atribución y funcionamiento de la APK. No recopila datos de usuarios finales. Los registros de GitHub son registros de compilación; no se cargan directorios de datos, credenciales o logs de la aplicación.

## Estado de validación

Preparación local: estructura YAML, bloques Python y shell comprobados estáticamente. Todavía debe verificarse una ejecución real en GitHub con los secretos y reglas de entorno configurados. No se declara una APK nueva publicada hasta que el artefacto figure en una ejecución exitosa.

Fuentes oficiales:

- [Ejecutar manualmente un workflow](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow).
- [Entornos de despliegue y protección](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments).
- [Descargar artefactos](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/download-workflow-artifacts).
- [Checkout](https://github.com/actions/checkout/releases/tag/v6.0.2), [setup-java](https://github.com/actions/setup-java/releases/tag/v5.5.0) y [upload-artifact](https://github.com/actions/upload-artifact/releases/tag/v6.0.0).


La selección de APK utiliza `select_apk.py` y el `output-metadata.json` de AGP:
acepta las ubicaciones `build/outputs/apk` y `build/intermediates/apk`, exige
`variantName=afatRelease`, tipo APK, paquete del canal y un único archivo sin
splits. Si AGP conserva copias en ambas ubicaciones, sólo acepta duplicados con paquete,
versionCode, versionName y SHA-256 del APK idénticos, dando prioridad a outputs.
Rechaza rutas externas, resultados diferentes y cualquier fallback a debug.
Después se mantienen todas las comprobaciones criptográficas y de manifiesto.
La ubicación intermedia por sí sola no autoriza una publicación: se requiere
compilación completada correctamente, metadatos release y verificación posterior.
