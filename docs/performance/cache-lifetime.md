# Animation cache lifetime

The optional interpolated-frame copies now expire after 15 seconds without animation use.
Sweep once per second, and release immediately on world changes or shutdown. Source textures
remain owned by Minecraft; freeing an optional copy must not close a source image.

Legacy 1.5 groups copies by animation owner, limits each owner to 8 MiB within the existing
32 MiB total, and closes partially allocated copies on failure. Active animations keep admitted
frames even when their complete cycle exceeds the budget, avoiding repeated eviction/copy churn.

The 26.1.2 1.5 and Underworld registries retain owners until cleanup completes. Previously weak
owners could disappear without releasing the shared memory-budget reservation. Only successful
allocations register an owner; idle eviction and close both unregister it and return the budget.

Validation:
- `gradlew.bat -I tools/cache-validation.gradle :1.20.1:checkTextureCache :1.21.1:checkTextureCache`
  in `ports/1.5` exercises real NativeImage copies, per-owner/shared limits, stable cache hits,
  idle-only eviction, 50 world changes, and continued validity of source images.
- `gradlew.bat -I tools/cache-validation.gradle :26.1.2-fabric:checkTextureCacheLifecycleSmoke :26.1.2-fabric:checkTextureFrameBudgetSmoke`
  in the root or `ports/1.5/ports/26.1.2` checks idle eviction, 50 release/repopulate cycles,
  and concurrent aggregate-budget admission.

Both suites pass. Fabric 1.20.1/1.21.1, Forge 1.20.1/1.21.1, NeoForge 1.21.1, and Fabric/NeoForge
26.1.2 compile with the changes. Forge 1.20.1 was compiled with JDK 21 in Java 17 release mode;
the local NeoForge 1.20.1 artifact-generation task still requires an unavailable Java 17 runtime.
No FPS improvement has been measured in-game.
