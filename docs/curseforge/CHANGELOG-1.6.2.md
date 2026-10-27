# Asterion 1.6.2

- Added Minecraft 1.21.11 and 26.3 builds for Fabric, Quilt, Forge and NeoForge.
- Adapted the custom Amnetic renderer, lighting and item rendering for both versions.
- Kept the world image at full resolution with crisp sampling through the new versions' atmosphere shaders, while preserving smooth sun glow and fog.
- Embedded required mod libraries in every build and corrected the bundled renderer's startup notice.
- Updated world features, advancements and loot data for the newer Minecraft formats while keeping the existing Labyrinth gameplay and artwork.
- Eclypse now stays in the surface maze. Entering the Catacombs or Forge stops its effects, and returning to the surface rejoins an eclipse still in progress.
- Fixed Forge staircases sometimes losing their connection to the Catacombs. Existing entrances are repaired as their chunks load.
- Fixed half of the Minotaur entrance gate being overwritten when neighbouring chunks generate.
- Fixed ancient skeleton ragdolls staying upright on Minecraft 1.20.1 and 1.21.1.
- Ancient skeleton corpses now last up to two minutes and keep their armor and held items after the original entity disappears.
- Restored the missing armor layer on ancient skeletons in Minecraft 1.20.1 and 1.21.1.
- Fixed a shader-state error on Minecraft 26.1.2 that could break equipped ragdolls and hand rendering after Asterion's effects.
