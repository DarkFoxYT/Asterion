# Structure templates

The underwater ruin is a standard Minecraft structure-template NBT at:

`src/main/resources/data/labyrinth/structure/underwater_ruin.nbt`

Its editable blueprint is `RuinTemplateBuilder.java`. Change its palette or block-placement loops, then rebuild it with:

```powershell
./gradlew rebuildStructureTemplates
```

Run `./gradlew build` afterward. The NBT is used by the registered
`labyrinth:underwater_ruin` jigsaw structure and by the legacy placed-feature path.
The registered structure is generated on ocean floors, rotates naturally, uses
`data/labyrinth/loot_table/chests/underwater_ruin.json` for its barrel, and can be found with:

```mcfunction
/locate structure labyrinth:underwater_ruin
```

It is also appended to Minecraft's `#minecraft:ocean_ruin` structure tag so standard
ocean-ruin discovery systems can recognize it.

For fast testing, temporarily set `underwaterRuinChance` to `1` in `config/labyrinth.json` or through the F8 ImGui panel, then explore newly generated ocean chunks. Restore the value afterward; existing chunks are not regenerated.

## Maze NBT landmarks

Maze landmarks are ordinary Minecraft structure-template NBT files, not Java block-placement code.
Their data-driven catalog is:

`src/main/resources/data/labyrinth/maze_structures.json`

Each entry only needs a template resource ID and a weight. Resource IDs may point to a vanilla
template (for example `minecraft:village/taiga/houses/taiga_small_house_1`) or to a custom file such
as `src/main/resources/data/labyrinth/structure/maze/sanctuary.nbt`, referenced as
`labyrinth:maze/sanctuary`.

```json
{
  "template": "labyrinth:maze/sanctuary",
  "weight": 3
}
```

`spacing_cells` controls how far apart candidates are, `chance` controls how often a candidate is
accepted, and `padding_blocks` reserves walking room around the measured structure. Rotation and
placement are deterministic for the world seed. The loader reads the real rotated NBT bounds, clears
the corresponding maze walls/decorations, protects the entrance-to-center solution route, loads all
affected chunks, and then places the template. Jigsaw markers, structure helper blocks, and copper are
cleaned from the final landmark automatically.

Structure-layout changes affect newly generated maze chunks. Use a fresh Labyrinth dimension when
testing footprint changes, because already-generated maze walls cannot be safely rerouted afterward.
