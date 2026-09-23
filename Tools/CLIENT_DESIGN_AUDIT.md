# Revisión de fidelidad de los diseños

Fecha: 2026-09-23. Base revisada: `4de459bd9` y las capturas del visor de `e4947983c`.

## Resultado

Los seis perfiles **no reproducen fielmente los clientes de referencia**. La
navegación genérica, una conversación dibujada común y pequeñas variaciones de
burbujas no bastan. Las capturas anteriores son del selector, no de seis clientes
portados ni de seis pantallas principales equivalentes.

Se revisaron las fuentes raíz y 19 archivos adicionales de listas, compositores,
carpetas y distribución. Los commits, URLs y huellas de los archivos están en
`client-design-references.json`. Es una auditoría estructural del código y de las
capturas locales; no una comparación píxel a píxel de clientes autenticados.

Telegram incluye Unigram entre los clientes **no oficiales**:
[directorio de aplicaciones](https://telegram.org/apps#unofficial-apps).

## Errores compartidos

1. `SkinPreviewDialog` dibuja cabecera de conversación, historias, mensajes,
   compositor y navegación de nivel raíz simultáneamente. Es una composición
   artificial; no sirve como pantalla de referencia para comprobar fidelidad.
2. `ClientNavigationView` reutiliza cuatro destinos e iconos Android. Las barras
   de carpetas de Web A, Web K y Unigram tienen otro contenido y comportamiento.
3. `ClientChrome` cambia colores de una ActionBar; no implementa las cabeceras de
   los clientes, su búsqueda, botones, transparencias o distribución.
4. Los perfiles no controlan la geometría completa de `DialogCell`, el compositor,
   los menús y ajustes. Mantienen mayoritariamente las pantallas Android.
5. Los anchos de 60/64/72 dp y alturas de 48/56 dp del enum no constituyen una
   especificación contrastada. Las fuentes usan unidades y reglas adaptativas
   diferentes; no se pueden convertir todas directamente a dp.
6. Los números de versión del cliente no equivalen a una versión del diseño.
   Una etiqueta sin cambios visuales no debe crear una variante ficticia.

## Diferencias por cliente

| Cliente | Evidencia en sus fuentes | Divergencia local y cambio necesario |
|---|---|---|
| iOS | `TelegramRootController` construye contactos, llamadas opcionales, chats y ajustes. `ChatListItem` calcula un avatar normal de hasta 60 puntos según fuente y contexto. El compositor contiene controles y fondos Glass propios. | La barra local fuerza Llamadas, usa iconos Android y omite materiales, búsqueda y geometría de filas. Separar lista y conversación; reproducir cabecera, pestañas, filas y compositor de un commit concreto. |
| Telegram X | `MainController` incluye drawer, filtros y carpetas; no se limita a dos pestañas fijas. `ChatView` define filas de 72/78/82 dp y avatares de 52/58/60 dp según modo. | Dos botones superiores no equivalen a su navegación. Implementar drawer, modos de carpetas, densidad, tipografía y cabecera; conservar sus opciones en lugar de forzar un único aspecto. |
| Unigram | `MainPage.xaml` tiene `MasterDetailView`, lista y navegación de carpetas de 72 unidades. `ChatCell.xaml` define avatar de 48 y mínimo de fila de 64. `ProfilePicture` usa elipse para usuarios normales. | La barra local contiene destinos en vez de carpetas. El factor de avatar 0.2 era incorrecto para usuarios normales. Rehacer panel, búsqueda y filas; adaptar ventanas anchas y estrechas. |
| macOS | `MainViewController` usa controladores de navegación y tabs con iconos propios. `ChatListRowItem` usa 70 puntos para chats normales; `ChatListRowView` crea avatar de 50. `ChatInputView` separa adjuntos, texto y acciones. | Ocultar etiquetas en una barra Android no reproduce macOS. Faltan columnas, búsqueda, densidad de lista, selección y compositor. No confundir este cliente Swift con Telegram Desktop Qt. |
| Web A | `Main.scss` usa reglas a 600 y 926 píxeles CSS, columnas, paneles con márgenes y radios. `LeftMainHeader` integra menú y búsqueda. `FoldersSidebar` lista carpetas y ofrece ajustes. | La barra de cuatro botones no corresponde a ese panel. Se necesitan estados móvil/escritorio, lista, carpetas, cabecera y compositor independientes; no escalar una vista de escritorio dentro del teléfono. |
| Web K | `_leftSidebar.scss` contiene panel plegable, búsqueda, estados con/sin carpetas y reglas adaptativas. `_sidebar.scss` define una cabecera mínima de 3.5 rem. | No es Web A con un radio diferente. Hay que reproducir su propia estructura de panel, barra de carpetas, estados de búsqueda y controles de conversación. |

Las medidas anteriores describen casos concretos de las fuentes revisadas, no
valores universales: foros, tamaños de texto, opciones y otros estados cambian el
resultado. Los colores deben verificarse para el tema concreto elegido.

## Orden de sustitución

1. Elegir cliente, hito exacto y tamaño de pantalla de referencia.
2. Reemplazar la vista previa híbrida por pantallas separadas de lista y chat,
   con las mismas vistas usadas en la aplicación y datos locales de demostración.
3. Sustituir la navegación incorrecta, especialmente las barras de carpetas.
4. Implementar filas, búsqueda, cabeceras, compositor, iconos y menús de ese hito.
5. Validar ajustes/perfil, selección, archivos, foros, teclado y navegación Atrás.
6. Comparar capturas con la misma escala, fuente, tema, contenido y estado; revisar
   transiciones con vídeo. Solo después marcar el hito como reproducido.

## Selector histórico

El catálogo separa la investigación de un hito y su implementación. Una selección
histórica pendiente se guarda como **referencia**, no sustituye silenciosamente el
diseño activo por otro con el mismo aspecto. Las entradas deben mostrar el cambio,
fecha, commit, etiqueta si existe, origen y estado de implementación. No usar una
fecha de commit como fecha garantizada de publicación en la tienda.

El primer catálogo es una selección curada, no una historia exhaustiva desde el
primer lanzamiento. macOS y Web K no devolvieron etiquetas ni releases en la
consulta; para ellos se usan instantáneas de código identificadas por commit.

## Fuentes concretas

### iOS

- [Fuente raíz](https://github.com/TelegramMessenger/Telegram-iOS/blob/6ad963e5b62d354da79040f388ae2b9132fb17b8/submodules/TelegramUI/Sources/TelegramRootController.swift).
- [ChatListItem.swift](https://github.com/TelegramMessenger/Telegram-iOS/blob/6ad963e5b62d354da79040f388ae2b9132fb17b8/submodules/ChatListUI/Sources/Node/ChatListItem.swift).
- [ChatTextInputPanelNode.swift](https://github.com/TelegramMessenger/Telegram-iOS/blob/6ad963e5b62d354da79040f388ae2b9132fb17b8/submodules/TelegramUI/Components/Chat/ChatTextInputPanelNode/Sources/ChatTextInputPanelNode.swift).

### Unigram

- [Fuente raíz](https://github.com/UnigramDev/Unigram/blob/55c94e0fb3e42fe06fee8f70f6ef94ec7b10fd1b/Telegram/Views/MainPage.xaml).
- [ChatCell.xaml](https://github.com/UnigramDev/Unigram/blob/55c94e0fb3e42fe06fee8f70f6ef94ec7b10fd1b/Telegram/Controls/Cells/ChatCell.xaml).
- [ChatPage.xaml](https://github.com/UnigramDev/Unigram/blob/55c94e0fb3e42fe06fee8f70f6ef94ec7b10fd1b/Telegram/Views/ChatPage.xaml).
- [ProfilePicture.cs](https://github.com/UnigramDev/Unigram/blob/55c94e0fb3e42fe06fee8f70f6ef94ec7b10fd1b/Telegram/Controls/ProfilePicture.cs).
- [ChatView.xaml](https://github.com/UnigramDev/Unigram/blob/55c94e0fb3e42fe06fee8f70f6ef94ec7b10fd1b/Telegram/Views/ChatView.xaml).

### Telegram X

- [Fuente raíz](https://github.com/TGX-Android/Telegram-X/blob/9312ace35291f68fdd66ddff348d7321bee2a4bd/app/src/main/java/org/thunderdog/challegram/ui/MainController.java).
- [ChatView.java](https://github.com/TGX-Android/Telegram-X/blob/9312ace35291f68fdd66ddff348d7321bee2a4bd/app/src/main/java/org/thunderdog/challegram/component/dialogs/ChatView.java).
- [ChatsController.java](https://github.com/TGX-Android/Telegram-X/blob/9312ace35291f68fdd66ddff348d7321bee2a4bd/app/src/main/java/org/thunderdog/challegram/ui/ChatsController.java).

### macOS

- [Fuente raíz](https://github.com/overtake/TelegramSwift/blob/579cebbf0c01fd41b712eff3647fa7f69db9665d/Telegram-Mac/MainViewController.swift).
- [ChatListRowView.swift](https://github.com/overtake/TelegramSwift/blob/579cebbf0c01fd41b712eff3647fa7f69db9665d/Telegram-Mac/ChatListRowView.swift).
- [ChatInputView.swift](https://github.com/overtake/TelegramSwift/blob/579cebbf0c01fd41b712eff3647fa7f69db9665d/Telegram-Mac/ChatInputView.swift).
- [ChatListRowItem.swift](https://github.com/overtake/TelegramSwift/blob/579cebbf0c01fd41b712eff3647fa7f69db9665d/Telegram-Mac/ChatListRowItem.swift).

### Web A

- [Fuente raíz](https://github.com/Ajaxy/telegram-tt/blob/ea0d226147a80f05253bf1a6ffef08d694b8e6e4/src/components/left/main/LeftMain.tsx).
- [LeftMain.scss](https://github.com/Ajaxy/telegram-tt/blob/ea0d226147a80f05253bf1a6ffef08d694b8e6e4/src/components/left/main/LeftMain.scss).
- [Chat.scss](https://github.com/Ajaxy/telegram-tt/blob/ea0d226147a80f05253bf1a6ffef08d694b8e6e4/src/components/left/main/Chat.scss).
- [Main.scss](https://github.com/Ajaxy/telegram-tt/blob/ea0d226147a80f05253bf1a6ffef08d694b8e6e4/src/components/main/Main.scss).
- [Message.scss](https://github.com/Ajaxy/telegram-tt/blob/ea0d226147a80f05253bf1a6ffef08d694b8e6e4/src/components/middle/message/Message.scss).
- [LeftMainHeader.tsx](https://github.com/Ajaxy/telegram-tt/blob/ea0d226147a80f05253bf1a6ffef08d694b8e6e4/src/components/left/main/LeftMainHeader.tsx).
- [FoldersSidebar.tsx](https://github.com/Ajaxy/telegram-tt/blob/ea0d226147a80f05253bf1a6ffef08d694b8e6e4/src/components/main/FoldersSidebar.tsx).

### Web K

- [Fuente raíz](https://github.com/morethanwords/tweb/blob/cac8174ea6833147fc89991629e6c34296f32eb8/src/scss/partials/_leftSidebar.scss).
- [_chat.scss](https://github.com/morethanwords/tweb/blob/cac8174ea6833147fc89991629e6c34296f32eb8/src/scss/partials/_chat.scss).
- [_sidebar.scss](https://github.com/morethanwords/tweb/blob/cac8174ea6833147fc89991629e6c34296f32eb8/src/scss/partials/_sidebar.scss).

