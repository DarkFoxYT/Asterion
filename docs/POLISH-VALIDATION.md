# Minotaur and rendering polish

The Minotaur's procedural bone edits now clear GeckoLib's change markers after rendering. Unanimated channels therefore reset correctly instead of accumulating the previous frame's offsets. Grab aim stops following the held player back into its own hand, and both entities use interpolated positions. Chain grappling explicitly retains its authored body/arm pose and only the chain attachment is rendered dynamically. A 120-frame regression verifies that procedural offsets leave the authored grapple rotations unchanged. Clip sampling wraps looping animations and holds non-looping animations below their endpoint.

Other changes restore red health fill, forge gradients, the green attack cue, model-based targeting, chain attachment transforms, held-player rendering, ragdoll use guards, Afterblow display contexts, and particle rotation. Worn armor increases Minotaur recovery/combo pressure. Maze caches use primitive coordinate keys. An exact-pixel animation cache has a 32 MiB cap and does not continually evict/reallocate when full.

## Validation

- Minecraft 1.20.1 Fabric production JAR: 6,000 real GeckoLib processor frames with alternating Minotaurs, bounded rotations/positions/scales; grab/release anchors; animated hitboxes; blood mask; gear scaling; server ragdoll item-use rejection.
- Minecraft 1.21.1 Fabric production JAR: same focused checks passed.
- Minecraft 1.21.1 NeoForge packaged JAR: focused animation checks passed; final full client suite passed for particles, dimension rendering, TAA disabled, physics, forging, crafting, advancements, miniboss awakening/combat/defeat, and cutscene control restoration.
- Both Fabric ports: all five required server game tests passed; 2,103 JSON resources and local references validated per version.
- Release audit: all eight installation JARs passed ZIP integrity, metadata, Java-version, nested-Amnetic, geometry/texture parity and absence-of-test/Veil checks.

The final NeoForge scenario measured 15.615 ms median and 31.5732 ms p95 across 395 frames on the local Radeon 610M. This is a scene measurement, not a controlled before/after performance claim. No visual quality settings or source textures were reduced.

Quilt uses the same Fabric artifacts; this revision's runtime tests used Fabric and NeoForge. External shader packs, long multiplayer sessions and NeoForge 1.20.1 remain outside this validation. These checks do not establish that every possible gameplay issue is eliminated.


## Ash-grey sky update (2026-09-20)

The port shaders lift distant open-air scattering to a soft ash-grey tone. Near geometry, dark caves and eclipse shading retain their existing treatment. The packaged NeoForge atmosphere regression passed all three quality settings: visible dust over sky and geometry, no blue cast, non-flat dust banks, TAA disabled and no OpenGL errors. The 1.20.1 and 1.21.1 builds share these shader resources.


## Vine, animation, audio and remains update (2026-09-20)

- Vine rendering now culls the complete model before GeckoLib and emissive submission, using a conservative three-block margin. Geometry, textures, bloom strength and shader quality are unchanged.
- A same-client ABBA scene with 1,875 vines measured 25.1302 ms median / 41.898 ms p95 without culling (232 frames), and 12.1795 ms median / 24.052 ms p95 with culling (426 frames), on the local Radeon 610M. These measurements describe this fixed dense-vine scene, not a general FPS guarantee.
- Negative animation sample times now delegate to GeckoLib's normal clock instead of freezing natural clips at frame zero. The packaged 1.20.1 and native 1.21.1 NeoForge clients passed fractional-clock, 6,000-frame procedural, and authored-grapple regressions.
- All five supplied axe clips are mono Vorbis. Draw and throw cues follow their actions, the two swings are balanced variants, and the flight loop follows the axe with distance attenuation and stops on rest/removal. The flight lifecycle regression passed in native NeoForge.
- Debris sounds are mono, attenuated over 16 blocks, quieter, and limited to three impacts per four-tick window independently of visual physics quality.
- Resting rubble wakes when unsupported; embedded rubble recovers from overlapping block updates; broken doors wait two ticks for block updates and are protected from ordinary fragment-budget eviction. Both physical door leaves moved in the runtime regression.
- Skeleton rendering preserves child traversal when hiding flesh. Runtime checks verify visible harvested bones, removed-arm exclusion from rendering/targeting, and continued visibility/targeting of the remaining regions.

The new focused physics/remains regressions passed on 1.20.1 Fabric and 1.21.1 native NeoForge. Quilt and third-party shader-pack combinations were not rerun for this update. This update does not assert that every possible gameplay issue is eliminated.
