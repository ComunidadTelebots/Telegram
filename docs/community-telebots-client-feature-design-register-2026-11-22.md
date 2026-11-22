# Comunidad Telebots DEV — registro de novedades de clientes y control de diseño
Fecha de revisión: 2026-11-22
Rama de distribución: release/samsung-a56-tab-a9plus-dev-20261122

## Reglas de integración
1. Comparar cada novedad con Telegram Android oficial, el código de esta rama y la rama dev antes de incorporarla. El crawler puede contener indicios o recursos, no pruebas de funcionalidad acabada.
2. No copiar código de Android, iOS, macOS, Desktop o Web sin evaluar licencia, compatibilidad con Android, API disponible, privacidad y seguridad.
3. Conservar variantes anteriores de Figma y Canva; generar nuevas versiones y capturas comparativas sin sobrescribir los modelos aprobados.
4. Mantener la sección «Comunidad Telebots» exclusivamente en Ajustes. Referencia aprobada: Premium grafito/negro y dorado.
5. Ninguna función nueva pasa a la alfa cerrada si no supera CI, instalación, arranque, navegación y regresión en Samsung Galaxy A56 y Galaxy Tab A9+ 5G (SM-X216B).
6. Nunca bloquear la compilación DEV por una función experimental: desarrollar en rama feature, proteger con un flag desactivado y fusionar sólo con pruebas verificadas.

## Inventario de diseños existentes en Canva (NO SOBRESCRIBIR)
- «Comunidad Telebots - Galería de los 16 diseños actuales» (16 páginas): https://www.canva.com/d/J58hv7UDR4SQOtY
- «Comunidad Telebots Android · Referencia visual fija · 2026-11-06» (19 páginas): https://www.canva.com/d/ajkv7S954i6W3hE
- «Estilos Comunidad Telebots» (16 páginas): https://www.canva.com/d/bgQj5HFNzAAzKIy
- «Comparación real de clientes con el código Android · 06 noviembre 2026» (16 páginas): https://www.canva.com/d/945GI3juIGo4sAc
- «Comunidad Telebots Android — Manual del estado actual»: https://www.canva.com/d/n2BetkDI5MEJEyq

## Registro de candidatos, no funciones implementadas
| Candidato | Fuente de pista | Comprobación obligatoria | Estado |
| --- | --- | --- | --- |
| Transcripción de voz local | Recursos rastreados de clientes | API/modelos Android oficial, permisos, almacenamiento y consumo | PENDIENTE VERIFICAR |
| Subtítulos durante llamadas | Clientes rastreados | Disponibilidad Android, accesibilidad, permisos de micro | PENDIENTE VERIFICAR |
| Widgets y miniaplicaciones HTML5 | Clientes rastreados | Mini Apps oficiales, sandbox WebView, permiso de acceso, duplicados | PENDIENTE VERIFICAR |
| Permisos específicos de bot y miniapp | Clientes rastreados | Controles ya presentes en Telegram Android | PENDIENTE VERIFICAR |
| Administración de traducciones de bots | Desktop | API BotFather y compatibilidad Android | PENDIENTE VERIFICAR |
| Atajos configurables de WebA | Web | Relevancia para teclado externo/tablet Android | PENDIENTE VERIFICAR |
| Personalización asistida de temas | macOS | Evaluación de privacidad y viabilidad Android | PENDIENTE VERIFICAR |

## Revisión de implementación en la rama release
Comparación dev...release consultada el 2026-11-22: 39 commits por delante de dev y cero por detrás. Ya existen archivos para perfiles adaptativos de dispositivo, ajustes de rendimiento, herramientas de comprobación de Telegram, firma/validación de APK y flujo Samsung. No duplicar esas capacidades.

## Protocolo de aprobación de diseños
1. Mantener las variantes originales y numerarlas como referencia, no eliminarlas.
2. Entregar por cada propuesta una pantalla Android móvil, una tablet y un estado claro/oscuro, cuando aplique.
3. Anotar «existente», «propuesta», «aprobada» o «implementada y probada» sin confundirlas.
4. Pedir confirmación visual de cualquier nueva propuesta antes de sustituir el aspecto aprobado en el cliente.

## Prioridad
P0: APK instalable y arranque fiable en ambos dispositivos.
P1: incorporar únicamente novedades verificadas, empezando por mejoras de accesibilidad y widgets con APIs oficiales, tras pasar QA.
P2: propuestas visuales y variantes en Figma/Canva para aprobación.
