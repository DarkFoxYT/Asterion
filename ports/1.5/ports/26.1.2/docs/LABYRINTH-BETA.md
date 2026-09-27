# Labyrinth beta — Minecraft 26.1.2

This build focuses on the Labyrinth: enter the Asterion dimension, explore the maze and its deeper areas, collect runes and keys, and confront the Minotaur. The Underworld chapter's code and assets are present for development, but first death does not send players to Limbo. An explicit `-Dasterion.enableUnderworld=true` JVM option enables that unfinished passage for development testing.

## Install

Use the jar matching your Minecraft 26.1.2 loader: Fabric, Quilt, or NeoForge. Use Java 25 and a separate game profile. Fabric API and GeckoLib 5.5.2 are required on Fabric and Quilt. NeoForge needs its GeckoLib build; Forgified Fabric API is embedded in the jar. Amnetic is embedded in every mod jar, so do not add another copy. Use a new world for terrain feedback because existing chunks do not regenerate.

## Beta test route

1. Enter the Asterion dimension through the mod's gateway and explore the maze above and below ground.
2. Try the runes, doors, keys, loot, enemy encounters, and the Minotaur fight.
3. Save, exit, reload, and check that progress and placed blocks persist.
4. Die and respawn in each area; the Labyrinth experience should remain playable without an automatic Limbo passage.
5. Test with and without a shader pack and report visual or performance regressions.

When reporting a problem, include the loader, Java version, mod list, new or existing world, reproduction steps, and `latest.log` or a crash report. Keep a backup before using an existing world.
