# Asterion 1.5.3 validation

The release covers Minecraft 1.20.1, 1.21.1, and 26.1.2 on Fabric, Quilt,
Forge, and NeoForge. Fabric/Quilt use the same artifact on the two legacy
Minecraft versions; CurseForge uploads should follow the deduplicated manifest.

## Build and archive checks

From `ports/1.5`, run `gradlew.bat prepareCurseForgeRelease`, then
`python gradle/verify-release.py`. The first command runs the release builds,
asset validation, and camera-clearance checks. The archive audit checks all
twelve files, release versions, Java levels, mixin classes, the server item-use
guard, composable window hooks, and server-safe Assimp modules. It also checks
that the 1.21.1 Fabric/Quilt renderer integration from 1.5.2 is retained.

## Runtime coverage

- NeoForge 1.21.1: packaged dedicated-server checks for boss grab authority,
  rejection of client movement during a grab, release, ragdoll recovery,
  respawn grace, dimension isolation, malformed poses, pose ordering, and
  protection of players, the Cursed Brazier, and vanilla bosses from client kills.
- NeoForge 26.1.2: the same dedicated-server checks against the packaged release.
- NeoForge 1.21.1 with Sodium 0.8.13: full client scenario covering dimension
  transfer, rendering, finite ragdoll physics and recovery, crafting, miniboss
  damage and attacks, and cutscenes.
- NeoForge 26.1.2 with Sodium 0.9.2: world load, Minotaur renderer and procedural
  timelines, finite ragdoll body states, and recovery in the Labyrinth dimension.
- Fabric 1.21.1 with Sodium 0.8.13: the full production client scenario using
  the integrated renderer, including boss attacks, crafting, and cutscenes.
- Fabric 26.1.2 with Sodium 0.9.2: the production client scenario, including the
  patched GLFW window hook, Minotaur animation, and ragdoll physics and recovery.
- Fabric 1.20.1 and 1.21.1: the required server game tests and resource checks
  included in `prepareCurseForgeRelease`.
- Pose relay regression: 819,200 accepted moving-body updates on Java 17, 21,
  and 25, plus stale sequences, signed counter wrap, reconnect, history limits,
  and idle entity-ID reuse. Pass marker: `RAGDOLL_RELAY PASS`.

See `NEOFORGE-STARTUP-CHECKS.txt` for commands and pass markers. Harnesses are
excluded from release JARs. A successful Gradle exit alone is insufficient:
dedicated-server runs can write crash reports while returning exit code zero.

## Performance scope

Sleeping bodies refresh every 20 ticks instead of every tick; moving bodies
retain a pose each tick. A pose pass visits the body pieces once. Server
recipients come from entity tracking, and pose history is bounded per sender.
The physics solver settings and boss combat timing are preserved.

The integrated Fabric 1.21.1/Sodium scenario recorded a 16.63 ms median and
21.70 ms p95 across 421 frames on the development machine. An earlier NeoForge
run with other builds active recorded a 16.83 ms median and 66.79 ms p95 across
335 frames. These are functional test measurements, not controlled before/after
FPS benchmarks. Archive checks
and the listed runtime tests do not establish compatibility with every modpack
or guarantee zero frame drops on arbitrary hardware.
