# Fuentes de referencia — Comunidad Telebots Android DEV

Actualizado: 2026-11-22. Documento de **referencias**, no de dependencias Gradle ni de código importado automáticamente.

## Base oficial y protocolo (prioridad máxima)

| Fuente | URL | Uso |
|---|---|---|
| Telegram Android oficial | https://github.com/DrKLO/Telegram | Comparar upstream antes de portar funciones; origen principal |
| Telegram Android (organización) | https://github.com/TelegramMessenger/Telegram-Android | Referencia adicional; comprobar equivalencia con upstream |
| Telegram Android X | https://github.com/TGX-Android/Telegram-X | Estudiar implementaciones y UX alternativas, sin copiar sin verificar licencias |
| Telegram iOS | https://github.com/TelegramMessenger/Telegram-iOS | Comparación funcional y UX, no mezclar Swift con Android |
| Telegram Web K | https://github.com/morethanwords/tweb | Comparación de flujos Web |
| Telegram Web A | https://github.com/Ajaxy/telegram-tt | Comparación de flujos Web |
| Telegram Desktop | https://github.com/telegramdesktop/tdesktop | Funciones y ajustes de escritorio como inspiración |
| TDLib | https://github.com/tdlib/td | Referencia de API, no sustitución de la pila nativa sin justificación |
| API Telegram | https://core.telegram.org/api | Contratos y cambios de protocolo |
| MTProto | https://core.telegram.org/mtproto | Seguridad y compatibilidad |
| Telegram Android traducciones | https://translations.telegram.org/en/android/ | Localización |
| Telegram cambios oficiales | https://telegram.org/blog | Novedades para contrastar |

## Detección temprana, sugerencias y diseño

| Fuente | URL | Uso |
|---|---|---|
| telegram-crawler (MarshalX) | https://github.com/MarshalX/telegram-crawler | Detectar APK/versiones y pistas de cambios; NO asumir que todos los builds son oficiales |
| Registro de cambios de crawler | https://github.com/MarshalX/telegram-crawler/commits | Evaluar novedades en una revisión reproducible |
| Telegram suggestions | https://bugs.telegram.org/?type=suggestions | Ideas; nunca tratarlas como funcionalidades ya implementadas |
| Telegram Designers | https://t.me/designers | Referencias creativas sujetas a verificación y permisos de uso |
| Android Material Design | https://m3.material.io/ | Accesibilidad, componentes y adaptación de pantallas |
| Android Developers | https://developer.android.com/ | APIs, restricciones, compatibilidad y rendimiento |
| Android large screens | https://developer.android.com/large-screens | Tablet Galaxy Tab A9+ 5G SM-X216B |
| Android performance | https://developer.android.com/topic/performance | Rendimiento y batería |
| Android accessibility | https://developer.android.com/guide/topics/ui/accessibility | Accesibilidad |
| Samsung Developers | https://developer.samsung.com/ | Particularidades Galaxy y One UI; comprobar compatibilidad |
| Samsung One UI | https://developer.samsung.com/one-ui | Pautas visuales y convenciones cuando correspondan |

Los diseños Premium negro/dorado aprobados para «Ajustes → Comunidad Telebots» se mantienen como referencia de producto. Figma/Canva son fuentes de diseño a contrastar contra las versiones aprobadas; no incorporar recursos sin identificar licencia o permiso.

## Construcción, calidad, diagnósticos y publicación

| Fuente | URL | Uso |
|---|---|---|
| Android Gradle Plugin | https://developer.android.com/build | Compatibilidad compilación |
| Gradle | https://docs.gradle.org/current/userguide/userguide.html | Diagnóstico de builds |
| Google Play Console | https://play.google.com/console/ | Distribución en prueba cerrada |
| Pruebas Google Play | https://support.google.com/googleplay/android-developer/answer/9845334 | Gestión de testers y pistas |
| Firebase Crashlytics | https://firebase.google.com/docs/crashlytics | Reportes de fallos |
| Firebase Android setup | https://firebase.google.com/docs/android/setup | Configurar applicationId y google-services.json correctos |
| Android vitals | https://developer.android.com/topic/performance/vitals | ANR, crashes, estabilidad |

## Política de integración en DEV

1. Comparar cada novedad con la **versión exacta** del upstream oficial y la rama DEV (commit SHA, archivos y comportamiento); marcar existente / ausente / parcialmente implementada.
2. Usar telegram-crawler como **índice de versiones y pistas**, nunca como fuente incuestionable ni como proveedor directo de APKs para redistribución.
3. Analizar licencias y dependencias antes de portar código de forks, TDLib, clientes de otras plataformas o recursos gráficos.
4. Implementar cambios pequeños detrás de flags cuando corresponda; conservar «Comunidad Telebots» **dentro de Ajustes**, nunca como launcher principal.
5. Ejecutar compilación, lint, pruebas, instalación real (móvil Samsung y Galaxy Tab A9+ 5G SM-X216B), tests de UI y registro de regresiones antes de promocionar a alfa cerrada.
6. Verificar package/applicationId, firma propia segura, credenciales Firebase específicas, privacidad, Play App Signing y pistas de prueba. **No usar keystores ni credenciales ficticias de upstream** para publicar.
7. Mantener un historial por mejora: URL, commit/tag de origen, comprobación upstream/DEV, licencia, cambios, tests y estado del build.
8. **No** conectar ni compilar automáticamente todas las fuentes: son referencias auditables y la inclusión de código es selectiva.

## Estado

- Registro de fuentes: documentado en DEV.
- Integración funcional de novedades: **no acreditada por este documento**.
- Build, APK/AAB, Crashlytics y publicación Play: **no verificados por este documento**.
