# Asterion 1.5.2 validation — 2026-10-17

All 12 release jars built: Fabric, Quilt, Forge and NeoForge for Minecraft
1.20.1, 1.21.1 and 26.1.2. Outputs are in the repository `modbuilds` folder.

Passed:

- The complete `prepareCurseForgeRelease` task, including `buildAllVersions`
  and its separate Java 25 wrapper invocation for 26.1.2.
- Camera geometry regressions on 1.20.1, 1.21.1 and 26.1.2: a safe origin beside
  the closed door, diagonal camera-plane obstruction, 301 wall-safe return
  samples, and exact return to the player's eye.
- All 14 required server game tests on each older Minecraft version; asset
  validation checked 2,181 JSON resources and their local references per target.
- The 1.21.1 automated client smoke scenario: rendering, bloom, OpenGL checks,
  Minotaur weapons, physics/recovery, forging, crafting, advancements, miniboss,
  Brazier/roof cutscenes, and HUD/control recovery.
- All 12 packaged-jar checks: ZIP integrity, mod versions, Java bytecode levels,
  entrypoints and mixins, nested dependencies, guarded renderer preset storage,
  required gameplay classes, assets/audio and excluded unfinished dimensions.

The release manifest contains 10 unique upload entries. The 1.20.1 and 1.21.1
Fabric/Quilt pairs are byte-identical and share one entry with both loader tags.
The local upload preview also declares bundled dependencies as embedded libraries.
No files were uploaded.

These checks verify build/runtime behavior and packaging. They do not constitute
a complete visual playthrough of the Minotaur entrance or establish the reason
for CurseForge's existing manual review. CurseForge controls moderation status.
