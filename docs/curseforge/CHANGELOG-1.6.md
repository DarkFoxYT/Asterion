# Asterion 1.6

- Fixed a point-light crash when entering the Labyrinth on Minecraft 1.20.1 and 1.21.1.
- Fixed light cleanup when lights are updated or removed during rendering.
- Fixed a startup conflict with Fantasy Ending on Forge 1.20.1.
- Fixed release packaging to keep the required startup fixes and exclude unfinished dimensions.
- Asterion bosses now take 50% less damage from TaCZ bullets. Added a damage-type tag so modpacks can apply this to other guns.
- Doubled Cursed Brazier health from 140 to 280. Existing braziers keep the same percentage of health when updated.
- Labyrinth terrain now grows back after explosions. Restoring terrain no longer drops farmable loot, and large blasts rebuild gradually.
- Moved the objective panel below Xaero's minimap when they overlap.
- Dropped items no longer hold up terrain repairs.
