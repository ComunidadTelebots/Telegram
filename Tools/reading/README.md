# Biblioteca de lectura local

Entrada del hub: `new CommunityReadingActivity()`. El menú del navegador y del lector nativo ofrece **Leer después** y **Guardar artículo público sin conexión**. Las traducciones fuente de esta función están en `strings-en.xml` y `strings-es.xml`; deben incorporarse a los `strings.xml` principales para el generador de recursos de Telegram.

Las publicaciones de canales de difusión también incluyen **Leer después** en su menú contextual. Se conserva exclusivamente su enlace `https://t.me/<usuario>/<mensaje>` o `https://t.me/c/<canal>/<mensaje>` y el título del canal con el número de publicación. Abrir ese enlace regresa a la publicación original mediante el navegador/enrutador interno de Telegram y conserva las comprobaciones de acceso del servidor. No se copian ni descargan mensajes, fotos o publicaciones protegidas; los enlaces de Telegram no ofrecen ni permiten la descarga offline. No aparece en chats secretos, mensajes efímeros, patrocinados o programados. La posición del mensaje está codificada en su enlace; no se crea un mensaje nuevo ni se publica nada.

La pantalla distingue explícitamente esta biblioteca **local, sin sincronización** de las funciones oficiales. Sus accesos a Marcadores y Mensajes guardados abren las pantallas nativas sin copiar entradas ni enviar enlaces. La búsqueda por título/URL y los filtros Todos/Sin conexión/Leer después/Publicaciones operan sobre el listado en memoria; admiten diferencias de mayúsculas y tildes. “Leer después” incluye enlaces aún no descargados, también publicaciones. El tamaño mostrado suma bytes de archivos existentes de esta cuenta (documentos, imágenes, índice y transacciones temporales), mediante una tarea de IO: no es una estimación basada en límites ni bloques físicos del sistema de archivos. Se actualiza al entrar, refrescar y terminar operaciones de guardado/borrado, no mientras se escribe una búsqueda. La confirmación de borrado individual permanece activa.

## Comportamiento

- La cola conserva URL original HTTPS y título. Abrir original es una acción explícita con conexión.
- Guardar vuelve a solicitar el original público, sin cookies de sesión, usando el transporte acotado de `AmpResolver`. No serializa el DOM de una sesión autenticada ni el contenido de un mensaje.
- La respuesta se rechaza ante indicadores de acceso restringido, suscripción, contraseña o paywall. Este filtro es conservador: rechaza atributos o configuraciones de acceso restringido, sin rechazar una página solo por mencionar esos mecanismos en su texto. No intenta autenticación, evasión ni recuperar contenido ausente del original público.
- El HTML público se convierte mediante el lector existente a JSON nativo sanitizado. Sirve tanto para enlaces AMP como para originales de Vista rápida que ofrezcan un documento público legible. El diseño o contenido pueden diferir del documento IV del servidor. Un original no disponible, protegido o que depende de JavaScript puede no guardarse.
- Se guarda el JSON y se descargan hasta 20 imágenes HTTPS con el mismo transporte acotado. No se guardan vídeos, iframes ni scripts. El estado muestra cuántas imágenes se guardaron y cuántas faltan o superan límites.
- Abrir sin conexión reconstruye la página nativa desde JSON y usa un cargador exclusivo de archivos para las imágenes. Una imagen ausente queda ausente; no hay descarga automática de reemplazo. La página se registra como local para evitar el refresco `getWebPage` de Telegram. Pulsar un enlace dentro del artículo sigue siendo una navegación explícita con conexión.
- La posición se almacena por cuenta y URL, al cerrar/cambiar la página. Para entradas de la biblioteca no se utiliza la posición global de artículos con `id=0`.

## Almacenamiento y límites

Cuenta identificada por ranura e ID de usuario real, para evitar reutilizar la biblioteca al iniciar otra cuenta en la misma ranura. Documentos e imágenes en `getNoBackupFilesDir()/reading/<slot>_<userId>/`; índice y posiciones en preferencias privadas `reading_<slot>_<userId>`. El `BackupAgent` actual solo incluye `saved_tokens` y `saved_tokens_login`, no estas preferencias. No se exportan archivos a almacenamiento compartido ni se sincronizan con un servidor.

Máximo de 100 entradas y 20 artículos offline por cuenta. Cada artículo: JSON máximo 1 MiB, hasta 20 imágenes, máximo total 5 MiB; imágenes decodificadas con muestreo hasta 1600 píxeles por dimensión. Hasta cuatro solicitudes de guardado pendientes, sin duplicar una misma URL en una cuenta. Los archivos temporales pueden necesitar espacio adicional durante una descarga/actualización.

Eliminar una entrada borra su documento, imágenes, enlace y posición. Borrar biblioteca elimina todos esos datos de la cuenta. Ambas acciones invalidan guardados pendientes de esa cuenta; el commit y borrado están coordinados para evitar que una descarga terminada reponga contenido borrado. No hay borrado automático al cerrar sesión: los archivos privados quedan separados por el ID de la cuenta y pueden eliminarse desde su biblioteca al volver a iniciar sesión, o borrando los datos de la aplicación.

## Notas personales y comparación local

Cada artículo descargado admite una nota de texto por URL, editable desde la biblioteca y desde el menú del lector. No se ancla a párrafos ni se sincroniza. El límite es 4000 caracteres y 8 KiB UTF-8 adicionales al presupuesto de 5 MiB del artículo. Se almacena mediante `AtomicFile` en el mismo directorio privado de la cuenta, fuera del directorio sustituido al actualizar el artículo: actualizar conserva la nota; eliminar la entrada o la biblioteca la borra. Un guardado fallido conserva el borrador visible. Los callbacks comprueban cuenta/generación; la presentación comprueba que el fragmento o artículo original sigue abierto.

La comparación guarda únicamente las dos últimas capturas de texto de guardados explícitos, hasta 8000 caracteres y 200 párrafos por versión. `changes.json` está limitado a 128 KiB y entra en el presupuesto del artículo, reservando 160 KiB durante la descarga para comparación y metadatos. La primera captura no tiene versión anterior. No consulta cambios en segundo plano. El borrado del artículo también elimina sus versiones.

`ReadingNotePolicyTest`: nueve comprobaciones de tamaño, incluyendo límites UTF-8 multibyte y emoji. La compilación aislada del almacén incluye las clases reales de notas y comparación; no prueba diálogos ni almacenamiento en un dispositivo.

## Verificación realizada

`OfflineArticlePolicyTest`: doce comprobaciones, incluyendo artículo público, señal de suscripción, JSON de acceso no libre, contraseña y tamaño excesivo.

`OfflineReadingFilterTest`: diez comprobaciones de filtros, búsqueda por URL, mayúsculas, tildes y valores vacíos.

`python Tools/tests/check_offline_compile.py <ruta-a-android.jar>` compila el almacén contra Android SDK y stubs mínimos del proyecto. Comprueba tipos y APIs del almacén, pero no equivale a compilar la APK ni a probar almacenamiento Android real.

La compilación conjunta, apertura real sin red, captura visual y posición tras reiniciar necesitan la verificación de aplicación que coordina el agente principal. No se ha controlado el emulador desde esta tarea.
