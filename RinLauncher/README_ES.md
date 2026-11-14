# Rin Launcher (Android)

Launcher horizontal tipo consola para el Xiaomi 14T, con la estética del fondo animado de Rin.
Es una app aparte: no reemplaza la pantalla de inicio del teléfono.

## Qué incluye (v1.0)
- **Pantalla horizontal a pantalla completa** (gira según el sensor entre ambas orientaciones horizontales).
- **Fondo de circuitos dorados** generados, con una corriente de luz periódica (cada 6 s) que nace del
  lado de Rin, y partículas tenues.
- **Rin a la derecha, recortada sin fondo** (PNG con transparencia) sobre los circuitos grandes del
  launcher, con un halo dorado suave y la misma brisa del fondo animado (lazo, mechón, pañuelo y correa).
  El recorte se genera con `tools/make_rin_cutout.py` (modelo de segmentación `isnet-anime` de rembg +
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
- La parte estática del fondo se dibuja una sola vez en un bitmap.
- No pide permisos: la lista de apps se obtiene con `<queries>` (Android 11+) y las carátulas con el
  selector de fotos del sistema.

## Estructura
- `MainActivity.kt`: pantalla, carrusel, pestañas, menús y barra de estado.
- `LauncherStore.kt`: datos (apps, nombres, carátulas, categorías) en `files/launcher.json`.
- `CircuitBackgroundView.kt`: fondo de circuitos y corriente de luz.
- `RinPanelView.kt` + `BreezeMesh.kt`: Rin recortada con halo y brisa (`assets/rin_static.png`, `assets/breeze_layer.png`).
- `CarouselAdapter.kt`, `AppPickerDialog.kt`, `BatteryView.kt`.
- `src/test/.../ScreenshotTest.kt`: prueba con Robolectric que abre la pantalla con apps de ejemplo,
  verifica la carga de datos y guarda una captura en `app/build/screenshots/main.png`.

## Compilar
`gradle assembleDebug` (o Android Studio → Build → Build APK(s)).
APK: `app/build/outputs/apk/debug/app-debug.apk`.
