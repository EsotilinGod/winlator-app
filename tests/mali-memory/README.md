# Mali memory/cache regression checks

Run `python3 tests/mali-memory/run.py app/src/main/cpp/vortekrenderer`.
The harness extracts the actual production functions, compiles with UBSan,
and supplies a mock Vulkan driver. No Android device is simulated.

Coverage: exact memory-property matching; import/export capability direction
and nonzero buffer usage; warm cache on first upload and after buffer growth;
disabled cache avoids hashing and reads; mip uploads avoid whole-image cache;
allocation failure prevents decode/submission; VkBuffer logical capacity is
separate from aligned memory size; allocation cleanup/retry; failed Android
hardware-buffer property queries, memory-type selection and image binding.

The previous source fails the exact-property regression assertion. APK build
and existing Java/native checks also run in CI. GPU behavior, AHB descriptor
lifetime on Android, performance, temperature and battery require device tests.
