# Rin Live Wallpaper (Android)

Proyecto Android Studio de un fondo animado vertical para el Xiaomi 14T.

## Qué incluye esta primera versión
- Ilustración vertical de Rin a 1220 × 2712 px.
- Pulsos dorados que se desplazan lentamente por trazos electrónicos superpuestos.
- Partículas ambientales discretas.
- Dibujo limitado a unos 30 FPS y solo mientras el fondo está visible.
- No solicita permisos de red, ubicación, almacenamiento ni sensores.

## Brisa en pelo y ropa (v1.3: capas separadas)
El lazo, el mechón izquierdo, las dos colas del pañuelo y la correa se mecen con una brisa suave
**sin deformar el fondo**:
- `assets/breeze_layer.png`: capa que contiene solo esas piezas, recortada de la ilustración original.
  Es lo único que se deforma (malla `Canvas.drawBitmapMesh` de 36 × 64 celdas, `BreezeMesh.kt`).
- `assets/base_patch.png`: fondo reconstruido (inpainting) detrás de las piezas, solo donde se mueven.
  Se pega una vez sobre la ilustración original al cargar; el resto conserva los píxeles originales.
- Ambos archivos se generan con `tools/make_breeze_layer.py` (OpenCV: GrabCut para el lazo; color y
  morfología para pelo, pañuelo y correa).

Cada pieza tiene un ancla fija y el desplazamiento crece hacia la punta (máx. ≈ 22 px en el pañuelo, v1.4), con una onda
viajera de 3,4 s y ráfagas lentas de 11 s. La cara no se mueve.

Limitación: la franja de fondo que queda al descubierto cuando una pieza se desplaza es reconstruida,
por lo que se ve algo más suave que el dibujo original (solo unos píxeles, junto a los bordes).

## Circuitos iluminados y corriente de luz (v1.4)
`app/src/main/assets/circuit_glow.png` es una capa con los trazos dorados del fondo, extraída por
color de la ilustración y sin la silueta de Rin. `circuit_flow.png` guarda, para cada punto de
circuito, la distancia medida a lo largo del trazo desde Rin (camino mínimo, algoritmo de Dijkstra).
Ambos se generan con `tools/make_circuit_glow.py`.
- Brillo tenue permanente que "respira" (ciclo de 6 s).
- Android 13 o superior: cada 6 s sale de Rin una corriente de luz que recorre las pistas hacia
  afuera (cabeza dorada brillante y estela), calculada en la GPU con un shader AGSL
  (`CircuitCurrent.kt`).
- Android 12 o inferior: en su lugar, dos bandas de luz diagonales (`Flow` en `RinWallpaperService.kt`).

Algunos trazos muy finos o tapados por Rin no forman parte de la capa.

## Modo horizontal (v1.5)
Cuando la pantalla está en horizontal (por ejemplo, con Rin Launcher abierto), el fondo muestra la
ilustración original a escala 1:1 respecto al alto (del lazo a las piernas) en una columna a la derecha
(centro en el 72 % del ancho). Los lados muestran el fondo original sin Rin, reconstruido con LaMa y
traspuesto a 2712 × 1220 (`land_bg.jpg`, generado con `RinLauncher/tools/make_launcher_bg.py`), con la
misma escala de circuitos; los bordes de la columna se funden con él. Brisa, brillo y corriente de luz
funcionan en ambas partes (`land_glow.png`, `land_flow.png`). Ajustes: constantes `LAND_*` al inicio de
`RinWallpaperService.kt`. La prueba `RenderTest` renderiza un fotograma en cada orientación.

## Tamaño de Rin en horizontal (v1.6)
El fondo acepta el comando `com.example.rinlivewallpaper.SET_LANDSCAPE_SCALE` (extra `scale`, 0,45–1,6)
enviado con `WallpaperManager.sendWallpaperCommand()`; Rin Launcher lo usa desde su botón de tamaño.
El valor se guarda y se mantiene al reiniciar. 0,45 muestra a Rin entera; 1 es el tamaño original.

## Requisitos para compilar
- Android Studio actualizado.
- JDK 17.
- Android SDK Platform 35.
- Conexión a Internet la primera vez para descargar Gradle y los plugins de Android.

## Cómo abrir y compilar
1. Descomprime `RinLiveWallpaper.zip`.
2. Abre Android Studio y selecciona **Open**.
3. Selecciona la carpeta `RinLiveWallpaper` (la que contiene `settings.gradle`).
4. Espera a que termine la sincronización de Gradle.
5. Conecta el Xiaomi 14T por USB y activa Depuración USB, o genera un APK desde **Build > Build APK(s)**.
6. Instala la aplicación en el teléfono.
7. Abre **Rin Live Wallpaper** y pulsa **Previsualizar y aplicar fondo**.
8. En la vista previa de Android, selecciona **Establecer fondo de pantalla** y elige pantalla de inicio (o ambas, si lo prefieres).

## Ajustes recomendados en Xiaomi
- Mantener la tasa de animación en 30 FPS.
- Si MIUI/HyperOS restringe la actividad, permitir que la aplicación no esté restringida en batería.
- No es necesario dejar la aplicación abierta: Android ejecuta el servicio de fondo animado.

## Personalización
El servicio está en:
`app/src/main/java/com/example/rinlivewallpaper/RinWallpaperService.kt`

La frecuencia se controla con `frameDelay = 33L`. El periodo y la velocidad de la corriente están en `CircuitCurrent.PERIOD` y `CircuitCurrent.SPEED`; la amplitud de la brisa, en la lista `flaps` de `BreezeMesh.kt`.
