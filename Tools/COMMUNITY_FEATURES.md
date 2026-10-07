# Ajustes Comunidad Telebots: integración de octubre de 2026

Todas las opciones propias se agrupan en `CommunitySettingsActivity`. El buscador original de Telegram indexa el hub y sus pantallas; no se implementó un segundo motor de búsqueda. Los ajustes nativos mantienen su navegación, salvo el enlace al hub.

Inventario auditado de las 20 capacidades propias y diferencias frente a Telegram: [COMMUNITY_20.md](COMMUNITY_20.md). La siguiente lista describe los diez frentes originales, no diez novedades exclusivas.

## Funciones y alcance

1. **Biblioteca offline:** guardado explícito del original público de AMP/IV, extracción nativa, imágenes acotadas y posición por cuenta. No es una copia exacta del IV del servidor y puede rechazar páginas restringidas o dependientes de JavaScript. Véase `reading/README.md`.
2. **Frases rápidas:** categorías, búsqueda, favoritas, alta/edición/borrado, ejemplos voluntarios e inserción al cursor desde el menú del chat, sin envío automático.
3. **Búsqueda:** resultados del hub, DNS, diagnósticos, Tor, diseños, lectura, frases, descargas, perfiles, accesibilidad, actualizaciones y privacidad dentro del buscador nativo.
4. **Perfiles:** Ahorro, Equilibrado y Máxima fluidez. Muestran los cambios antes de aplicar y conservan una instantánea para deshacer. Cambian efectos/autoplay/animaciones/precarga/AMP, respetando las protecciones de Android y los límites nativos de descarga.
5. **Rendimiento medido:** módulo optativo de Baseline Profiles y Macrobenchmark. Compilación del módulo verificada. No se afirma una aceleración ni se distribuye un perfil inventado: generación y comparación requieren una sesión autorizada y pruebas en dispositivo físico/Android Go. Véase `performance/README.md`.
6. **Diagnóstico:** diferencia fallos observados de DNS/TLS/tiempo de espera/conexión/ruta, sin llamar censura a un fallo aislado. Acciones explícitas para abrir ajustes de red o revisar proxy/Tor; nunca desactiva validación TLS.
7. **Leer después:** artículos y enlaces a publicaciones de canales; abre la publicación original sin copiar mensajes. La posición de artículos es local; un marcador de canal identifica la publicación elegida.
8. **Descargas:** acceso al gestor oficial de Telegram para priorizar/pausar/reanudar y límites originales por red; restricción opcional de descargas automáticas a redes sin límite. No es un planificador por hora ni un limitador de velocidad y no bloquea una descarga manual solicitada.
9. **Accesibilidad:** controles propios con objetivos de 48 dp, textos adaptables, roles TalkBack y navegación/historial/vistas previas ajustados. No equivale a una auditoría completa de cada pantalla, diseño o lector de pantalla.
10. **Actualizaciones:** rama, notas, tamaño, SDK/ABI cuando el servidor los proporciona. Selección de APK local, cotejo SHA256/manifiesto/paquete/versión/certificado con instalada antes de ofrecer el instalador, que realiza su verificación final. Servidor antiguo sin metadatos no se presenta como verificado; el contrato actualizado aún debe desplegarse y una publicación aportar esos metadatos.

## Identidad por fase

Cinco variantes de icono generadas a partir del original, DEV/ALFA/BETA/RC/STABLE. `gradle/community-branding.gradle` selecciona recursos por `APP_CHANNEL` para módulos APK. El launcher y splash Android 12+ usan un icono adaptativo; Moonbot e intro inicial usan el bitmap de la fase. Título de chats Comunidad Telebots · FASE y marca de agua decorativa semitransparente en la vista del chat: no se incorpora a mensajes ni archivos enviados y no impide capturas o su difusión.

## Validación

Pruebas focalizadas de límites de frases, política de red, perfiles aplicar/deshacer, iconos, diagnóstico, verificación APK y lectura pública. En emulador se verificaron la confirmación del perfil, aplicar/deshacer Ahorro, crear/borrar una frase de prueba y guardar/abrir el artículo público IANA Example Domains con Wi-Fi y datos desactivados, restaurando ambos después. No se enviaron mensajes para probar frases.

No se han publicado releases, desplegado backend ni añadido servidores para estas funciones durante esta integración.
