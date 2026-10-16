# Mali-G615: primera revisión de memoria y caché

Base: rama mali-14t, commit 39d3101d3d26f4564aa379104e6388bc4fffbe49.
Esta revisión conserva Vortek y el controlador del sistema. No incorpora un
binario PanVK, MaliSX2 ni un DXVK nuevo. No cambia las opciones por juego.

## Investigación y decisiones

- [PanVK kbase Android](https://github.com/zenithblue-oss/panvk-kbase-android):
  controlador Mesa para el kernel Mali de Android, con pruebas publicadas en
  G615. Sus optimizaciones de compilación, BC y envío de comandos pertenecen
  al controlador. Integrarlo requiere validar carga, memoria externa y
  presentación con Vortek; no se trasplantaron sus parches a ARM propietario.
  La variante lowmem también altera la calidad de ciertas texturas; no se
  activa implícitamente aquí.
- [DXVK-Sarek Mali](https://github.com/zeyadadev/DXVK-Sarek/releases/tag/v3.0.2-mali-compat-r1):
  evita asumir funciones clip/cull y ofrece un fallback de recursos locales.
  Este último pierde capacidad de compartir recursos entre procesos; no es
  una optimización global segura. Una integración futura necesita compilar
  y probar sus DLL y los requisitos Vulkan de esa versión.
- [Winlator Mali](https://github.com/celeste411/winlator_mali): buena parte de
  sus propuestas son configuraciones de versiones antiguas. Nuestra base ya
  filtra extended_dynamic_state en DXVK anterior a 2 sobre ARM propietario.
  No se añadieron overrides que anuncien versiones de OpenGL no verificadas.
- [WSI Woes on Mali](https://leegao.github.io/winlator-internals/wrapper/2026/07/28/wsi-woes-mali.html):
  investigación de memoria compartida y presentación en otros wrappers.
  Orientó la auditoría de importación AHB y liberación de recursos; su código
  WSI no es intercambiable directamente con Vortek.

## Cambios implementados

Implementación propia sobre Vortek, sin copiar código de los proyectos anteriores:

1. Seleccionar memoria solo si satisface TODAS las propiedades solicitadas.
   Si no existe, devolver un fallo explícito y manejarlo en todos los usuarios.
2. Consultar memoria externa con el uso real del buffer y distinguir exportación
   OPAQUE_FD de importación DMA_BUF. Evita seleccionar rutas por capacidades
   que solo existen en la dirección contraria.
3. Duplicar el descriptor prestado por AHardwareBuffer antes de asumir propiedad.
   La liberación de ResourceMemory ya no cierra el descriptor que Android posee.
4. Validar consultas AHB y liberar memoria/imágenes si falla crear el swapchain.
5. Reservar espacio de decodificación BC antes de leer caché, también en el
   primer acierto. No decodificar ni enviar comandos tras fallo de asignación.
6. No calcular hashes ni consultar disco cuando la caché de texturas está
   desactivada. Limitar sus claves existentes a imágenes 2D completas y compactas.
7. Distinguir capacidad del VkBuffer y tamaño alineado de su memoria; restaurar
   capacidad cero al liberar. Usar memoria coherente para escrituras de CPU
   que esta ruta envía sin una llamada explícita de flush.

Estas correcciones buscan reutilizar trabajo y evitar accesos inválidos y
recursos sin liberar. No prueban un aumento de FPS ni un ahorro de batería.
No se eliminan esperas de GPU ni se implementa un nuevo camino zero-copy.

## Validación

Pruebas extraídas de funciones C reales con Vulkan simulado y UBSan, más los
checks Java/nativos previos y compilación Android en GitHub Actions. El código
anterior falla la nueva prueba de selección de memoria.

En el Xiaomi 14T: usar re3 con las mismas opciones que en la versión anterior;
comprobar imágenes/estabilidad, primera carga y segunda carga con caché, y
repetir con caché desactivada. Comparar duración/FPS en las mismas escenas,
brillo, límite de FPS y temperatura inicial. Registrar controlador y logs si
hay regresión. La firma permanente y com.winlator se mantienen; versionCode 34.
