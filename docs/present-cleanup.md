# Liberación del programador Present al cerrar una sesión

Versión 11.2-Mali-14T-present-cleanup, versionCode 45.

Present crea un hilo programador cuando un juego solicita avisos de cuadro con
`targetMSC`. Ese hilo podía seguir vivo después de cerrar el contenedor, junto
con referencias al servidor X y a la actividad. Ahora `onDestroy` detiene los
componentes y después cierra el programador, cancela avisos pendientes, retira
los listeners y vacía los eventos. Un indicador impide programar trabajos tras
el cierre. La modificación busca liberar recursos entre sesiones; no pretende
cambiar la sincronización mientras un juego está activo.

El código pasa la comprobación de sintaxis Java. La compilación Android y la
prueba de firma se ejecutan en GitHub Actions. La liberación real de memoria y
hilos debe comprobarse en el dispositivo tras varias aperturas de contenedor.
La ruta PanVK sigue desactivada para GPUs Mali.
