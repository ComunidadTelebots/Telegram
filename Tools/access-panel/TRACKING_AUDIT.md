# Auditoría del control de acceso y estadísticas de Todosobrealltech

Fecha: 7 de octubre de 2026. Revisión técnica del código y comprobación agregada del
servicio desplegado; no certificación legal ni aprobación de Telegram/Google Play.
No se extrajeron identidades, conversaciones ni direcciones IP de producción.

## Alcance y resultado

Se revisaron `app.py`, `moonbot_access_bridge.py`, `frontend/`, `ReleaseAccess.java`,
`ReleaseAccessActivity.java` y `../telegram-react/src/index.js`. Se examinaron las
dependencias declaradas en `TMessagesProj/build.gradle`, no todos sus tratamientos
de datos en ejecución. La revisión de Hostinger fue de solo lectura.

El módulo de acceso no solicita coordenadas, no usa la API de ubicación Android ni
`navigator.geolocation` y no transmite GPS a Todosobrealltech. No existe un mapa
de ubicaciones en su esquema SQL. Esto no significa que el cliente Telegram completo
carezca de funciones de ubicación: compartir ubicación en un chat y los SDK de
mapas/localización son flujos distintos que requieren su propia evaluación.

El usuario eligió estadísticas opcionales de país. Se implementó una elección
manual independiente, sin GPS ni inferencia por IP y sin condicionar permisos.
El país es autodeclarado y no prueba residencia ni ubicación actual.

## Inventario exacto del módulo

| Dato | Fuente y uso | Persistencia |
| --- | --- | --- |
| ID Telegram, nombre y @usuario | Moonbot verifica la identidad; administración de permisos | `users` |
| Producto y rama | Cliente/solicitud; separación Android/React y permisos | `grants`, `requests`, `challenges`, `sessions` |
| Versión Android | `device/start`, mostrada al administrador | `challenges`, `sessions` |
| Paquete y versionCode | Validación de rama/consulta de actualización | No se guardan por usuario en tablas del actualizador |
| Código aleatorio, hash del token, caducidad y estado | Emparejamiento y autenticación | `challenges`; códigos de uso único |
| ID aleatorio de sesión, hash de bearer/cookie, CSRF, creación, expiración, última comprobación, revocación | Autorización y sesión reciente | `sessions`; no se exportan secretos |
| SHA-256 de `request.remote_addr` | Límite de solicitudes | `challenges.source`; detrás de Traefik puede identificar al proxy, no al usuario |
| Actor, acción, destino y fecha | Confirmación, cambio de permisos, revocación | `audit` |
| País opcional, fecha y versión de aviso | Elección explícita para estadística | Nueva `country_preferences`, pendiente de desplegar |

No se envían al panel mediante estos endpoints teléfono, IMEI, Android ID, modelo
de dispositivo, contactos, mensajes, claves MTProto ni coordenadas. El servidor y
el proxy sí procesan conexiones IP. No se verificaron todas las políticas de logs
y copias de seguridad del proveedor; no se puede prometer ausencia de IP en ellos.
El hash de una IP no equivale a anonimización irreversible.

## Qué cubre «sesión reciente»

Backend: `revoked=0`, `expires>ahora` y `last_seen>ahora-180` para sesiones de
tipo `device` o `react`. No es el estado de Telegram ni una prueba de interacción
humana. `last_seen` se actualiza; no hay una tabla histórica de cada heartbeat.

Android, en el código inicialmente auditado, revalida cada 60 segundos incluso con
la aplicación en segundo plano mientras el proceso siga vivo. El cambio a primer
plano se está realizando en paralelo y requiere nueva compilación. React consulta
cada 30 segundos; pausar pestañas ocultas también se prepara en paralelo. La ventana
de tres minutos puede seguir mostrando una sesión después de ocultar/cerrar la app.

Stable omite la identificación del gate: no existe un censo de usuarios anónimos
de stable. Una consulta de actualización stable no crea una identidad. React
privado solo aparece cuando se despliega con la protección de servidor y el gate.
El único contenedor React observado corresponde al despliegue existente; esta
auditoría no confirma que haya despliegues privados React activos.

Se corrigió el panel local: «Activos ahora» pasa a «Sesiones recientes» y el contador
suma sesiones dentro del filtro, no usuarios con alguna sesión. La fila individual
se refiere igualmente a comprobación reciente, sin prometer «en línea».

Comprobación agregada de producción al realizar esta revisión: 1 perfil, 8 permisos,
2 sesiones (1 web y 1 Android dev), 0 solicitudes guardadas, 3 acciones de auditoría
y 0 sesiones recientes. No se mostraron IDs. Son una instantánea, no métricas de
uso de toda la aplicación ni evidencia de cero usuarios reales.

## Conservación y controles

- Códigos: caducan a los 5 minutos. Se eliminan cuando llevan más de 5 minutos
  caducados y se inicia otra solicitud (aproximadamente 10 minutos desde creación
  como mínimo, sin garantía de máximo si no hay solicitudes nuevas).
- Sesiones: lease de dispositivo 5 minutos renovable, autenticación absoluta
  máxima 24 horas; cookie web 1 hora. Limpieza de filas caducadas hace más de
  30 días, también disparada por una nueva solicitud.
- Auditoría: limpieza de acciones de más de 90 días, disparada por solicitudes.
- Perfil, permisos y solicitudes: sin caducidad automática definida. Se eliminan
  mediante el nuevo borrado autenticado del perfil.
- País: hasta retirada voluntaria o borrado del perfil. No se usa para conceder o
  retirar acceso. Solo el interesado puede modificarlo con cookie y CSRF.
- Copias de seguridad, logs y registros propios de Moonbot/Telegram: fuera del
  borrado de la base activa; falta inventario y procedimiento verificable.

Nuevos endpoints: GET `/profile`, POST `/profile/country`, GET `/profile/export`,
POST `/profile/delete` y GET `/admin/countries`, bajo `/access/api`. Exportación y
borrado solo afectan al usuario autenticado. Borrado exige CSRF y confirmación,
revoca sesiones al eliminar sus filas y no borra una cuenta Telegram. Incluye las
filas de auditoría que apunten a sus IDs de sesión. El propietario configurado no
pierde esa condición administrativa al borrar su perfil y volver a identificarse.

País: lista ISO alpha-2 admitida, consentimiento afirmativo separado, retirada con
`country:null`. El listado administrativo de usuarios no devuelve el país. Los
agregados solo muestran países con al menos 3 aportaciones; grupos menores se
suprimen. La base sigue asociando país e identidad para permitir retirada: no es
anónima. Este umbral reduce exposición pero no garantiza anonimato ante inferencias.

## Riesgos y pendientes concretos

1. **Identidad antes de confirmación en Moonbot, corregido localmente:** el bridge
   desplegado auditado transmitía datos personales durante `/inspect`. Ahora la
   inspección envía solo el código; el aviso aparece antes del botón y `/approve`
   requiere `consent:true` antes de transmitir y registrar identidad. Sin permisos,
   se registra la solicitud consentida y se devuelve 403, sin emitir sesión. La
   auditoría registra la versión del aviso. Deben desplegarse backend y bridge
   coordinadamente antes de considerar resuelto el comportamiento en producción.
2. **Responsable/contacto/país de establecimiento pendientes:** no inventarlos.
   `/access/privacy` es una descripción técnica y `/access/terms` un borrador
   identificado; no presentarlos como política contractual definitiva.
3. **Logs/backups:** definir retención, acceso y atención a borrado; borrar filas
   activas no prueba destrucción de todas las copias o páginas liberadas de SQLite.
4. **SDK y permisos:** Gradle incluye Firebase Messaging/datatransport/appindexing
   y Google Maps, Location, Auth, ML Kit, entre otros. Revisar versiones, inicialización,
   datos y configuración efectiva por variante antes de completar Data safety.
5. **Marca y funciones Telegram:** comprobar nombre/icono de la APK y tienda,
   API ID propio, funciones normales y mensajes patrocinados. Esta revisión del
   gate no demuestra conformidad de todo el fork.
6. **Acceso al APK:** el gate puede retirarse de un cliente modificado y una cámara
   externa puede capturar la pantalla. No publicitar protección absoluta contra
   filtraciones ni considerar permisos de rama una restricción adicional de GPL.

## Referencias oficiales consultadas

- [Términos de la API de Telegram](https://core.telegram.org/api/terms): privacidad,
  funcionalidad ordinaria, transparencia del cliente independiente, marca y límites
  del uso de datos de Telegram para IA. Revisión del gate no certifica el fork.
- [Privacidad de Telegram](https://telegram.org/privacy): distingue los tratamientos
  de su servicio, incluyendo ubicación compartida en chats, de esta administración.
- [Google Play: información y consentimiento](https://support.google.com/googleplay/android-developer/answer/11150561?hl=en): explicar datos y finalidad en el flujo antes de usos no esperados; un enlace aislado no sustituye el aviso.
- [Google Play: ubicación en segundo plano](https://support.google.com/googleplay/android-developer/answer/9799150?hl=en): requiere justificación ligada a funciones esenciales y declaración. No justifica añadir GPS para contar usuarios.
- [Google Play: Data safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en): inventariar los tratamientos reales de la aplicación y sus SDK; no extrapolar los del gate a toda la app.
- [Google Play: eliminación de cuenta](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en): revisar aplicación del requisito al alta/SSO, con acceso dentro y fuera de la app. Los nuevos controles resuelven la base activa del perfil Alltech, no todas las obligaciones.
- [Firebase: privacidad](https://firebase.google.com/support/privacy) y [Tor: alcance](https://support.torproject.org/about-tor/): tecnologías presentes que requieren explicación propia, sin prometer anonimato total.

## Validación de los cambios locales

Se prueban anonimato del endpoint administrativo, aislamiento por producto/rama,
revocación, CSRF, ausencia de geolocalización añadida, país opcional/ISO/retirada,
supresión de grupos pequeños, exportación sin bearer, borrado limitado al perfil
y rutas públicas de información. Se valida sintaxis de JavaScript. No se desplegó
esta revisión ni se activó telemetría adicional en Hostinger.
