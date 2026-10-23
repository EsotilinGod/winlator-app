# Guía de pruebas en el Xiaomi 14T

Objetivo: tener números comparables antes de cambiar nada. Sin medición no se
puede saber si un cambio mejora o empeora. Repetir siempre con las mismas
condiciones.

## Antes de empezar
- Respaldar los contenedores de Winlator (esta APK tiene otra firma que la oficial).
- Batería sobre 50 %, brillo fijo, modo de rendimiento del teléfono fijo,
  sin cargador conectado y con el equipo a temperatura normal (no recién usado).
- Controlador gráfico: dejar **System** en Vortek. No activar PanVK
  (provocó un reinicio completo; ver `panvk-reboot-response.md`).

## Qué probar
Elegir 1 o 2 programas o juegos y usar siempre los mismos, con la misma
escena y la misma resolución. Para cada prueba anotar:

| Dato | Cómo |
|---|---|
| Abre / no abre | Sí o no |
| Se cierra solo | Cuántos minutos tarda |
| FPS promedio y mínimo | HUD de rendimiento de la app (ver `quick-performance-hud.md`) |
| Tiempo de carga | Con cronómetro, primera y segunda vez |
| Temperatura | Sensación al tacto o app de monitoreo, al inicio y a los 10 min |
| Fallos gráficos | Describir o capturar pantalla |

Probar una sola variable por vez: resolución, preset de Box64, DXVK o límite
de FPS. Anotar el valor usado en cada prueba.

## Si algo falla (cierre, pantalla negra, reinicio)
1. Anotar exactamente qué se estaba haciendo y qué opciones estaban activas.
2. Si la app quedó abierta, usar el diálogo de depuración de Winlator para
   guardar el registro.
3. Si el teléfono se reinició: después de encenderlo, sacar los registros del
   sistema (por ejemplo, un informe de errores desde Opciones de desarrollador,
   o `adb logcat -b all -d` y `adb shell ls /sys/fs/pstore` con un cable USB).
   Esos registros son los que permiten encontrar la causa.

## Qué enviar
Una tabla con los datos anteriores y, si hubo fallos, los registros. Con eso
se decide qué ajustar.
