# Winlator 11.2 — adaptación experimental para Mali

Base verificada: 3981d86efa4f333b2a34a7da8b6521476cd8c8b9.

Este trabajo corrige el separador de extensiones entre Java y Vortek, conserva la selección de una sola extensión, fija el controlador Vulkan del sistema dentro de Vortek en Mali/Immortalis y conserva ajustes OpenGL cuando el bloque Vulkan está vacío. La parte nativa limita la enumeración de extensiones a la capacidad solicitada, devuelve VK_INCOMPLETE cuando corresponde y limpia el contador al fallar la consulta. También añade información del controlador a los registros.

La base ya dispone de correcciones Mali. No se anuncian funciones nuevas de la GPU ni ganancias de FPS. La selección general de Turnip sigue existiendo; la protección añadida se aplica al controlador personalizado dentro de Vortek.

## Compilación

El workflow `Winlator Mali 14T APK` se ejecuta al actualizar la rama `mali-14t`. Usa Java 17, SDK 35, NDK 24.0.8215888 y CMake 3.22.1. Si GitHub deshabilita Actions al crear el fork, habilitarlo desde la pestaña Actions y volver a ejecutar/disparar la compilación.

Al terminar correctamente, descargar el artefacto `Winlator-Mali-14T-...` desde la ejecución de Actions. Contiene una APK de depuración, su SHA-256 y el commit utilizado. Las comprobaciones previas incluyen 43 casos de lógica Java y 14 escenarios C con controlador y transporte simulados; no sustituyen pruebas con Android real.

## Instalación y límites

La APK conserva el identificador `com.winlator`, necesario para las rutas nativas existentes. No se instala en paralelo a otra aplicación con ese identificador. Su firma de depuración es distinta de la oficial: no intentar reemplazar una instalación con datos sin guardar antes una copia de los contenedores. La firma generada en CI puede cambiar entre compilaciones.

Pendiente: pruebas físicas en Xiaomi 14T, Android 16 y HyperOS 3.0.303.0, compatibilidad de juegos, memoria y tamaño de página. Una compilación correcta solo demuestra que el código genera una APK.
