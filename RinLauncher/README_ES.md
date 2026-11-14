# Rin Launcher (Android)

Launcher horizontal tipo consola para el Xiaomi 14T, con la estética del fondo animado de Rin.
Es una app aparte: no reemplaza la pantalla de inicio del teléfono.

## Qué incluye (v1.0)
- **Pantalla horizontal a pantalla completa** (gira según el sensor entre ambas orientaciones horizontales).
- **Fondo original del fondo de pantalla, en horizontal** (v1.4): la ilustración sin Rin (reconstruida
  con el modelo LaMa) y traspuesta a 2712 × 1220, la pantalla del Xiaomi 14T, sin reescalar. Sus
  circuitos brillan tenuemente y, en Android 13+, una corriente de luz nace de Rin cada 6 s y recorre
  las pistas (mismo shader AGSL que el fondo animado). Lo genera `tools/make_launcher_bg.py`.
- **Rin grande a la derecha, detrás del carrusel** (v1.3), a todo color y recortada sin fondo sobre
  los circuitos, con un halo dorado suave. Respira suavemente (ciclo de 4,2 s) y mantiene la brisa del
  fondo animado (lazo, mechón, pañuelo y correa). El carrusel termina antes de su cara.
  Con `HOLOGRAM = true` en `RinPanelView.kt` se muestra como holograma dorado (tinte, líneas de
  escaneo, banda de luz e interferencia). Tamaño y posición: `SIZE` y `CENTER_X`.
  El recorte sin fondo se genera con `tools/make_rin_cutout.py` (modelo `isnet-anime` de rembg +
  limpieza por contorno) a partir de `tools/source/`.
- **Carrusel de tarjetas**: la tarjeta central se agranda y tiene borde dorado.
  - Toca una tarjeta lateral para centrarla; toca la central para abrir la app.
  - Mantén pulsada una tarjeta para: cambiar el nombre, elegir carátula de la galería, quitar la
    carátula, asignar categorías, mover a la izquierda/derecha o quitarla del launcher.
- **Carátulas**: se recortan a 3:4 y se guardan dentro de la app (no dependen de la galería).
- **Categorías** en pestañas ("Todos" + las tuyas). ＋ crea una; mantén pulsada una pestaña para
  renombrarla, moverla o eliminarla. Las apps añadidas con una categoría abierta entran en ella.
- **Botón ＋**: lista de apps instaladas con buscador; marca o desmarca las que quieras.
- **Barra de estado propia**: hora, fecha, batería (nivel, icono y si está cargando) y temperatura de
  la batería, que cambia a naranja desde 39 °C y a rojo desde 43 °C.

## Consumo
- La animación corre a ~30 FPS solo mientras el launcher está en primer plano; al abrir un juego se
  detiene por completo.
- El fondo es una imagen fija; la corriente se calcula en la GPU con un solo dibujo por fotograma.
- No pide permisos: la lista de apps se obtiene con `<queries>` (Android 11+) y las carátulas con el
  selector de fotos del sistema.

## Estructura
- `MainActivity.kt`: pantalla, carrusel, pestañas, menús y barra de estado.
- `LauncherStore.kt`: datos (apps, nombres, carátulas, categorías) en `files/launcher.json`.
- `CircuitBackgroundView.kt` + `CircuitCurrent.kt`: fondo original, brillo de circuitos y corriente de luz.
- `RinPanelView.kt` + `BreezeMesh.kt`: Rin con respiración y brisa (opción holograma) (`assets/rin_static.png`, `assets/breeze_layer.png`).
- `CarouselAdapter.kt`, `AppPickerDialog.kt`, `BatteryView.kt`.
- `src/test/.../ScreenshotTest.kt`: prueba con Robolectric que abre la pantalla con apps de ejemplo,
  verifica la carga de datos y guarda una captura en `app/build/screenshots/main.png`.

## Compilar
`gradle assembleDebug` (o Android Studio → Build → Build APK(s)).
APK: `app/build/outputs/apk/debug/app-debug.apk`.
