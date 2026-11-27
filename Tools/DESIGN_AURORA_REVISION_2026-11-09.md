# Aurora Android · revisión solicitada el 9 de noviembre de 2026

Esta revisión conserva intactas las referencias del 6 de noviembre. Es una
adaptación móvil del diseño original de Comunidad Telebots React, no un cliente
oficial de Telegram ni una nueva certificación de los otros 15 estilos.

## Fuentes primarias

Repositorio React: https://github.com/ComunidadTelebots/telegram-react

Commit de los dos CSS: `55ff2366823eba3d2a2b7f0f599a186d9a39c9a3`.
No se atribuyen al commit los cambios locales de `Design.js` y `Theme.js`.

| Regla original | Fuente exacta | Adaptación Android |
|---|---|---|
| Manrope, pesos 400–700 | `src/designs/aurora.css`, líneas 9–19 | Manrope estática 400/600 incluida en assets, con licencia OFL; mensajes, títulos de diálogos, cabecera y compositor. Código monoespaciado conservado. |
| Fondo #0d1117, panel #161b22, texto #e6edf3, acento #34d9a8 | mismo archivo, líneas 22–73 | Tokens `.attheme` superpuestos mientras Aurora esté activo. No se sobrescribe el tema guardado del usuario. |
| Burbujas 14/14/14/4 y 14/14/4/14, sin cola | mismo archivo, líneas 80–85 y 189–205 | Ruta nativa de MessageDrawable; radio 14 dp y esquina interior 4 dp. Agrupaciones, multimedia y botones mantienen su semántica Android. |
| Controles planos, radio 8 y selección menta translúcida | mismo archivo, líneas 107–124 | Navegación móvil propia con Chats, Contactos, Llamadas y Ajustes. Son destinos Android, no las carpetas web. |
| Título de diálogo 14 y secundario 13 | mismo archivo, líneas 143–152 | Tamaños base equivalentes en dp, ampliables con el tamaño de texto del sistema. |
| Midnight #060a0e, #0a0f14, #00e5c8, burbujas #002e29/#0e1620 | `src/designs/aurora-midnight.css`, líneas 5–32 y 35–57 | Variante Midnight dentro de la misma familia Aurora. |

Base Android: https://github.com/ComunidadTelebots/Telegram,
commit `87e98351b34160fd99391eb102910144c3a8379c` más cambios locales previos.
`AuroraSkin.java` original, líneas 5–11, solo definía radio 18, ausencia de cola,
avatares y separadores. Esa configuración no reproducía la paleta React.
La modificación intencionada cambia ese radio de 18 a 14 exclusivamente en Aurora.

Referencia Canva consultada: https://www.canva.com/design/DAHaJwE0B-A/edit
(diapositiva 16). Sigue siendo una propuesta histórica rechazada; sus dimensiones
locales no son la fuente del nuevo diseño. Véase `DESIGN_BASELINE.md`.

## Diferencias móviles y límites

- La barra inferior de 64 dp (ampliable para texto grande) es una adaptación nueva
  para móvil. El CSS original usa un lateral de 330 px en escritorio, líneas 88–100;
  no se afirma que esa barra exista en React ni se fuerza ese ancho en teléfonos.
- Se conservan la altura nativa de las filas, la cabecera accesible, los tamaños
  personalizados del texto de los mensajes y las acciones de Telegram Android.
- Los controles que no existen en el CSS usan la paleta nativa Dark Blue como
  respaldo. No se atribuye ese diseño a Aurora React.
- No se cambian las duraciones de movimiento. La animación configurable existente
  se conserva; no se afirma que los tiempos Android reproduzcan CSS no verificado.
- La variante de mensajes cortos con cuatro esquinas iguales del CSS, las medidas
  completas del compositor web, el diseño de escritorio/tablet, las llamadas en
  curso y todos los tipos de multimedia no se declaran idénticos ni verificados.
- Las capturas de burbujas están rotuladas como componentes con datos de prueba.
  Las capturas de navegación se redactan para ocultar conversaciones privadas;
  no constituyen una comprobación visual de toda la lista de chats.

## Verificación reproducible

`AuroraDeviceTest` comprueba ambos estilos en la aplicación: paleta, ausencia de
gradientes heredados, fuente, radio, navegación a Llamadas, compositor real y
restauración del tema. No envía mensajes ni realiza llamadas. La prueba exige
una sesión Telegram en el emulador y la opción DEV de previsualización ya
existente; las restricciones de acceso de release y teléfonos se conservan.

Ejecución del 9 de noviembre: **PASS** en Pixel 6 emulado, API 37, páginas de
memoria de 16 KB. Se comprobaron las dos variantes, el compositor real, la
navegación a Llamadas, el retorno al tema anterior y la barra con texto al 160 %
en 360 dp. Esta última prueba detectó un recorte y motivó aumentar la altura
de Aurora con el tamaño de texto; las etiquetas largas se reparten en dos líneas.
No equivale a probar todos los tamaños, idiomas o teléfonos físicos.

Se inspeccionaron visualmente las capturas de ambos compositores y la navegación.
Se mantienen las formas redondeadas de la cabecera y del compositor nativos;
el mensaje fijado todavía utiliza el azul de la paleta nativa de respaldo.
Estas superficies no se presentan como reproducciones exactas del compositor web.

Evidencias y registros de esta ejecución: `output/aurora-20261109/`, con ocho
capturas, `device-test.log`, galería `index.html` y `release/status.json`.

La fuente se descarga de Google Fonts y se convierte en TTF estática para evitar
depender del soporte de fuentes variables en Android antiguos. Origen, versión
de la herramienta y huellas se conservan en `assets/fonts/aurora/provenance.json`.
