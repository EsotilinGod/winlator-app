# Vortek: espera de datos sin sondeo continuo

Versión 11.2-Mali-14T-vortek-wait, versionCode 43.

El hilo de solicitudes de Vortek antes repetía una búsqueda en la lista de
datos adicionales mientras esperaba un mensaje. Tras varios intentos cedía
CPU o dormía un intervalo corto y volvía a buscar. La nueva ruta usa una
variable de condición: el hilo duerme y recibe una señal cuando llega un
bloque o cuando se destruye el contexto. Mantiene la búsqueda por ID para
respetar bloques que llegan fuera de orden.

Esta modificación puede reducir tiempo de CPU empleado en la espera y su
impacto en batería durante esas transferencias. No modifica las llamadas
Vulkan ni elimina esperas de la GPU; no implica más FPS por sí sola.

La prueba con pthreads extrae y ejecuta las funciones reales: datos fuera de
orden, mensaje correcto y cancelación. La compilación Android y las pruebas
previas se ejecutan en CI. Hace falta medir CPU, batería y estabilidad en el
teléfono para cuantificar el beneficio. PanVK sigue desactivado en GPUs Mali
tras el reinicio informado; se usa el Vulkan del sistema.
