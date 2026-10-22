# Asterion multiversion port

The `multiversion` branch succeeds `1.21.1` and preserves its history.
Stonecutter 0.9.8 maintains 1.20.1 and 1.21.1 from the active 1.21.1 sources.
Fabric is the default target. Quilt uses the same release JAR. NeoForge uses native Asterion and Amnetic adapters on every supported Minecraft version; neither Connector nor Forgified Fabric API is required. Required libraries are embedded in every release: Amnetic, GeckoLib, Fabric API on Fabric/Quilt, and the matching native runtime libraries on Forge/NeoForge. Their nested runtime dependencies, including Assimp, remain intact. Install one matching Asterion JAR alongside the mod loader.

## Building

Use Java 21 for Gradle and the included wrapper. Client/server runs need Java 17 for Minecraft 1.20.1 and Java 21 for 1.21.1. Install both JDKs or pass `-Porg.gradle.java.installations.paths=<path-to-jdk17>` when running the legacy tests:

```
gradlew.bat buildAllVersions
gradlew.bat stageTestBuilds
```

The shipped Fabric files come from `remapJar`, never the named development JAR.
`src/gametest` and `neoforge/src/smoke` are test-only source sets.

For release 1.5.2, `buildAllVersions` includes Fabric, Quilt, Forge and NeoForge
for 1.20.1, 1.21.1 and 26.1.2 (12 jars). The wrappers select Java 21 for the
older build and Java 25 for 26.1.2; Java 17 is also required as a toolchain. Finished jars are copied
to the repository `modbuilds` folder. The PowerShell helper from the repository
root also selects the JDK separately for each build:

```powershell
./tools/build-all-mods.ps1 -Release 1.5.2
```

Forge resource tools require Python 3.11+. Set `ASTERION_PYTHON` or pass
`-PasterionPython=<python-executable>` if it is not available on PATH.
For one Minecraft version only, use `:1.20.1:buildAllLoaders` or
`:1.21.1:buildAllLoaders`.

`checkCameraClearance` runs the closed-door, camera-corner and wall-safe return
regressions and is included in the Fabric/Quilt `check` tasks. It verifies camera
geometry; an in-game pass is still needed to judge framing and visual quality.

`gradlew.bat prepareCurseForgeRelease` builds the matrix and writes
`build/curseforge/release-manifest.json`. Each manifest entry is one upload with
all its listed loader tags. Identical Fabric/Quilt jars share an entry instead of
being submitted twice. The bundled renderer's preset-storage safety is checked
before release jars are collected. CurseForge still controls moderation status.

## Rendering

Veil has been removed. Amnetic supplies bloom, emission, portal surfaces and dimension postprocessing. Its model readers are privately relocated to avoid loader dependency conflicts. `libs/amnetic-build.json` records the upstream source, patch list and production artifact hashes.

TAA is disabled by Asterion and guarded in Amnetic. The dimension uses a dark neutral sky. Dust integrates across the complete world image, including empty sky; distant scattering transitions to neutral grey with the same extinction on sky and geometry. The low-cost path reconstructs camera-relative depth and samples smooth world-space haze. High quality preserves full-resolution scene and volume targets; medium reduces only the volume buffer. Optional Iris integration distinguishes an installed loader from an active shader pack; active third-party packs still require their own compatibility testing.

The 1.20.1 Gecko renderer refreshes vertex buffers after nested weapon draws, preventing Minotaur texture/UV corruption. Weapon attachment pivots, object renderer centering, and sword origin adjustments follow the main branch. Authored model, animation and texture assets are retained. A dedicated unlit emission material preserves eye/glow textures on both Gecko versions; full-resolution depth and captured world matrices keep bloom aligned and occluded by walls.

The updated Amnetic source archive accompanies the builds. `docs/amnetic-asterion.patch` also contains tracked and new compatibility files against its recorded upstream commit. Release JARs omit redundant authoring copies and keep authored audio quality.

## Gameplay and tests

Shared server gameplay, legacy item components/NBT, recipes, loot and advancement resources are version-adapted. Client code includes the main rigid-body solver, held-item attachments, player recovery, Cursed Brazier cinematic and roof-collapse cinematic.

```
gradlew.bat :1.20.1:runGameTest :1.21.1:runGameTest :1.20.1:runQuiltGameTest :1.21.1:runQuiltGameTest
gradlew.bat :1.20.1:runSmokeClient :1.21.1:runSmokeClient
gradlew.bat :1.20.1:runQuiltSmokeClient :1.21.1:runQuiltSmokeClient :neoforge:runSmokeClient
```

The client scenario checks dimensions, particles, bloom on/off, Minotaur weapons, occlusion, TAA, OpenGL errors, ragdoll stability/recovery, hot/cold forging, mixed-material crafting, saved-item durability, advancements, shielded miniboss combat and cutscene camera/HUD recovery. Final results belong in the distribution validation report; compilation alone is not a runtime pass.

Additional focused runs:

```
gradlew.bat :1.20.1:runSmokeClient :1.21.1:runSmokeClient :neoforge:runSmokeClient -PatmosphereSmoke
gradlew.bat :1.20.1:runSmokeClient :1.21.1:runSmokeClient -PbloomSmoke
gradlew.bat :1.20.1:runProductionSmokeClient :1.21.1:runProductionSmokeClient
gradlew.bat :neoforge:runPackagedSmokeClient
gradlew.bat :neoforge:runPackagedSmokeClient -PbloomSmoke
gradlew.bat :neoforge:runPackagedSmokeClient -PatmosphereSmoke
```

`-PshaderSmoke` adds only the optional local Iris/Sodium JARs under `build/shader-smoke/<version>` to development runs. These are not shipped dependencies. Shader smoke coverage uses Iris installed without an active external shader pack.

Main-branch visual fidelity has not been quantified as a percentage. Frame times describe the individual 854x480 test scene and GPU recorded in each log; they are not a general frame-rate guarantee. NeoForge 1.20.1 was attempted in an isolated probe but did not complete validation and is not a supported artifact in this delivery.

The native Amnetic adapter is built from `neoforge/src/amneticNative` and the recorded official-mapped Amnetic development JAR by `gradle/amnetic-neoforge.gradle`. Packaged NeoForge tests use release JARs, assert Connector is absent, and load the embedded Assimp native library.
