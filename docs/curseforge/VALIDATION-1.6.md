# Asterion 1.6 validation

## Release scope

Minecraft 1.20.1, 1.21.1 and 26.1.2, each on Fabric, Quilt, Forge and NeoForge. The two older Fabric/Quilt pairs are byte-identical: 12 variants require 10 uploads. The separate future 2.0 source tree and sibling Amnetic checkout were left unchanged.

## Reproductions and fixes

- Reproduced the original 1.5.9 Forge 1.20.1 point-light crash with a reentrant visibility hook. The shared 1.20.1/1.21.1 renderer now snapshots the access-ordered light map and synchronizes related updates and cleanup. Production tests exercise 128 refresh/clear cycles and check visibility, disposal and LRU behavior. Light budgets and visual settings are unchanged.
- Reproduced Fantasy Ending's exclusive boss-name redirect conflict using the supplied mod/version combination. Replaced Asterion's redirect with a composable component-expression hook, keeping its custom Minotaur bar. Applied the equivalent change to the 26.1.2 renderer.
- Updated release-port packaging guards to remain active at version 1.6. Audited embedded startup repairs and exclusion of unfinished dimensions.
- Connected vanilla and Forge explosion callbacks to Labyrinth terrain restoration. Renewal waits five seconds, processes at most 64 queued entries per tick, waits for loaded chunks and living occupants, and preserves tracked player replacements. Containers, fluid-bearing blocks, TNT, skeletons and lift anchors retain their normal behavior. Renewable terrain does not produce item drops.
- Reproduced a repair stall with a stationary dropped item using the earlier 1.6 candidate. Changed the obstruction check to living entities, preserving player/mob protection while allowing loose items to be pushed out normally.
- Reduced tagged gun damage to Minotaur boss fights and Cursed Braziers by 50%. The optional TaCZ bullet tag covers its four bullet damage types; packs can extend `asterion:gun_damage`. Other damage types keep their existing handling.
- Changed Cursed Brazier base health from 140 to 280. Old entities still using base 140 migrate at the same health percentage; different custom base values are preserved.
- Added optional Xaero HUD-bound detection to move the objective panel below an overlapping minimap. Only reflection metadata is retained; no world/session is cached and no Xaero dependency or setting override is added.

## Verified builds and tests

- All 12 final release JARs passed `ports/1.5/gradle/verify-release.py`: metadata, Java compatibility, mixin presence, embedded renderer repairs, new fixes and existing release safeguards. Final checksums are in the generated `ports/1.5/build/release-1.6-manifest.json`.
- Minecraft 1.20.1 and 1.21.1 server suites: 16 required tests each, 32 passed. The lightweight GameTest server intentionally omits the custom dimension; explosion tests run in full integrated-server worlds instead.
- Final Forge 47.4.28 / Minecraft 1.20.1 packaged client: PASS. Terrain restoration with a hovering dropped item, player-placement/replacement behavior, Brazier health and saved injuries, all four TaCZ damage tags, unchanged magic damage, point-light stress, magic block renderers, TaCZ draw/fire, Iron's Firebolt, resource-pack reload, player ragdoll/recovery and shader dimension switching.
- Final NeoForge 26.1.2 packaged client: PASS. Terrain renewal with a hovering item, player block preservation, health 280, empty optional gun tag without a gun mod, cache/GPU cleanup, Minotaur rendering, procedural timelines and ragdoll recovery.
- Final Fabric 1.21.1 packaged client: PASS. Terrain renewal with a hovering item, player-block preservation and all 128 reentrant point-light cycles.

Full-client tests use 8 GiB maximum heap, VSync disabled and Minecraft's unlimited FPS setting. QA fixtures and profiler/test mods are excluded from published JARs.

## Representative modpack

Forge 47.4.28; Fantasy Ending 2.7.20h; EndingLibrary 2.2.2; Blood Magic 3.3.8-50; Iron's Spells 3.16.3 / Iron's Lib 2.1.0; TaCZ 1.1.8-hotfix2; Xaero Minimap 26.6.0 / World Map 1.47.0; Not Enough Animations 1.12.6; Repurposed Structures 7.1.25; Oculus 1.8.0 / Embeddium 0.3.31; Complementary Unbound r5.9.3; the report's Curios, GeckoLib, Player Animator and Caelus versions. A small QA resource pack verifies an actual reload and block-rendering resource override.

External shaders remain active in the Overworld, are suspended in the Labyrinth while Asterion renders its own appearance, and resume on return. Screenshots were inspected for the magic block renderers, gun model and HUD layout.

Xaero automatically followed the tested vertical floors without forcing its configuration:

| Floor | Test Y | Reported biome | Minimap cave mode | World-map layer |
| --- | --- | --- | --- | --- |
| Maze surface | 100 | minecraft:the_void | off | surface |
| Catacombs | 70 | asterion:catacombs | on | 4 |
| Forge | 20 | asterion:forge | on | 1 |
| Caves | -55 | asterion:shale_caves | on | -4 |

The Labyrinth's structure-set whitelist remained Asterion-only with Repurposed Structures installed. Coverage is limited to the named mod versions and configurations; no global entity/rendering bans or hard dependencies were introduced.

## Local evidence

- `build/hotfix-point-lights-baseline.log`: original concurrent-modification reproduction.
- `build/fantasy-baseline-startup.log`: original Fantasy Ending startup conflict.
- `build/release-1.6-final-legacy.log`: 32 server checks and release build.
- `build/release-1.6-final-item-repair-legacy.log` and `build/release-1.6-final-item-repair-modern.log`: final builds after the dropped-item repair fix.
- `build/compat-pack-1.6-item-baseline.log`: repair stall before the dropped-item fix.
- `build/compat-pack-1.6-release-final.log`: complete final Forge modpack PASS.
- `build/release-1.6-26-runtime-final.log`: final packaged NeoForge 26.1.2 PASS.
- `build/release-1.6-21-runtime-release-final.log`: final Fabric 1.21.1 runtime.
- `build/release-1.6-final-audit.log`: all 12 release audits.
- `build/compat-forge-game/screenshots/compat-*.png`: modpack and per-floor screenshots.

## CurseForge publication

Upload receipts will be recorded here after CurseForge accepts each transfer.
