# Backend optimization checks

The target is CPU/background overhead without changing shaders, meshes, draw distance,
particle counts, physics substeps, collision precision, or lighting settings.

## Changes

- Axe flight audio uses Minecraft's nearby entity-section query instead of scanning
  all loaded entities every client tick. The original 40-block distance check remains.
- Ragdoll terrain collision results are cached by exact AABB for one synchronous
  simulation tick, with a 512-query limit. Shape order and duplicate boxes remain
  unchanged. The cache is cleared before and after each tick, including exceptions.
- Web synchronization sends immutable geometry and each cut once per nearby viewer.
  State is discarded on disconnect, respawn, leaving the dimension, leaving the nearby
  set, and server shutdown. Client level tracking occurs before applying cut packets,
  so the first tick after joining cannot erase initial cut state.
- Web support checks avoid temporary bit sets and endpoint arrays. Generation-cache
  ownership and time lookup happen once per search rather than once per cell.

## Evidence and limits

Run `./gradlew.bat -I tools/backend-validation.gradle :26.1.2-fabric:checkBackendOptimizations`.
It checks exact bounds, ordered shapes, empty results, capacity overflow, terrain
changes after reset, new viewers, unsent cuts, and incremental cuts.

In a synthetic repeated-query workload, 256,000 queries require 12,800 terrain
collections (95% fewer). A stable web with three cuts requires four packets across
120 synchronization updates, compared with 480 previously. These figures are
workload counts, not measured CPU timings or gameplay FPS improvements. Moving
collision bounds may yield substantially fewer cache hits.

The available live recording captured menu frame limiting and contained no mod
execution samples. At the user's request, subsequent verification used code and
headless checks. The cause of approximately 100 FPS during normal Labyrinth
exploration remains unverified; the web changes primarily affect Limbo, and collision
caching helps when ragdolls are active. No claim is made that these changes alone
resolve the reported steady Labyrinth frame rate.

Fabric/Quilt builds, Forge compilation, Essential compatibility checks, spider motion,
support/IK, web physics, and web persistence checks are used for regression coverage.
The rebuilt runtime jar requires restarting the existing dev client to take effect.
