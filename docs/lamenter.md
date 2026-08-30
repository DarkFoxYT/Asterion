# Lamenter

`asterion:lamenter` uses the supplied `textures/block/lamenter.png` unchanged on its front, with ancient stone on the remaining faces. It is available in Asterion's creative tab and drops itself when mined.

Use an empty hand to toggle crying, or power the block with redstone. The face points toward the player on placement. Tears emerge from four separate points on the lower eyelids, two per eye; small, staggered droplets use ordinary world lighting with no emission or bloom.

Put a brazier in the column immediately in front of the face, up to six blocks below it. The generated arena places each face two blocks above and one block behind its brazier. The entire path must be clear. Eight continuous seconds (160 server ticks at normal tick speed) extinguish the first lit brazier in that column. Stopping the crying, obstructing the path, changing the target, or unloading the block resets the timer. Multiple Lamenters do not add their timers together. No water source blocks are created by tears.

Each remaining arena brazier contributes one quarter of the Minotaur's Greek fire damage. The final brazier going out cancels an attack already in progress and prevents a new fire attack. Existing water/shovel extinguishing still works.

New catacomb rooms contain inward-facing Lamenters. Existing arenas receive the four faces once on world startup, only where their positions are air; saved braziers, doors and pillar damage are preserved. New/reset arenas include the faces directly.

## Flood event integration

No flooding event controller was present in this checkout. `CatacombFloodState.setActive(serverLevel, true/false)` provides a persistent signal for that controller to call when flooding starts/ends. While it is active, Lamenters automatically weep in the Asterion catacomb layer and central boss chamber. Surface blocks and other dimensions are excluded. Their normal redstone/manual controls remain independent.

For testing, `/asterion catacombs flooding start` and `/asterion catacombs flooding stop` toggle that signal (operator permission required). These commands do **not** raise or drain water. Automatic flood timing and physical water movement are not added by this change.

## Verification

`gradlew runLamenterTest` creates an isolated disposable client world under `build/lamenter-gametest`. It checks empty-hand activation, all four facing directions, exact 160-tick extinction, redstone, interrupted/blocked streams, flood scope, generated arena placement, and the boss's active-brazier count. It also saves crying/extinguished screenshots. A generated test-only mod keeps this suite separate from the existing door tests and is never included in the release jar.

`gradlew build emissiveRegression modDistribution` runs the existing regression suites, checks the upgraded renderer, and produces the combined Asterion jar with Amnetic included.
