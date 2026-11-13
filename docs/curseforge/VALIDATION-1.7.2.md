# Asterion 1.7.2 — Embeddium checks

Tested on Windows with an NVIDIA RTX 5070 on 2026-11-11.

- Minecraft 1.20.1, Forge 47.4.28, Embeddium 0.3.31: the packaged release reached the title screen and passed client-mixin and entity-attribute checks.
- Minecraft 1.20.1, NeoForge 47.1.106, Embeddium 0.3.31: the packaged release passed the same startup checks.
- Minecraft 1.21.1, NeoForge 21.1.248, Embeddium 1.0.15: the packaged release passed startup, texture-hook exclusion, fullscreen/windowed transitions and framebuffer checks. A separate world run passed Overworld and Labyrinth entry, chunk rendering, ragdoll recovery and the OpenGL error check.
- Minecraft 1.21.1, NeoForge 21.1.248 without a renderer mod: startup passed and Asterion's vanilla texture-animation cache remained active.

The report supplied no Minecraft version, loader, crash log or rendering symptoms. These checks establish compatibility for these combinations, but cannot identify the reporter's specific failure. The texture-hook detection now checks class resources without requesting renderer-mixin bytecode transformation, and covers the older Sodium package used by Embeddium 0.3.31.

To run the NeoForge 1.21.1 regression from `ports/1.5`, supply the path to an official Embeddium 1.0.15 jar:

```powershell
./gradlew.bat :neoforge:runPackagedSmokeClient -PembeddiumSmoke '-PembeddiumJar=C:/path/to/embeddium-1.0.15+mc1.21.1.jar'
```

Add `-PstartupSmoke` to check startup and fullscreen transitions instead of the world scenario. The Embeddium profile uses its own test directory.

## Starcatcher and updated Amnetic — November 13

- Reproduced a StackOverflowError with Forge 47.4.28, Starcatcher 3.3.10, and its embedded NeoBackports 1.4.2 on Minecraft 1.20.1. NeoBackports dispatches vanilla block-entity load/save methods to modern hooks; Asterion's legacy bridge called back into the same dispatch. The bridge now uses Asterion-specific hook names.
- The same Labyrinth-entry scenario passed after the fix, including dimension rendering and world saving. It passed again with Amnetic built from upstream commit `50883bac26bd49a464105afa46ac6d1e09bda333` as `1.0.0-asterion.10`. Log: `build/starcatcher-amnetic172.log`.
- Selling Bin 1.4.3 also embeds NeoBackports (1.2.0). The bridge fix addresses the shared method collision; this is not a claim that every combination of mods has been tested.
- Renderer updates retain Asterion's TAA guard and upstream preset path protections. The upstream Minecraft 1.20.1 and 1.21.1 renderer builds passed; Windows symlink cases were skipped because the current account cannot create symlinks.
- With the updated renderer, the packaged NeoForge 1.21.1 release passed the Embeddium 1.0.15 world scenario again: Overworld and Labyrinth entry, chunk rendering, 720 combat hits, ragdoll recovery, and no OpenGL errors. This run used the AMD Radeon 610M. Log: `build/embeddium-amnetic172.log`.
- All 12 release archives for Minecraft 1.20.1, 1.21.1 and 26.1.2 passed `verify-release.py --version 1.7.2`; both legacy game-test suites passed 18 tests each.
- All 12 extra-port archives (1.21.11, 26.2, 26.3) passed the archive audit, and every loader/renderer mixin binding and camera-clearance check passed. The archive assertion now follows the shared native bloom prefilter used by current Amnetic instead of the removed legacy fallback adapter. Logs: `build/asterion172-extra-build-final4.log`, `build/asterion172-extra-archive-audit.log`.
- Maintained upstream compatibility patch: the hand-depth render-pass hook applies only on 26.3, and the redundant main-target redirect is removed. Asterion uses the native prefilter draw decision without changing its threshold uniform and respects 26.3's native layer-buffer ownership.
- Packaged Fabric 1.21.1 passed the integrated-renderer and gameplay/polish scenario. The flamethrower fixture now observes its short cooldown on server ticks, eliminating a client-frame timing race while still testing the real network packet. Log: `build/fabric21-amnetic172-recheck.log`.
- Packaged NeoForge 26.1.2 passed world entry, Minotaur rendering, explosion restoration, ragdoll physics and recovery. Log: `build/neo261-amnetic172.log`.
- Packaged Quilt and NeoForge 26.3 passed the world/rendering scenarios, including native point lights and framebuffer sharpness. Quilt was retested with a fresh transform cache after excluding Fabric's duplicate Mixin library from the test launcher; no cache workaround is required. Log: `build/modern263-amnetic172-final.log`.
- Packaged Forge 26.2 passed its world/rendering scenario with the updated hand-depth compatibility patch. Log: `build/forge262-amnetic172.log`.
- Release relations are derived from packaged integrations and matching published Minecraft/loader versions. Amnetic remains bundled; no unrelated project is linked in its place. ReplayMod and Flashback are linked only on Modrinth because no official CurseForge project was verified.
