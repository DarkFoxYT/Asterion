# Asterion 1.5.7

- Fixed the bundled Amnetic layer-visibility hook conflicting with Majrusz Library 7.0.8 on Forge 1.20.1. The hook now composes with other renderer modifications while retaining Asterion's layer-hiding effects.
- Restored the bundled renderer's Mixin registration on NeoForge 1.20.1 so repackaging no longer silently skips its rendering hooks.
- Fixed NeoForge respawn callbacks losing the original player, which could erase Queen Beetle quest progress and skip Labyrinth death recovery. Fixed Forge 1.21.1 invoking that recovery before the replacement player is connected and positioned. These adapters now clear pending respawns on disconnect and server shutdown.
- Fixed a missing Forge 1.21.1/26.1.2 player-tracking adapter that could crash ragdoll recovery or entity synchronization. Tracking state now clears when entities leave, players disconnect or the server stops.
- Removed Sickened Twinblades, Rune Zone Gate, Charged Respawn Rune and Respawn Altar from gameplay. Hidden legacy IDs keep existing saves loadable; old gates and altars no longer obstruct, light or tick in the world.
- Removed the Twinblades combo/modifier system, eliminating its duplicate-attribute crash and per-tick updates. Afterblow guarding and stored damage are retained.
- Replaced the obelisk's approximate inventory model with an export of its actual in-game geometry and texture, scaled for the GUI. The placed obelisk's model and effects are unchanged.
- Updated Minecraft 26.1.2 creative-tab creation and NeoForge heavy-water tinting to their current APIs, removing deprecated compatibility calls and the fluid-to-block tint adapter.
