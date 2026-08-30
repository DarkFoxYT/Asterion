# Rendering performance without quality reductions

Amnetic remains bundled and unmodified. Asterion enables its GPU instancing path for the existing animated flame/smoke mesh and supplies an order-preserving culling step through a narrowly scoped mixin. Other mods' Amnetic meshes are unaffected.

## Particle submission

- With 256–2048 tracked particles and compute support, Amnetic uploads the instance/bounds buffers and draws indirectly. Two compute passes perform frustum tests, a local prefix scan, and stable scatter into Amnetic's instance buffer. No visibility counts are read back to the CPU.
- Back-to-front sorting stays unchanged. Unlike Amnetic's unordered atomic compaction, stable scatter retains the exact order of surviving translucent sprites. Payloads, atlas coordinates, colors, opacity, emission strength, draw phase and particle lifetime are unchanged.
- Bounds are camera-relative from the outset, avoiding float truncation of world positions near the world border. Bounding spheres still include every billboard corner.
- Small batches stay on the CPU. Scenes above the existing 2048-particle budget also keep the original CPU nearest-visible selection, so off-screen candidates cannot displace visible sprites. The 64-block range and 2048 budget are unchanged.
- Unsupported compute hardware or shader compilation failure falls back to the existing CPU path. `-Dasterion.disableGpuParticleCulling=true` explicitly selects that path for diagnosis.
- Scratch storage grows only when necessary and is reused. Uniform locations are cached. Indexed SSBO bindings, including ranges, and the active GL program are restored after dispatch. Shader reload and mesh shutdown release the culling resources.

This is frustum culling, not depth/occlusion culling. It does not lower particle counts or use asynchronous visibility results that can lag the camera. GPU work is not automatically cheaper on every system; the batch thresholds are conservative policies, not measured universal crossover points.

## Other changes

The Dead Sun shader returns immediately where nearby opaque geometry completely hides the sun and its corona, provided the separate radiance volume is inactive. It also skips zero-opacity/zero-strength output. Fog integration computes invariant wind and dust tint once and skips depth reconstruction for sky pixels. Sample counts, render resolutions, noise functions, blur kernels, colors and animation timing remain unchanged.

Portal layers reuse their staging matrices, vectors and instance records. They retain CPU visibility tests: adding a compute dispatch for each one-instance portal layer would add overhead. Lamenter tear emission computes its phase once and keeps the same four origins, cadence and trajectories. Bone emissive rendering remains on its existing sharp cached-mesh path.

## Verification

`gradlew renderPerformanceRegression` runs actual OpenGL compute dispatches and checks stable payload order, 64-thread group boundaries, buffer growth/reuse, full/empty visibility, large-coordinate subtraction and SSBO range restoration. It compares the optimized Dead Sun/fog shaders with the preserved originals under `docs/verification/shader-baselines`.

On the RTX 5070, all 192 shader comparisons produced a maximum RGBA difference of **0.0**, covering quality settings, sky/terrain depth, Eclipse, radiance, finale, camera rotation, both depth conventions and large coordinates. This is validation of those cases, not a claim of exhaustive pixel identity on every GPU.

`gradlew runRenderPerformanceTest` uses a disposable client world to verify actual Amnetic GPU particle draws, the explicit CPU fallback and shader reload. `gradlew build renderPerformanceRegression emissiveRegression modDistribution` also runs the existing gameplay/geometry/GPU regressions and builds the single mod jar. No overall FPS gain has been measured or promised.

### Portal emission

Both portal layers explicitly participate in Amnetic's `WORLD_LAST` emissive capture. Their shader is fullbright without a lightmap, so disabling bloom does not darken the portal artwork. The shared restrained bloom settings remain unchanged; emission does not add Minecraft block lighting to nearby terrain.

`runRenderPerformanceTest` also runs `PortalEmissionGameTest`: it reads the actual HDR emission target with scene-brightness extraction disabled, samples the core and a halo region outside the core geometry, then checks shader reload, solid-block occlusion, reappearance and clearing on close. Both layers passed at midnight on the RTX 5070. GPU readback and reflective buffer inspection exist only in this explicitly launched development test.

The bundled Amnetic version uses instanced `.emissive(strength)` to select capture, but does not apply its numeric strength as a gain. The existing portal values are retained for compatibility; this verification checks emitted pixels rather than assuming those numbers brighten the shader. No Amnetic source or portal color/blur changes were needed.
