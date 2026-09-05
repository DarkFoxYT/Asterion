# Asterion sound asset checklist

This is the production list for the current mod. It separates sounds already present from original sounds still needed. A “variant” is a separate recording selected randomly by one sound event; it is not a separate gameplay mechanic.

## Priority legend

- **P0** — essential combat or interaction feedback; make these first.
- **P1** — identity and polish; strongly recommended before a public build.
- **P2** — ambience and extra variation; useful after the core mix works.

## Delivery rules

- Export Minecraft audio as OGG Vorbis.
- Use mono for positional SFX. Use stereo only for music, non-positional UI stingers, and broad ambience.
- Prefer 48 kHz / 16- or 24-bit source masters; keep the uncompressed masters outside the game assets.
- Leave combat SFX short and dry. The game environment can supply space; baked-in long reverb makes rapid attacks muddy.
- Make loops sample-accurate with no silence at their seam.
- Suggested loudness: UI around -18 LUFS, ordinary world SFX around -16 LUFS, boss impacts around -14 LUFS, and music around -18 to -16 LUFS. Avoid clipping; keep true peak at or below -1 dBTP.
- Name files in lowercase snake case with no spaces, for example `minotaur/axe_cleave_01.ogg`.
- Positional one-shots should normally be 0.1–2.5 seconds. Loops should normally be 4–20 seconds.
- Each frequent sound needs variants. Footsteps and weapon swings become repetitive very quickly with only one file.

## What already exists

### Wired custom gameplay sounds

| Event | Current file | Status |
|---|---|---|
| `asterion:minotaur_roar` | `sounds/roar_minator.ogg` | Works, but the filename should eventually be normalized and 2–3 roar variants would help. |
| `asterion:minotaur_step` | `sounds/minotaur_step.ogg` | Registered and defined, but not currently called; the boss still uses pitched vanilla Ravager steps. |
| `asterion:minotaur_door_openclose` | `sounds/minotaur_door_openclose.ogg` | Used by the entrance cinematic; ordinary door movement still uses vanilla sounds, and opening/closing share one file. |
| `asterion:metal_hit_sound` | `sounds/metal_hit_sound.ogg` | Works as a general metal impact; needs specialized light/heavy variants. |
| `asterion:cave_ambience` | `sounds/ambiance/cave_ambiance.ogg` | Wired as a streamed ambience event. The file is roughly 46 MB and should be optimized. |
| `asterion:maze_ambience` | `sounds/ambiance/maze_amb.ogg` | Wired as a streamed ambience event. The file is roughly 43 MB and should be optimized. |

### Music files present

- Ancient: `the best piece of music ive ever made - keys.ogg`
- Overgrowth: `Where the Stars Fall - CryPika.ogg`; `mojang, please hire me - joabi.ogg`
- Crimson Marshlands: `ill see you, at the edge of the world - keys.ogg`; `if we could roll back the credits, one last time - keys.ogg`
- Forge: `the withered remnant - MONGOPSY.ogg`; `the green hour. - MONGOPSY.ogg`
- Arena: `i made a dark souls boss theme - keys.ogg`; `ATONEMENT - MONGOPSY.ogg`

The code expects `assets/asterion/music_tracks.json`, but that playlist is currently absent. Until it is added, the files being in the folder alone does not make the biome music system load them. The two Crimson tracks are also currently selected as the ordered victory music.

## P0 — essential gameplay set

### Asterion / Minotaur boss

| Proposed event | Variants | What it should communicate |
|---|---:|---|
| `minotaur.aggro` | 2 | The encounter has truly started; shorter than the full roar. |
| `minotaur.roar` | 3 | Full chesty roar with breath and room tail; replace the single reused take. |
| `minotaur.hurt_light` | 4 | Armored, restrained reaction to ordinary damage. |
| `minotaur.hurt_exposed` | 3 | Fleshier, louder reaction while vulnerable. |
| `minotaur.stagger` | 2 | Clear opening for the player, distinct from normal hurt. |
| `minotaur.death_start` | 1 | Final vocal synced to the death animation. |
| `minotaur.death_body_fall` | 2 layers | Huge body impact: body weight plus arena stone. |
| `minotaur.revive` | 1 | Reverse-breath/stone-energy rise for revival. |
| `minotaur.step_walk` | 5 | Heavy but controlled walking steps. |
| `minotaur.step_run` | 5 | Faster, harder steps with armor rattle. |
| `minotaur.charge_start` | 2 | Hoof scrape, inhale, and low warning growl. |
| `minotaur.charge_loop` | 1 loop | Rapid hooves/armor during the charge; must stop cleanly. |
| `minotaur.charge_hit_player` | 2 | Powerful body impact that matches knockback/ragdoll. |
| `minotaur.charge_hit_wall` | 3 | Stone fracture plus horn/body impact. |
| `minotaur.land_light` | 2 | Regular leap landing. |
| `minotaur.land_slam` | 3 layers | Boss slam: body, bass impact, and debris. |
| `minotaur.fist_swing` | 4 | Fast cloth/air movement, not a sword sound. |
| `minotaur.fist_hit` | 4 | Heavy blunt hit; player ragdoll cue. |
| `minotaur.horn_ram` | 3 | Horn/armor strike with a short low-end punch. |
| `minotaur.back_kick` | 3 | Hoof swing and hard contact. |
| `minotaur.sword_draw` | 3 layers | Scabbard scrape, grip, and metal ring. |
| `minotaur.sword_sheathe` | 2 | Controlled metal slide and seat. |
| `minotaur.sword_swing` | 5 | Broad blade whooshes for both combo hit frames. |
| `minotaur.sword_hit_player` | 4 | Blade/armor/flesh combination. |
| `minotaur.sword_hit_world` | 4 | Blade scraping or striking stone. |
| `minotaur.axe_draw` | 2 | Heavy leather/metal release from his back. |
| `minotaur.axe_swing` | 5 | Slower and heavier than the swords. |
| `minotaur.axe_impact` | 4 | Crushing metal and stone impact. |
| `minotaur.axe_throw` | 2 | Release plus rotating air pass. |
| `minotaur.axe_flight_loop` | 1 loop | Subtle rotating whistle; avoid making it tiring. |
| `minotaur.axe_catch` | 2 | Hard metal catch with gauntlet movement. |
| `minotaur.chain_launch` | 3 | Chain uncoiling and hook launch. |
| `minotaur.chain_latch` | 3 | Separate player and stone/metal latch variants if possible. |
| `minotaur.chain_pull` | 2 loops | Tension, links, and dragging; synced to the grapple. |
| `minotaur.grab_player` | 3 | Armor/cloth grab without an exaggerated gore sound. |
| `minotaur.throw_player` | 3 | Release whoosh plus a separate landing sound. |
| `minotaur.rubble_pickup` | 3 | Stone tearing loose. |
| `minotaur.rubble_throw` | 3 | Heavy projectile release. |
| `minotaur.rubble_impact` | 5 | Rock break with strong danger feedback. |
| `minotaur.smoke_belch` | 2 | Deep exhale with ember/fire texture. |
| `minotaur.rage_rise` | 1 | Escalating phase-change cue. |

### Arena destruction and finale

| Proposed event | Variants | Use |
|---|---:|---|
| `arena.pillar_crack` | 4 | Small cracks as each pillar takes damage. |
| `arena.pillar_break` | 3 | Full pillar failure and first falling debris. |
| `arena.roof_warning` | 1 | Deep structural groan before the collapse. |
| `arena.roof_crack_near` | 5 | Directional cracks that travel across the roof. |
| `arena.roof_debris_small` | 6 | Frequent small rubble impacts. |
| `arena.roof_debris_large` | 4 | Rare dangerous chunks; much heavier low end. |
| `arena.roof_collapse` | 3 layers | Long collapse bed, central impact, and dust wave. |
| `arena.minotaur_crushed` | 1 | Final impact synced to the cinematic shot. |
| `arena.gate_lock` | 2 | Encounter gate slams shut. |
| `arena.gate_unlock` | 2 | Chains release and gate begins moving. |
| `arena.omega_lock_vanish` | 1 | Magical/metal removal at cutscene start. |
| `arena.omega_lock_return` | 1 | Reassembly at cutscene end. |
| `arena.victory_stinger` | 1 | Short transition before victory music. |

### Cursed Brazier miniboss

| Proposed event | Variants | Use |
|---|---:|---|
| `cursed_brazier.awaken` | 1 | Ignition plus hostile magical rise. |
| `cursed_brazier.idle_loop` | 1 loop | Small close fire/mechanical bed. |
| `cursed_brazier.anger_step` | 3 | Short escalation layers as damage makes it more aggressive. |
| `cursed_brazier.shield_charge` | 1 | Telegraph for the damage-blocking shell. |
| `cursed_brazier.shield_loop` | 1 loop | Quiet energized shield tone. |
| `cursed_brazier.shield_block` | 4 | Unmistakable rejected-hit sound. |
| `cursed_brazier.shield_break` | 2 | Reward cue when the player solves the shield condition. |
| `cursed_brazier.vulnerable` | 1 | Short opening cue. |
| `cursed_brazier.floor_jet_warn` | 2 | Ground warning before Greek-fire jets. |
| `cursed_brazier.floor_jet_burst` | 4 | Each jet ignition. |
| `cursed_brazier.beam_charge` | 2 | Aimed beam telegraph. |
| `cursed_brazier.beam_loop` | 1 loop | Sustained Greek-fire beam. |
| `cursed_brazier.spin_start` | 1 | Announces the feet-level spinning fire attack. |
| `cursed_brazier.spin_loop` | 1 loop | Rotating flame with audible motion. |
| `cursed_brazier.dash` | 3 | Cardinal dash movement. |
| `cursed_brazier.overload_charge` | 1 | Rising pulse warning. |
| `cursed_brazier.overload_release` | 2 | Wide dangerous pulse. |
| `cursed_brazier.hurt` | 3 | Metal/ceramic damage response. |
| `cursed_brazier.death` | 2 layers | Flame extinguish plus body/core collapse; no ragdoll audio. |

### Construct

| Proposed event | Variants | Use |
|---|---:|---|
| `construct.wake` | 2 | Ancient mechanism waking. |
| `construct.move` | 5 | Segmented body/chain movement, synchronized sparingly. |
| `construct.turn` | 3 | Smooth constraint/servo adjustment, not constant chatter. |
| `construct.attack_charge` | 2 | Attack animation wind-up before frame 30. |
| `construct.attack_fire` | 3 | Greek-fire laser release at frame 30. |
| `construct.projectile_loop` | 1 loop | Very restrained projectile flight. |
| `construct.projectile_hit` | 4 | Greek-fire impact. |
| `construct.armored_hit` | 4 | Damage rejected outside its vulnerability window. |
| `construct.vulnerable_open` | 1 | Core opens at the damageable animation frame. |
| `construct.vulnerable_close` | 1 | Core seals for the recovery period. |
| `construct.hurt` | 4 | Stone/metal chip with small debris. |
| `construct.death` | 3 layers | Core shutoff, chain collapse, and debris. |

### Crucible and forging

| Proposed event | Variants | Use |
|---|---:|---|
| `crucible.open` | 1 | Enter the angled crucible camera/UI. |
| `crucible.close` | 1 | Leave the interface. |
| `crucible.fuel_insert` | 3 | Material drops into the firebox. |
| `crucible.ignite` | 2 | Fuel catches. |
| `crucible.burn_loop` | 1 loop | Scales subtly with temperature. |
| `crucible.metal_insert` | 4 | Ingot/material placed into the crucible. |
| `crucible.metal_melt` | 3 | Short hiss/sizzle once material joins the mixture. |
| `crucible.control_up_press` | 2 | Bellows/air-pressure click. |
| `crucible.control_down_press` | 2 | Vent release click. |
| `crucible.control_hold_loop` | 1 loop | Quiet mechanical loop while a button is held. |
| `crucible.temperature_enter_band` | 1 | Gentle confirmation, not a loud success jingle. |
| `crucible.temperature_leave_band` | 1 | Soft warning when drifting out. |
| `crucible.overheat_warning` | 1 loop | Escalating but non-annoying danger tone. |
| `crucible.mold_insert` | 3 | Ceramic/stone mold seating. |
| `crucible.mold_remove` | 2 | Empty mold removal. |
| `crucible.mold_locked` | 2 | Failed removal during pouring. |
| `crucible.pour_start` | 2 | Ladle/metal flow begins automatically. |
| `crucible.pour_loop` | 1 loop | Molten flow during the 40-tick pour. |
| `crucible.pour_finish` | 2 | Metal settles and mold closes. |
| `crucible.result_ready` | 1 | Forged result is ready. |
| `crucible.result_take` | 3 | Finished part lifted from the mold. |
| `forge.part_assemble` | 4 | Blade/guard/pommel/axe part assembly. |
| `forge.weapon_complete` | 1 | Full forged weapon completion stinger. |
| `forge.key_complete` | 1 | Special Minotaur key completion cue. |

### Player weapons

#### Afterblow

- **P0** `afterblow.raise` (2), `afterblow.lower` (2), and `afterblow.block` (4): physical sword guard and impact.
- **P0** `afterblow.store_damage` (3 intensity layers): energy entering the blade; intensity should reflect stored damage.
- **P0** `afterblow.fully_charged` (1): brief capped-power cue.
- **P0** `afterblow.release` (3 intensity layers): charged strike, layered over the ordinary sword hit.
- **P1** `afterblow.decay_loop` (1 subtle loop): begins after five seconds and fades completely by ten.
- **P0** `afterblow.break` (1): distinct break because blocked damage is also durability damage.

#### Sickened Twinblades

- **P0** `twinblades.swing_right` (4) and `twinblades.swing_left` (4): slightly different stereo character, but keep world playback positional/mono.
- **P0** `twinblades.combo_3` (1): confirms activation after the third hit.
- **P1** `twinblades.combo_rise` (3 tiers): increasing speed/damage feedback without playing on every frame.
- **P0** `twinblades.combo_end_hit` (1) and `twinblades.combo_end_timeout` (1): distinguish interruption from natural timeout.
- **P1** `twinblades.sickened` (1): quiet stomach/curse cue when Hunger is applied.

## P1 — creatures, quests, traversal, and puzzle identity

### Beetles and centipede

| Family | Needed events |
|---|---|
| Bombardier beetle | `idle` (3), `step` (5), `alert` (2), `spray_charge` (2), `spray` (3), `hurt` (3), `death` (2). |
| Rune beetle | `idle_chitter` (3), `step` (5), `rune_correct` (2), `rune_wrong` (2), `hurt` (3), `death` (2). The correct-rune cue should be harmonic; wrong-rune should be dull/dissonant. |
| Queen beetle | `greeting_calm` (3), `greeting_wary` (3), `greeting_angry` (3), `idle` (4), `step` (5), `quest_offer` (1), `quest_progress` (1), `quest_complete` (1), `refuse` (1). She is invulnerable, so no hurt/death sounds are needed. |
| Scarlet centipede | `crawl_loop` (1), `wall_transition` (3), `turn` (3), `mount` (2), `dismount` (2), `hurt` (3), `death` (2). Keep the crawl quiet so wall traversal does not become exhausting. |
| Ancient skeleton / curseling | `idle` (4), `alert` (3), `step` (5), `greek_fire_charge` (2), `greek_fire_throw` (3), `hurt` (4), `death` (3). |

### Queen quest and objective UI

- `ui.objective_show`, `ui.objective_hide`
- `ui.quest_accept`, `ui.quest_progress`, `ui.quest_complete`
- `ui.quest_angry` for increased beetle-kill anger tier
- `ui.reward_receive`
- `ui.waypoint_reached`
- `ui.denied` for locked/blackened forge recipes and unavailable molds

All UI cues should be short (roughly 80–500 ms), quiet, and easy to distinguish without looking at the HUD.

### Runes, keys, and progression

- `rune.activate`, `rune.deactivate`, `rune.sequence_correct`, `rune.sequence_wrong`
- `key.insert`, `key.accept`, `key.reject`
- `omega_key.hum_loop`, `omega_key.obtain`
- `minotaur_key.obtain`
- `antikythera.pickup`, `antikythera.activate`, `antikythera.solve`
- `respawn_obelisk.activate`, `respawn_obelisk.bind`, `respawn_obelisk.revive`
- `portal.open`, `portal.loop`, `portal.travel`, `portal.close`

### Doors, mechanisms, and traps

- Minotaur door: `tap` (3 slightly escalating hits), `unlock`, `open_loop`, `open_end`, `close_loop`, `close_end`.
- Barrel door: separate `unlock`, `open`, `close`, and `jam` sounds so disappearing/failure states are audible.
- Cursed Brazier door: `chain_start`, `chain_loop`, `stone_move_loop`, `open_end`, `close_end`.
- Omega lock: `activate`, `segment_move` (3), `vanish`, `reappear`.
- Winch: `engage`, `turn_loop`, `chain_tension`, `release`.
- Chain lift: `start`, `travel_loop`, `stop`, `blocked`.
- Fire-burst trap: `prime`, `warning_tick`, `burst`, `reset`.
- Pressure puzzle: `button_down`, `button_hold_loop`, `button_up`, `success`, `reset`.
- Lamenter: `cry_loop` and 3 sparse `sob` one-shots; these should replace the repeated drip placeholder.
- Greek brazier/torch: `ignite`, `fire_loop`, `extinguish`, `greek_fire_burst`.

### Physical debris and ragdolls

- `debris.stone_small` (6), `debris.stone_large` (5), `debris.metal` (5), `debris.wood` (5).
- `ragdoll.body_light` (5), `ragdoll.body_heavy` (5), `ragdoll.armor` (5), `ragdoll.slide` (2 loops).
- `player.powerful_hit` (4) and `player.ragdoll_land` (4) for attacks that fully ragdoll the player.
- `debris.scrape_stone` (2 loops) and `debris.scrape_metal` (2 loops) for massive objects the player can push or become trapped against.

## P2 — environmental soundscape

Each region needs one restrained loop plus sparse one-shots. Do not fill every second; the empty space is part of the maze.

| Region | Main loop | Suggested random one-shots |
|---|---|---|
| Ancient maze | Distant stone air/low maze resonance | Stone settling, far chain, dead wood creak, impossible distant footstep. |
| Overgrowth | Damp enclosed foliage | Leaf movement, vine tension, dripping canopy, distant beetle call. |
| Crimson Marshlands | Wet organic cavern | Mud bubble, tainted heartbeat, distant splash, gas vent. |
| Catacombs | Dry, close burial chambers | Bone shift, dust fall, tomb knock, remote rune chime. |
| Forge district | Dormant industrial heat | Furnace breath, cooling metal tick, chain sway, deep vent, distant hammer. |
| Shale caves | Cold mineral cavern | Shale flakes, water ticks, rock groan, distant cave gust. |
| Arena before fight | Huge quiet chamber tone | Chain strain, sand/pebble fall, Minotaur movement beyond the door. |
| Dead Sun event | Oppressive tonal bed | Geyser warning, vent rumble, fire wave, block rebuild, event end release. |

Additional world cues:

- Maze wall break layers: outer stone, inner unbreakable mazesteel core, and failed-break clang.
- Maze floor rebuild: warning pulse, blocks reforming, completion thump.
- Greek-fire block destruction: ignition, blast, burning loop, magical reconstruction.
- Tree/dead wood: trunk break, heavy fall, branch debris, shattered wood physics.
- Ancient moss/vines: soft break/place; raw-vine eat and popped-vine eat.
- Gas clouds: leak loop, ignition, flare, dissipate.
- Dynamic maze shift: distant warning horn, moving-wall loop, final lock-in impact.

## Recommended production order

1. Minotaur attack telegraphs, hits, charge, weapons, death, and roof collapse.
2. Cursed Brazier shield and its six attack families.
3. Construct vulnerability window and Greek-fire attack.
4. Crucible controls, temperature band, pour, and result sounds.
5. Afterblow and Twinblades feedback.
6. Queen quest UI and all creature vocals/movement.
7. Doors, keys, runes, traps, debris, and ragdolls.
8. Region loops and environmental one-shots.

This order makes the game readable first, gives each major mechanic its identity second, and leaves broad atmospheric layering until the gameplay mix is stable.
