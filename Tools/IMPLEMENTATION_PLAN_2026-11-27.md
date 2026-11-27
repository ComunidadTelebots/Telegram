# Plan ampliado: Telegram oficial 13.2.2 + funciones Comunidad Telebots

Revisión: 27 de noviembre de 2026. Estado: plan de implementación; no equivale a funcionalidades terminadas ni a diseño aprobado.

## Mandato y alcance conservado

Este documento amplía el plan que estaba abierto en Android Studio. Se conservan sus 100 objetivos P2P, malla, caché, karma, utilidades offline y reparación de los diseños históricos. Se añade la actualización completa al código oficial verificado de Telegram Android 13.2.2 (7303), sin sustituir las funciones propias por las oficiales.

La última indicación del usuario fija ambas obligaciones: actualizar a Telegram oficial y mantener lo que ya quería implementar. La actualización debe preservar AMP/InstantView, lectura offline, Tor/proxy/DNS, seguridad y acceso, herramientas de mensajería, perfiles de consumo, personalización, navegación y marca del fork. No reiniciar el desarrollo desde una APK 12.10.6.1.

## 1. Estado comprobado y ramas

- Fuente oficial consultada: `https://github.com/DrKLO/Telegram`, referencia `upstream/master`, commit `21f7d109da6ab3506c9e9577f98c6a38771ab335`. Versión 13.2.2 / 7303, declarada en `gradle.properties`, líneas 16 y 17 de ese commit.
- `dev` local y `codex/aurora-play-alfa-20261109`: `87e98351b34160fd99391eb102910144c3a8379c`, versión 13.1.1 / 7237. Ambas contienen por ascendencia el commit oficial 13.1.1 `3de37c8cfb039452be7398909461da6288babf15`; no es solo un cambio de número.
- `origin/dev`: `b6d8e69e0db7ceec4f3cdad75430b075a51c41b2`, declara 12.10.6. Diverge de `dev` local: 47 commits solo locales y 9 solo remotos en la revisión. No hacer un reset ni sustituir una rama por la otra.
- La revisión completa de ramas está en `output/branch-audit-20261127.csv` y `.json`. Son inventarios, no prueba de compilación.
- El árbol principal tiene cambios locales previos. Se deben conservar y registrar por separado. Una fusión exploratoria con 13.2.2 encontró conflictos en 18 archivos; fue abortada sin modificar las ramas ni sobrescribir esos cambios. `scratch/dev-upstream-20261127` queda como copia separada limpia de `dev`.

## 2. Orden de implementación y entregables

### Fase A. Consolidar DEV sin perder trabajo

1. Guardar un inventario del árbol principal y una copia recuperable de sus cambios, incluidos archivos nuevos; no incorporarlos a ciegas ni borrar el árbol del usuario.
2. Comparar `dev`, `origin/dev` y las ramas de funciones. Reconciliar los 9 commits exclusivos remotos con la base local 13.1.1. Revisar también arreglos de P2P, Firebase API21, QA e interfaz que existan en otras ramas; documentar cuáles están ya incluidos y cuáles faltan.
3. Mantener el paquete `org.comunidadtelebots`, canal DEV y las funciones del fork. Separar el soporte histórico Android del soporte efectivamente compatible con dependencias oficiales; no prometer API21 por cambiar `minSdk`.
4. Trabajar en una copia aislada de DEV. Conservar la referencia anterior y dejar cada integración revisable. No publicar ni sobrescribir ramas remotas sin instrucción para ello.

Criterio de salida: base DEV consolidada, diferencias documentadas y ningún cambio del usuario perdido.

### Fase B. Incorporar Telegram Android oficial 13.2.2 completo

1. Integrar el rango oficial `3de37c8cf..21f7d109da6ab3506c9e9577f98c6a38771ab335` con su historial y dependencias; no limitarse al último commit ni cambiar únicamente `APP_VERSION_NAME`.
2. Inventario observado: 1.145 archivos cambiados entre ambas bases oficiales, 97.283 líneas añadidas y 44.935 eliminadas. La magnitud exige una integración por bloques verificables.
3. Incorporar los cambios de protocolo/modelos, mensajería, panel lateral de temas, multimedia, llamadas/PiP, autenticación, descargas, widgets y sus dependencias, según el código realmente presente. No considerar que toda clase nueva sea por sí sola una función terminada.
4. Adaptar los cambios propios al despiece de `ChatMessageCell`: los botones AMP/InstantView y su accesibilidad deben trasladarse o conectarse a los nuevos colaboradores correspondientes. Evitar conservar una copia antigua completa que pierda correcciones oficiales.
5. Integrar las correcciones oficiales de mensajes protegidos, fotos eliminadas, fondos, vídeo a pantalla completa, fotos con movimiento, velocidad de reproducción, traducciones y temas. Mantener las restricciones oficiales de contenido.
6. Resolver explícitamente compilación nativa, Gradle/Firebase, manifiestos y nuevos submódulos `twidget` y `widget-sandbox`, fijados a sus commits exactos. Un submódulo inaccesible es un bloqueo de compilación, no una tarea superada.
7. Preservar identidad, navegación y perfiles visuales existentes al resolver conflictos. Los cambios visuales necesarios para una función nueva requieren comparación y documentación; integrar código oficial no autoriza reemplazar todos los diseños por uno nuevo.

Criterio de salida: versión 13.2.2 con las funciones propias conservadas, sin conflictos, compilada y con regresiones verificadas. No se ha alcanzado todavía.

### Fase C. Cumplir el plan P2P original con funciones reales

1. Mantener los 100 objetivos y sus identificadores. El catálogo completo se conserva más abajo junto al plan original.
2. Auditar `TelebotsP2pFeatureRegistry`, `TelebotsP2pHubController`, caché, rutas, economía, sincronización y mensajería independiente. En la inspección actual, las 100 entradas del registro solo llaman a `FileLog.d(...)`. La existencia de otros motores no demuestra que esas entradas estén conectadas a ellos.
3. Crear una matriz por ID con: comportamiento esperado, implementación real, dependencias, alcance de red/datos, estado, prueba y evidencia. Estados: pendiente, implementado sin verificar, verificado y bloqueado. No marcar una función operativa porque un interruptor guarde una preferencia o aparezca una descripción.
4. Sustituir cada ejecutor de registro por una operación real del módulo correspondiente, con cancelación, límites de recursos, errores visibles y separación por cuenta cuando proceda. Las operaciones no implementadas deben identificarse como pendientes y no comunicar éxito.
5. Preservar las cinco áreas de objetivos originales aunque el registro actual use diez categorías. Conciliar diferencias entre el texto original y los IDs; no eliminar objetivos porque aún no tengan un ID exacto, como sincronización de borradores, retransmisión geográfica o recomprensión dinámica.
6. Comprobar AES autenticado y gestión de claves antes de afirmar cifrado; pruebas criptográficas antes de afirmar zero-knowledge o prueba de relevo; transporte y peers reales antes de afirmar mDNS, BLE, Wi-Fi Direct, Tor, entrega offline o karma ganado. No usar tiempos, nodos o métricas dibujadas como evidencia.
7. Mantener independencia entre la red P2P propia y Telegram. La compatibilidad con condiciones del servicio requiere revisión específica; el texto del plan no la acredita.

Criterio de salida: cada ID tiene un resultado demostrable o un bloqueo explícito. Cien botones y descripciones no equivalen a cien funciones implementadas.

### Fase D. Exposición en Ajustes Comunidad Telebots y diseños

1. Mantener el destino de página 7 (`P2P_MESH`/alias `P2P_MESH_HUB`) y un acceso claro desde Ajustes Comunidad Telebots. Evitar duplicar paneles y conservar la cuenta al navegar.
2. Mostrar las 100 entradas del registro por categorías, con descripción y estado real. Conectar los controles a las operaciones de la fase C, sin cambiar el diseño de referencia innecesariamente.
3. Conservar los selectores de año, diseño y funciones futuras en Ajustes Comunidad Telebots. Mantener `rebuildAllFragmentViews(true, true)` al aplicar un cambio y comprobar que afecta a fragmentos retenidos, celdas, radios, colores y navegación.
4. Antes de modificar UI, consultar `Tools/DESIGN_BASELINE.md` y su referencia Canva. Preservar las referencias fechadas; la galería Apply/Cancel y las propuestas rechazadas no son el objetivo visual. Las 16 vistas sintéticas no verifican todas las pantallas ni temas.
5. Para cada regla de diseño o movimiento atribuida a un cliente, guardar repositorio, commit exacto, ruta y líneas de su fuente original. Unigram es upstream externo, no cliente oficial de Telegram; Aurora es el diseño propio. Marcar variantes históricas o fuentes no disponibles como no verificadas. No obtener dimensiones oficiales de parámetros locales.
6. Verificar antes/después en la aplicación real y documentar diferencias necesarias. El código aún no ejecutado no acredita aprobación visual.

Criterio de salida: panel navegable, funciones con estado veraz, selectores efectivos y capturas comparables de las pantallas realmente probadas.

### Fase E. Compilación, dispositivos y candidata

1. Compilar DEV 13.2.2 con sus submódulos y dependencias; revisar Firebase/Crashlytics en el módulo de aplicación y generar su build ID cuando corresponda. No confundir el fallo de una APK antigua con el estado de una compilación nueva.
2. Ejecutar pruebas de protocolo, AMP/InstantView, accesibilidad, diseños, navegación, Tor/proxy/DNS y módulos P2P, con pruebas de error y aislamiento por cuenta. Para la malla se requieren al menos dos instancias o peers reales de prueba; no simular dispositivos físicos.
3. Instalar y arrancar en Pixel 9 AVD; añadir AVD aproximados de SM-A566B y SM-X216B con resolución/densidad documentadas e imagen Android adecuada. Registrar API efectiva, imagen, ABI y límites: AVD no reproduce One UI, chipset, módem 5G ni comportamiento físico Samsung.
4. Perfiles preparados en `output/qa-candidate-17/avd`: Pixel 9 1080×2424 a 420 dpi con la imagen API37 disponible; A56 aproximado 1080×2340 a 420 dpi, API35 prevista; Tab A9+ aproximada 1200×1920 a 240 dpi, API33 prevista. Las densidades Samsung son aproximaciones de prueba, no medidas oficiales. Las imágenes API35/API33 no están instaladas; esos perfiles no se han arrancado.
5. Evidencia de la candidata original: Actions run 45781796560, artifact 15100921393, commit `5a56812fd2a7a38f0e971109c6873a3602bcc624`. APK universal DEV 12.10.6.1 / 71139; minSdk24, targetSdk36, cuatro ABI. SHA-256 `47dd853a360370b65f26a1b36635c8ea83cd70df95b0cf9e69b0ceb5118b1a40`, coincide con el archivo publicado.
6. `apksigner verify` terminó correctamente y el certificado se identifica como DEV/Testing. Esto no acredita firma estable ni continuidad de una clave de producción.
7. La candidata se instaló correctamente en el AVD Pixel 9/API37. El arranque falla con `FirebaseInitProvider`: falta el Crashlytics build ID. Log en `output/qa-candidate-17/pixel9-crash.log`. No se ha validado el panel ni los 100 controles con esa APK.
8. Entregar la nueva APK con versión, commit, hash, resultado de firma, instalación, PID posterior al arranque, logcat y capturas. Marcar los fallos como fallos. Mantener identificada la APK original sin modificarla ni sustituirla silenciosamente.

Criterio de salida: APK nueva verificable, arranque persistente y pruebas funcionales/visuales documentadas. Pendiente.

## 3. Fuentes oficiales fijadas para la actualización

Las referencias siguientes justifican el inventario de código, no dimensiones ni reglas de movimiento aprobadas:

| Fuente en `DrKLO/Telegram` | Commit | Líneas | Evidencia |
|---|---|---|---|
| `gradle.properties` | `21f7d109da6ab3506c9e9577f98c6a38771ab335` | 16 | `APP_VERSION_CODE=7303` |
| `gradle.properties` | `21f7d109da6ab3506c9e9577f98c6a38771ab335` | 17 | `APP_VERSION_NAME=13.2.2` |
| `TMessagesProj/src/main/java/org/telegram/ui/Components/TopicsSidePanel.java` | `21f7d109da6ab3506c9e9577f98c6a38771ab335` | 77 | `public class TopicsSidePanel extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {` |
| `TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatMessageAccessibilityNodeProvider.java` | `21f7d109da6ab3506c9e9577f98c6a38771ab335` | 52 | `class ChatMessageAccessibilityNodeProvider extends AccessibilityNodeProvider {` |
| `TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatMessageLinkPreview.java` | `21f7d109da6ab3506c9e9577f98c6a38771ab335` | 63 | `class ChatMessageLinkPreview {` |
| `TMessagesProj/src/main/java/org/telegram/ui/Components/WidgetSandboxPlayer.java` | `21f7d109da6ab3506c9e9577f98c6a38771ab335` | 25 | `public final class WidgetSandboxPlayer {` |
| `TMessagesProj/src/main/java/org/telegram/utils/glass/GlassSettings.java` | `21f7d109da6ab3506c9e9577f98c6a38771ab335` | 5 | `public class GlassSettings {` |
| `TMessagesProj/src/main/java/org/telegram/messenger/HeifMotionPhoto.java` | `21f7d109da6ab3506c9e9577f98c6a38771ab335` | 12 | `public class HeifMotionPhoto {` |
| `TMessagesProj/src/main/java/org/telegram/messenger/DownloadSpeedEstimator.java` | `21f7d109da6ab3506c9e9577f98c6a38771ab335` | 9 | `public class DownloadSpeedEstimator {` |
| `.gitmodules` | `21f7d109da6ab3506c9e9577f98c6a38771ab335` | 58 | `[submodule "TMessagesProj/jni/twidget/twidget"]` |
| `.gitmodules` | `21f7d109da6ab3506c9e9577f98c6a38771ab335` | 59 | `path = TMessagesProj/jni/twidget/twidget` |
| `.gitmodules` | `21f7d109da6ab3506c9e9577f98c6a38771ab335` | 61 | `[submodule "TMessagesProj/jni/twidget/widget-sandbox"]` |
| `.gitmodules` | `21f7d109da6ab3506c9e9577f98c6a38771ab335` | 62 | `path = TMessagesProj/jni/twidget/widget-sandbox` |

La consulta de fuentes mediante rastreo debe guardar fecha, URL y commit. Usar el código original como fuente primaria; publicaciones y capturas solo como apoyo. Hay un comparador de recursos en `origin/telebots-crawler-beta-audit:scripts/compare_telegram_beta_resources.py`; auditarlo antes de ejecutarlo e incorporarlo al inventario. Un comparador de recursos no demuestra equivalencia funcional ni autoriza fusionar automáticamente.

## 4. Catálogo conservado de las 100 funciones

Estado de las entradas del registro a esta revisión: ejecutores de log, pendientes de conectar y demostrar. El nombre describe el objetivo, no una capacidad ya acreditada.

| ID | Objetivo conservado | Categoría del registro | Comportamiento previsto |
|---|---|---|---|
| P2P-001 | AES-256 Encrypted Local Content Cache | Storage | Stores static InstantView/AMP content using authenticated AES-256 encryption. |
| P2P-002 | Merkle Tree Content Verification | Storage | Verifies chunk integrity via Merkle tree hashes. |
| P2P-003 | Decentralized Offline Vault | Storage | Secure offline key-value storage for P2P records. |
| P2P-004 | Local Chunk Garbage Collector | Storage | Prunes unverified cache chunks based on LRU. |
| P2P-005 | Encrypted Blob Chunking | Storage | Splits offline documents into 512KB AES-encrypted chunks. |
| P2P-006 | Autonomous Storage Quota Manager | Storage | Manages storage bounds for P2P mesh relay. |
| P2P-007 | Zero-Knowledge Proof Cache Validator | Storage | Validates authenticity via ZKP. |
| P2P-008 | ZeroNet SQLite State Mirror | Storage | Maintains lightweight DB mirrors of site states. |
| P2P-009 | Peer Cache Snapshot Exporter | Storage | Exports encrypted cache snapshots. |
| P2P-010 | ZeroNet Site Manifest Indexer | Storage | Indexes content manifests for fast retrieval. |
| P2P-011 | Store-and-Forward Outbox Relay | Routing | Queues and relays payloads via trusted P2P nodes. |
| P2P-012 | Mesh Packet Router | Routing | Routes encrypted micro-packets opportunistically. |
| P2P-013 | TTL-Based Packet Expiration | Routing | Enforces strict TTL limits on relayed packets. |
| P2P-014 | Hop-by-Hop Envelope Encryption | Routing | Wraps relay payloads in onion-style encryption. |
| P2P-015 | Mesh Network Flooding Mitigation | Routing | Prevents broadcast storms in the local mesh. |
| P2P-016 | Off-Grid Packet Delivery Confirmation | Routing | Cryptographic delivery receipts for offline packets. |
| P2P-017 | Asynchronous Peer Synchronization | Routing | Syncs relay queues when a peer reconnects. |
| P2P-018 | Opportunistic Bluetooth LE Transport | Routing | Utilizes BLE for short-range packet hops. |
| P2P-019 | Wi-Fi Direct Payload Offloading | Routing | Uses Wi-Fi Direct for large chunk transfers. |
| P2P-020 | Local Network (mDNS) Peer Discovery | Routing | Discovers peers on the same subnet. |
| P2P-021 | Bandwidth Accounting Tracker | Economy | Measures bytes served to other peers. |
| P2P-022 | Cryptographic Karma Validator | Economy | Generates signed proofs of bandwidth served. |
| P2P-023 | Hub Karma Synchronizer | Economy | Submits bandwidth proofs to the Hub. |
| P2P-024 | P2P Ratio Enforcement | Economy | Limits leeching by enforcing minimum upload ratios. |
| P2P-025 | Karma Badge Renderer | Economy | Generates UI badges based on karma score. |
| P2P-026 | Dynamic Chunk Priority Scheduler | Economy | Prioritizes requests from high-karma peers. |
| P2P-027 | Bandwidth Throttling Controller | Economy | Adjusts upload speeds based on network conditions. |
| P2P-028 | Karma Decay Engine | Economy | Slowly decays unused karma points over time. |
| P2P-029 | Decentralized Reputation Ledger | Economy | Maintains local trust scores of connected peers. |
| P2P-030 | Karma-gated Content Discovery | Economy | Unlocks premium static content indices for high-karma nodes. |
| P2P-031 | InstantView Pre-fetcher | Acceleration | Proactively fetches popular InstantView templates. |
| P2P-032 | AMP Content Optimizer | Acceleration | Strips bloat from AMP pages for offline storage. |
| P2P-033 | Thumbnail Mesh Broadcaster | Acceleration | Shares media thumbnails across the local mesh. |
| P2P-034 | Static Resource Deduplicator | Acceleration | Deduplicates CSS/JS resources across cached pages. |
| P2P-035 | Offline Search Indexer | Acceleration | Builds a local search index for cached articles. |
| P2P-036 | Adaptive Bitrate Media Cache | Acceleration | Caches video streams at multiple quality levels. |
| P2P-037 | P2P Live Audio Streaming | Acceleration | Relays low-latency audio chunks locally. |
| P2P-038 | WebP Image Transcoder | Acceleration | Compresses cached images to WebP format. |
| P2P-039 | Background Cache Warmer | Acceleration | Updates stale content during idle charging periods. |
| P2P-040 | Offline Link Resolver | Acceleration | Intercepts browser intents to serve local cached content. |
| P2P-041 | Tor SOCKS5 Integration | Anti-Censorship | Routes P2P signaling via integrated Tor network. |
| P2P-042 | MTProto Proxy Validator | Anti-Censorship | Checks health and latency of shared proxies. |
| P2P-043 | Decentralized Proxy Exchange | Anti-Censorship | Swaps working proxies with trusted peers. |
| P2P-044 | Domain Fronting Fallback | Anti-Censorship | Uses domain fronting for initial Hub discovery. |
| P2P-045 | Obfuscated P2P Handshake | Anti-Censorship | Hides P2P protocol signatures from DPI. |
| P2P-046 | Automated Proxy Rotation | Anti-Censorship | Switches proxies seamlessly on connection failure. |
| P2P-047 | Proxy Latency Historian | Anti-Censorship | Logs proxy performance over time. |
| P2P-048 | Pluggable Transport Interface | Anti-Censorship | Supports custom transports like Snowflake. |
| P2P-049 | Censorship Event Detector | Anti-Censorship | Detects active blocking and switches strategies. |
| P2P-050 | Secure Hub Ping | Anti-Censorship | Cryptographic heartbeat to the Hub. |
| P2P-051 | Decentralized P2P Avatar | Identity | Resolves user avatars from the local mesh instead of servers. |
| P2P-052 | Zero-Knowledge ID Proofs | Identity | Proves identity without revealing public keys. |
| P2P-053 | Mesh Bio Cache | Identity | Stores user biographies locally for offline viewing. |
| P2P-054 | Anonymous Peer Alias | Identity | Generates rotating anonymous aliases for local routing. |
| P2P-055 | Cryptographic Badges | Identity | Verifies community badges using multi-signatures. |
| P2P-056 | Offline Contact Sync | Identity | Syncs contacts securely over Wi-Fi Direct. |
| P2P-057 | Web-of-Trust Verifier | Identity | Calculates trust distance between peers. |
| P2P-058 | Local Identity Backup | Identity | Encrypts and stores identity keys in the mesh. |
| P2P-059 | Peer Status Broadcaster | Identity | Broadcasts online status to trusted local peers. |
| P2P-060 | Anti-Spoofing Signature | Identity | Prevents impersonation attacks in the local subnet. |
| P2P-061 | Offline Mesh Groups | Social | Creates temporary chat rooms without internet. |
| P2P-062 | Peer-to-Peer Comments | Social | Attaches offline comments to cached InstantView pages. |
| P2P-063 | Local Trending Topics | Social | Aggregates popular hashtags from nearby peers. |
| P2P-064 | Mesh Reactions | Social | Syncs emoji reactions over Bluetooth LE. |
| P2P-065 | Proximity Feed | Social | Discovers public posts shared by nearby nodes. |
| P2P-066 | Decentralized Polls | Social | Conducts cryptographic polls without a central server. |
| P2P-067 | Peer Content Tipping | Social | Tips Karma points to content creators offline. |
| P2P-068 | Mesh Broadcast Channels | Social | One-to-many broadcasting using flood routing. |
| P2P-069 | Collaborative Playlists | Social | Shares and syncs audio playlists in the local mesh. |
| P2P-070 | Social Graph Mapper | Social | Visualizes connections within the local mesh network. |
| P2P-071 | Mesh Health Monitor | Diagnostics | Real-time dashboard of mesh connectivity and drops. |
| P2P-072 | Packet Tracer | Diagnostics | Traces the multi-hop route of a P2P payload. |
| P2P-073 | Battery Impact Analyzer | Diagnostics | Measures power drain from background mesh activity. |
| P2P-074 | Bandwidth Speedtest | Diagnostics | Tests throughput between two local nodes. |
| P2P-075 | Storage Fragmentation Tool | Diagnostics | Defragments the local offline content database. |
| P2P-076 | Peer Node Inspector | Diagnostics | Shows technical details of connected peers. |
| P2P-077 | Routing Table Viewer | Diagnostics | Displays the current DHT routing table. |
| P2P-078 | Protocol Downgrade Fallback | Diagnostics | Automatically switches to simpler protocols if needed. |
| P2P-079 | Verbose Logging Mode | Diagnostics | Saves detailed logs of all mesh events to disk. |
| P2P-080 | Self-Healing Network Fixer | Diagnostics | Attempts automatic repairs when routing fails. |
| P2P-081 | Internet Gateway Node | Gateway | Acts as an exit node for offline peers to access the web. |
| P2P-082 | SMS Fallback Bridge | Gateway | Sends critical payloads via SMS when internet is down. |
| P2P-083 | LoRaWAN Integration | Gateway | Interfaces with long-range radio modules for distant peers. |
| P2P-084 | Captive Portal Bypass | Gateway | Tunnels traffic through restricted public Wi-Fi. |
| P2P-085 | Satellite Link Optimizer | Gateway | Optimizes packets for high-latency satellite connections. |
| P2P-086 | Sneakernet USB Sync | Gateway | Exports/Imports mesh data via USB drives. |
| P2P-087 | Cross-Platform Hub | Gateway | Bridges connections between Android and Desktop clients. |
| P2P-088 | Tor Exit Enforcer | Gateway | Ensures all gateway traffic passes through Tor. |
| P2P-089 | Data Cap Manager | Gateway | Prevents exceeding cellular limits while acting as gateway. |
| P2P-090 | Micro-Transaction Tolls | Gateway | Charges Karma for relaying data to the public internet. |
| P2P-091 | PFS Handshake Enforcement | Security | Requires Perfect Forward Secrecy for all local connections. |
| P2P-092 | Anti-Sybil Defense | Security | Detects and blocks peers spawning fake identities. |
| P2P-093 | Hardware Keystore Binding | Security | Binds P2P keys to the Android hardware enclave. |
| P2P-094 | Steganographic Payload | Security | Hides P2P data inside standard image files. |
| P2P-095 | Self-Destructing Cache | Security | Wipes all local mesh data on tampering detection. |
| P2P-096 | Honeypot Node Detector | Security | Identifies hostile nodes attempting to monitor the mesh. |
| P2P-097 | DDoS Mitigation Rules | Security | Rate-limits incoming connections to prevent resource exhaustion. |
| P2P-098 | Memory Encryption | Security | Encrypts sensitive P2P variables in RAM. |
| P2P-099 | Biometric Lockout | Security | Requires fingerprint to access the Mesh Diagnostics UI. |
| P2P-100 | Quantum-Resistant Signatures | Security | Uses post-quantum algorithms for node validation. |

## 5. Plan original conservado

Se mantiene íntegro como alcance solicitado. Su frase «el motor ya está programado ... con las 100 funciones» queda corregida por la evidencia de la fase C: el registro tiene 100 entradas, pero sus ejecutores no implementan esos comportamientos. Las garantías criptográficas, de red y de cumplimiento del texto original son objetivos pendientes de verificar. Esta ampliación prevalece al informar del estado.

---

# Plan Detallado de las 100 Funciones P2P y Reparación de los Diseños

## Objetivo
1. **Exponer Físicamente 100 Funciones P2P Independientes de Telegram**: Integrar 100 utilidades modulares que se comunican de forma local, encriptada (ZeroNet AES-256) y mediante red de malla (P2P), sin tocar los servidores oficiales de Telegram ni violar sus Términos de Servicio.
2. **Reparar y Mover los Diseños Históricos**: Los selectores de diseño (Telegram X, iOS, Unigram, etc.) se trasladarán correctamente al panel de **Ajustes Comunidad Telebots**. Al activarse, forzarán una recarga completa de la interfaz (`rebuildAllFragmentViews`) para que la forma de las celdas, radios y colores se aplique correctamente.

---

## Detalle de las 100 Funciones P2P (Malla, Karma y ZeroNet) que implementaremos en la UI:

El motor (`TelebotsP2pFeatureRegistry`) ya está programado en segundo plano con las 100 funciones. Lo que vamos a hacer es inyectarlas en el dispositivo para que puedas pulsarlas y probarlas. Se dividen en estas 5 categorías:

### 🗄️ Categoría 1: Caché Local y Storage (ZeroNet-Style) [Funciones 1-20]
Incluye herramientas para descargar páginas (InstantView, AMP) y compartirlas offline entre usuarios cercanos.
1. **Caché AES-256 Encriptada**: Todo contenido guardado localmente está cifrado.
2. **Validador Zero-Knowledge**: Valida que un nodo P2P tiene el contenido sin revelarlo.
3. **Sincronización de Borradores**: Envía borradores cifrados entre tus propios dispositivos vía P2P local (Wi-Fi Direct).
4. **Exportador de Instantáneas P2P**: Crea un archivo `.backup` cifrado de tu caché local.
5. **Basurero Autónomo de Chunks (LRU)**: Borra archivos viejos cuando el móvil se queda sin espacio.
*(... y 15 funciones adicionales de gestión de almacenamiento offline)*.

### 🌐 Categoría 2: Enrutamiento y Store-and-Forward (Rebote) [Funciones 21-40]
6. **Enrutador de Rebote**: Permite mandar un paquete local; si el destinatario no tiene internet, los nodos intermedios se lo pasan hasta que le llegue.
7. **Descubrimiento Local (mDNS y Bluetooth LE)**: Encuentra automáticamente otros clientes `comunidadtelebots` en la misma red Wi-Fi o por Bluetooth.
8. **Control de Congestión en Malla**: Evita tormentas de broadcast en redes locales.
9. **Retransmisión Geográfica**: Los paquetes P2P priorizan el salto a dispositivos físicamente más cercanos.
*(... y 16 funciones de red y enrutamiento).*

### 🛡️ Categoría 3: Anti-Censura y Proxy Management [Funciones 41-60]
10. **Tor SOCKS5 Mesh Backbone**: Enruta de forma automática todo el tráfico P2P por Tor si se detecta DPI (bloqueo profundo de paquetes).
11. **Rotador de MTProto P2P**: Los usuarios intercambian silenciosamente sus servidores Proxy operativos por P2P para eludir censura estatal.
12. **Domain Fronting de Rescate**: Finge conexiones a Google o Amazon para esquivar bloqueos de DNS y llegar al hub central.
13. **Ofuscador de Handshake TLS**: Aleatoriza firmas de conexión.
*(... y 16 herramientas anti-bloqueo y ofuscación).*

### 💎 Categoría 4: Economía de Karma y Reputación [Funciones 61-80]
14. **Karma Tracker**: Cuenta cuántos Megabytes (MB) de contenido has compartido offline.
15. **Leaderboard de Karma Local**: Renderiza tu reputación en base a tu ancho de banda aportado.
16. **Prueba Criptográfica de Relevo (PoR)**: Garantiza que no se puede "falsificar" el Karma ganado.
17. **Castigo por Leeching (Sybil)**: Bloquea a usuarios que solo descargan contenido offline pero nunca comparten.
*(... y 16 funciones de reputación sin moneda fiduciaria).*

### 🚀 Categoría 5: Aceleradores y Utilidades Offline [Funciones 81-100]
18. **Indexador de Búsqueda Desconectada**: Permite buscar artículos leídos o guardados en caché, aunque estés sin 4G/Wi-Fi.
19. **Compartir Archivos Directos (Wi-Fi Direct)**: Envía archivos pesados a usuarios cercanos a gran velocidad sin gastar datos del servidor de Telegram.
20. **Re-compresor WebP Dinámico**: Comprime las imágenes recibidas por P2P al momento para ahorrar batería y espacio.
*(... y 17 utilidades adicionales).*

---

## Proposed Changes

### [MODIFY] [CommunitySettingsActivity.java](file:///C:/Users/adria/OneDrive/Documentos/Visual Studio/Telegram/DBTeamV2/Telegram/TMessagesProj/src/main/java/org/telegram/ui/CommunitySettingsActivity.java)
- Crear una pestaña dedicada (`P2P_MESH_HUB = 7`).
- Renderizar las 100 funciones descritas iterando sobre la lista proporcionada por `TelebotsP2pFeatureRegistry`.
- Trasladar los ajustes de Año y Diseño, añadiendo `parentLayout.rebuildAllFragmentViews(true, true);` para asegurar que el tema (Telegram X, iOS) cargue correctamente al instante.

## Verification Plan
1. Compilar (`gradle_build`).
2. Desplegar en el Pixel 6 y entrar en *Ajustes Comunidad Telebots -> Malla P2P*.
3. Leer y pulsar los botones de las 100 funciones programadas, comprobando que se muestren las descripciones en pantalla.
