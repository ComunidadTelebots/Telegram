# Auditoría y criterios de fidelidad — SOLO Telegram Android

Fecha de revisión de fuentes: 2026-11-18
Rama auditada: `dev`
Alcance: **APK Android Comunidad Telebots**, no ports de iOS, macOS, Windows, Web A/K ni Webogram. Incluye estilos históricos del cliente Telegram Android, Telegram X (otro cliente para Android) y Aurora (diseño original que se adapta a Android).

## Objetivo de producto

Al escoger un perfil, cambiar **la interfaz Android completa** (lista de conversaciones, chat, compositor, búsqueda, carpetas, ajustes, perfiles, llamadas, multimedia, menús y animaciones), no solamente radios de burbujas y posición de la barra. Cada edición histórica debe corresponder a un hito visual fechado/documentado. Mantener operativas las funciones modernas y adaptarlas al vocabulario visual del perfil, sin fingir que existían en aquella época.

## Evidencia actual

- `SkinManager.java` registra los perfiles y cambia preferencias de burbujas, avatares, divisores y `ClientLayout`. La selección activa es persistente.
- `ClientLayout.java` asigna `TELEGRAM_X` a pestañas superiores de 48 dp; para los estilos históricos Android y Aurora devuelve `ANDROID`.
- `ClientNavigationView.java` reutiliza iconos y destinos propios de Android; el caso Telegram X contiene solo Chats/Llamadas y menú de otros destinos.
- `TelegramXSkin.java` solo define radio de burbuja 14 dp, cola, avatares circulares y divisores.
- `AuroraSkin.java` únicamente define radio 18 dp, burbuja sin cola, avatar 0.3 y divisores ocultos. No importa la identidad visual de React.
- La documentación existente `Tools/CLIENT_DESIGNS.md` y `Tools/CLIENT_DESIGN_AUDIT.md` ya reconoce equivalencia visual no alcanzada; las vistas previas no son capturas autenticadas.
- `client_design_history.json` únicamente fija dos hitos Android de la base oficial: historias (2023-08-14) y navegación inferior (2026-02-14). Ambos se marcan `reference-only`; **no** está demostrada la reconstrucción de cada año.

## Matriz de perfiles Android

| Perfil Android | Objetivo de fidelidad | Estado comprobable en código | Pendiente |
|---|---|---|---|
| Android actual | Aspecto del hito actual de DrKLO/Telegram elegido | Perfil registrado, hereda interfaz Android | Comparativa visual con APK de referencia; navegación y componentes |
| Android Original | Hito Android temprano **por determinar** | `AndroidOriginalSkin` existe | Fijar año/tag/commit; recrear toolbar, lista, chat, menús |
| Android Holo | Hito Holo Android **por determinar** | `AndroidHoloSkin` existe | Recursos Holo, densidad, tipografía, controles y movimiento |
| Android Material | Hito Material Android **por determinar** | `AndroidMaterialSkin` existe | Toolbar, iconos, vistas, compositor y estados de esa edición |
| Android Classic | Hito clásico **por determinar** | `AndroidClassicSkin` existe | Diferenciar de Original y Holo mediante fuentes y capturas |
| Android Redesign | Hito de rediseño **por determinar** | `AndroidRedesignSkin` existe | Reconstruir navegación, filas, búsqueda, cabeceras y chat |
| Android Glass | Hito Glass **por determinar** | `AndroidGlassSkin` existe | Transparencias y tratamiento de superficies + degradación accesible |
| Telegram X | Seleccionar versión Android X por etiqueta/commit, incluidos filtros y carpetas propios | `TelegramXSkin` + dos pestañas genéricas | Drawer, filas, navegación, conversación, compositor y ajustes TGX; preservar funciones de la base |
| Aurora | Identidad original de `telegram-react` llevada al Android nativo | Perfil Aurora mínimo, sin esquema completo | Tipografía Manrope, fondos oscuros, acento menta, filas y mensajes sin cola, búsqueda, ajustes |
| Aurora Midnight | Variante oscura adicional basada en `telegram-react/src/designs/aurora-midnight.css` | No registrada como perfil Android | Definir ID propio, colores, selección y pruebas de regresión |

No asignar retrospectivamente años exactos a `Original/Holo/Material/Classic/Redesign/Glass` hasta elegir un commit verificable. Las denominaciones locales no equivalen a publicaciones oficiales.

## Fuentes de producto por familia

- Android: https://github.com/DrKLO/Telegram
- Telegram X (Android): https://github.com/TGX-Android/Telegram-X
- Aurora React, **solo como referencia visual** para Android: https://github.com/ComunidadTelebots/telegram-react/blob/master/src/designs/aurora.css
- Aurora Midnight: https://github.com/ComunidadTelebots/telegram-react/blob/master/src/designs/aurora-midnight.css
- Hito Android historias 2023: https://github.com/DrKLO/Telegram/commit/702d37ce69fca77e78072ad408e9d29dfd2d2be8
- Hito Android pestañas 2026: https://github.com/DrKLO/Telegram/commit/fa4e8aa0ddbd2416c382d36eaab6ec1c08e9fa00
- Hito X carpetas 2024: https://github.com/TGX-Android/Telegram-X/releases/tag/v0.26.9.1730
- Hito X edge-to-edge 2025: https://github.com/TGX-Android/Telegram-X/releases/tag/v0.28.1.1771

## Reglas de compatibilidad entre épocas

1. **Funciones originales:** si una función estaba presente en el hito seleccionado, respetar interacción, distribución y semántica documentadas de esa edición.
2. **Funciones nuevas:** mantener mensajería, llamadas, historias, vídeo, reacciones, carpetas, accesibilidad y seguridad del motor Android vigente; darles controles visuales coherentes con el estilo histórico. No usar iconos falsamente históricos para acciones nuevas.
3. **Sin regresión:** los mismos chats, datos, cuentas, permisos y cifrado deben sobrevivir al cambio de apariencia. No importar ni mezclar motores externos.
4. **Adaptación:** diferencias por tamaño, pantalla plegable, orientación, idioma RTL, texto grande, contraste, lector de pantalla y modo oscuro.
5. **Integridad histórica:** cada año/versión tiene código/tag/captura fuente verificable; no confundir evolución de funcionalidades con cambio de diseño.
6. **Pruebas:** comparar sobre las mismas cuentas ficticias/datos de prueba, resolución, densidad, fuente y estado. Registrar imágenes y vídeos de interacción. Capturas del selector actual **no acreditan** equivalencia visual.

## Orden propuesto de implementación

1. **P0**: definir estructura de interfaz por perfil en Android sin alterar la base de mensajería, empezando por Telegram X (drawer/filtros/listas/chats) para resolver la discrepancia reportada.
2. **P1**: portar el sistema de tokens Aurora y Midnight al renderizado Android, sin copiar el React y sin forzar diseños oscuros a preferencias de otros perfiles.
3. **P1**: escoger hitos Android anclados a tags/commits, empezar por Original/Holo/Material, documentar qué UI existía realmente y cómo se insertan funciones posteriores.
4. **P2**: ampliar a Redesign/Glass/Classic con matrices de estados completos.
5. **Validación obligatoria**: compilación, pruebas Android instrumentadas/emulador y comparación de capturas antes de declarar fidelidad o promover ramas.

## Criterios de aceptación de cada perfil

- [ ] Hito original identificado mediante fuente primaria concreta.
- [ ] Lista de chats, búsqueda y carpetas adaptadas.
- [ ] Conversación, compositor, emojis, archivos, reacciones y multimedia adaptados.
- [ ] Ajustes, perfil, llamadas, menús y navegación coherentes.
- [ ] Funciones históricas comprobadas y funciones modernas accesibles.
- [ ] Cambio de perfil y reinicio conservan selección y sesión.
- [ ] QA de tema oscuro/claro, accesibilidad, RTL, pantallas y texto grande.
- [ ] Capturas de referencia comparadas con build real Android.
- [ ] Registro del SHA compilado y pruebas reproducibles; sin pruebas no se marca completo.

**Estado de esta auditoría:** revisión estática del código y fuentes citadas. No se ha ejecutado una APK ni se ha verificado equivalencia pixel a pixel.
