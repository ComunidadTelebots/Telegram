# Registro de cambios

Todos los cambios relevantes de este fork se documentarán en este archivo.

El formato está basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)
y el proyecto sigue la numeración de versiones de Telegram Android mientras no
se indique una versión propia del fork.

## [Sin publicar]

### Auditoría y versiones de referencia

- Auditoría de estructura, listas, carpetas y compositores de seis clientes,
  con fuentes fijadas a commits y discrepancias documentadas.
- Selector de 15 hitos de diseño para siete familias, con cambios, fuentes y
  persistencia independiente por cliente. Son referencias pendientes de portar;
  seleccionarlas no aplica una apariencia histórica inexistente.
- Corregidos los avatares normales de Unigram a circulares según sus fuentes.
- Los textos del selector identifican los perfiles como adaptaciones experimentales
  y distinguen estilos Android de versiones históricas verificadas.

### Navegación de clientes en desarrollo

- Contenedores inferiores para iOS/macOS, superiores para Telegram X y laterales
  para Unigram/Web A/Web K, conectados a chats, contactos, llamadas y ajustes.
- Cabeceras y espacios de navegación actualizados al cambiar de diseño, también
  cuando se reconstruyen vistas conservadas. Referencias de código fijadas por commit.
- Esta revisión todavía no reproduce íntegramente los seis clientes ni cuenta con
  comparación visual en dispositivo; permanece experimental en `dev`.
- Corregido el argumento `--exclude-libs` que impedía enlazar bibliotecas nativas.
- Los APK de compilaciones limitadas a una ABI excluyen las demás arquitecturas,
  evitando anunciar arquitecturas que carecen de la biblioteca principal.

### Diseños y animaciones independientes

- Perfiles seleccionables de Unigram, Telegram X, Web A, Web K, macOS e iOS;
  Web A conserva la selección Web anterior y Web K tiene identificador propio.
- Los perfiles aplican avatares, separadores y colas de burbujas a la interfaz real,
  además del radio de burbuja existente, con invalidación de cachés al cambiar.
- Selector independiente de transiciones Original, Suave, Rápida, Fundido y
  Ninguna, con persistencia y demostración; respeta los ajustes de animación.
- Documentación del alcance y las ramas locales `dev`, `alfa`, `beta` y `rc` en
  `Tools/CLIENT_DESIGNS.md`.

### Integración Android 16

- Fusión de DrKLO/Telegram `master` (`9552e5541`, versión 12.10.3 / 7089).
- SDK de compilación y destino 36, Gradle 8.13 y AGP 8.13.2 de upstream.
  Se conserva el mínimo real de Android 5.0; Android 1.0–4.4 no está soportado.
- Corregidas las firmas de la celda del canal oficial, el acceso a la URL AMP,
  las llamadas al navegador y un recurso inexistente que impedían compilar.
- Corregidas la validación de dominios AMP, la recuperación ante errores de carga,
  el cambio a modo lectura y la liberación del WebView y sus animaciones.
- Se respeta el radio de burbujas existente cuando no se ha elegido un diseño.
- Corregida la retirada del callback de navegación Atrás en Android 13 y la
  configuración de namespaces de Media3 en Windows.
- Añadidas pruebas de regresión de URLs y documentación de compatibilidad en
  `Tools/ANDROID_COMPATIBILITY.md`.

### Añadido

- Selector inicial de diseños de interfaz en los ajustes de apariencia.
- Selector jerárquico de familias y variantes de interfaz.
- Secciones independientes para los temas generales importables de Telegram y
  los diseños completos propios del cliente.
- Familia Android con las variantes Actual, Original, Redesign y Glass.
- Línea histórica Android con Original 2013, Holo 2014, Material 2015,
  Clásico 2018 y Actual 2026.
- Vista previa completa con confirmación antes de aplicar un diseño.
- Identificadores persistentes de variante como `android:original`.
- Definiciones de diseño para Android, Android Redesign, Android Glass, Web,
  Webogram, iOS, macOS, Telegram Desktop, Unigram, Telegram X y Aurora.
- Persistencia local del diseño seleccionado.

### Cambiado

- El diseño seleccionado puede modificar el radio de las burbujas de chat.
- Las preferencias antiguas de Android, Android Redesign, Android Glass y Web
  se migran automáticamente al nuevo formato.

### Pendiente

- Aplicar tipografía, forma de avatares, divisores y colas de burbuja definidos
  por cada diseño.
- Recrear la navegación, iconografía, filas y espaciado de Telegram Android
  Original sobre las funciones actuales del cliente.
- Actualizar inmediatamente todas las pantallas cuando cambia el diseño.
- Localizar los textos del selector.
- Añadir pruebas para la selección, persistencia y restauración del diseño.

## [12.7.3-base] - 2026-06-12

### Cambiado

- Sincronizada la base del fork con Telegram Android 12.7.3, código de versión
  6750.

[Sin publicar]: https://github.com/ComunidadTelebots/Telegram/compare/1c344b701...HEAD
[12.7.3-base]: https://github.com/ComunidadTelebots/Telegram/commit/1c344b701
