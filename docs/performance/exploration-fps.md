# Exploration FPS investigation

## Confirmed code issues corrected

- GeckoLib animated textures remain registered with TextureManager after their objects leave view. Their ticks previously continued CPU frame processing and GPU uploads. Asterion textures now stop animation work after one second without a texture lookup; visible materials refresh that timestamp through TextureManager.getTexture. Cached interpolation frames are freed after thirty idle seconds. Other mods' textures are unchanged.
- The previous interpolation cache allowed 32 MiB of native images per animated texture. Exploring and discovering more animated entities could multiply this allocation. It is now 32 MiB shared, at most 8 MiB per texture. Reload/close/idle cleanup returns the budget. A concurrent discovery test verifies the ceiling and recovery.
- Dynamic lights previously created an incoming light and evicted an old one whenever the budget was exceeded. Repeated updates across more sources than the budget could churn that set continuously. Admission now retains existing lights unless a new candidate is sufficiently closer. Rejected candidates do not enter the timestamp map. Dimension changes clear both light registries and timestamps.

These are demonstrated code paths, not proof that they explain the user's entire 400-to-70 FPS regression. Dense overgrowth also has more cutout geometry, vines and emissive decorations than enclosed maze corridors. Existing appearance, render distances, biome contents and shader quality settings were not reduced by these fixes.

## Live recording, 20 September

`build/reports/exploration-check.jfr` captured a successfully launched Fabric client and a local Limbo session. Filtering samples after startup found 256 render-thread stacks in water submission, 45 in shoreline topology, and server/worker stacks in structure placement and chunk meshing. This recording is not an overgrown before/after comparison.

The water renderer was still traversing the full configured render distance even though native Limbo fog becomes opaque at 48 blocks. Its candidate radius is now capped at four chunks, retaining at least a full chunk of padding beyond visible water. At a configured radius of 16 chunks, the maximum candidate square falls from 1,089 to 81 chunks. This bounds cache growth, shoreline scans and repeated vertex uploads without reducing visible terrain or changing the world render-distance setting.

Validation: Fabric successfully loaded all resources with the corrected dripstone/texture mixins; both jars build; 64-seed terrain checks and shared-memory stress tests pass. All six texture hashes match the supplied files. Full spike gameplay and the exact overgrown FPS recovery still need live comparison.

## Recording the remaining regression

Use `./gradlew.bat :runClient -PstartupProfile` to record with Java Flight Recorder. The leading colon selects Fabric only; unqualified `runClient` also selects the NeoForge subproject. The recording is written to `build/startup-profile.jfr` when the game exits normally. A live recording can also be dumped with the JDK's `jcmd` and the game's process ID, without closing it.

Record stationary maze, a full camera turn, stationary recovery, spectator movement, then stationary overgrowth. Keep resolution, GPU, render distance, frame cap and settings constant. `tools/AnalyzePerformanceRecording.java` prints sampled hot stacks after startup. CPU samples cannot establish GPU frame costs; use Amnetic's pass profiler alongside them.

Previous local launches selected AMD Radeon 610M rather than the installed RTX 5070 Laptop GPU. This must be held constant in comparisons. No registry or driver settings were changed.

## Blocks and compatibility

The six supplied PNG files are copied unchanged into two dead-stone blocks and the four texture stages of `asterion:shale_spike`. Existing shale/formation IDs remain. New Underworld terrain uses dead stone with rooted spike clusters and hanging spikes; old saved chunks are not rewritten.

The spike inherits vanilla dripstone behavior. Its helper integration recognizes custom spike states in vanilla's private chain/placement checks; custom slow ceiling growth retains the custom block rather than producing vanilla pointed dripstone. Manual placement supports upward/downward chains, joining, waterlogging, breakage and falling damage.

Validation also found a pre-existing NeoForge dependency-range mismatch: `1.0-SNAPSHOT-0b72718` sorted below the old minimum `1.0-SNAPSHOT`. The minimum now follows the bundled Amnetic coordinate.
