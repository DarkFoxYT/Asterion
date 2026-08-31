# Heavy Water

Heavy Water uses Minecraft's animated water textures with a blue-green tint (`#579FAD`). Outside a flood it uses normal water flow, immersion, swimming, extinguishing and bucket behavior. Find its bucket in the Asterion creative tab or use `/give @s asterion:heavy_water_bucket`.

Catacomb wet floors use Heavy Water. Existing floor water is converted when its chunk loads, including authored crypt rooms. This does not rebuild chunks, change surface water, or replace the sealed vanilla reservoirs which feed ceiling dripstone.

## Swimming fatigue

Survival swimmers accumulate exposure while immersed; shallow wading does not count. After 20 seconds, Mining Fatigue I and additional food exhaustion begin. At 40 seconds this becomes Mining Fatigue II and Slowness I; at 60 seconds, Mining Fatigue III and Slowness II. Exposure is capped at 60 seconds and recovers three times as fast when not swimming in Heavy Water. The short effect refreshes expire naturally and do not remove unrelated potion effects. Creative/spectator players are exempt; death/respawn clears exposure.

## Flooding and rarity

During a flood, a single world tide rises two blocks, using eight exact fill heights per block. These event layers remain flat and do not spread. Steps are at least two seconds apart; up to eight loaded chunks are reconciled per tick, and each pass finishes before the next height. Newly loaded chunks join the current tide. No chunks are force-loaded by the controller. Only water and clear air over an existing basin are changed; blocks, gates, puzzle props and ceiling reservoirs are preserved. When the tide recedes, normal flowing Heavy Water returns.

Natural eclipses require a random 2–4 hour quiet period; floods require 3–6 hours. These are world game-time hours at normal tick speed, not Minecraft day/night hours. The deadlines are persistent and apply before weighted event selection, so these events can occur later than their deadlines. Natural flooding also requires a living player in the catacombs. Boss encounters suppress events and make the tide recede.

Operator test commands bypass rarity:

- `/asterion catacombs flooding start`
- `/asterion catacombs flooding stop`
- `/asterionevent start flood`
- `/asterionevent start eclipse`
- `/asterionevent start rumble`

Rumbles release small visual physics fragments from actual wall faces on the surface, or the ceiling in catacombs. Players above the maze walls can see fragments from nearby solid surfaces, but there is no midair fallback. Rumbles neither remove maze blocks nor place rubble on the ground. Dust smoke accompanies the fragments. Only the beetle's unignited campfire-smoke gas is beige; its custom ignited flame sprites remain unchanged.

## Verification

`gradlew runHeavyWaterTest` runs a disposable client world with real fluid ticks and screenshots. It checks ordinary flow, all eight fixed heights, immersion/fatigue/recovery, cross-chunk tides, revisiting flooded chunks, a complete live rise/recede cycle, preservation of solid blocks, persisted rare-event deadlines, forced-event bypasses, and wall/roof emission points without a sky fallback. `gradlew catacombRegression` checks existing gallery and template contracts.
