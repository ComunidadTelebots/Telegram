# Diseños de clientes y animaciones

En Ajustes → Apariencia, el selector de diseños incluye perfiles nativos inspirados
en Unigram, Telegram X, Telegram Web A, Telegram Web K, macOS e iOS, además de las
familias que ya tenía el fork. Web A conserva el identificador `web` para migrar
la selección anterior; Web K usa `webk`.

Los perfiles aplican el radio y la cola de las burbujas, la forma de los avatares
normales y los separadores de la lista de chats. Los avatares especiales de foros
y comunidades conservan su tratamiento propio. Las vistas anteriores se reconstruyen
al elegir un diseño y la caché de burbujas se invalida cuando cambia su forma.

Son adaptaciones visuales sobre la interfaz Android existente: no incluyen el
código React/Swift/UWP de otros clientes ni reproducen sus barras, navegación,
tipografías o distribución completa. Los colores siguen perteneciendo al tema de
Telegram. Los nombres identifican la inspiración del perfil.

## Animaciones independientes

La sección Animaciones permite elegir Original, Suave, Rápida, Fundido o Ninguna.
La elección se guarda en `motion_prefs`, separada de `skin_prefs` y del tema de
colores, y ofrece una demostración al pulsarla.

Se personalizan duración y desplazamiento de las transiciones estándar al abrir
y cerrar pantallas. Las transiciones especiales de fragmentos, vistas previas y
gestos predictivos conservan su implementación de Telegram. La opción Ninguna y
los interruptores de animación del sistema/Telegram desactivan las transiciones
normales. No se sobrescriben las preferencias globales al cambiar de perfil.

## Ramas locales

`dev`, `alfa`, `beta` y `rc` parten del mismo commit de esta integración.
Las ramas son puntos de trabajo y promoción manual, no canales automáticos de
distribución ni aplicaciones con identificadores diferentes. No se ha modificado
la antigua rama remota `origin/dev` ni publicado cambios.

## Comprobaciones antes de distribuir

Validación local: compilación Java completada correctamente (137 tareas) y
recursos de animación en inglés y español válidos, sin nombres duplicados.
Todavía no se ha realizado verificación visual en un dispositivo ni generado APK.

- Compilar `:TMessagesProj:compileDebugJavaWithJavac`.
- Alternar los seis perfiles con temas claros y oscuros; revisar burbujas simples
  y agrupadas, sombras, degradados, selección de mensajes y avatares especiales.
- Elegir una animación, cambiar de diseño y reiniciar: ambas preferencias deben
  conservarse independientemente.
- Probar abrir/cerrar pantallas con cada animación y con animaciones del sistema
  desactivadas; probar también gestos Atrás y vistas previas.
- Generar y probar el APK en dispositivos antes de promoverlo a `rc`.
