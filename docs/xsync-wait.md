# X server: espera de señales sin sondeo

Versión 11.2-Mali-14T-xsync-wait, versionCode 44.

La extensión SYNC de X esperaba una señal consultando continuamente su estado:
hasta 500 llamadas a `Thread.yield()` y después un sondeo cada 100 µs. Ahora
usa el monitor de señales para dormir y despertar cuando se activa una señal
desde Present o el protocolo X. La destrucción también despierta al hilo para
informar el error existente; una interrupción termina la espera.

Esto reduce el trabajo de CPU durante esperas de presentación. No cambia el
ritmo de presentación ni las llamadas Vulkan. La prueba JVM ejecuta la clase
real con los cuatro caminos de despertar; GitHub Actions compila el APK y
verifica su firma. El efecto en batería y FPS requiere medición en el 14T.
PanVK continúa desactivado en GPUs Mali después del reinicio informado.
