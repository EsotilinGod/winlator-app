# PanVK Kbase en Vortek (experimental 9)

Objetivo: Xiaomi 14T, Dimensity 8300-Ultra, Mali-G615 v11/CSF, Android 16.

Esta revisión permite cargar desde Vortek el paquete Android oficial de
[PanVK Kbase RC3](https://github.com/zenithblue-oss/panvk-kbase-android/releases/tag/panvk-kbase-v0.1.0-rc3).
PanVK reemplaza al controlador Vulkan propietario del sistema; Vortek sigue
siendo el puente entre el juego y Vulkan. No se mezcla código de ambos drivers.

## Uso

1. Descargar `PanVK-Kbase-Android-g615-v11-csf-v0.1.0-rc3-5a07217f.adpkg.zip`
   desde la versión RC3 oficial. No usar el archivo glibc ni el EMULATOR zip.
2. Editar el contenedor, abrir la configuración de Vortek y usar el botón de
   instalación de `Vulkan Driver Library` para importar el `.adpkg.zip`.
3. Elegir `PanVK Kbase G615`, mantener Vortek como controlador gráfico y crear
   un contenedor de prueba. Elegir `System` devuelve el controlador propietario.

La variante `RC3-lowmem` reduce memoria a costa de precisión en BC1/BC7. Esta
primera integración se valida con RC3 normal para separar compatibilidad de
calidad y memoria.

## Validaciones y retorno seguro

- Solo se admite en Mali un paquete cuyo manifiesto declare backend
  `panvk-kbase`, interfaz `mali_kbase`, ABI `android-aarch64-bionic` y una API
  mínima compatible. RC3 exige API 35; Android 16 corresponde a API 36.
- El instalador busca el JSON que realmente contiene `libraryName`; el paquete
  oficial incluye varios JSON y el orden no se supone.
- Se comprueba que la biblioteca permanezca dentro del directorio extraído y
  se rechazan rutas ZIP que intenten salir de él.
- Si la biblioteca no carga o no exporta `vkGetInstanceProcAddr`, Vortek abre
  automáticamente el Vulkan del sistema. Un fallo posterior durante la creación
  del dispositivo todavía puede cerrar el juego; seleccionar `System` es la
  recuperación manual.
- Los paquetes Turnip/Adreno, glibc, antiguos o sin metadatos Kbase se ignoran
  en Mali y usan el controlador del sistema.

## Alcance real

El binario PanVK no se redistribuye dentro del APK: se importa el paquete oficial
para mantener clara su versión, procedencia y manifiesto. El proyecto del driver
publica licencias MIT/GPL separadas y el paquete es todavía RC. Esta compilación
solo añade el cargador validado y una ruta de retorno; las pruebas del repositorio
no pueden acceder al `/dev/mali0` del teléfono. El funcionamiento, rendimiento,
presentación y estabilidad deben medirse en el Xiaomi 14T.
