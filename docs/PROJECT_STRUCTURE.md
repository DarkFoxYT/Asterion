# Project structure

Java sources live in `src/main/java/net/krodark/asterion`.

| Package | Contents |
| --- | --- |
| Root | Mod registration, configuration, saved world state, and rune definitions |
| `block` | Blocks, block entities, and block items |
| `entity` | Mobs, movement, combat, and centipede simulation |
| `worldgen` | World generation, structures, biome layout, and arena placement |
| `game` | Progression, gameplay systems, and shared content |
| `event` | Dimension events and their scheduling |
| `item`, `forging`, `recipe` | Items, forging logic, and crafting recipes |
| `network` | Client/server packets and handlers |
| `client/cinematic` | Cutscenes, camera controls, and transitions |
| `client/hud` | Boss bars, objectives, overlays, and tooltips |
| `client/forge` | Forge screen, recipe panel, and item flights |
| `client/audio` | Music, ambience, and Minotaur door shake timing |
| `client/render`, `client/light`, `client/particle` | Rendering, lighting, and particles |
| `client/ragdoll` | Client physics and ragdoll presentation |
| `compat` | JEI, REI, and other integrations |
| `mixin` | Hooks into Minecraft and library code |
| `dev/verification` | Regression checks and in-game verification |

`Asterion` and `client/AsterionClient` remain the common and client entry points.

Resources stay under `src/main/resources`: `assets/asterion` contains models, textures,
sounds, shaders, and translations; `data/asterion` contains structures, recipes,
loot tables, and world-generation data. Resource identifiers have not changed.

## Build and working directories

Run `./gradlew.bat build --console=plain` on Windows to compile and run the configured
regression checks. Minecraft requires the project's configured Java toolchain.

- `docs`: authoring and verification guides.
- `tools`: asset conversion and development utilities.
- `libs`: local dependencies.
- `run`: development game files, saves, and configuration.
- `build`, `.gradle`: generated build output and caches.

Keep gameplay code in its feature package. Add client-only behavior under `client`
and update mixin configuration when moving a mixin class.
