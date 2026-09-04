# Catacomb room authoring

The current catacomb layout uses authored structure NBTs selected by `AuthoredCatacombs`.
The template-pool JSON files document and expose the same room catalog, but vanilla jigsaw
expansion does **not** choose the runtime rooms. Adding a pool entry alone will therefore not
make a room appear; the Java selector must also include the new name.

## Standard room contract

- Build every ordinary module at exactly **19 x 31 x 19** blocks.
- Save the complete volume, including air. Partial saves can leave stone inside a room.
- The local floor is Y=0 and every doorway jigsaw is at local **Y=5**.
- Door centers are north `(9,5,0)`, east `(18,5,9)`, south `(9,5,18)`, and west `(0,5,9)`.
- Point each jigsaw outward from the room.
- Jigsaw name: `asterion:catacombs/door`
- Jigsaw target: `asterion:catacombs/door`
- Target pool: `asterion:catacombs/modules`
- Final state: `minecraft:air`
- Joint: `aligned`
- Do not save entities. Containers should be empty and use a loot table rather than saved items.

The placement processor replaces the jigsaw blocks with their final state, so the connector
will become air in the generated room.

## Naming

Use lowercase names with a two-digit variant suffix:

- `corridor_deadend_03` — one north-facing connector in its native rotation.
- `corridor_straight_05` — north and south connectors.
- `corridor_corner_01` — north and east connectors; this needs a corner branch in the selector.
- `corridor_t_03` — three connectors; record its native exit mask in the selector.
- `corridor_cross_03` — all four connectors.
- `forge_workshop_01`, `crypt_library_01`, etc. — special rooms; give each an explicit selector rule.

Avoid spaces, capitals, and names such as `newroom`. The category is used by validation and
helps the generator know which exit layouts the template supports.

## Saving from Minecraft

1. Build the room with its lower north-west corner treated as local `(0,0,0)`.
2. Place and configure the outward-facing jigsaws at the coordinates above.
3. Give yourself a structure block: `/give @s minecraft:structure_block`.
4. Set it to **Save**, use `asterion:catacombs/<room_name>`, enter the offset to the room's
   lower corner, and set size to `19 31 19`.
5. Disable **Include entities**, keep blocks enabled, then press **Save**.
6. Copy the generated file from
   `generated/asterion/structures/catacombs/<room_name>.nbt` in that world to
   `src/main/resources/data/asterion/structure/catacombs/<room_name>.nbt` in this project.

## Making the room generate

1. Add the filename (without `.nbt`) to `AuthoredCatacombs.TEMPLATES`.
2. Add it to the matching choice list in `AuthoredCatacombs.module(...)` and specify its native
   connector mask. The mask bits are north=1, east=2, south=4, west=8.
3. Add a matching entry to `data/asterion/worldgen/template_pool/catacombs/modules.json`.
4. Run `gradlew catacombRegression`. It checks size, complete block coverage, connector count,
   connector height/configuration, loot tables, and whether the selector can actually reach it.

Special arena pieces remain **41 x 48 x 41**, named `arena_part1` through `arena_part9`, and
are positioned directly rather than rotated by the module selector.

## Forge biome rooms (variable size)

The lower `asterion:forge` biome uses a separate connector-driven assembler. Its room set is:

- `forge`
- `t_junction_1`, `t_junction_2`, `t_junction_3`
- `corner_1`, `corner_2`
- `hallway_1`, `hallway_2`
- `gold_reserves`

Save these as `asterion:forge/<name>` and copy them to
`src/main/resources/data/asterion/structure/forge/<name>.nbt`. For compatibility, the loader
also finds the same names under `asterion:catacombs/` and accepts `01` or unseparated numeric
suffixes.

Forge rooms have **no fixed dimensions**. Save the exact bounding box of each real build;
the runtime reads that NBT size, rotates its actual bounds, and aligns the jigsaw positions
themselves. Rooms may also use different connector heights, because attachment translates the
whole next room until both connector blocks are adjacent at the same world Y.

Use the connector configuration shown in-game:

- Name: `asterion:catacombs/door`
- Target name: `asterion:catacombs/door`
- Final state / turns into: `minecraft:air`
- Joint: `rollable`
- Target pool may remain `minecraft:empty`

The custom Forge assembler deliberately owns room selection, so it does not ask vanilla's
target pool to expand the structure. It honors the saved connector orientation and replaces
the jigsaw with its final state. Point every connector horizontally outward from its room.
The `forge` room is the unique root. Every supplied variant is attached at least once, then
the junction, corner, hallway, and occasional `gold_reserves` NBTs are reused to grow a
36-room Forge-biome district. Real rotated bounding boxes prevent overlap, and unused ends
are sealed instead of opening into the foundation shell.
