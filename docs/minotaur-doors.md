# Minotaur doors

The supplied model is a single placeable double door occupying a **7-block-wide, 5-block-high** opening. Place its item at the bottom center with seven solid floor blocks underneath. It faces the player and opens away from them.

- Door: `asterion:minotaur_door` (also in the creative tab).
- Key: `asterion:minotaur_key`, crafted shapelessly from a tripwire hook and a gold ingot, or found in maze supply barrels.
- The key is reusable. Once unlocked, either leaf can be clicked to open or close both leaves without holding the key. Unlock state survives saving.
- Closing checks for occupants before moving and before restoring collision. Breaking one part removes the complete door.
- Door recipe: six iron ingots across the top and bottom rows, with ancient stone / dark oak door / ancient stone in the middle row.

## Boss entry

The complete arena, roof, pillars, furnishings and one descending player entrance and an enclosed boss staging room are built during world loading, before players join. Existing unfinished arenas migrate once to the new layout; subsequent loads preserve the saved chamber, repair missing doors and reopen encounter gates. Completed boss arenas are left alone.

Starting the fight requires crossing an entrance opened with the key. Dropping into the pit, approaching the center with a roaming Minotaur, or opening a gate using creative mode alone does not start it. Generated arena gates cannot be mined in survival. The south door is the only player entrance; the north door is reserved for the Minotaur. His room has a solid back wall, side walls, floor and ceiling, with no route out into the maze. The former east/west doors, gates and corridors are filled in when unfinished saves migrate. Three small jolts precede a kick at 3.5 seconds; the leaves detach and the boss strides inside. The lintel clears enough space for the configured boss height. Additional arriving players do not restart this sequence.

Pillars sit between the entrances, with their entire bases outside the entry lanes. The brazier jump routes and small decorations also leave these lanes clear. Doors render across the full chamber so the opposite gate remains visible.

Both doorways have a seven-block-wide mazesteel gate four blocks toward the arena. Starting the encounter moves the entrant and nearby party onto checked floor positions inside the gates, then closes the entry double doors behind them. The gates lower from top to bottom with chain and impact sounds. The boss's gate waits until the Minotaur has passed through before closing. Nearby winches cannot override these encounter gates.

A six-second camera shot frames the opposite door's rattling and kick, then returns to the player's previous camera mode. Movement, jumping, attacks and item use are blocked during the intro, with server-side movement rejection and temporary protection from damage. The shared client movement lock also covers the portal and boss-finale cinematics. Disabling cinematics skips the camera shot but retains the intro's safety lock. Players arriving later through the south entrance are moved inside without restarting the sequence.

Once the boss is inside and its gate is closed, the broken double doors rebuild with dust. Gates reopen and missing doors are restored on a wipe, victory, abandoned encounter or world reload. Reset, disconnect and dimension-change cleanup restore the player's prior invulnerability and gravity flags rather than leaving cinematic protection active.

Normal opening and closing emit animated brown smoke dust from the supplied `particleemitter1` and `particleemitter2` positions. Detached leaves use the supplied `minotaur_door_debirs` model and debris texture. The kick launches both leaves upward and into the arena with different spins. Swept rigid-body contacts use box inertia, restitution and friction; leaves tumble and slide naturally instead of being rotated toward a predetermined flat pose. Boss/explosion impulses can wake and throw them again.

Debris uses the existing **client-side visual physics system**: it collides with world blocks, but does not become a persistent block or a server entity that obstructs players. Leaves last about 45–55 seconds. Sleeping leaves recheck support twice per second, and a client keeps at most twelve door leaves.

## Verification

With Java 25, run:

```powershell
./gradlew.bat build emissiveRegression runDoorTest
```

`runDoorTest` creates a disposable world under `build/door-gametest`, leaving normal saves untouched. It checks readiness before player arrival, clear lanes for pillar counts 4–16, the sole player crossing, sealed side entrances and enclosed boss room, key bypass prevention, opposite-door boss entry, persistent arena bookkeeping, door placement/collision, physical flight, sleeping and explosion relaunch. It also verifies safe player placement, local and server movement locks, entry-door closure, delayed boss-gate closure, rebuilt doors, camera/control restoration and reset cleanup. Screenshots are written under `build/door-gametest/screenshots`.

## Smoke, impacts and arena reinforcements

The final door rattle releases rising smoke before the kick. A capped, short-lived emitter then fills the frame with expanding brown-grey billows as the leaves fly through. The center is thinner than the floor and edges, keeping the Minotaur's full-bright cyan eyes legible without rendering them through solid walls. Intro shake measures distance from the cinematic camera, and heavy door landings add brief, distance-scaled impacts. Sound and impact bursts are rate-limited.

During a fight, natural centipede spawning is blocked within the arena. Existing wild, unnamed and unridden centipedes are removed from the encounter area; named/ridden centipedes are preserved. Two beetles emerge shortly after the intro, then up to two more every 15 seconds, with at most four arena beetles present. Spawn locations avoid players and solid blocks. Encounter-created beetles are removed on reset or encounter cleanup.

Pillar/roof collapse, rubble throws and finale fragments mix authored debris models with spinning block-shaped chunks. Block chunks burst into chips on hard impact; they are client visuals and cannot place blocks or drop items. Other rubble tumbles, collides, settles and can be thrown again by an explosion. The boss burial phase uses a timed server state plus falling physics debris and smoke, rather than placing a solid pile around the boss or players. Attack damage remains server-controlled. Launches are batched once per tick (at most 96 per batch), culled by viewer distance, and bounded to 128 live client debris objects while preserving door leaves. No per-tick physics packets are sent.

Door breach smoke uses larger billows across a ten-block-wide cloud, extending into the arena in front of the doorway as well as around the leaves. Emission ends after roughly two seconds; the existing debris limits remain in effect.

## Overworld attack debugging

With cheats/operator permission, stand on open ground in the Overworld and run `/asterion minotaur debug`. This spawns a temporary boss nearby and starts automatic attack selection without requiring an arena, key or entrance cinematic. Creative mode is recommended for watching effects; use Survival to test damage and contact-dependent attacks.

- `/asterion minotaur attack <name>` forces one attack, then waits. Tab completion lists the supported attacks. Wait for the current attack to finish before forcing another.
- `/asterion minotaur pause` pauses the boss AI; `/asterion minotaur auto` resumes automatic combat.
- `/asterion minotaur status` reports the current state immediately.
- `/asterion minotaur stop` removes your test boss.

Chat reports the actual attack/state, attack tick, cooldown, health, rage, target, distance, line of sight and current AI decision. Changes are sampled at most twice per second, with a five-second heartbeat. The decision text describes the selection logic, rather than inventing dialogue. Messages go only to the player who started that test.

Debug attacks use local coordinates and skip arena terrain changes, persistent fire placement and the arena finale. The boss is not saved and is removed when its owner leaves, changes dimension or dies. Attacks can still damage entities. The integration test checks overworld spawning, forced rubble and laser attacks, block visuals, local coordinates, save exclusion and command cleanup.

## Grab and wall combo

Weapon combat now tracks free hands, axe, swords, and an axe left in the world. Switching equipment takes 20 ticks: the old weapon is stowed first, then the new one is drawn. Swords render on the back whenever they are not in the hands; the axe is absent from both hand and back while thrown. Cleave, axe slam and axe throw require the axe. Sword combos require drawn swords; chain grapple and unarmed attacks require free hands. Physical weapon telegraphs use dust instead of lightning. Existing explicitly magical attacks remain magical.

`/asterion minotaur attack axe_throw` throws the physical axe; `/asterion minotaur attack retrieve_axe` tests retrieval. Automatic combat retrieves it after a short interval. The boss must reach the axe before using axe attacks again; he can still draw swords while it is out. The axe's identity and last position persist across saves, and removing the boss cleans up its loaded axe. Debug chat reports weapon state, swap progress and whether the axe is out. The current weapons use scaled iron axe/sword item visuals attached to the supplied model.

Both attack lockouts and recovery scale down with rage, reaching 46% of their unscaled duration at rage 12, with minimum recovery limits. Wall shove detects a single nearby wall, uses collision-checked pushing to pin the player briefly, and never destroys its supporting wall.

Horn ram takes priority over grabs when its cooldown is ready and a target is within six blocks: either at least 12 damage was taken from nearby players in a two-second window, or two visible survival players surround the boss from directions at least 90 degrees apart. Successful unblocked hits deal 7 base damage and guarantee a ragdoll, with 7–10 blocks of controlled knockback on clear ground. Terrain can stop it early. Horn impacts do not add the grab throw's wall damage or schedule follow-up combos. Test with `/asterion minotaur attack horn_ram`.

Close players are priority grab targets when the grab cooldown is ready, especially players about two blocks above the floor. The catch has an eight-tick contact window and requires line of sight. Once caught, movement is locked until release; the visible player attaches to a grip locator on the selected animated hand bone. A missed catch releases the attack normally.

The throw deals 10 base damage and launches the player roughly 50–80 blocks over clear level ground. Server-side swept collision stops the flight at terrain, with another 10 base damage (five unarmored hearts) on wall impact. Damage still respects armor. After a throw, the boss prioritizes charge when the lane is clear and its cooldown is ready; chain grapple is the fallback. He can then pin a victim who remains near the wall. Normal medium/long-range combat also favors charge over chain grapple, while preserving cooldowns and attack variety. Debug mode supports this combo too. `/asterion minotaur attack grab` forces the initial attack; stand close enough to be caught.
