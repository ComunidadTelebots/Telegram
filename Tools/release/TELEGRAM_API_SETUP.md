# Credenciales Telegram y comprobación de servidores — APK DEV

## Alcance

Aplicación Android independiente: `org.comunidadtelebots.dev`.
Workflow: `.github/workflows/normal-stable-apk.yml`, build Gradle `assembleAfatRelease` (no depurable).

### Configurar credenciales propias

1. Crear una aplicación y obtener `api_id` y `api_hash` mediante https://my.telegram.org/apps. Deben ser credenciales del cliente Comunidad Telebots, no credenciales de terceros.
2. En GitHub, abrir **Settings → Environments → android-dev → Environment secrets** (o **Settings → Secrets and variables → Actions** si se administran como secretos del repositorio). Guardar:
   - `TELEGRAM_API_ID`: entero positivo asignado por Telegram.
   - `TELEGRAM_API_HASH`: hash hexadecimal de 32 caracteres.
3. El workflow ya requiere también `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` y `CERT_SHA256` en el entorno accesible a la compilación; no sustituirlos por claves de ejemplo.
4. La compilación valida los formatos y genera temporalmente `TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java` desde `Tools/release/BuildVars.java.template`. Se elimina al final del workflow. No subir el archivo generado ni compartir los secretos en issues, logs, comentarios o chats.

**Importante:** el identificador de aplicación y su hash se incorporan al APK como requiere Telegram. No son secretos ocultables dentro de un binario distribuido; los secretos de firma sí deben permanecer privados.

## Servidores Telegram

El script `Tools/release/check_telegram_datacenters.py` comprueba conectividad TCP en los puntos iniciales de acceso al **DC2**:

| Entorno | IP inicial | Puerto | Función |
|---|---|---|---|
| Producción | `149.154.167.51` | 443 | Conectividad de red |
| Pruebas | `149.154.167.40` | 443 | Conectividad de red, cuentas separadas |

Direcciones de acceso iniciales, no lista exhaustiva ni garantía de disponibilidad permanente. Telegram puede cambiar IPs/puertos; el cliente Android debe seguir `help.getConfig` y el mecanismo de selección de DC del motor Telegram existente.

La comprobación **no** autentica MTProto, no envía `api_id`, `api_hash`, teléfonos o tokens, no inicia sesión y no cambia la configuración del cliente hacia el entorno de pruebas. Los datos de producción y pruebas NO se mezclan.

### Ejecutar sin conexión real para validar la lógica

```sh
python3 -m unittest discover -s Tools/release -p 'test_check_telegram_datacenters.py' -v
```

### Comprobar accesibilidad real de los dos entornos

```sh
python3 Tools/release/check_telegram_datacenters.py --environment both --timeout 5 --output telegram-dc-network.json
```

Para comprobar formatos de credenciales desde un entorno donde ya están definidos, añadir `--require-credentials`; nunca escribir valores directamente en la línea de comandos.

La tarea de build incorpora el informe `artifacts/telegram-dc-network.json` al artefacto final. Un fallo temporal de red se registra, pero no provoca por sí solo que falle el ensamblado APK. Una ausencia o formato inválido de `api_id` o `api_hash` sí bloquea el build.

Fuentes:
- https://core.telegram.org/api/datacenter
- https://core.telegram.org/api/auth
- https://docs.pyrogram.org/faq/what-are-the-ip-addresses-of-telegram-data-centers

## Alcance de la validación posterior

Para confirmar realmente las credenciales y MTProto se necesita ejecutar la APK y comprobar establecimiento de sesión con Telegram en un entorno de prueba independiente, sin enviar contraseñas ni capturar sesiones. No marcar conectividad TCP como autenticación funcional.

**No se modifican logotipos, marcas de agua, ni la pantalla de bienvenida definida para DEV.**
