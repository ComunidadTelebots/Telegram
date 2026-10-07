# Las 20 funciones de Comunidad Telebots

## Criterio y referencia

Comparación con Telegram Android `upstream/master`, commit `f2908b14133bbffbf7ab04f641ecb5bfaf533242`, versión 12.10.6 del 30 de septiembre de 2026. No es una afirmación de exclusividad frente a todos los clientes o versiones futuras. Verificado el 7 de octubre mediante `git ls-remote https://github.com/DrKLO/Telegram.git refs/heads/master`: el remoto devuelve ese mismo commit.

Se cuentan tareas diferenciadas con interfaz utilizable. Favoritas, filtros, búsqueda y posición de lectura son componentes, no funciones adicionales. Algunas diferencias resuelven necesidades similares a Telegram mediante almacenamiento exclusivamente local o integraciones de este proyecto; se explicita esa distinción.

Las opciones propias están en **Ajustes Comunidad Telebots**; herramientas contextuales también aparecen en mensajes/artículos. Se reutilizan el buscador y el gestor de descargas originales.

## Inventario

Los puntos 18–20 están integrados en la DEV compilada e instalada en el emulador; la validación concreta figura al final.

| # | Función y acceso | Diferencia concreta y límites |
|---|---|---|
| 1 | Tor integrado · Conexiones → Tor | Motor y VPN para este paquete. Requiere consentimiento Android; no transporta UDP como TCP ni garantiza llamadas. |
| 2 | Gestión DNS · Conexiones → DNS | Selección y comparación de proveedores DoH/DoT y altas propias. Telegram ya usa DoH internamente; aquí se expone elección. No modifica todo Android; Tor conserva su resolución. |
| 3 | Catálogo Alltech · Conexiones → Proxies | Consulta, prueba y ordenación de candidatos publicados por Alltech, sin activarlos automáticamente. Estar listado no acredita confianza. |
| 4 | Fiabilidad local de proxies · Conexiones → Proxies | Historial temporal y exclusiones explícitas, complementando la rotación oficial. No demuestra manipulación ni bloqueos. |
| 5 | Comparación entre DC · Conexiones → Dominios | Tiempo de respuesta de solicitudes MTProto a centros de datos; no ICMP ni traslado de la cuenta. |
| 6 | Diagnóstico guiado de servicios · Conexiones → Dominios | Inventario configurado/observado de dominios y CDN, clasificación de fallos y acciones reversibles. Es parcial; un fallo no prueba censura. |
| 7 | Informe de conexión minimizado · Conexiones → Dominios | Revisión/exportación voluntaria de estados agregados sin cuentas, IP, hosts ni credenciales. Diferente de exportar logs generales. |
| 8 | Prevalidación de APK propio · Actualizaciones | Cotejo con manifiesto de rama, hash, paquete, versión, compatibilidad y certificado de la instalada. Android realiza la verificación final. Requiere metadatos nuevos del publicador. |
| 9 | Acceso privado por rama mediante Moonbot | Autorización adicional en DEV/ALFA/BETA/RC; stable pública. No sustituye la sesión Telegram ni evita extraer/modificar APK. |
| 10 | Diseños multicliente · Diseños | Adaptaciones de navegación, burbujas, avatares y separadores. No reproducen íntegramente cada cliente. |
| 11 | Estilos de transición · Diseños | Selector independiente de duración/desplazamiento para transiciones compatibles. No cambia todas las animaciones. |
| 12 | Frases locales · Ajustes / menú del chat | Biblioteca por cuenta, categorías, favoritas y búsqueda; inserta texto en borrador. Business ya tiene respuestas rápidas; esta biblioteca no sincroniza ni utiliza sus RPC o multimedia. |
| 13 | Historial documentado de diseños · Diseños | Consulta de versiones y referencias a cambios. Elegir una referencia no aplica una reproducción visual histórica. |
| 14 | Lector AMP nativo · Mensajes/artículos | Descubrimiento AMP y conversión al lector local junto a Vista rápida cuando existe. No evita restricciones del editor. |
| 15 | Enlaces bajo fotos y álbumes · Mensajes | Vista previa adicional del enlace del pie sin sustituir el objeto multimedia. AMP/IV dependen de cada enlace. |
| 16 | Biblioteca pública offline · Lectura | Guardado explícito/acotado del original público con imágenes y apertura sin red. Puede diferir del IV; páginas restringidas o dependientes de JavaScript pueden fallar. |
| 17 | Leer después local · Lectura / menú del canal | Referencias por cuenta a artículos/publicaciones sin reenviar ni sincronizar con Mensajes guardados. Los marcadores nativos siguen disponibles. |
| 18 | Copia de ajustes propios | Exportación/importación con lista permitida y vista previa. Copia diseño, transiciones, AMP y precarga; excluye identidad, credenciales, proxies y cuentas. Archivo de hasta 1 KiB. |
| 19 | Notas privadas de artículos offline | Una nota por artículo, editable desde biblioteca/lector, hasta 4000 caracteres y 8 KiB; sin sincronización. Actualizar conserva la nota; borrar artículo la elimina. |
| 20 | Comparación de versiones de artículos | Texto añadido/eliminado entre las dos últimas copias tras guardar explícitamente, con fechas de guardado. Hasta 8000 caracteres/200 párrafos por versión, sin comparación de imágenes/formato. |

## Funciones oficiales que no sumamos

Telegram ya ofrece [respuestas rápidas Business](https://telegram.org/blog/telegram-business), [Mensajes guardados](https://telegram.org/blog/new-saved-messages-and-9-more), [marcadores del navegador](https://telegram.org/blog/w3-browser-mini-app-store), [gestión y prioridad de descargas](https://telegram.org/blog/downloads-attachments-streaming) y [ahorro de energía](https://telegram.org/blog/power-saving). También dispone de búsqueda de ajustes, ping/rotación de proxies, accesibilidad, posición IV y actualizaciones.

Los perfiles Ahorro/Equilibrado/Máxima fluidez son macros reversibles sobre ajustes existentes más controles propios AMP/precarga. Se abre el gestor oficial de descargas: no hay una segunda cola ni un planificador por hora. La restricción adicional a redes no medidas tampoco se cuenta como capacidad totalmente nueva.

Los cinco iconos DEV/ALFA/BETA/RC/STABLE, título y marca de agua son identidad visual, no cinco funciones ni protección contra filtraciones. La marca no modifica contenido enviado.

## Validación y pendientes

Pruebas focalizadas de frases, filtros/favoritas, red, perfiles aplicar/deshacer, iconos, diagnóstico, minimización de informes, verificación APK y lectura pública. En emulador se verificó crear/borrar una frase, aplicar/deshacer Ahorro, buscar DNS en mayúsculas y guardar/abrir el artículo público IANA Example Domains sin Wi-Fi/datos, restaurándolos después.

La medición real de Baseline Profiles quedó aplazada por petición del usuario; se restauró DEV de pruebas. El módulo y sus APK compilan, pero no se afirma una mejora medida. Véase `performance/README.md`.

El backend actualizado de metadatos APK no se ha desplegado durante esta integración. Un manifiesto antiguo sin esos campos no se presenta como verificado. No se han publicado releases durante esta integración; las herramientas locales no requieren un servidor nuevo.

## Validación DEV del 7 de octubre

Build `assembleAfatDebug` correcta (2 min 25 s), instalada conservando cuenta y datos. Se probó exportar una copia de ajustes mediante SAF, reabrirla, revisar los cuatro valores e importar los mismos ajustes: operación completada. En IANA Example Domains se creó una nota, se reabrió con el mismo texto y se comprobó que permanecía tras guardar de nuevo el artículo. La pantalla de comparación mostró fechas anterior/actual y ausencia de cambios de texto. Se eliminó únicamente la nota de prueba. Los tests JVM sí cubren versiones con texto añadido/eliminado. La marca de agua se observó visible tras corregir las coordenadas del editor anidado.

Pruebas nuevas: 120 combinaciones de copia de ajustes y rechazos de esquema/UTF-8/campos/tamaño; notas UTF-8/emoji; diferencias de párrafos y snapshots; informe minimizado. No se ha verificado cada una de las veinte capacidades en todos los dispositivos ni todas las variantes en esta sesión.
