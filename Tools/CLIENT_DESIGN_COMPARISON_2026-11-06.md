# Comparación del código integrado con los clientes de referencia

Revisión estructural del árbol local basado en `87e98351b`, con cambios locales.
No es una prueba visual del APK ni certifica equivalencia con la última versión
de cada cliente. No se modificó la implementación de la interfaz en esta revisión.

Presentación: https://www.canva.com/d/9yN-NLMA6tgKtBS

## Fuente principal exigida por el usuario

Las reglas deben derivarse del repositorio original, fijando commit, archivo y
líneas. Las imágenes de la presentación anterior son apoyo visual, no la fuente
de medidas, comportamiento o animaciones. Esa presentación aún no incorpora
todos los enlaces de esta revisión de fuentes.

La comprobación está en
`output/design-source-audit/2026-11-06/verified-sources.json` y sus archivos locales:

- Se recuperaron 21 archivos de iOS, Telegram X, Unigram, Web A y Web K en los
  commits de la auditoría anterior. Coinciden las 16 huellas previamente registradas.
- Android: `MainTabsActivity.java`, commit
  `3de37c8cfb039452be7398909461da6288babf15`.
- Desktop: `dialogs_widget.cpp`, commit
  `93624f698c9a883c3a76428b96d6573c4dd63a6d`.
- macOS: los cuatro enlaces fijados y la consulta del repositorio devolvieron 404.
  No considerar revalidadas sus antiguas medidas hasta recuperar la fuente.
- Webogram: la ruta de plantilla consultada devolvió 404. Pendiente localizar el
  archivo correcto y fijarlo. No sustituir esa evidencia por una maqueta inventada.
- Los commits identifican instantáneas de código, no garantizan una versión de tienda.

Ejemplos comprobados directamente: Unigram define `MasterDetailView` en
`MainPage.xaml:484`, ancho de carpeta en `:153`, fila mínima 64 en
`ChatCell.xaml:26` y avatar 48 en `:61`; son unidades del framework original.
Web A usa condiciones CSS de 600 y 926 px en `Main.scss:19,30`. Web K define
cabecera mínima de 3.5 rem en `_sidebar.scss:10`. No convertir estas unidades en
dp Android sin analizar densidad, tamaño de texto y contexto.

## Resultado

Existen 16 perfiles registrados, pero no 16 implementaciones independientes de
pantallas. Las dos presentaciones anteriores fueron rechazadas por el usuario y
no constituyen una especificación aprobada. Sus radios locales no deben tomarse
como medidas oficiales de los clientes originales.

## Conexiones comprobadas en el código

Todos los paths Java siguientes parten de `TMessagesProj/src/main/java/org/telegram/ui/`.

| Elemento | Integración encontrada | Límite |
|---|---|---|
| Radio | `skins/SkinManager.java`, `applyToSharedConfig` asigna `SharedConfig.bubbleRadius` | No cambia la estructura de la conversación |
| Colas | `ActionBar/MessageDrawable.java:868,888` consulta `hasBubbleTail` | Es una variación del drawable compartido |
| Avatares | `Cells/DialogCell.java:700,3260,3712` consulta el factor | No sustituye la fila por la del cliente original |
| Separadores | `Cells/DialogCell.java:4823` consulta `showChatDividers` | Misma celda Android |
| Cabeceras | `DialogsActivity.java:5660`, `ChatActivity.java:9063` llaman `ClientChrome.apply` | `ClientChrome` cambia colores, no crea cabeceras propias |
| Navegación | `MainTabsActivity.java:408` crea `ClientNavigationView` | Reutiliza destinos e iconos Android |
| Pantallas raíz | `MainTabsActivity.java:853-900` crea DialogsActivity, ContactsActivity, CallLogActivity y SettingsActivity | No selecciona implementaciones propias por cliente |
| Tipografía | `skins/SkinBase.getTypeface`, override iOS | Las llamadas del sistema de estilos están en SkinPreviewDialog, no en las pantallas reales revisadas |
| Animación | `ActionBar/ActionBarLayout.java:1843,1905,1920,2010,2235,2575` usa MotionController | Preferencias comunes de transición, no repertorios propios de cada cliente |

## Diferencias por familia

- Android: conserva la base nativa. Original, Holo, Material, Classic, Redesign y
  Glass cambian parámetros, no reconstruyen una interfaz histórica completa.
- Telegram X: pestañas Chats/Llamadas y menú genéricos. Faltan sus opciones de
  navegación, densidad y modos de conversación, incluido el modo sin burbujas
  documentado en el anuncio original.
- Web A y Web K: comparten barra lateral y pantallas Android. Los destinos
  Chats/Contactos/Llamadas/Ajustes no equivalen a sus carpetas y paneles propios.
- iOS: barra inferior y colores. No se reproduce la geometría completa, los
  controles Glass ni la tipografía nativa. No confundir Glass heredado de la base
  Android con una implementación de la interfaz iOS.
- macOS: barra inferior sin etiquetas y colores. Faltan columnas y controles de
  su cliente nativo. Es distinto de Telegram Desktop.
- Unigram: barra lateral genérica. No reproduce su vista lista/detalle y carpetas.
- Desktop y Webogram: caen en ClientLayout.ANDROID. No tienen navegación propia.
- Aurora: perfil propio de parámetros. Falta una especificación visual independiente.

## Referencias visuales

`output/design-comparison/2026-11-06/sources.json` conserva URLs y SHA-256 de ocho
imágenes publicadas por Telegram o los repositorios de los clientes. Cada slide
declara el origen y la antigüedad conocida. Telegram X usa una referencia histórica
de 2018. Las imágenes promocionales sin versión no prueban la apariencia actual.
Los ocho perfiles restantes se declaran sin referencia independiente verificada,
en lugar de inventar una pantalla idéntica con distinto nombre.

## Criterio para implementar

Fijar versión y estado de referencia por cliente. Implementar lista, conversación,
cabecera, búsqueda, compositor, menús y ajustes como componentes específicos cuando
la referencia difiera. Compartir lógica de Telegram y datos, no forzar un único
layout. Comparar capturas con el mismo estado y revisar vídeos de transiciones
antes de afirmar fidelidad. Las animaciones no se validan por una tabla de tiempos.
