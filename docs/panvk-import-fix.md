# Corrección de importación PanVK
Versión 11.2-Mali-14T-panvk-loader2, versionCode 39.

El selector se lee con ContentResolver y una copia temporal privada, sin
convertir el URI a una ruta del almacenamiento. Copia y extracción en un
hilo de trabajo; actualización del selector en el hilo de interfaz.
Se eliminan temporales al terminar y se controlan resultados cancelados o nulos.
Los errores de importación muestran un aviso y se registran con WinlatorImport.

Prueba JVM de copia, proveedor sin stream, permiso denegado, lectura interrumpida
y limpieza. Esta prueba usa dobles de Android; requiere validación en HyperOS.
No confirma la causa exacta del cierre visto en el video ni el funcionamiento
de PanVK en la GPU. Probar primero la importación del adpkg original y luego
el arranque de un contenedor de prueba.
