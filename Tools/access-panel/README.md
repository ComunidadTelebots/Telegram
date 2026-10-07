# Panel privado de acceso Android y React

Servicio independiente de la página pública, en `https://chat.todosobreall.tech/access/`.
Solo el propietario configurado ve usuarios, actividad y permisos. Las ramas dev,
alfa, beta y rc deniegan el acceso por defecto. Stable conserva el acceso público.
La actividad significa que un cliente autenticado contactó con el servidor durante
los últimos 180 segundos; no significa que todos los usuarios de Telegram estén
conectados. Stable anónima no genera un listado de identidades.

## Despliegue

Crear `.env` desde `.env.example`, configurar `ACCESS_OWNER_ID` con el ID de Telegram
del propietario verificado y generar `ACCESS_BRIDGE_SECRET` aleatorio de al menos
32 caracteres. Mantener el archivo privado, fuera de Git. `docker compose up -d
--build` usa la red externa Traefik, volumen SQLite persistente y ningún puerto
público de contenedor. Hacer copia privada del volumen antes de migraciones.

La clave del bridge es distinta de un token Telegram; el panel no necesita el token
del bot. En Moonbot, copiar `moonbot_access_bridge.py` a `core/`, importar
`from core.moonbot_access_bridge import handle_access_update` y añadir al principio
del bucle `for u in [update]:` de `_process_update`:

```python
if handle_access_update(self, u):
    continue
```

Configurar en el proceso Moonbot:

```text
ACCESS_BOT_USERNAME=CintiaBot
ACCESS_BRIDGE_URL=https://chat.todosobreall.tech/access/api/bridge
ACCESS_BRIDGE_SECRET=<mismo secreto privado del panel>
```

Reiniciar Moonbot de forma controlada tras validar sintaxis. El bridge usa el
sondeo existente, únicamente para CintiaBot. Un `/start access_CODIGO` presenta la
solicitud; requiere pulsar explícitamente el botón de autorización en chat privado.
Compara el código con tu navegador o aplicación antes de aceptar. Una cuenta sin
permisos que solicita acceso queda registrada para que el propietario la autorice.

## Contrato API

Prefijo `/access/api`; cuerpos JSON. No enviar contraseñas Telegram, SMS ni tokens
del bot. El panel usa cookies Secure, HttpOnly, SameSite=Strict y token CSRF en
`X-CSRF-Token` para cambios. No se admite identidad aportada sin verificar por el
cliente. El inicio de sesión web está vinculado al navegador que lo solicitó.

| Operación | Entrada | Resultado |
| --- | --- | --- |
| GET `/auth/start` | — | `login_token`, `verification_uri` Moonbot, `expires_in:300` |
| POST `/auth/poll` | `login_token` | `status:pending` o `status:approved`, `user`, `is_admin`, `csrf_token`, cookie |
| GET `/auth/session` | cookie | `user`, `is_admin`, `csrf_token` |
| POST `/auth/logout` | cookie + CSRF | revoca sesión web |
| POST `/device/start` | `product`, `channel`, `package` Android, `version` opcional | `device_token`, `user_code`, `verification_uri` página web, `interval:5`, `expires_in:300` |
| POST `/device/poll` | Bearer `device_token` | `pending` o `approved`, `session_token`, `expires_in:300` |
| POST `/device/approve` | cookie + CSRF, `code` | aprueba si tiene permiso para esa rama |
| POST `/session/check` | Bearer `session_token` | `allowed:true`, `expires_in`; denegación 403 |
| GET `/admin/users` | cookie propietario, filtros `product`, `channel` | usuarios, permisos, solicitudes y sesiones recientes |
| PUT `/admin/grants` | propietario + CSRF, `user_id`, `product`, `channels` | sustituye permisos de ese producto; revoca sesiones de ramas retiradas |
| POST `/admin/revoke` | propietario + CSRF, `session_id` | revoca esa sesión |
| GET `/react/authorize?channel=dev` | cookie | comprueba permiso y registra actividad React |
| POST `/android/update` | `channel`, `package`, `version_code`; Bearer de dispositivo para ramas privadas | `available`, `current` o `unavailable`; versión confirmada y enlace Telegram si hay actualización |

Los productos son `android` y `react`. Los canales son `dev`, `alfa`, `beta`, `rc`
y `stable`. Para Android se comprueba `package=org.comunidadtelebots.<canal>`; esto
valida coherencia, no es atestación criptográfica de la APK.

El actualizador lee `ACCESS_RELEASE_DIR/android/<canal>.json` (por defecto
`/releases/android/<canal>.json`) desde un volumen privado de solo lectura. El
publicador escribe ese mismo volumen mediante `PRIVATE_RELEASE_DIR`; nunca se
monta bajo la web pública. Crear su raíz con permiso de lectura/recorrido para el
UID 10001 del panel. Solo las publicaciones confirmadas por Telegram generan
manifiestos, con package, versionName y versionCode extraídos por `aapt` de la APK
firmada. Sin manifiesto válido responde `unavailable`, no «actualizado». Las ramas
privadas requieren una sesión vigente Android de esa misma rama; stable es pública.
El enlace apunta al mensaje confirmado del canal y Telegram sigue comprobando la
pertenencia a los canales privados. Publicaciones manuales antiguas sin estos
metadatos no se convierten automáticamente en actualizaciones verificadas.

La sesión del dispositivo caduca a los cinco minutos sin revalidación; el cliente
debe revalidar cada 60 segundos y bloquearse al caducar. Hay reautenticación diaria
incluso si sigue conectado. Retirar permisos revoca también sesiones ya emitidas;
volver a concederlos no resucita esas sesiones. Los códigos son aleatorios, duran
cinco minutos y se consumen una vez. Si se pierde la respuesta de aprobación,
iniciar un emparejamiento nuevo. No registrar tokens en URLs o logs.

## React privado

El gate JavaScript mejora la experiencia pero debe acompañarse del control de
servidor. `nginx-react-private.conf.example` protege también la descarga de todos
los recursos mediante `auth_request`. Configurar el canal fijo de cada despliegue
y el origen HTTPS explícito en `ACCESS_TRUSTED_ORIGINS` (lista separada por comas).
El proxy `/access/` en el mismo host permite cookies locales sin ampliar el dominio
de la cookie. No aplicar este template al React stable público actual. Eliminar
service workers antiguos que sirvan recursos privados sin pasar por el servidor.

Una APK modificada puede eliminar un bloqueo del cliente y una cámara externa
puede fotografiar cualquier pantalla. Este sistema controla las distribuciones
oficiales, no ofrece DRM ni impide capturas externas. No recopila chats, contactos,
mensajes o credenciales de la cuenta Telegram.

## Verificación

`python -m unittest discover -s Tools/access-panel -p 'test_*.py' -v`

Pruebas de aislamiento por producto/rama, denegación por defecto, identidad del
bridge, consentimiento explícito, CSRF, vínculo al navegador, uso único, expiración,
revocación y actividad reciente. La integración real requiere desplegar el bridge
y confirmar una solicitud desde la cuenta del propietario; no se simula esa firma.
