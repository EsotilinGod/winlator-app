# Propuestas de optimización para Xiaomi 14T (sin activar)

Estado: ideas para probar con la guía `guia-de-pruebas-14t.md`. Ninguna está
medida en el 14T. Las fuentes son foros y forks de la comunidad; no hay
referencias específicas para Mali-G615 / Dimensity 8300.

## Para probar primero (solo ajustes, sin cambiar código)
1. Vortek + DXVK 1.x como combinación base en Mali. Si un juego falla,
   probar VirGL o Gladio (OpenGL para Mali, añadido en Winlator 11.0).
2. Preset de Box64 "Performance". Ya es el valor por defecto; comparar con
   "Intermediate" si hay cierres, ya que es más estable a costa de FPS.
3. Si faltan texturas en DXVK, desmarcar `VK_EXT_extended_dynamic_state` en
   la configuración de Vortek. El código ya lo filtra en DXVK anterior a 2.
4. Juegos antiguos con problemas gráficos: variable de entorno
   `MESA_EXTENSION_MAX_YEAR=2003` (solo en ese juego).
5. Reducir resolución y limitar FPS: suele ser la mejora más grande en
   temperatura y estabilidad.

## Para evaluar con código (después de medir)
- DXVK-Sarek (versión Mali): evita asumir funciones clip/cull. Requiere
  integrar y probar sus DLL. Ver `mali-memory1.md`.
- Mesa PanVK: potencial de rendimiento, pero causó un reinicio completo.
  No reactivar sin los registros del reinicio. Con código de bypass de
  `/dev/dri` (stubs) el riesgo es mayor, no menor.

## Qué falta saber
- Causa del reinicio (registros del sistema).
- FPS base en tus juegos con el controlador del sistema.
