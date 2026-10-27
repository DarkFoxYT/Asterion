# Asterion 1.6.2 validation — 27 October 2026

## Release scope

Minecraft 1.20.1, 1.21.1, 26.1.2, 1.21.11 and 26.3 on Fabric, Quilt,
Forge and NeoForge: 20 variants. The user authorized resuming game launches and
CurseForge publication once validation passes, and requested all 1.6.1 changes
in 1.6.2. The gameplay baseline remains 1.6.1 at 17f4df94.

## Completed checks

All 20 final archives passed the combined release audit and recursive dependency
closure audit. Every required mod library is embedded; Minecraft, Java and the
selected loader remain the base runtime. The audit verifies nested declarations,
ZIP integrity, Java targets, GeckoLib, Amnetic, expression libraries and Assimp
bindings/natives, and rejects development/test code. Evidence:
`build/release-1.6.2-final-manifest-audit.log`,
`build/curseforge/release-1.6.2-dependencies.json`,
`build/curseforge/release-1.6.2-manifest.json`.

The twelve existing variants rebuilt with the retained 1.6.1 fixes checked at
version 1.6.2. Both older server suites passed 16 required tests each (32 total).
Evidence: `build/release-1.6.2-all-embedded-legacy.log`.

All eight new targets passed compilation, loader/mixin bindings, camera checks,
archive checks and live server/client floor and corpse regressions. Live checks
cover eclipse floor gates/late packets, 128 seeds and 81 districts for Forge
connections, chunk-clipped Minotaur gates, falling/equipped corpses surviving
owner removal and 30 seconds, two-minute cleanup, and zero detected GL errors.
The shader check additionally requires the real atmosphere scene target to exist
at exactly the main framebuffer's resolution.

| Targets | Final runtime evidence | Shader result |
| --- | --- | --- |
| Fabric and Quilt, both versions | `build/release-1.6.2-embedded-final-validation.log` | All four PASS, 854x480 |
| Forge, both versions | `build/release-1.6.2-forge-full-validation.log` | PASS, 2560x1529 and 854x480 |
| NeoForge 26.3 | `build/release-1.6.2-26.3-neoforge-sharpness-final.log` | PASS, 854x480 |
| NeoForge 1.21.11 | `build/release-1.6.2-neoforge-texture-validation.log` | PASS, 854x480 |

The first aggregate runtime log fails at its later NeoForge 1.21.11 task; its
four Fabric/Quilt targets passed. The final separate NeoForge run above resolves
that failure and reports server PASS, shader PASS, client PASS and BUILD SUCCESSFUL.
Forge runs use the matching development runtime and a separate test helper;
the other six runs load the actual release JAR. All eight production archives
are audited independently. Authored textures match byte-for-byte (405 per new
target); Fabric and final NeoForge 1.21.11 screenshots were visually inspected.

The 26.3 static codec check passed 42 advancements, 144 loot schemas, three
biomes, one dimension type and 12 placed features. It uses synthetic holders and
vanilla stand-ins for static mod references, excluding advancement icons; live
world tests additionally exercise real mod registration.

## Corrections found during final validation

- Fixed the 26.3 Minotaur renderer signature conversion to handle Windows line
  endings. It now uses the generated-source adapter.
- Fabric/Quilt now let Fabric API generate separate asset/data pack metadata.
  The format numbers differ (1.21.11: assets 75.0/data 94.1; 26.3: assets
  97.1/data 121.0). NeoForge uses its supported automatic version metadata.
  Forge's hidden client-pack wrapper supplies the asset version; its file range
  starts above both legacy codec thresholds and includes current server data.
- Removed deprecated supported_formats from the supplied 1.21.11 renderers.
  Comparing both input JARs to Git confirmed only pack.mcmeta changed; their
  rendering classes and artwork were preserved.
- The 26.3 production test initially crashed even without Asterion inside a
  native shader-compiler callback. Mojang's launcher metadata requires
  `-XX:StackShadowPages=32`; adding the official JVM arguments fixed the vanilla
  and Asterion launches. Final runs use normal JIT settings, not C1 limits or
  disabled FFM. No GPU/system preferences were changed. Earlier trials of a
  higher SDL OpenGL context did not solve it and were removed from the sources.
- Removed shaded LWJGL core classes from the staged 26.3 NeoForge renderer.
  They created a second SharedLibrary type and broke FreeType class loading.
  Assimp remains bundled. Forge uses vanilla TinyFD to avoid duplicate modules.
  The archive audit now rejects root LWJGL classes in native renderer JARs.
- Migrated 26.3 native biome-modifier minCount/maxCount to constant/uniform
  count providers, preserving spawn counts and weights. Live registry loading
  caught this gap after the standalone vanilla codec checks had passed.
- Kept the old Quilt polish harness opt-in so ordinary runs do not load unrelated
  test entrypoints. The port harness remains separate from release JARs.

- Added valid pack metadata to the separate Forge/NeoForge test helper. This
  fixes the ResourcePackInfo warning dialog shown in the user's screenshot.
  The helper never ships in release files.
- Replaced the bundled renderer's misleading standalone-install warning with
  an accurate embedded-renderer INFO message across all variants.
- The new atmosphere pipelines retain a full-resolution scene and use crisp
  scene/final-blit sampling, while volume, bloom and sun effects stay smooth.
- NeoForge 1.21.11 wraps GPU textures for validation. A native-only bridge unwraps
  them when resolving GL IDs, keeping GPU validation enabled. Without it the
  first atmosphere pass silently stopped. AfterLevel now flushes its queued
  post effects after the level render pass; the strict shader check passes.
- Forge 26.3's shipped Mixin supports JAVA_21 rather than JAVA_25. Its configs
  use JAVA_21 compatibility while the game still runs on Java 25.

## Reproduction

Use the full checksum-verified JetBrains JDK 25.0.3 under
`build/toolchains/jbrsdk-25/jbrsdk-25.0.3-windows-x64-b508.16` as JAVA_HOME and set
ASTERION_PYTHON to
`C:/Users/darkf/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe`.
Use `--max-workers=1 --no-parallel`. The final launches use normal JIT settings
and Mojang's required native-access, export and stack-shadow arguments.
Run `ports/1.5/gradle/prepare-release-1.6.2.py` after building both release trees.
A runtime pass requires all three server/client/shader markers and no smoke FAIL.

Upstream/development console warnings (optional graphics integrations, GeckoLib
refmaps, offline Realms and Windows performance counters) are not suppressed.
The loading dialog and misleading embedded renderer warning are fixed; this is
not a claim that every third-party development log is warning-free.

## Publication

The 20 variants group into 18 unique uploads: Fabric/Quilt share identical
archives on 1.20.1 and 1.21.1. All uploads use release status, client/server,
correct Minecraft/Java/loader tags, automatic publication after approval,
embedded GeckoLib relation, and the combined 1.6.2/1.6.1 changelog.
Receipts and staged SHA-256 values are recorded in
`build/curseforge/release-1.6.2-receipts.json`. Ten preliminary uploads were
archived and superseded by the final corrected artifacts.
At final verification, all 18 files were accepted: 11 approved and seven
processing/review. None remained uploading or rejected. Approved files are
public; the rest publish automatically after CurseForge approval. The filtered
author list showed exactly 18 active 1.6.2 files, confirming the ten preliminary
files remain archived. Both early Forge uploads were checked on public pages
and display the final merged changelog.

| Minecraft | Fabric | Quilt | Forge | NeoForge |
| --- | --- | --- | --- | --- |
| 1.20.1 | 9342176 | 9342176 | 9342202 | 9342208 |
| 1.21.1 | 9342250 | 9342250 | 9342253 | 9342255 |
| 26.1.2 | 9342285 | 9342286 | 9342287 | 9342358 |
| 1.21.11 | 9342359 | 9342374 | 9342063 | 9342380 |
| 26.3 | 9342360 | 9342376 | 9342059 | 9342378 |

Public file URLs use
`https://www.curseforge.com/minecraft/mc-mods/asterion/files/<file ID>`.
Final publication record: `build/curseforge/release-1.6.2-publication.json`.
Publication screenshot: `build/curseforge/release-1.6.2-upload-proof.jpg`.
