# New-world freeze fix — 6 September 2026

The client crash workaround used `-XX:TieredStopAtLevel=3`, leaving full profiling active even though C2 was disabled. Client launches now default to `-XX:TieredStopAtLevel=1`: C2 remains disabled and C1 runs without the unused profiling overhead. Gradle client launches and the IDE launch task inherit this setting. Restart the game from the project to apply it; copying a JAR cannot change the compiler flags of a running JVM.

OpenJDK describes tier 3 as C1 with full profiling and tier 1 as pure C1 in its [compilation policy](https://github.com/openjdk/jdk/blob/master/src/hotspot/share/compiler/compilationPolicy.hpp). This local change retains the workaround for the saved C2 compiler crashes; it is not a guarantee against every JVM or game crash.

## Controlled comparison

The same normal Survival seed (20260906), with the same test client and settings, took:

- Previous tier 3: **107.236 seconds**.
- Revised tier 1: **13.889 seconds**.

Times include world creation, spawn search, joining and the first 60 client ticks. The old-setting run was intentionally stopped after obtaining this result; its nonzero process exit is not an unexpected crash or a passing complete suite. Its thread snapshot shows terrain-noise workers computing while the server waits for spawn chunks.

The initial tier-1 run also passed seed 20260907 in 8.489 seconds.

## Final validation

The final run started cold with the seed from the user's reported world, then created two additional normal Survival worlds. All three joined successfully, ran additional live ticks, and kept the unused maze dimension unloaded.

| Seed | Creation plus 60 ticks |
|---|---:|
| -1950657107712358405 | 16.188 s |
| 20260906 | 8.464 s |
| 20260907 | 8.797 s |

The original user log shows 66 seconds in spawn selection alone (23:32:15–23:33:21). That log includes additional installed mods; the controlled comparison above uses the Fabric test runtime. The test recreates the user's seed without modifying their save. These timings measure new-world creation, not total application/resource startup or every possible seed.

Reproduce with `runCatacombTest -PauditOnly -PauditTests=NormalWorldCreationGameTest`. Add `-PasterionSafeJvmTier=3` only to compare the old setting. The full audit now includes normal-world creation, because its other flat worlds skip this workload.

Evidence is in `build/world-creation-fixed.log`, `build/world-creation-fixed-results.tsv`, `build/world-creation-c1.log`, `build/world-creation-c3-baseline.log`, `build/world-creation-c3-threads.txt` and `build/world-freeze-user.log`.
