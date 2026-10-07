# Panel privado y contrato React

El frontend de esta carpeta se sirve en `/access/` desde `app.py`. No requiere npm ni compilación. Los tres archivos desplegables son `index.html`, `styles.css` y `app.js`. Copiarlos juntos al actualizar; el servicio devuelve `Cache-Control: no-store`.

## Inicio de sesión y administración

- `GET /access/api/auth/session` devuelve la identidad, `is_admin` y `csrf_token`. Una respuesta 401 muestra el inicio de sesión.
- `GET /access/api/auth/start` devuelve `login_token`, `user_code` (24 caracteres hexadecimales), `verification_uri` HTTPS de t.me y caducidad. El usuario compara el código visible con Moonbot antes de aprobar. El token queda sólo en memoria, nunca en una URL ni localStorage.
- `POST /access/api/auth/poll` espera la autorización y la cookie HttpOnly del servidor. Se conserva el CSRF sólo en memoria. Todas las mutaciones de sesión llevan `X-CSRF-Token`.
- `GET /access/api/admin/users?product=android|react&channel=dev|alfa|beta|rc|stable` obtiene datos reales. `channel=` muestra todas las ramas. La vista no incorpora usuarios de ejemplo.
- `PUT /access/api/admin/grants` recibe `{user_id,product,channels}`. `POST /access/api/admin/revoke` recibe `{session_id}`. El propietario conserva acceso administrativo; la interfaz lo identifica y no ofrece retirarle permisos ficticios.
- `?code=...` carga `/device/info`, muestra producto y rama y permite aprobar explícitamente el dispositivo. `?next=/` vuelve al cliente React del mismo origen después del login. Ninguna URL de redirección arbitraria se acepta.

La actividad se actualiza cada 30 segundos mientras el panel está visible. Su ventana de actividad la devuelve el servidor. Stable permanece pública y sus usuarios anónimos no se cuentan como identidades verificadas.

## Integración del repositorio React

Cambios en el repositorio hermano `telegram-react`:

- `src/index.js`: comprueba `/access/api/react/authorize?channel=<canal>` antes de importar `bootstrap`. Requiere HTTP correcto y `allowed === true`; revalida cada 30 segundos y al volver a primer plano. Recarga al perder acceso para cerrar workers y sockets.
- `src/bootstrap.js`: contiene el arranque previo del cliente. Registra service workers únicamente en stable.
- `src/AccessGate.css`: vista privada de inicio.
- `vite.config.js`: variable de entrada `VITE_RELEASE_CHANNEL` (alternativa `REACT_APP_RELEASE_CHANNEL`), valores `dev`, `alfa`, `beta`, `rc`, `stable`. Modos `alpha`→`alfa`, `development`/`test`→`dev`, `production`/`analyze`→`stable`. Canal inválido cancela la compilación.
- `src/AccessGate.test.js`: pruebas de denegación, autorización, fallo de red, respuesta 200 sin permiso y canal inválido.

Para cada despliegue privado se requiere además el template nginx del directorio superior, un canal fijo consistente con el compilado y un origen HTTPS permitido. No aplicar el template privado a `tg.todosobreall.tech` stable. Una barrera JavaScript por sí sola no protege archivos que el servidor entrega públicamente.

## Verificaciones realizadas

- `node --check Tools/access-panel/frontend/app.js`.
- Cinco pruebas Vitest de acceso React correctas.
- Compilaciones Vite stable y beta correctas en carpetas temporales separadas del build existente. Advertencias previas de tamaño de chunks y dependencias que usan eval siguen presentes.
- Panel de login verificado en navegador contra backend local anónimo.
- Administración y editor de permisos verificados contra SQLite local de demostración, con banner explícito y datos ficticios. Los scripts de demostración están sólo en `.gradle/access-preview/`, fuera del despliegue; no existe autenticación de prueba en producción.
