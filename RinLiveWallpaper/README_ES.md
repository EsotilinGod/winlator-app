# Rin Live Wallpaper (Android)

Proyecto Android Studio de un fondo animado vertical para el Xiaomi 14T.

## Qué incluye esta primera versión
- Ilustración vertical de Rin a 1220 × 2712 px.
- Pulsos dorados que se desplazan lentamente por trazos electrónicos superpuestos.
- Partículas ambientales discretas.
- Dibujo limitado a unos 30 FPS y solo mientras el fondo está visible.
- No solicita permisos de red, ubicación, almacenamiento ni sensores.

## Brisa en pelo y ropa (v1.1)
El lazo, el mechón izquierdo, el pañuelo amarillo y la correa del short se mecen con una brisa suave.
Se usa una malla deformable (`Canvas.drawBitmapMesh`, 40 × 80 celdas) sobre la ilustración original:
cada zona tiene un ancla fija y el desplazamiento crece hacia la punta (máx. ≈ 13 px en la imagen de
1220 × 2712), con una onda viajera de 3,4 s y ráfagas lentas de 11 s. La cara está protegida por una
máscara con peso cero. Los parámetros están en `BreezeMesh.kt`.

Limitación: como la deformación actúa sobre la imagen plana, el fondo que queda justo detrás de esas
piezas también se desplaza unos píxeles. Para un resultado perfecto habría que separar pelo y ropa en
capas transparentes y reconstruir el fondo detrás de ellas.

Los trazos luminosos son una aproximación visual sobre los circuitos del fondo; no siguen
automáticamente cada línea original.

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

La frecuencia se controla con `frameDelay = 33L`. Los trazos de luz están en `buildCircuitPaths()`.
