# Google Play y canales de Telegram

Se mantienen ambos destinos. Google Play recibe un Android App Bundle firmado
(`.aab`) de `org.comunidadtelebots.stable`. Las pistas de pruebas internas,
cerradas, abiertas y producción de Play utilizan **ese mismo paquete**; no son
los paquetes `.dev`, `.alfa`, `.beta` y `.rc` distribuidos por Telegram.

La cuenta de Play Console todavía debe crearla el propietario. El registro,
contrato, pago y verificaciones se realizan desde su cuenta Google. Las cuentas
personales nuevas tienen requisitos de pruebas antes de solicitar producción.
No hay credenciales de Play ni publicación automática a Google configuradas.

## Compilar el bundle

La imagen `telebots-android-builder:local` admite `BUILD_FORMAT=aab` y exige
`CHANNEL=stable`, sin limitar ABI. Ejecutar sobre un checkout limpio del commit
que se quiera publicar, montado en `/src`, con `/signing` y `/out` como en el APK:

```sh
docker run --rm --memory=3g --memory-swap=4g --cpus=1 \
  -e CHANNEL=stable -e BUILD_FORMAT=aab \
  -v /RUTA/CHECKOUT:/src \
  -v /srv/telebots-release/signing:/signing:ro \
  -v /RUTA/SALIDA:/out \
  telebots-android-builder:local
```

El resultado es `application.aab`. El script valida la firma, la huella esperada,
el paquete del manifiesto combinado y que no sea depurable. No sube el bundle a
Play. Quedan pendientes la compilación real del AAB, la validación con bundletool
y la comprobación de compatibilidad de bibliotecas nativas de 16 KiB en esa salida.

## Firma y versiones

Para que las instalaciones de Play y las APK de Telegram del mismo paquete
puedan actualizarse entre sí, debe planificarse la firma antes de la primera
publicación: proporcionar a Play la clave actual mediante su procedimiento
PEPK, o distribuir en Telegram APK firmadas que se descarguen de Play. No
aceptar una firma distinta sin revisar esta compatibilidad.

La clave actual puede servir para la carga inicial. Es recomendable separar la
clave de subida de la clave de firma de distribución al configurar Play App
Signing. No exportar ni subir la clave privada a Google fuera de ese flujo.

Incrementar APP_VERSION_CODE en cada actualización. El proyecto usa sufijos de
versión distintos por variante (bundleAfat: 1, afat: 9); revisar los versionCode
resultantes al alternar Play y APK directas para evitar una actualización con un
código inferior. Un cambio de nombre de etiqueta Git no incrementa la versión
del manifiesto.

## Antes de enviar a revisión

Preparar nombre e icono propios, ficha, capturas de la aplicación real, política
de privacidad, declaración de seguridad de datos, clasificación de contenido,
declaraciones de permisos y acceso de revisión cuando corresponda. Revisar el
uso de VPN/Tor y los demás permisos según las políticas vigentes. No afirmar
que esta preparación constituye aprobación de Google Play.

Fuentes oficiales:
- https://support.google.com/googleplay/android-developer/answer/6112435
- https://support.google.com/googleplay/android-developer/answer/9842756
- https://support.google.com/googleplay/android-developer/answer/14151465
- https://support.google.com/googleplay/android-developer/answer/9859152
