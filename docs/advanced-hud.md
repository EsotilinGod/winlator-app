# Medidor avanzado

Activar en Editar contenedor → HUD Mode → Avanzado (tiempos por cuadro).
Los modos Desactivado, Simple y Completo conservan sus valores anteriores.

Muestra FPS medios recientes, intervalo medio entre cuadros, percentil 99
(rango más cercano) y máximo, junto con una gráfica de hasta 120 intervalos.
La gráfica tiene escala fija 0–100 ms; picos mayores se recortan visualmente,
pero el máximo numérico conserva su valor. Líneas de referencia 16,7/33,3 ms.

La fuente es el evento de actualización de la ventana seleccionada por
Winlator. No es una medición del tiempo de ejecución de GPU, ni garantiza
coincidir con un contador interno del juego. Los FPS son 1000/media de ms;
no son el refresco de la pantalla ni un contador independiente de DXVK.
P99 es un tiempo en ms, no un porcentaje de uso ni el indicador 1% low.

El primer evento establece el tiempo inicial. Pausas superiores a 2 segundos
se consideran inactividad: muestra 0 FPS y esperando cuadros; el siguiente
evento inicia una serie nueva. Cambiar la ventana medida también reinicia.

RAM es memoria usada/total del sistema. CPU muestra frecuencia máxima leída
entre núcleos, no porcentaje de utilización. GPU usa la identificación que
expone la ventana; N/A significa que esa información no está disponible.
No se inventan utilización, temperatura ni potencia de GPU.

Solo se guardan 120 intervalos en RAM, sin archivo de registro ni permisos
nuevos. Muestreo sin asignaciones por cuadro, panel/gráfica a 2 Hz y datos
del sistema a 1 Hz. Se suspenden refrescos en segundo plano y se eliminan
callbacks al retirar la vista. La lógica de intervalos tiene pruebas JVM;
la legibilidad y el comportamiento gráfico necesitan prueba en el teléfono.
