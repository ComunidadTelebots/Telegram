# Estado de integración — 2026-10-05

## Actualizaciones Android — 2026-10-07

Se añadió Ajustes → Actualizaciones → Buscar actualizaciones. Compara el
versionCode instalado con la última publicación confirmada de la misma rama.
La descarga abre el mensaje exacto de Telegram y la instalación requiere la
confirmación normal de Android. Las instalaciones procedentes de Play abren
su ficha en Google Play. Sin una publicación verificada se informa de ello.

La API se desplegó y se comprobó: stable sin publicación devuelve unavailable;
dev sin sesión devuelve 403. Pasaron 16 pruebas de acceso y 11 del publicador.
La APK local assembleAfatDebug compiló correctamente. Los metadatos se comparten
mediante un volumen privado entre publicador y API, fuera de la web pública.
No se ha probado una instalación posterior desde un APK de distribución nuevo.
La compilación firmada anterior de Hostinger terminó con error; no se anunció
como publicación disponible ni se creó un manifiesto de actualización ficticio.

## Verificación previa del acceso

El panel está desplegado en `https://chat.todosobreall.tech/access/` mediante
el proyecto Docker independiente `/srv/telebots-access`. Se conserva SQLite
en un volumen. Moonbot tiene el bridge instalado; existe copia privada del
archivo y configuración anteriores en el directorio de backups del despliegue.
El propietario confirmó que ha entrado al panel con su cuenta real.

Verificación realizada:

- 12 pruebas del backend/bridge: identidad, autorización, CSRF, caducidad y revocación.
- Android `assembleAfatDebug`: correcto, instalado en emulador conservando datos.
- Sin autorización, arranque, enlace profundo y compartir muestran el bloqueo.
- Emparejamiento Android contra HTTPS real: código emitido, consulta pendiente,
  sin conceder un permiso ficticio. Falta probar el desbloqueo real del dispositivo.
- React: cinco pruebas correctas y compilaciones stable y beta correctas.
- Backend público: interfaz 200; datos administrativos sin sesión 401.

La compilación firmada Android ARM64 con este cambio se inició en Hostinger en
`/srv/telebots-release/work/access-dev/`; su resultado se registra en `build.exit`
y `build.log`. No se considera publicado hasta recibir confirmación del canal.
La exportación inicial carecía de submódulos; se corrigieron las fuentes del
trabajo y el publicador para obtener los submódulos fijados por Git.

Los despliegues privados de React todavía requieren el proxy de autorización
incluido en este directorio. El React stable existente continúa público.

El permiso autoriza la sesión de instalación de Moonbot; todavía no obliga a
usar dentro del cliente la misma cuenta de Telegram que concedió ese permiso.
El token Android permanece en memoria y requiere emparejar tras reiniciar el
proceso. Las versiones privadas suprimen notificaciones y contenido de widgets
para evitar vistas conservadas por Android después de una revocación. Stable
mantiene su comportamiento público. Las capturas solicitadas corresponden al
panel de acceso, que no contiene conversaciones.
## Privacidad y estadísticas opcionales — 7 octubre 2026

Desplegados backend, páginas legales y bridge de Moonbot conjuntamente, con
copia previa de los archivos en el directorio privado de backups del servidor.
21 pruebas correctas. Verificación HTTPS: acceso, privacidad y condiciones 200;
estadísticas de países y perfil sin sesión 401. No se ha simulado la identidad
del propietario ni aportado países ficticios a producción.

El país es declarado voluntariamente y puede retirarse. Los agregados ocultan
grupos de menos de tres participantes. La inspección inicial del código de
Moonbot ya no transmite la identidad; la confirmación explícita sí lo hace.
Los documentos legales siguen siendo borradores pendientes del responsable,
contacto y país de establecimiento; no acreditan cumplimiento integral.

Android dev ARM64 debug compilado con enlaces legales y comprobaciones de
acceso limitadas a primer plano. El build release previo de Hostinger falló en
R8 por Java heap space (heap 1800 MB). No se ha publicado una release firmada.
La transferencia de secretos de firma a GitHub está pendiente de autorización
explícita tras el rechazo de la revisión automática; no se transfirieron.
