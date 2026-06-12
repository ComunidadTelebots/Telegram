# Registro de cambios

Todos los cambios relevantes de este fork se documentarán en este archivo.

El formato está basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)
y el proyecto sigue la numeración de versiones de Telegram Android mientras no
se indique una versión propia del fork.

## [Sin publicar]

### Añadido

- Selector inicial de diseños de interfaz en los ajustes de apariencia.
- Selector jerárquico de familias y variantes de interfaz.
- Familia Android con las variantes Actual, Original, Redesign y Glass.
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
