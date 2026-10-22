# PanVK Kbase: reinicio informado en Xiaomi 14T

El usuario informó artefactos gráficos seguidos de un reinicio completo del
teléfono al probar PanVK en la GPU Mali. La causa concreta requiere registros
del sistema; el retorno automático de Vortek solo cubre errores de carga de la
biblioteca, no fallos posteriores del controlador o del kernel.

Esta compilación desactiva la selección de controladores Vulkan personalizados
en Mali y fuerza el controlador del sistema también para contenedores guardados
o accesos directos que todavía tengan PanVK seleccionado. El paquete importado
permanece instalado para inspección, pero no se carga. Las GPU no Mali conservan
su comportamiento actual. No volver a activar PanVK en este dispositivo hasta
analizar el registro del reinicio y validar una corrección específica.
