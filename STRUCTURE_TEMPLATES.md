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
