# Estado local del catálogo comunitario

Checkpoint del 7 de octubre de 2026, guardado a petición del usuario.

Objetivo: catálogo en el Hub de CintiaBot (`https://cintiabot.todosobreall.tech/hub.html`) con enlaces a temas de Telegram y propuestas de archivos `.attheme`, moderación y vista previa nativa antes de aplicar.

## Código preparado

- Backend y pruebas en `backend/`: catálogo, validación, almacenamiento y moderación. Es código de integración, no un servicio desplegado.
- Frontend en `frontend/`: catálogo dentro del Hub, envío de propuestas y controles del propietario. El instalador todavía no se ha aplicado al repositorio de CintiaBot.
- Android: `CommunityThemesActivity`, `CommunityThemeLoader`, `CommunityThemePalette` y `CommunityThemePreview`; textos en `Tools/strings/community-themes-*.xml`. Transporte del catálogo añadido a `AmpResolver`.
- La vista previa requiere recibir datos y usar almacenamiento temporal; no exige descargar/importar manualmente un archivo. Los temas no ejecutan código.

## Pendiente antes de considerar terminada la función

1. Revisión final del contrato backend/Android y de validaciones de `.attheme`; documentar los formatos rechazados, incluidos recursos no admitidos.
2. Conectar entrada del catálogo en los ajustes propios y enlace `tg://telebotstheme?id=...` en `LaunchActivity`.
3. Crear el puente HTTPS `/community-theme` con apertura explícita de la rama Android elegida.
4. Integrar recursos de traducción, compilar la APK y probar Aplicar/Cancelar/restauración, errores y límites.
5. Aplicar y probar los cambios en el código del Hub de CintiaBot, fuera de este repositorio.
6. Desplegar y verificar el catálogo real. Los módulos Hostinger registrados necesitan reiniciar Codex para estar disponibles; no se ha realizado este despliegue.

Los tests focalizados ejecutados durante el desarrollo no equivalen a una validación completa de extremo a extremo. Este checkpoint conserva el trabajo incompleto sin presentarlo como una versión publicada.
