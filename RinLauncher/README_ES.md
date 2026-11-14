# Rin Launcher (Android)

Launcher horizontal tipo consola para el Xiaomi 14T, con la estética del fondo animado de Rin.
Es una app aparte: no reemplaza la pantalla de inicio del teléfono.

## Qué incluye
- **Pantalla horizontal a pantalla completa** (gira según el sensor entre ambas orientaciones horizontales).

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

## Vista XMB (v2.0, por defecto)
Estilo consola tipo XrossMediaBar sobre la escena de Rin (`XmbView.kt`): fila horizontal de categorías
(Ajustes, Todos y las tuyas, con iconos según el nombre), lista vertical de apps con el último uso
("hoy", "ayer", "hace N días"), onda de luz animada, velo de legibilidad, reloj y batería.
- Táctil: deslizar en horizontal cambia de categoría, en vertical de app; tocar abre; mantener pulsado
  abre las opciones de la app.
- Mando: ◀ ▶ / L1 R1 categorías, ▲ ▼ apps, A abrir, X opciones, Y añadir apps, SELECT tamaño de Rin.
- Ajustes: añadir/quitar apps, nueva categoría, editar categorías, tamaño de Rin y cambiar a la vista
  carrusel. Desde el carrusel, el botón con barras vuelve al XMB.

## Accesos directos (v2.3)
Ajustes → Añadir acceso directo (o el botón «Acceso directo…» en la lista de apps):
- **Juego desde archivo**: eliges el emulador y el archivo del juego; el launcher lo abre con
  `ACTION_VIEW` en ese emulador (permiso de lectura persistente). Funciona sin ser launcher predeterminado.
- **Desde la app**: los accesos directos que ofrecen las apps en el selector de widgets
  (`ACTION_CREATE_SHORTCUT`). Los clásicos funcionan siempre; los modernos (ShortcutManager) solo si Rin
  Launcher es el launcher predeterminado (Ajustes → Usar como launcher predeterminado).
- Siendo predeterminado, el «Añadir a la pantalla de inicio» de los emuladores llega directo al launcher
  (`ConfirmPinActivity`).
Los accesos directos se editan (nombre, carátula, categorías) igual que las apps. `Shortcuts.kt`,
pruebas en `ShortcutsTest.kt`.

## Iconos y sonidos del menú (v2.1)
- Pack de iconos propio en estilo consola (glifos blancos sólidos con brillo), en `XmbView.kt`. Las
  categorías eligen icono según su nombre: juegos → mando, emuladores/retro → cartucho, música → nota,
  fotos → cámara, internet/web → globo, vídeo/películas → película, favoritos → estrella; el resto,
  carpeta.
- Sonidos originales sintetizados (`tools/make_sounds.py` → `res/raw/xmb_*.wav`): moverse, cambiar de
  categoría, abrir y opciones. Se reproducen con `SoundPool` (`MenuSounds.kt`) con el volumen
  multimedia y se activan/desactivan en Ajustes → Sonidos del menú.
- No se usan iconos ni sonidos de Sony: solo el estilo general.
- v2.2: iconos PNG diseñados por el usuario (`res/drawable-nodpi/xmb_icon_*.png`, reducidos a 192 px).
  Tienen prioridad sobre los vectoriales, que quedan como respaldo (carpeta, nueva categoría,
  favoritos, silencio). Palabras clave extra: social/chat, ejercicio/gym, podcast/noticias,
  comida/cocina, café, herramientas/utilidades.

## Escena de Rin (v1.7)
El launcher dibuja él mismo la escena horizontal del fondo animado (`RinSceneView.kt`): la ilustración
original completa (sin recortes) en una columna a la derecha, fundida con el fondo original sin Rin
traspuesto a horizontal, con brisa, circuitos con corriente de luz y partículas. No depende del fondo de
pantalla del sistema: HyperOS no gira el fondo de pantalla en horizontal (lo amplía y recorta en
vertical), así que mostrarlo detrás del launcher no funcionaba.

## Tamaño de Rin
Botón con flechas diagonales en la barra superior: control deslizante del 45 % (Rin entera) al 160 %,
con vista previa en vivo. "Listo" guarda, "Restablecer" vuelve al 100 %, "Cancelar" deshace.

## Mando en la vista carrusel (v1.8)
Funciona con mandos Bluetooth o USB. La cruceta y el stick izquierdo mueven el carrusel (mantener = avance
continuo). Con un mando conectado, la línea de ayuda muestra los botones.

| Botón | Acción |
|---|---|
| ◀ ▶ | Moverse por el carrusel |
| A | Abrir la app seleccionada |
| X | Menú de edición |
| Y o START | Añadir/quitar apps |
| L1 / R1 | Categoría anterior / siguiente |
| SELECT | Tamaño de Rin |
| B | Cerrar menús (en la pantalla principal no hace nada) |

En los menús: cruceta para moverse, A para elegir, B para cerrar. La prueba `gamepadNavigation`
verifica la navegación, las categorías y el lanzamiento con A.

## Consumo
- La animación corre a ~30 FPS solo mientras el launcher está en primer plano; al abrir un juego se
  detiene por completo.
- El fondo es una imagen fija; la corriente se calcula en la GPU con un solo dibujo por fotograma.
- No pide permisos: la lista de apps se obtiene con `<queries>` (Android 11+) y las carátulas con el
  selector de fotos del sistema.

## Estructura
- `MainActivity.kt`: pantalla, carrusel, pestañas, menús y barra de estado.
- `LauncherStore.kt`: datos (apps, nombres, carátulas, categorías) en `files/launcher.json`.
- `RinSceneView.kt` + `BreezeMesh.kt` + `CircuitCurrent.kt`: escena de Rin (ilustración, brisa, circuitos y corriente).
- `CarouselAdapter.kt`, `AppPickerDialog.kt`, `BatteryView.kt`.
- `src/test/.../ScreenshotTest.kt`: prueba con Robolectric que abre la pantalla con apps de ejemplo,
  verifica la carga de datos y guarda una captura en `app/build/screenshots/main.png`.

## Compilar
`gradle assembleDebug` (o Android Studio → Build → Build APK(s)).
APK: `app/build/outputs/apk/debug/app-debug.apk`.
