# Asterion 1.5

Changes and improvements since 1.2.2.

## Minecraft and loader support

- Added Minecraft 26.1.2 support alongside 1.20.1 and 1.21.1.
- Expanded the release to Fabric, Quilt, Forge, and NeoForge builds for all three Minecraft versions.
- Added native Forge ports, with fixes for content registration, networking, particles, rendering, and client startup.
- Integrated Amnetic into every release jar for lighting and rendering. It no longer needs a separate installation.
- Removed the Connector requirement from the native NeoForge builds and embedded the required Forgified Fabric API where applicable.
- Fixed recipe synchronization, resource loading, fluid registration, and loader-specific startup issues.

## Visuals, atmosphere, and performance

- Reworked lighting and emissive rendering around Amnetic, including glowing models, held-item lights, and environmental effects.
- Improved glow occlusion so emissive models respect scene depth.
- Restored and polished Labyrinth skies, fog, haze, particles, and biome atmosphere across supported versions.
- Improved portal transitions, boss entrance and finale presentation, camera effects, and recovery after cinematics.
- Added atmosphere controls and improved the in-game settings interface.
- Reduced unnecessary particle, vine, and off-camera glow work, and improved lighting and texture-cache cleanup when leaving worlds.
- Fixed rendering-state issues, first-person item effects, and the flamethrower's custom model on older Minecraft versions.
- Updated music labels and sound resources.

## Combat, creatures, and physics

- Improved Minotaur combat, attack timing, procedural poses, animation blending, and weapon presentation.
- Added and refined physical chains, chain climbing, and weapon-chain attachments.
- Improved ragdolls, dismemberment, debris collisions, and the presentation of dropped creature remains.
- Improved ragdoll recovery without requiring crouching.
- Improved centipede movement, surface detection, body constraints, and interaction behavior.
- Improved boss attack telegraphs and arena encounter presentation.

## Labyrinth, exploration, and progression

- Improved Queen Beetle tree placement and its candidate search.
- Restored and refined forging presentation, forged item parts, tooltips, and cross-loader interfaces.
- Improved catacomb generation and repairs, including arena entrances and the boundary between the central arena and surrounding maze.
- Fixed unwanted water and waterlogged blocks in the arena, and protected Labyrinth levers from water-flow removal.
- Improved generated chain placement, maze wall-height consistency, and scenery near the arena.
- Renamed the dimension to `asterion:labyrinth` and added migration handling for saves using the previous dimension ID.
- Updated dimension commands and advancement references for the renamed Labyrinth.
