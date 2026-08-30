# Underwater catacombs

The maze generator now builds a continuous catacomb layer at Y=3–16 beneath every newly generated maze chunk, including the central arena. Passage floors sit at Y=5 or 6, with a shared water surface at Y=7: the water is one or two blocks deep, with breathing room above it. Existing generated chunks and saves are not rebuilt or overwritten.

Each 32×32 tile contains a vanilla jigsaw assembly: a crossing, two guaranteed ossuary passages, and two weighted puzzle/parkour wings. Outer flooded galleries connect the tiles even when a crypt is locked. Placement is deterministic, clipped to the current chunk, and works at negative coordinates. Structure blocks can load all four NBT templates from `asterion:catacombs/`.

## Finding and exploring the layer

- Soul-lit ladder shafts appear approximately every 128 blocks where a selected chunk has a clear surface corridor, outside the central arena and reserved surface landmarks. The lined shaft reaches a flooded gallery. An entrance barrel supplies a reusable grappling hook and a water bucket.
- Sluice rooms have three raised levers. Match their settings to the three lamps: **on, off, on**. The waterlogged gate opens permanently. Cache rooms contain food, tools, and another hook.
- Ossuaries include doors, burial niches, and skulls. Parkour wings have stepping stones, a grapple anchor, and an elevated cache.
- Sealed water reservoirs feed pointed dripstone in the ceilings.

## Arena changes

The existing Minotaur arena has twelve recessed puddles over slick stone. Water movement preserves some momentum when a player brakes or changes direction; sneaking gives grip. Four raised routes lead to green fire braziers. Extinguish each with a water bucket or shovel from the upper platforms. Each extinguished brazier removes one quarter of the boss's extreme-phase regeneration; all four stop it. Existing brazier-dependent attack selection also uses these raised braziers.

Aim the hook at mazesteel blocks or chains within 32 blocks and use it to pull yourself up. Sneak to release. Movement uses normal collision; the hook cannot pull through a wall. A short fall-protection window covers dismounting. The recipe uses three iron ingots and two string. The inventory icon currently reuses Minecraft's fishing rod.

The boss fires additional small fireballs below 25% health and during its death sequence. The existing fire-ring attacks remain; the floor is not permanently covered with fire. Parkour platforms and anchors are protected from routine boss obstacle clearing. Arena rebuilding leaves the catacomb ceiling intact, and players below the arena are excluded from its entry checks.

## Development and testing

Requires Java 25. Run `gradlew rebuildCatacombTemplates` after editing `CatacombTemplateBuilder`, then `gradlew build`. The build includes `catacombRegression` (gallery connectivity, water depths, NBT bounds, connector contracts, reservoir seals, and underwater gate states) and the existing centipede regression suite.

A separate headless test world verified natural generation at positive and negative coordinates, generated crypt/cache contents, an uninterrupted entrance ladder with supplies, rejection of the wrong lever combination, successful gate operation in all four rotations, water extinguishing a brazier, and persistence of solved gates after a server restart.

For a quick check in a **new test world**, enter the Asterion dimension and teleport to `16 8 16`. The editable templates are `asterion:catacombs/crossing`, `ossuary`, `sluice`, and `parkour`. Rebuilding the NBT files does not update rooms already stored in a world. Multiplayer movement and combat balance still need an in-game playtest.
