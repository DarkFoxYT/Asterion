# Asterion 2.0 rendering migration

Uses the supplied adjacent `Amnetic` checkout. Both repositories have local
changes; renderer provenance and artifact hashes are recorded in
`libs/amnetic-native-build.json`.

## Rendering changes

- Native 26.2/26.3 post passes use Minecraft GPU resources, explicit shader
  outputs, uniform blocks and separate feedback targets.
- Scene fog reads the world depth before Minecraft clears it for hand rendering.
  Sky detection and nearest-depth sampling account for reversed depth.
- Bloom collects emissive instances into a full-resolution HDR target with the
  scene depth attached. A zero threshold no longer blooms every terrain pixel.
  The final composite compresses overlapping emitters while preserving hue.
- Particle textures preserve Minecraft's registered atlas. Instance buffers use
  the actual attribute layout; 26.2 cannot use a fake padding attribute because
  its backend assigns shader locations consecutively.
- Firefly radial masks use defined smoothstep bounds and straight alpha.
- GPU cleanup runs on the render thread when disconnecting.
- The native ports also repair changed geometry, gameplay, recipes,
  advancements, fluid callbacks and feature APIs needed to enter both dimensions.

## Results

All eight packages built and passed embedded-renderer and shader-resource
validation: 26.1.2 Fabric, Quilt, Forge and NeoForge; 26.2/26.3 Fabric and Quilt.
Each native target passed 201 mixin binding checks. The 26.1.2 driver checks
compiled eight fragment shaders, linked the actual Limbo water pipeline,
validated depth copies and 96 instance uploads, and passed 49 renderer API
checks plus startup compatibility.

The final wall screenshots passed for both dimensions on all four native
targets. Counts refer to bright pixels in the center region, excluding the HUD
and crosshair; zero bright pixels were visible behind the wall in every case.

| Minecraft / loader | Labyrinth visible | Limbo visible | Behind wall |
| --- | ---: | ---: | ---: |
| 26.2 Fabric | 19,077 | 29,813 | 0 |
| 26.2 Quilt | 11,860 | 29,900 | 0 |
| 26.3 Fabric | 15,231 | 30,252 | 0 |
| 26.3 Quilt | 14,299 | 30,802 | 0 |

These stress images contain multiple overlapping Greek Fire particles; their
animation frame and elapsed ticks account for differences in visible counts.

Logs: `build/rendering-build-matrix-final.log`,
`build/rendering-checks-final.log`,
`build/native-rendering-matrix-final.log` (26.2 Fabric),
`build/native-rendering-cache-contained.log` (26.3 Quilt), and
`build/native-rendering-contained-retry.log` (26.2 Quilt / 26.3 Fabric).
Earlier attempts retained in these logs expose the loader cache failures;
only the final successful runs and screenshots are used for the results above.

## Reproduce

Build the adjacent Amnetic checkout using JDK 25:

```powershell
./gradlew.bat --no-daemon :26.2-fabric:build :26.3-fabric:build
```

Then from Asterion:

```powershell
./tools/update-amnetic.ps1 -NativeOnly
./tools/build-all-mods.ps1 -Release 2.0.0 -Native
./gradlew.bat --no-daemon -PasterionCompatibilityPorts=true -I tools/native-mixin-smoke.gradle :26.2-fabric:nativeMixinSmoke :26.2-quilt:nativeMixinSmoke :26.3-fabric:nativeMixinSmoke :26.3-quilt:nativeMixinSmoke
./gradlew.bat --no-daemon -PasterionCompatibilityPorts=true -PasterionUseJetBrainsRuntime=true -I tools/native-rendering-test.gradle :26.2-fabric:runClient :26.2-quilt:runClient :26.3-fabric:runClient :26.3-quilt:runClient
java tools/NativeRenderingImageSmoke.java versions/26.2-fabric/build/run/fabric/screenshots versions/26.2-quilt/build/run/quilt/screenshots versions/26.3-fabric/build/run/fabric/screenshots versions/26.3-quilt/build/run/quilt/screenshots
python tools/verify-amnetic-update.py --native
```

The client test creates a disposable world, enters Labyrinth and Limbo, spawns
Greek Fire behind a stone wall, takes a screenshot, removes the wall and takes
another screenshot. Image validation requires visible particles, rejects
magenta missing textures and checks that the wall hides the particles and bloom.
It is test tooling only and is not included in the distributed jars.

The validated runtime is the local JetBrains Java 25 runtime with OpenGL.
Quilt 0.31.0-beta.4 launches store their transform cache without external file
references using `-Dloader.transform_cache.disable_external_references=true`;
the Gradle development launch supplies this option automatically. Its default
cache writer produced invalid cache files in this environment, and disabling
compression instead failed while mounting transformed classes.

This covers the OpenGL backend without an external shader pack. Vulkan,
Iris/Sodium integrations and a complete gameplay playthrough require separate
validation. The 26.2/26.3 Forge and NeoForge ports and the 2.0 backport to
1.20.1/1.21.1 remain unfinished; the older jars are Asterion 1.5.
