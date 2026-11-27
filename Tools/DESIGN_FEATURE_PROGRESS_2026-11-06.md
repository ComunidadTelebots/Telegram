# Diseños, funciones por año y revisión de fuentes

## Cambios implementados en esta iteración

- Sustituido el año que avanzaba con cada pulsación por una lista explícita.
- Retirado de Diseño el segundo selector `uiThemeProfile`: no controlaba las
  pantallas reales y podía contradecir SkinManager. Se conserva su dato anterior
  para no destruir preferencias. SkinSelectorCell sigue siendo el selector activo.
- Separados apariencia, año funcional y modo todas/histórico.
- Conectado el modo histórico a la creación de encuestas (desde 2018) y la
  disponibilidad de publicación de historias (desde 2023). Respeta permisos del
  servidor. No altera protocolo, autenticación ni decodificación de mensajes.
- La interfaz declara el alcance parcial. No afirma que toda la app se comporte
  como en ese año. Compartir una función con un diseño viejo no cambia su motor.
- Añadidas referencias de desarrollo iOS y Web K al catálogo, como pendientes.

Fuentes temporales: https://telegram.org/blog/polls y el hito Android de historias
`702d37ce69fca77e78072ad408e9d29dfd2d2be8` ya documentado en el catálogo. Un año
representa disponibilidad a finales de ese año, no una fecha exacta de lanzamiento.
Las reglas de interfaz siguen requiriendo código original y commit.

## Revisión remota

Android HEAD `3de37c8cfb039452be7398909461da6288babf15` ya es antecesor de DEV.
No hay una actualización Android posterior que fusionar en la consulta realizada.

Frente a las referencias históricas anteriores, las ramas consultadas tienen:
iOS 1163 commits posteriores, Unigram 850, Telegram X 201, Web A 93 y Web K 428.
Son conteos de código, no de rediseños. La comparación general de GitHub limita
archivos, por lo que se descargaron y compararon también archivos concretos.

- iOS `279f89e4d4f63f17227bf877dc3d8990f22ecd58`: NewsScreen y showNewsTab en
  TelegramRootController; cambios en ChatListItem. No portado a Android.
- Web K `ae67ff9763a77cce0b9d3ff993c1e0e11637f904`: propiedades CSS no heredadas
  para plegado de historias y altura de superposición. No copiar CSS a Android;
  trasladar el principio de aislar invalidaciones tras medir vistas nativas.
- Unigram `9cdf0490a66c0ff87f0eaade33409e4ea2eb40f7`: MainPage añade plantilla de
  sesiones y cambia controles; ChatCell cambia selección y recursos de iconos.
- Web A `6002824b6532f3d913997ce496d9ab5f9b86bc22`: LeftMain cambia localización;
  LeftMain.scss no cambió respecto a la referencia. No inventar un rediseño.
- Telegram X `08ee6c2c5a9275dc51ecb004f336abc1982799d5`: los archivos revisados no
  justifican una nueva variante visual: ChatView sin cambios y MainController
  cambia hooks de prueba.
- macOS devuelve 404 en las consultas realizadas. Pendiente de recuperación.

Evidencia: `output/design-source-audit/2026-11-06/upstream-check.json` y `new/`.

## Verificación y pendientes

72 combinaciones y límites de año superados en DesignFeaturePolicyTest, compilado
con Java 8. Tres pruebas del catálogo histórico superadas. Compilación DEV ARM64
completa superada con JDK 21. La compatibilidad integral Android 5 no se ha vuelto
a verificar en esta iteración; la compilación estándar mantiene mínimo API 23.

Capturas reales de las cuatro pestañas de la DEV anterior en
`output/design-runtime/index.html`. Son privadas y locales, no se suben a Canva.

Pendiente: implementación independiente completa de cada cliente, cronología de
todas las funciones y subfunciones, adaptación visual moderna a cada época,
pruebas de interacción y animaciones por versión y validación en Android 5.0.
No declarar esas tareas terminadas por tener un selector o un catálogo.

## Comprobación final en el Pixel

DEV ARM64 recompilada e instalada con la excepción local de Moonbot para emulador
ya autorizada; sesión conservada. Se recorrieron Perfil, Ajustes, Comunidad
Telebots y Diseños y animaciones. Ambos selectores abren y muestran años/modos.
Se cancelaron sin modificar preferencias (2026, todas las funciones).

La prueba detectó LOC_ERR en los textos nuevos: el generador de traducciones solo
incluye strings.xml. Las claves se trasladaron a los strings.xml inglés/español;
se recompiló, reinstaló y comprobó ausencia de LOC_ERR en pantalla y diálogos.
Capturas: design-settings.png, years.png y modes.png en output/design-runtime.
No se ha probado envío de encuestas ni publicación de historias en esta sesión;
la política dispone de pruebas Java, pero falta esa validación integral.
