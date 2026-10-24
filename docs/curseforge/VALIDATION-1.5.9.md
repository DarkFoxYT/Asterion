# 1.5.9 performance and cache validation

## Scope

Supported release ports: Minecraft 1.20.1, 1.21.1, and 26.1.2. Shared changes cover Fabric/Quilt/Forge/NeoForge where each code path is used. The future 2.0.0 source tree is separate and is not part of this release.

The patch changes lifetimes and avoids redundant rendering work. It does not reduce texture dimensions, shader passes, light/shadow quality, particle counts, view distance defaults, physics fidelity, or gameplay. Test worlds use explicit distances and input shields for repeatability; fixture settings are not shipped. Final performance runs disable VSync, select Minecraft's unlimited FPS setting, and disable adaptive quality reductions.

## Findings and ownership

| Area | Audit result / fix |
| --- | --- |
| Weapon/grab/chain render anchors | Strong world references persisted until another matching render. Add client-tick cleanup, preserving same-world entries for their existing validity period. |
| Weapon follow-through springs | Entity-local animation ages are not comparable across entities. Expire idle entries by shared world time and reset at world boundaries. |
| Ragdoll scratch collections | `clear()` released primary bodies but left scratch lists/maps retaining body/model/item data. Clear both sets of owned collections. |
| Amnetic particle context | Reused context retained the last world between render steps. Release its world in `finally`, after simulation consumers finish. |
| Amnetic layer captures | Color/depth `TextureTarget`s were outside the framebuffer registry and omitted by `LayerSystem.dispose()`. Destroy these targets, clear their map and reset redirection. |
| Older emission/post caches | Explicitly release frame copies, render targets, queued model references, render-type entries and the owned VAO on reload/disconnect/shutdown. |
| Older ground telegraphs | Render-only cleanup did not run after leaving a world. Add tick lifecycle cleanup. |
| Ordinary mob render hook | No attachment transform applies to non-player mobs; skip the redundant surrounding push/pop while retaining vanilla's normal render call. |
| Native interpolation copies | Existing aggregate 32 MiB and per-owner 8 MiB caps retained. Check pixel equality, idle eviction, idempotent disposal and reuse. No cache-budget reduction. |
| Other server/world caches | Checked shutdown clears/weak keys/bounds in world generation, generated chains, gas clouds, transient encounters and saved runtime state. Persistent gameplay state is not discarded. |
| Post targets / mesh / collision caches | Preserve active reusable data; retain idle/world/reload disposal and exact collision-query semantics. Do not flush caches per frame. |
| Background model conversion | Existing single daemon executor and model conversion cache retained. No additional background workers or forced gameplay GC. |
| Collision-query storage | Reuse up to the existing 512 cell lists after removing their old shape references. Terrain is re-queried every tick; returned lists stay independent. Synthetic allocation benchmark: 5,299 to 3,191 bytes/tick (about 40% less for this workload, not a whole-game FPS improvement). |
| Grapple renderer | Keep a weak world identity for tether reset; renderer lifetime cannot keep an old world alive. |
| 26.1.2 recipe search | Live heap path: current connection → `SessionSearchTrees.reloaders` → recipe callback → previous `ClientLevel`. Refresh recipe search once after a level change, using vanilla's `updateRecipes` API. 1.21.1 accepts registry access rather than a Level, so it does not use this retention path. |

## Profiling

Spark is a test-only mod downloaded from the official project on Modrinth for the exact Minecraft version. SHA-1 pins:

- 1.20.1: Spark 1.10.53 Fabric, `252de323db10d2b49ef74f8e7a6a5c72929be140`.
- 1.21.1: Spark 1.10.109 Fabric, `eff42cbeee6fe9a7f3c57031c23200256366dcd1`.
- 26.1.2: Spark 1.10.187 Fabric, `0940b4089affdfca37d6b6d0ea5ecae4dbd8fe98`.

Each controlled profile has warmup, 60 seconds in the overworld, transfer/warmup, then 60 seconds in the labyrinth. Commands: `spark profiler start --thread * --interval 10`, then `spark profiler stop --save-to-file`. Profiles stay local. Windows uses Spark's Java sampler; allocation/GC evidence comes from JFR `settings=profile`. See [Spark commands](https://spark.lucko.me/docs/Command-Usage).

Use `-PperformanceSmoke` with the production smoke tasks. Put the pinned Spark JARs in ignored `build/performance-fixtures/`. Old ports: `:1.20.1:runProductionSmokeClient` / `:1.21.1:runProductionSmokeClient` under `ports/1.5`; modern: `:26.1.2-fabric:runProductionPolishClient` under `ports/1.5/ports/26.1.2`. Set a matching Java toolchain and `ASTERION_PYTHON` as in normal builds.

Local reports include `.sparkprofile`, `asterion-performance.jfr`, and `build/performance-*.log`. `tools/ReadPerformanceJfr.java` summarizes JFR with an optional UTC start time; `tools/ReadSpark.java` reads profiles against the matching Spark JAR. `tools/VoxelCacheAllocationSmoke.java` runs the same allocation workload against baseline/fixed classpaths. Allocation weights are sampled allocations, not retained memory. Inclusive Spark method times overlap and must not be added together as CPU percentages.

## Results

All figures below are controlled stationary views in fresh test worlds, with the profiler running. They are not general gameplay or crowded-modpack FPS promises. No before/after FPS gain is claimed from runs with different caps/settings.

1.21.1, NVIDIA RTX 5070, 3440×1440, render/simulation distance 12, VSync off, unlimited FPS, full configured effect quality, 8 GiB heap:

| View | Mean FPS | Best one-second FPS | Median frame | 99th-percentile frame |
| --- | ---: | ---: | ---: | ---: |
| Overworld | 1,010 | 1,132 | 0.756 ms | 3.957 ms |
| Labyrinth | 377 | 415 | 2.133 ms | 18.302 ms |

The 1.21.1 run passed 3,000 attachment-cache cycles, 1,000 collision-cache terrain-change cycles, native texture-budget/pixel checks, repeated GPU target destruction/recreation, and three actual resource reloads. The old client world was collected: **0/1 retained after GC**. Heap after the reload/GC diagnostic was about 744 MiB; the 8 GiB value is a heap ceiling, not actual usage.

26.1.2 retention regression: before the recipe-search fix, **1/1 old client world retained**, with the live heap path recorded above. After the fix, **0/1 retained**; the fast production-client scenario and all cache/GPU checks passed.

26.1.2 Fabric, same GPU/resolution/distances/heap, VSync off, unlimited FPS, inactivity throttling disabled, full configured quality. The fixture asserts `throttle=NONE` throughout each measured section:

| View | Mean FPS | Best one-second FPS | Median frame | 99th-percentile frame |
| --- | ---: | ---: | ---: | ---: |
| Overworld | 693 | 723 | 1.382 ms | 2.760 ms |
| Labyrinth | 313 | 319 | 3.141 ms | 5.031 ms |

The final 26.1.2 run passed all cache tests, three reloads, recipe search after a language rebuild (184 matching results), and **0/1 old client worlds retained**. Earlier capped/small-window runs and a run without an inactivity assertion are not used for these FPS figures. Different versions use different fresh test worlds; the table is not a version-to-version speed comparison.

1.20.1 passed a separate uncapped 8 GiB production run with the same attachment/native/GPU checks and three reloads. That earlier fixture used a smaller window/distance, so its FPS is not compared with the full-resolution runs.

## Final matrix and regression checks

- All 12 1.5.9 distributions built and passed `ports/1.5/gradle/verify-release.py`: three Minecraft versions × Fabric, Quilt, Forge, NeoForge. The audit checks compatibility repairs, cache hooks, bytecode Java versions, metadata, preserved Afterblow, retired content, and absence of test fixtures/Spark in shipped files.
- 1.20.1 and 1.21.1 GameTests: **16/16 required passed each**.
- Native NeoForge 26.1.2 packaged client: cache/GL lifecycle checks, world load, Minotaur renderer, procedural timelines, finite ragdoll physics and recovery passed. The existing test profile also contains Iris/Sodium/Complementary; it logs a shader-pack `BIOME_SULFUR_CAVES` expression warning, which is separate from Asterion's cache changes.
- Native Forge 47.4.28 / 1.20.1 with Majrusz Library 7.0.8, Embeddium, Oculus and Complementary: startup/layer visibility, framebuffer restoration, shader enable → disable in maze → restore on exit, heavy-water appearance, 720 combat hits and Afterblow regression checks passed.
- No shader, texture, model, effect-quality or gameplay-configuration assets were modified by the patch. Full-resolution FPS figures above are Fabric production-client measurements; the other loaders received build/artifact checks and the specifically listed runtime tests.
- Local manifest: `ports/1.5/build/release-1.5.9-manifest.json`; audit log: `ports/1.5/build/release-1.5.9-audit.log`. Runtime logs: `build/performance-3440-21.log`, `build/performance-3440-26-final.log`, `build/retention-fixed-26.log`, `build/performance-neoforge-26-smoke.log`, `build/performance-forge-20-shader-smoke.log`.

No claim of zero lag or universal FPS is made: chunk generation, graphics hardware, other mods, server/network load, and profiler overhead remain relevant.

## CurseForge upload receipts

All ten files were received on 23–24 October 2026, covering all twelve distributions. The 1.20.1 and 1.21.1 Fabric/Quilt pairs have identical SHA-256 hashes and share one upload per Minecraft version, tagged for both loaders. Saved Minecraft, loader, Java and client/server tags were verified for every upload. Each saved changelog exactly matches `CHANGELOG-1.5.9.md`, including every fix in this patch.

All files are configured to publish automatically after approval. At final verification, nine were approved and the final NeoForge 1.21.1 file was processing.

| Minecraft | Loaders | File | Status at verification |
| --- | --- | --- | --- |
| 1.20.1 | Fabric / Quilt | [9302841](https://authors.curseforge.com/#/projects/1671346/files/9302841) | Approved |
| 1.20.1 | Forge | [9303338](https://authors.curseforge.com/#/projects/1671346/files/9303338) | Approved |
| 1.20.1 | NeoForge | [9303437](https://authors.curseforge.com/#/projects/1671346/files/9303437) | Approved |
| 1.21.1 | Fabric / Quilt | [9303084](https://authors.curseforge.com/#/projects/1671346/files/9303084) | Approved |
| 1.21.1 | Forge | [9303543](https://authors.curseforge.com/#/projects/1671346/files/9303543) | Approved |
| 1.21.1 | NeoForge | [9303678](https://authors.curseforge.com/#/projects/1671346/files/9303678) | Processing |
| 26.1.2 | Fabric | [9302109](https://authors.curseforge.com/#/projects/1671346/files/9302109) | Approved |
| 26.1.2 | Quilt | [9302247](https://authors.curseforge.com/#/projects/1671346/files/9302247) | Approved |
| 26.1.2 | Forge | [9302388](https://authors.curseforge.com/#/projects/1671346/files/9302388) | Approved |
| 26.1.2 | NeoForge | [9302596](https://authors.curseforge.com/#/projects/1671346/files/9302596) | Approved |

Machine-readable file IDs, compatibility coverage and local release hashes are saved in `ports/1.5/build/curseforge/publication-receipt-1.5.9.json`. Dashboard proof is saved in `build/curseforge-1.5.9-uploaded.png`.
