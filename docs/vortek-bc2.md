# Vortek 2.1: adaptación BC para Mali (experimental 8)

Base: `213ffbdabec153b22e2c8c22a33a7692bb273c13`, rama `mali-14t`.
Android versionCode 37, versionName `11.2-Mali-14T-vortek-bc2`.
Objetivo de prueba: Xiaomi 14T, Dimensity 8300-Ultra / Mali-G615.

## Comparación con el autor

Se revisaron las fuentes oficiales de
[Vortek](https://github.com/brunodev85/vortek/tree/b1730c5def9b575672e671aee11d79ae7adc63d1)
y del [renderer Android](https://github.com/brunodev85/winlator-app/tree/8077f374ef2d54127b088de9e8cd7b4af14d9c8b/app/src/main/cpp/vortekrenderer).
El cliente distribuido `vortek-2.1.tzst` y el serializador coinciden con nuestra
base. No había una versión oficial posterior que importar. Esta actualización
modifica el renderer Android y conserva el protocolo y el cliente Vortek 2.1.
No instala un controlador Mali nuevo ni PanVK y no cambia DXVK o Wine.

## Correcciones

- BC4: usa bloques de 8 bytes al dividir el trabajo entre hilos. El código
  anterior calculaba 16 y podía leer fuera de memoria. Una textura 256x256
  reproduce el error con AddressSanitizer; la implementación nueva pasa.
- Decodificación con paso entre filas, varias capas/caras y cortes de profundidad.
  Valida offsets, tamaño comprimido disponible y tamaño de salida antes de leer.
  La copia Vulkan posterior recibe filas/capas de salida compactas, manteniendo
  mip, baseArrayLayer y offsets de destino. Esto no garantiza resolver otros
  problemas de orden de comandos del renderer.
- Conserva sRGB en BC1/2/3. BC4/5 usan RGBA UNORM o SNORM según corresponda,
  inicializan canales ausentes y alfa, y normalizan -128 a -127 según BC SNORM.
  Imágenes, vistas, listas de formatos y consultas usan la conversión consistente.
- Consulta soporte BC por formato en todos los drivers, además de la característica
  global: los formatos BC nativos muestreables evitan la conversión por CPU.
  ETC/ASTC/BC6/BC7 ya no se desvían al decodificador que solo implementa BC1–5.
  Esto no añade emulación BC6/BC7.
- Elimina desplazamientos de enteros con comportamiento indefinido y lecturas
  de palabras de 64 bits desalineadas en el decodificador.

## Trabajo evitado

La lista de tareas de decodificación es local y acotada a ocho, sin calloc/free
por imagen. El reparto depende de la cantidad de bloques: mipmaps pequeños o
estrechos se ejecutan en el hilo actual. Las texturas grandes conservan reparto
entre trabajadores. El umbral de 1024 bloques por tarea es conservador y debe
medirse en el teléfono. No se cambian las esperas de GPU ni su sincronización.

La caché decodificada usa `vortek-bc2` para no reutilizar píxeles creados con
el decodificador anterior. La primera carga volverá a generar esas entradas.
La caché anterior no se borra automáticamente.

## Validación y límites

`tests/vortek-bc/run.py` ejecuta 84 escenarios de píxeles, filas, tamaños pequeños,
trabajadores diferidos, valores con signo y límites con ASan/UBSan. También prueba
las funciones reales de consulta/selección de formatos con Vulkan simulado.
`tests/mali-memory/run.py` prueba cargas de seis capas con relleno, offsets y
rechazo de asignaciones truncadas, además de las regresiones anteriores.
La reproducción BC4 falla con el código previo y pasa con el actualizado.

La compilación Android y estas pruebas no sustituyen la prueba con el driver
del teléfono. No hay medición de aumento de FPS ni de ahorro energético.
Para comparar: misma escena de re3 u otro juego, mismo contenedor/resolución,
temperatura inicial similar y dos pasadas para distinguir caché fría/caliente.
Observar texturas, iluminación, reflejos y tirones al cargar zonas. El HUD sigue
midiendo actualizaciones de ventanas, no tiempo de ejecución de GPU.
