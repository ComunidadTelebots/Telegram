# Opciones de Plus Messenger para Comunidad Telebots

Revisión del 7 de octubre de 2026, frente a Telegram Android `f2908b14133bbffbf7ab04f641ecb5bfaf533242`. Investigación, no implementación de estas opciones.

La [web oficial](https://plusmessenger.org/) conserva referencias históricas (8.1.2.1); contrastar con [el canal oficial](https://t.me/s/plusmsgr) y [su ficha de Google Play](https://play.google.com/store/apps/details?id=org.telegram.plus). No se ha ejecutado Plus para comprobar todas sus opciones actuales.

## Candidatas

| Función | Integración propuesta | Coste y diferencias |
|---|---|---|
| Iconos, títulos o ambos en pestañas de carpetas | `FilterTabsView`, `DialogsActivity`; ajuste propio de presentación | Medio. Las carpetas y sus filtros ya existen. [Referencia Plus](https://t.me/s/plusmsgr?before=160). |
| Contadores por carpeta: chats o mensajes pendientes | Cálculo incremental de datos disponibles, separado del contador del launcher | Medio. El contador del icono ya ofrece esta elección; no duplicarlo. [Referencia Plus](https://t.me/s/plusmsgr/268). |
| Orden local alfabético o pendientes primero | Lista de presentación en `DialogsAdapter`; conservar fijados y no modificar orden sincronizado | Medio. [Ficha oficial](https://play.google.com/store/apps/details?id=org.telegram.plus). |
| Barra de acceso rápido a otros chats | Reutilizar carpetas y orden existentes dentro del chat; conservar cuenta, borrador y posición | Medio. La existencia de carpetas o el gesto de siguiente canal no son novedades. [Ficha oficial](https://play.google.com/store/apps/details?id=org.telegram.plus). |
| Tamaño independiente de avatar y densidad | `DialogCell`; mantener historias, RTL y objetivos táctiles mínimos | Alto: requiere medir y probar cada diseño. La forma del avatar ya depende de nuestros skins. [Web Plus](https://plusmessenger.org/). |
| Fuente del sistema | `AndroidUtilities` y pinturas de `Theme`, invalidando medidas | Alto: conservar negrita, cursiva, monoespaciado y emoji. El tamaño del texto ya es nativo. [Web Plus](https://plusmessenger.org/). |

Confirmación opcional antes de enviar stickers/GIF/voz y «Primer mensaje disponible» son otras propuestas útiles, pero no se encontró evidencia primaria suficiente para atribuirlas a Plus. La primera necesita cubrir todas las rutas de envío; la segunda no puede asumir que exista el mensaje 1.

No sumar como novedades guardar documentos con nombre original, seleccionar parte del texto, desactivar «Acercar para escuchar», filtros por tipo, temas básicos o tamaño de texto: ya hay implementaciones nativas. Ampliar a diez cuentas implica revisar Java, JNI, notificaciones, almacenamiento y consumo; no basta cambiar una constante.

## Código y condiciones

Existe código público de [rafalense en GitHub](https://github.com/rafalense/Plus-Messenger), con [GPLv2](https://github.com/rafalense/Plus-Messenger/blob/master/LICENSE), y un [GitLab del autor](https://gitlab.com/rafalense/plus-messenger/-/commits/master) cuyo historial consultado muestra código 4.2.1.1 de 2017 y README de 2019. No se localizaron fuentes correspondientes a la versión actual; esto no demuestra su inexistencia en otro lugar.

Conviene implementar funciones seleccionadas sobre DrKLO actual, evitando fusionar un árbol tan antiguo. Reutilizar código GPL exige cumplir sus condiciones de distribución, atribución y fuente correspondiente. La licencia de código no concede automáticamente derechos sobre marca o logotipo.

Mantener las [condiciones de Telegram API](https://core.telegram.org/api/terms): no añadir modo fantasma que altere estados de leído/actividad/escritura, saltarse protección de contenido o simular Premium. Reenviar sin autor ya existe mediante [`drop_author`](https://core.telegram.org/method/messages.forwardMessages), cuando el contenido admite reenvío; no es una vía para evadir restricciones.

Prioridad: presentación de pestañas, orden local y contadores de carpetas. El catálogo de temas comunitarios solicitado después se desarrolla como integración con el Hub CintiaBot, no como una copia de la marca o servicio de Plus.
