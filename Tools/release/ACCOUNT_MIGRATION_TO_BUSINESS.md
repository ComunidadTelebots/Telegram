# Migración de titularidad operativa — Comunidad Telebots

**Cuenta de destino indicada por el propietario:** `redes@todosobreall.tech`.
**Cuenta de origen:** `adrian.gonzalez.vallejo@gmail.com`.
**Firebase existente:** proyecto `comunidad-telebots` (Google Cloud project ID, mantenerlo intacto).
**Paquete Play/Firebase DEV:** `org.comunidadtelebots.dev`.

## Importante: cuenta de correo, proyecto Firebase y cuenta desarrollador Google Play NO son lo mismo

El camino seguro es **dar acceso primero, transferir después**. No crear otro proyecto Firebase ni volver a registrar el paquete Android: esto rompería la continuidad de Crashlytics, instalaciones y estadísticas.

### 1. Firebase y Google Cloud

Con la cuenta de origen, abrir https://console.firebase.google.com/project/comunidad-telebots/settings/iam y elegir «Añadir miembro». Invitar a la cuenta empresarial y asignar **Propietario** si se desea transferir realmente el control. Aceptar la invitación iniciando sesión con la cuenta empresarial (debe existir como cuenta Google/Google Workspace).

En Google Cloud IAM: https://console.cloud.google.com/iam-admin/iam?project=comunidad-telebots confirmar que la cuenta empresarial figura como propietaria y puede ver Crashlytics, Analytics, facturación y credenciales. Comprobar los responsables de facturación y contactos esenciales. **No retirar la cuenta original** hasta que la nueva propietaria confirme acceso efectivo y la nueva infraestructura de despliegue esté operativa. Evitar cambiar el project ID, eliminar el proyecto o regenerar `google-services.json` sin necesidad.

### 2. Google Play Console

Si ambas direcciones pertenecen a la **misma cuenta desarrollador** (la cuenta origen es la titular), se puede invitar a `redes@todosobreall.tech` en Play Console → Usuarios y permisos, con los permisos apropiados para administrar la app y sus pistas de pruebas, sin transferir aplicaciones ni resetear la cuenta.

Si la intención es que `redes@todosobreall.tech` sea **titular de otra cuenta de desarrollador distinta**, esa cuenta de destino debe estar verificada y activa. Iniciar la transferencia oficial de apps desde Play Console con ambas cuentas registradas; Google Play requiere aprobación de la cuenta de destino. La cuenta desarrollador de destino nueva puede exigir la tasa de registro. Las pistas/grupos de pruebas cerradas deben recrearse y las integraciones de Firebase/Cloud vincularse de nuevo cuando corresponda. Documentación: https://support.google.com/googleplay/android-developer/answer/6230247?hl=es

Antes de transferir: verificar el paquete `org.comunidadtelebots.dev` existente en Play Console. Si nunca se creó, hay que **crear la ficha en la cuenta definitiva**, en vez de transferir una app inexistente. No se puede subir una AAB a un paquete que no exista en la consola.

### 3. Automatización GitHub Actions

La identidad de publicación es una **cuenta de servicio IAM/Google Cloud** con permisos limitados a la app y pista cerrada pertinentes; no se debe poner la contraseña personal ni un token OAuth personal en GitHub.

Secretos propuestos de GitHub Actions, en un environment protegido `google-play-alpha`:
- `PLAY_SERVICE_ACCOUNT_JSON` (cuenta de servicio autorizada en Play Console)
- `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `CERT_SHA256` (firma de subida estable y verificada)
- `TELEGRAM_API_ID`, `TELEGRAM_API_HASH` (ya usados por CI)

La cuenta de servicio no equivale a la titular de Firebase: será autorizada por la cuenta empresarial. Usar permisos mínimos, rotación de claves y nunca almacenar secretos en el repositorio.

**Regla:** ningún despliegue Play puede firmarse con la keystore temporal generada por CI. Google Play App Signing mantiene su propia clave de firma de distribución, distinta de la clave de subida si se delega el firmado a Google.

### 4. Control de calidad y telemetría

Publicar en pista **alpha** (closed testing) después de: lint y comprobaciones de recursos, compilación Android App Bundle, validación de paquete y versión, firma estable, validación de manifest, instalación/emulación de una APK universal o splits derivados del bundle, smoke test UI, y asociación Firebase. Los errores de instalación se consultan en Google Play Console/ADB, mientras que Crashlytics recopila cierres/ANR una vez arrancada la app y con cobertura real en dispositivos.

El cambio de titularidad **no se ha realizado** desde GitHub. Requiere iniciar sesión con las cuentas Google pertinentes, invitaciones y aceptación, y en su caso solicitud de transferencia.
