# Vista previa local del emulador

Para probar la interfaz sin emparejar Moonbot, compilar `assembleAfatDebug` con
`-PAPP_CHANNEL=dev -PEMULATOR_ACCESS_PREVIEW=true`.

La excepción está desactivada por defecto. El campo generado para release es
siempre false, incluso pasando esa propiedad. Además se comprueba debug, el
indicador debuggable de la aplicación, canal dev y el emulador Google SDK con
hardware ranchu/goldfish. La detección de emulador no es una protección contra
manipulación del dispositivo; esta APK es exclusivamente una herramienta local.

No publicar esta APK en los canales ni en Google Play. Los workflows de
distribución no pasan esta propiedad. La excepción omite sólo el control de
acceso de Moonbot; iniciar sesión en Telegram sigue siendo necesario para chats.
Para recuperar el control de Moonbot en el emulador, reinstalar una build debug
normal sin esta propiedad. No es necesario borrar los datos.

## Validación de login — 7 octubre 2026

La configuración local ignorada de BuildVars conservaba el API ID de ejemplo 4.
Se sustituyó por las credenciales privadas ya disponibles del proyecto, sin
añadirlas al repositorio. La versión anterior recibía API_ID_PUBLISHED_FLOOD;
AlertsCreator no presentaba una alerta para ese error de auth.sendCode.
Ahora hay alertas de configuración y los volcados MTProto de FileLog omiten
los payloads, incluidos hash de API y número de teléfono.

El Pixel_6 API 37 x86_64 ejecuta esta APK ARM64 mediante traducción. Con 2 GB
Android registró cierres LOW_MEMORY. Se reinició sin borrar datos, usando
`-memory 4096 -no-snapshot-load -no-snapshot-save` para esta sesión.

La compilación debug corregida terminó correctamente y se instaló con adb
install -r -t. El servidor devolvió TL_auth_sentCode y la interfaz avanzó a
Check your Telegram messages. Queda pendiente que el propietario introduzca
el código y, si corresponde, su contraseña de verificación en dos pasos.
No se ha completado ni simulado la autenticación de la cuenta del usuario.
