# Evaluación de TDLib — 7 octubre 2026

Estado: evaluación técnica, sin migración del motor ni datos de usuarios.

## Decisión recomendada

TDLib es viable para Comunidad Telebots, pero no sustituye directamente a
ConnectionsManager. Conservar el cliente DrKLO para las versiones actuales y
validar un prototipo aislado antes de decidir una migración. Telegram X es una
referencia existente basada en TDLib; partir de su arquitectura también exige
portar nuestras funciones y comprobar la paridad funcional y las licencias.

## Evidencia de este repositorio

- ConnectionsManager serializa TLObject/NativeByteBuffer y llama a
  native_sendRequest; los eventos terminan en MessagesController.processUpdates.
- MessagesStorage utiliza cache4.db. TDLib administra otro almacenamiento y
  otros modelos y eventos; no se deben compartir los archivos de base de datos.
- En el directorio ui hay 587 archivos Java que mencionan TLRPC, 251 que
  mencionan ConnectionsManager, 455 MessagesController y 102 MessagesStorage.
  Los conjuntos se solapan y no representan una estimación de trabajo.
- La presencia de tde2e y tdutils en JNI no implica tener integrado el cliente
  completo TDLib.

## Funciones y coste de adaptación

| Área | Consecuencia |
| --- | --- |
| Diseños y animaciones | Reutilizar recursos y componentes independientes; adaptar las pantallas enlazadas a los modelos actuales. TDLib no proporciona temas ni interfaces. |
| Moonbot, permisos, actualizador | El protocolo HTTPS puede conservarse, revisando el bloqueo en todos los puntos de entrada. |
| Autenticación, mensajes, archivos | Adaptar estados, envíos, descargas y procesamiento de eventos; no traducir solicitudes aisladamente. |
| Multicuenta y push | Clientes y bases separados; rehacer asociación de cuentas, directorios y notificaciones. |
| Chats secretos | Soportados, pero no se ha encontrado una migración documentada de claves y datos DrKLO. Preservar la instalación anterior y no prometer traslado automático. |
| Llamadas y vídeo | Integrar señalización TDLib con motor multimedia/tgcalls y adaptar VoIPService. |
| Tor | SOCKS5 permite conectar con Tor local; conservar la protección por paquete y verificar DNS y fallos. No garantiza llamadas funcionales ni cubre automáticamente otros SDK. |
| Android Go | Medir memoria, batería, arranque, disco y tamaño. No hay evidencia de mejora automática; dos motores simultáneos añaden coste. |
| Novedades oficiales | Comprobar función por función contra un commit fijado; no prometer paridad inmediata. |

## Prueba de viabilidad propuesta

1. Aplicación experimental aislada, otra base de datos y autorización propia.
2. Validar una cuenta: acceso, chats, texto, archivos y recuperación tras reinicio.
3. Integrar Moonbot, revocación, push y Tor, incluido bloqueo cuando Tor falla.
4. Medir frente al cliente actual en el mismo dispositivo, especialmente Android Go.
5. Evaluar llamadas, multicuenta, chats secretos y funciones recientes antes de
   cualquier migración de producción. No importar ni sobrescribir datos existentes.

## Fuentes primarias consultadas

- [Descripción de TDLib](https://core.telegram.org/tdlib)
- [Guía de integración](https://core.telegram.org/tdlib/getting-started)
- [Notificaciones](https://core.telegram.org/tdlib/notification-api)
- [Esquema de API actual](https://raw.githubusercontent.com/tdlib/td/master/td/generate/scheme/td_api.tl)
- [Compilación Android](https://github.com/tdlib/td/tree/master/example/android)
- [Telegram X](https://github.com/TGX-Android/Telegram-X)
- [Protocolo SOCKS de Tor](https://spec.torproject.org/socks-extensions.html)

Los enlaces a master cambian con el tiempo. Esta revisión no ejecutó un cliente
TDLib ni constituye una prueba de rendimiento o de paridad funcional.
