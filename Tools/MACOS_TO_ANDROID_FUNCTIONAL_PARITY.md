# Telegram para macOS → Comunidad Telebots Android: auditoría funcional

Fecha: 2026-11-18  
Destino: Android `dev`, no sustitución del motor MTProto ni port Swift.

## Fuente de referencia

Cliente nativo y oficial: https://github.com/TelegramMessenger/telegram-macos  
Registro de pantallas: `docs/analytics/coverage/macos.md` (472 elementos inventariados, 356 mapeados a pantallas en la versión revisada). Estos números **no equivalen** a funciones exclusivas o faltantes en Android.

Cliente de destino: https://github.com/ComunidadTelebots/Telegram/tree/dev

La última versión macOS publicada en macos.telegram.org en el momento de la auditoría es 13.2.0 (2026-11-12). No confundir el cliente nativo macOS con Telegram Lite / Telegram Desktop Qt.

## Matriz con evidencia del código

| Área | macOS (código) | Android (código dev) | Diagnóstico | Acción |
|---|---|---|---|---|
| Listas y chat | `MainViewController`, `ChatListRowView`, `ChatInputView` | `DialogsActivity.java`, `ChatActivity.java`, `ClientLayout.java` | Implementaciones distintas; el perfil macOS Android es cosmético/parcial | Diseñar layout de dos/tres paneles en tablet/plegable, sin romper móvil |
| Navegación macOS | controladores y tabs de iconos | `ClientNavigationView.java`: barra inferior compacta e iconos Android | Divergencia visual verificada | Especificar navegación adaptativa, foco, teclado y selección |
| Mensajes programados | controles de chat en Mac | Base de mensajería Telegram Android | No marcar como faltante sin prueba funcional | Prueba de paridad, no duplicar |
| Chat folders | interfaz de carpetas Mac | componentes de filtros/carpetas Android | Ya hay soporte Android | Comparar UX y acceso con teclado |
| Respuestas rápidas de negocios | `BusinessQuickReplyController.swift` | `Business/QuickRepliesActivity.java` y `QuickRepliesController.java` | Existe en ambos, no copiar | Auditoría de comportamiento/estados |
| Edición asistida / formato | `TranslateModalController`, `RichTextEditorWindowController` | `Components/AIEditorAlert.java`, `EditTextEffects.java`, `TranslateAlert2/3.java` | Hay componentes Android; paridad exacta no comprobada | Comparar opciones, edición de código/fórmulas y flujo de teclado |
| Traducción | `packages/Translate`, `packages/TranslateUI` | `Components/TranslateAlert2/3.java` | Hay implementación Android | Test de idiomas y selección |
| Calendario | `CalendarController.swift` | `CalendarActivity.java` | Implementación presente | Comparar navegación histórica |
| Llamadas y pantalla compartida | `CallsUI`, `DesktopCapturerWindow.swift` | `CallLogActivity.java`, `GroupCallActivity.java`, etc. | Soporte de llamadas Android presente; captura de pantalla no comprobada | Auditar Android MediaProjection, consentimiento y llamadas activas |
| Exportación | `packages/ChatExportUI` | No identificada en el inventario superficial de UI inspeccionado | **Candidato a brecha, NO ausencia demostrada** | Investigar exportación, permisos SAF, privacidad, cifrado y límites de API |
| Atajos | macOS Shortcuts, teclado/foco | existen clases Android de teclado pero no auditoría de atajos equivalentes | **Brecha de UX probable, NO falta absoluta** | Añadir matriz de Ctrl/Alt en teclado físico y TalkBack, tras inspección |
| Ventanas independientes | integración nativa AppKit | cliente Android centrado en fragments y Activity | No hay paridad demostrada | Propuesta Android multiwindow y layouts responsivos |
| Recuperación de fallo y espacio insuficiente | `CrashRecovery.swift`, `StorageUsageController.swift` | requiere inspección dirigida de Android | No comprobado | Pruebas de fallos recuperables / almacenamiento |
| Mensajes secretos | implementados en macOS | base Telegram Android dispone de chats secretos | No duplicar | Test de compatibilidad y seguridad |

### No portar directamente

- Touch Bar, AppKit, controles NSWindow y menú Compartir de macOS: son API de otra plataforma; buscar equivalentes Android.
- Swift/SwiftUI/AppKit, captura de pantalla de escritorio nativa o motores de red completos: no integrarlos dentro de la APK.
- No crear mecanismos alternativos para acceder a contenidos privados ni debilitar cifrado al exportar.

## Implementación incremental propuesta

**PR funcional 1: navegación y teclado en pantallas grandes.** Antes de modificar el código, comprobar cómo `DialogsActivity`, `ChatActivityContainer`, `ClientNavigationView` y la navegación actual responden en móvil/tablet y con teclado físico. Implementar master/detail únicamente cuando el tamaño disponible lo permita, con selección de chat persistente y accesibilidad.

**PR funcional 2: gestión documental y exportación** solamente si se comprueba la ausencia de una función equivalente, y después de definir el alcance técnico permitido por la API de Telegram y el consentimiento explícito del usuario.

**PR funcional 3: productividad** cubrir exclusivamente diferencias reproducibles entre los controles avanzados Mac y Android: atajos, editor de mensajes, búsqueda de carpetas y navegación por resultados.

## Matriz de QA necesaria antes de publicar

- Android pequeño 360dp, tablet 840dp+, plegables y rotación.
- Teclado físico vs táctil, accesibilidad y TalkBack.
- Temas Android actual, macOS-inspired y Aurora, claro/oscuro según la configuración de cada perfil.
- Sesión y cuenta conservadas después de cambiar el diseño o de pasar de una columna a varias.
- Chats secretos no expuestos en paneles adicionales y sin exportaciones no autorizadas.
- Reproducción de mensajes, descargas y video sin pérdida de estado de navegación.
- Capturas y registros de pruebas reales, nunca solo `SkinPreviewDialog`.

### Estado de la auditoría

Análisis estático inicial, **no prueba de APK ni comparación exhaustiva de todas las funciones**. Cada posible brecha se mantiene como `por comprobar` hasta reproducirla en Android y Mac. Las capacidades ya existentes se reutilizan antes de programar otras nuevas.
