# Diseños de clientes y animaciones

En Ajustes → Apariencia, el selector de diseños incluye perfiles nativos inspirados
en Unigram, Telegram X, Telegram Web A, Telegram Web K, macOS e iOS, además de las
familias que ya tenía el fork. Web A conserva el identificador `web` para migrar
la selección anterior; Web K usa `webk`.

Los perfiles aplican el radio y la cola de las burbujas, la forma de los avatares
normales y los separadores de la lista de chats. Los avatares especiales de foros
y comunidades conservan su tratamiento propio. Las vistas anteriores se reconstruyen
al elegir un diseño y la caché de burbujas se invalida cuando cambia su forma.

Son adaptaciones visuales sobre la interfaz Android existente. La revisión en
`dev` añade contenedores de navegación diferenciados y cabeceras; todavía no
reproduce la interfaz completa de los otros clientes. Los colores siguen
perteneciendo al tema de Telegram. **La equivalencia visual solicitada no está
terminada ni verificada.** No se han importado sus motores React/Swift/UWP.

## Navegación experimental en dev

- iOS: navegación inferior con contactos, llamadas, chats y ajustes.
- Telegram X: pestañas superiores Chats/Llamadas y menú para contactos/ajustes.
- Unigram, Web A y Web K: contenedores laterales con anchos y selección distintos.
  Son una base de adaptación: aún no reproducen sus paneles de carpetas completos.
- macOS: barra inferior compacta de iconos.
- Las acciones abren los fragmentos reales y conservan la sesión de Telegram.
  Llamadas y Ajustes tienen destinos propios dentro del contenedor al usar estos
  diseños, independientemente de la preferencia de pestañas del modo Android.
- Se ajustan los márgenes de chats, contactos, llamadas y ajustes; las pulsaciones
  largas mantienen selectores de carpetas, contactos, llamadas y cuentas. Los
  márgenes se recalculan también al reconstruir pantallas después de cambiar de diseño.
- La vista previa del selector usa el componente de navegación real junto a un
  dibujo de ejemplo; no es una captura de un cliente externo.

Las fuentes raíz y sus commits están fijados en `client-design-references.json`.
La consulta de esas fuentes no sustituye las pruebas visuales. Quedan pendientes
los paneles y columnas completos, compositores, menús contextuales, pantallas de
ajustes/perfil, recursos tipográficos e iconografía específica, además de una
comparación por capturas en cada tamaño y estado. No promover esta revisión como
réplica terminada a alfa, beta o rc.

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

`dev`, `alfa`, `beta` y `rc` se crearon desde `db22d72c7`; las revisiones
experimentales posteriores avanzan únicamente en `dev`.
Las ramas son puntos de trabajo y promoción manual, no canales automáticos de
distribución ni aplicaciones con identificadores diferentes. No se ha modificado
la antigua rama remota `origin/dev` ni publicado cambios.

## Comprobaciones antes de distribuir

Validación local: compilación Java y generación del APK `afatDebug` completadas.
También pasan las comprobaciones de selección de perfiles, preferencias anteriores
y reserva de márgenes en `Tools/tests/ClientLayoutTest.java`. No se ha realizado
verificación visual en un dispositivo ni comparación de capturas con los clientes.

El APK de prueba se genera en
`TMessagesProj_App/build/intermediates/apk/afat/debug/app.apk`, para ARM64,
con mínimo API 21 y destino API 36. La variante debug usa el identificador
`org.telegram.messenger.beta`; ese sufijo no implica promoción a la rama `beta`.
Para reproducir la compilación en Windows:

```powershell
java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :TMessagesProj_App:assembleAfatDebug --console=plain '-Pkotlin.incremental=false' '-Pandroid.injected.build.abi=arm64-v8a'
```

El filtro de ABI también se aplica al empaquetado para no incluir arquitecturas
sin la biblioteca principal. La compilación completa sin ese parámetro conserva
las cuatro arquitecturas configuradas.

- Compilar `:TMessagesProj:compileDebugJavaWithJavac`.
- Alternar los seis perfiles con temas claros y oscuros; revisar burbujas simples
  y agrupadas, sombras, degradados, selección de mensajes y avatares especiales.
- Elegir una animación, cambiar de diseño y reiniciar: ambas preferencias deben
  conservarse independientemente.
- Probar abrir/cerrar pantallas con cada animación y con animaciones del sistema
  desactivadas; probar también gestos Atrás y vistas previas.
- Generar y probar el APK en dispositivos antes de promoverlo a `rc`.
