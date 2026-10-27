# Asterion 1.6.2 port handoff — 27 October 2026

Publication is on hold. The user requested no further game launches, then asked
to commit and push the current work for continued testing tomorrow. This is a
development checkpoint, not confirmation that all eight ports work in-game.

## Scope

Minecraft 1.21.11 and 26.3, each on Fabric, Quilt, Forge and NeoForge. The source
baseline is the 1.6.1 Labyrinth release at 17f4df94. Existing release targets stay
on 1.6.1. No 2.0 gameplay or content has been enabled.

## Confirmed checks

- All eight target sources compiled and production archives were assembled during
  this work. Latest data changes still need a complete final archive rebuild/audit.
- Asterion's mixin binding checks and camera geometry regressions passed across
  all eight targets. These inspect bytecode and geometry without starting a game.
- The four 1.21.11 production archives passed the metadata, nested dependencies,
  Java bytecode, excluded dimensions and texture checks. All 405 authored source
  textures were identical in every checked archive.
- The latest standalone 26.3 codec check passed 42 advancements, 144 loot table
  schemas, three biomes, one dimension type and 12 placed features. Advancement
  display icons are excluded from this schema test; dynamic references use
  synthetic holders, and static mod item/block references use vanilla stand-ins
  only in the test's in-memory JSON copy. This does not test live registration.
- Matching custom Amnetic inputs are recorded in libs/amnetic-build.json. Quilt
  0.31.0-beta.4's own metadata provides Fabric Loader API version 0.19.5, matching
  the declared loader requirement.

## Fixes made while validating

- Adapted 1.21.11 Java, GeckoLib, camera/FOV, lighting, fluid tint and loader APIs.
- Adapted 26.3 rendering submissions, input, recipes and world-feature codecs.
- Migrated 26.3 advancement conditions, loot predicates/functions and empty biome
  spawn settings. Drop IDs, quantities and chances are checked against the source.
- Used fresh recipe/feature instances instead of sharing values between registry
  entries. Feature codecs retain a stable codec identity per type.
- Corrected renderer and Asterion pack metadata for each target/loader.
- Corrected local renderer nesting and gave the remapped Fabric/Quilt outputs
  distinct Minecraft/loader filenames. Native event adapters are privately named.
- Updated the mixin checker to handle nested accessor packages and Mixin's
  compatible-overload selection (including generic bridges and loader overloads).
  Earlier aggregate logs contain false failures from the previous checker.

## Remaining verification

Run the complete final non-launch verification after this checkpoint:

```powershell
.\gradlew.bat -p ports/1.5/ports/1.6.2 verifyExtraPortsWithoutLaunching
```

It builds/audits all eight archives, checks Asterion and renderer hooks, runs
camera regressions and checks the 26.3 data codecs. Set ASTERION_PYTHON to a
working Python 3.11+ executable; use JDK 25 for Gradle.

The latest aggregate run began before the last loot migration and checker fixes;
its early failures are not a final result for the saved sources. A full green
aggregate rerun has not been established at this handoff.

All final packaged client/server, Labyrinth entry, shader/zoom, Afterblow,
ragdoll and modpack tests remain pending. Earlier intermediate 1.21.11 Fabric
and NeoForge world tests passed, but do not validate the final packaged builds.
The earlier Forge development smoke harness was not discovered by the loader;
a successful process exit from that run must not be counted as a world-test pass.

Do not start game tests or publish to CurseForge until the user authorizes those
actions again. Source commits/pushes are authorized. Local logs and generated
release JARs are deliberately excluded from Git.
