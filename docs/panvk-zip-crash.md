# ZIP initialization crash: experimental 11
Android reported ExceptionInInitializerError in ExtraFieldUtils.register:
AsiExtraField is not a concrete class. Commons Compress 1.20 uses
Class.newInstance() to register ZIP extra-field implementations. The original
configuration used -dontobfuscate but did not preserve these reflective classes.

Preserve ZipExtraField implementations and their members in R8. Also handle
LinkageError at the import boundary to show the existing import error and log
initialization failures instead of terminating the process.

CI inspects the optimized APK DEX for all 14 registered classes: each must
remain concrete and have a public no-argument constructor. This verifies the
specific packaging defect; import and PanVK startup still require device testing.
