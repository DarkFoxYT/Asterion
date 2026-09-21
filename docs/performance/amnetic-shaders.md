# Amnetic shader performance pass

Measured on AMD Radeon(TM) 610M with the installed Amnetic `Framebuffer`, `GpuTimer`, and `PassProfiler` APIs. The repeatable hidden-window harness compiles every Asterion post-processing fragment shader and validates all post-chain JSON with Minecraft's codec. It warms each program for 12 draws before collecting 28 GPU timing samples.

These are synthetic GPU pass measurements at a 1280×720 output resolution, using a uniform distant-depth input and RGBA16F intermediate targets. They are not live-world FPS measurements. Geometry, texture diversity, silhouettes, bandwidth between complete chained passes, and other mods can change real results. The edge-aware reconstruction path costs more near silhouettes; flat-depth pixels take the hardware-filter fast path.

| Pass group | Previous measured GPU ms | Revised measured pass sum, ms |
| --- | ---: | ---: |
| High-quality dust integration + composition | 8.9046 + 0.3724 = 9.2770 | 2.2355 + 0.3865 = 2.6220 |
| River mist + composition | 5.3008 | 1.3240 + 0.3139 = 1.6379 |
| Fast river mist + composition | 2.4667 | 0.1546 + 0.3139 = 0.4685 |
| Dead Sun horizontal + vertical blur | 2 × 0.5205 = 1.0410 | 2 × 0.0343 = 0.0686 |

The unchanged final scene blit is excluded from both sides. Pass sums are estimates assembled from separately timed shaders, not end-to-end chain timings.

## Runtime changes

- Amnetic `PostEffectConfig.externalTarget` supplies reused, depth-free `RenderTarget` buffers. This API requires Minecraft RenderTargets; Amnetic's standalone Framebuffer API is used by the benchmark.
- Heavy mist and high-quality volume integration use half width/height. Fast mist and sun blur use quarter width/height. Medium-quality dust uses one-third width/height.
- Final scene composition remains full resolution. Depth-guided upsampling protects geometry edges; flat-depth pixels avoid the expensive filter.
- Buffers resize only when the output resolution changes, and unused buffers are released after two seconds or on leaving a world.
- Atmospheric effects and bloom honor the existing adaptive performance governor. Quality variants do not overlap while fading out.
- The water geometry, opaque sea, wave spectrum, and boat synchronization are unchanged by this pass.

## Reproduce

With Java 25 configured:

```
./gradlew -I tools/underworld-validation.gradle profileAmneticShaders
./gradlew compileJava -I tools/underworld-validation.gradle checkLimboWater
```

The benchmark writes `build/reports/amnetic-shaders.csv`. The before/after results from this session are `build/reports/amnetic-shaders-before.csv` and `build/reports/amnetic-shaders-after.csv`.

The benchmark binds the actual Amnetic framebuffer allocation directly because its normal viewport restoration expects a running Minecraft window. GPU query waits happen only in this opt-in test, never in gameplay. Measured GPU times are recorded into Amnetic's PassProfiler and its snapshot is checked at the end.

## Wave-following mist update

The water and mist now import the same noise-warped wave field. Mist fits three surface-height samples along the ray, leaving a clear contact gap above the water and using scene depth to protect visible crests. High-quality mist now uses a one-third-size reusable target; fast mist remains quarter-size. Full-resolution depth-guided composition is retained.

`build/reports/amnetic-shaders-wave-mist.csv` records the updated synthetic AMD Radeon 610M timings: 1.0442 ms for high-quality mist at 426×239, 0.3892 ms for fast mist at 320×180, and 0.3134 ms for composition at 1280×720. These are isolated pass measurements, not in-game FPS or a multiplayer validation. The fast variant costs more than its previous flat-height version because it now follows the waves.

Nearby water uses a one-block mesh, stitched to the two-block distant mesh at chunk edges. Cached shoreline masks fade waves over eight blocks; only the central tile scans seabed depth. Hull masking, contact foam, and the wake share the water draw, using packed standard entity vertex attributes rather than another framebuffer or particle layer. The opt-in water check validates shoreline falloff, deck-plane rotations, CPU/GPU wave agreement and driver-rendered previews with and without the hull mask. The preview intentionally omits the boat model so the cutout is visible.

## Updated Amnetic, persistent wakes, and dust restoration

The current dependency is built from the supplied Amnetic checkout at `0b727183ec2d18514cdeb0d4bdc7a2c2692890c5`. Artifact hashes and the retained NeoForge adapter provenance are in `libs/amnetic-build.json`. Amnetic's optional missing `bench` directory is redirected by `tools/amnetic-local-build.gradle`; the upstream source/settings remain unchanged. Asterion now uses Amnetic's `UniformValue` API and `AmneticRenderTypeAccess`, and the instanced-render hook accepts `LevelCamera`. The old fixed-depth-format mixin is no longer registered because upstream now selects the actual depth format. `checkAmneticCompatibility` checks 34 private fields, methods, and wrapped calls against the bundled library.

Water has no block grid. Independent phase and amplitude packets travel in opposing directions; their horizontal footprint is stretched by 1/.65, with peaks smoothly bounded to 2.5 blocks. The shared CPU and GPU field also drives the mist. The physical deck and water cutout now use the model's three baked hull pieces, including their actual asymmetric ends and the 21-pixel deck height. Deck collisions sweep and slide along that footprint; a separate submerged hull collision replaces the oversized square collider.

Wake samples remain at their world positions for up to 360 ticks. A capped 64-sample history is rasterized into a reused 128×129 texture at most ten times per second. The texture window can move without moving the trail. Foam and small displacement are sampled by the water material itself; velocity-driven splash and spray particles supplement it. History clears on world changes/disconnect. Saved ferry journey data retains local deck coordinates, relative heading, and paid fare for relog recovery; reconnect loads the last tracked ferry chunk before restoring the player.

Dust integration, color grading, and target definitions are restored to the repository's earlier versions. Dust quality follows the user's cinematic setting instead of automatically switching to the flat fallback. High-quality dust therefore runs at full resolution again and is more expensive than the previous half-resolution optimization. `build/reports/amnetic-shaders-latest-water-dust.csv` contains the current isolated GPU measurements; it is not an in-game FPS report.

Validation: both distributable jars build, CPU/GPU hull distances agree, swept deck and coordinate roundtrips pass, seat/fare serialization roundtrips, wake window movement preserves world positions, wake history expires, and the water/mist/dust shaders compile on the driver. Actual in-game relog timing, multiplayer latency, camera motion and the final scene remain manual checks.
