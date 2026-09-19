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

