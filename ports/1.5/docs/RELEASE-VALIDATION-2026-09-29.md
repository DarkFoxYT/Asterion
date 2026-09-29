# Asterion 1.5 compatibility validation — 2026-09-29

Scope: the existing 1.5 compatibility release, without backporting the 2.0 Limbo spider.

## Release artifacts

All 12 configured artifacts built successfully and passed `tools/verify-v15-release.py`:

| Minecraft | Fabric | Quilt | Forge | NeoForge | Game Java |
| --- | --- | --- | --- | --- | --- |
| 1.20.1 | Pass | Pass | Pass | Pass | 17 |
| 1.21.1 | Pass | Pass | Pass | Pass | 21 |
| 26.1.2 | Pass | Pass | Pass | Pass | 25 |

The verifier checks ZIP CRCs, loader metadata and entrypoints, referenced mixin
classes, mixin Java compatibility, Asterion bytecode versions, embedded dependency
integrity, exactly one registered Amnetic dependency, and exclusion of test classes.
Artifacts are in the repository-root `modbuilds` folder.

## Fixes

- The older build now selects a Java 21 Gradle daemon instead of inheriting Java 8 from PATH.
- Forge and NeoForge 1.20.1 no longer reference the excluded `PortModelCubeAccessor` mixin.
- Those two targets now declare `JAVA_17` in their mixin configuration.
- Forge constructs heavy-water blocks and buckets with lazy fluid suppliers, avoiding registry-delegate access during mod construction.
- Biome spawn setup waits for entity registration on deferred-registry targets; Forge/NeoForge 1.20.1 drain those callbacks during common setup.
- The 26.1.2 Quilt smoke launcher uses Quilt's entrypoint and stages its test harness with the release jar.
- The 26.1.2 NeoForge smoke harness includes its native entrypoint and metadata instead of packaging a Fabric-only harness and duplicate game resources.
- Forge 26.1.2 uses corrected pack metadata for the embedded Amnetic jar in both development and release builds.
- Forge 1.21.1 bridges the shared client-stopping callback to Forge's shutdown event, fixing client setup's missing-class crash and retaining texture-cache cleanup.
- `tools/build-all-mods.ps1 -Release 1.5` builds and verifies only this release.

## Runtime coverage

- Fabric 1.20.1 and 1.21.1: all five server GameTests passed on each version; 2,163 JSON assets and local references validated on each.
- Fabric 26.1.2: development client world-load, Minotaur rendering, procedural look, and attack timeline checks passed.
- Quilt 26.1.2: the same scenario passed with the packaged Asterion jar.
- NeoForge 26.1.2: packaged-client scenario passed, including three custom fluids and 26 fluid states.

- Fabric 1.20.1 and 1.21.1: packaged-client gameplay scenarios passed.
- NeoForge 1.21.1: packaged-client gameplay scenario passed; embedded Amnetic and Assimp loaded without Connector.
- Quilt 1.20.1 and 1.21.1: development-client gameplay scenarios passed.

Each older-client scenario covers dimension travel, rendering, TAA disabled,
OpenGL error checks, physics, forging, crafting, saved items, advancements,
miniboss combat, and cutscene recovery. Five final `ASTERION_SMOKE PASSED`
markers and a successful Gradle completion were recorded in
`ports/1.5/runtime-verification.log` (local, ignored by Git).

- Forge 1.20.1, 1.21.1, and 26.1.2, plus NeoForge 1.20.1: development clients initialized Asterion, loaded resources/texture atlases, and initialized rendering without the earlier startup crashes. These non-automated clients were terminated after startup verification; the resulting Gradle run-task termination is not counted as a gameplay-test pass.

Build/package passes are not exhaustive playthroughs. Those four startup-only
targets have no complete automated client gameplay scenario recorded by this run.
Third-party modpacks, shader packs, and multiplayer sessions are outside this validation.

## Reproduce

From the repository root, with JDK 21 and JDK 25 installed and Python 3.11+:

```powershell
powershell -ExecutionPolicy Bypass -File tools/build-all-mods.ps1 -Release 1.5
python tools/verify-v15-release.py
```

From `ports/1.5`:

```powershell
.\gradlew.bat :1.20.1:runProductionSmokeClient :1.21.1:runProductionSmokeClient :neoforge:runPackagedSmokeClient :1.20.1:runQuiltSmokeClient :1.21.1:runQuiltSmokeClient
```

From `ports/1.5/ports/26.1.2`:

```powershell
.\gradlew.bat :26.1.2-fabric:runPolishSmokeClient :26.1.2-quilt:runPolishSmokeClient :26.1.2-neoforge:runPackagedPolishClient
```
