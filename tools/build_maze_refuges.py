"""Build the refuge and ruined settlements using the mod's existing block palette."""
from pathlib import Path
import math
import random
import nbtlib as n

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/data/asterion/structure'


class Structure:
    def __init__(self, width, height, depth):
        self.size = (width, height, depth)
        self.palette = []
        self.cells = {}
        self.box((0, 0, 0), (width - 1, height - 1, depth - 1), 'minecraft:air')

    def put(self, x, y, z, name, tag=None, **properties):
        assert all(0 <= p < s for p, s in zip((x, y, z), self.size))
        state = n.Compound({'Name': n.String(name)})
        if properties:
            state['Properties'] = n.Compound({k: n.String(str(v)) for k, v in properties.items()})
        if state not in self.palette:
            self.palette.append(state)
        block = n.Compound({'pos': n.List[n.Int]([x, y, z]), 'state': n.Int(self.palette.index(state))})
        if tag:
            block['nbt'] = n.Compound({k: n.String(v) for k, v in tag.items()})
        self.cells[x, y, z] = block

    def box(self, low, high, name, **properties):
        for x in range(low[0], high[0] + 1):
            for y in range(low[1], high[1] + 1):
                for z in range(low[2], high[2] + 1):
                    self.put(x, y, z, name, **properties)

    def save(self, name):
        path = ROOT / (name + '.nbt')
        path.parent.mkdir(parents=True, exist_ok=True)
        n.File({'DataVersion': n.Int(4786), 'size': n.List[n.Int](self.size),
                'palette': n.List[n.Compound](self.palette),
                'blocks': n.List[n.Compound](self.cells.values()),
                'entities': n.List[n.Compound]([])}).save(path, gzipped=True)
        print(name, self.size, len(self.cells), 'blocks')


def refuge():
    s = Structure(23, 11, 23)
    # Chamfered, double-thick shell with continuous bands rather than checkerboard masonry.
    for x in range(23):
        for z in range(23):
            dx, dz = abs(x - 11), abs(z - 11)
            if max(dx, dz) > 10 or dx + dz > 18:
                if dx + dz < 21:
                    s.put(x, 0, z, 'asterion:shale')
                continue
            s.put(x, 0, z, 'asterion:ancient_stone' if max(dx, dz) < 8 else 'asterion:shale_bricks')
            shell = max(dx, dz) >= 9 or dx + dz >= 16
            if shell:
                for y in range(1, 8):
                    s.put(x, y, z, 'asterion:shale_bricks' if y in (1, 6, 7) else 'asterion:ancient_bricks')
            s.put(x, 8, z, 'asterion:ancient_bricks')
            s.put(x, 9, z, 'asterion:shale_brick_slab')
    # North and south vestibules remain level with the maze's approach.
    for z in list(range(0, 5)) + list(range(18, 23)):
        s.box((10, 1, z), (12, 4, z), 'minecraft:air')
        s.box((10, 0, z), (12, 0, z), 'asterion:ancient_stone')
        s.put(9, 4, z, 'asterion:ancient_brick_stairs', facing='east', half='top')
        s.put(13, 4, z, 'asterion:ancient_brick_stairs', facing='west', half='top')
        s.box((10, 5, z), (12, 5, z), 'asterion:ancient_brick_slab', type='top')
    # Wall niches hold benches and warm light, leaving the centre entirely walkable.
    for x, facing in [(4, 'east'), (18, 'west')]:
        for z in range(7, 16):
            s.put(x, 1, z, 'asterion:ancient_plank_stairs', facing=facing)
        for z in (6, 16):
            s.put(x, 1, z, 'asterion:ancient_planks')
            s.put(x, 2, z, 'minecraft:lantern')
    for x in (6, 16):
        for z in (5, 17):
            s.put(x, 6, z, 'minecraft:lantern', hanging='true')
            s.put(x, 7, z, 'asterion:ancient_bricks')
    for x in range(7, 16):
        for z in range(7, 16):
            if max(abs(x - 11), abs(z - 11)) == 4:
                s.put(x, 0, z, 'asterion:shaded_shale_bricks')
    # The full multipart obelisk is centred, with a clear respawn pad on its south side.
    for x in range(3):
        for z in range(3):
            for row in range(3):
                s.put(10 + x, 1 + row, 10 + z, 'asterion:respawn_obelisk',
                      part_x=x, part_z=z, row=row, charge=1)
    s.put(11, 0, 14, 'minecraft:lodestone')
    s.put(6, 1, 4, 'minecraft:crafting_table')
    s.put(7, 1, 4, 'minecraft:furnace', facing='south')
    for x in (15, 16):
        s.put(x, 1, 4, 'minecraft:barrel', facing='south',
              tag={'id': 'minecraft:barrel', 'LootTable': 'asterion:chests/safe_rune_mid'})
    # Recessed slit windows; no holes through which mobs can enter.
    for x in (1, 2, 20, 21):
        for z in (8, 14):
            s.box((x, 3, z), (x, 4, z), 'minecraft:iron_bars', north='true', south='true')
    s.save('safe_room')


def ruin(name, variant):
    s = Structure(23, 12, 21)
    rng = random.Random(741 + variant)
    for x in range(2, 21):
        for z in range(2, 19):
            if rng.random() < .82:
                s.put(x, 0, z, 'asterion:ancient_stone' if (x // 4 + z // 5) % 3 else 'asterion:shale_bricks')
    # Each footprint is different: a gatehouse, a collapsed dwelling, a courtyard wall.
    lines = ([((3, 5), (19, 5)), ((3, 5), (3, 16)), ((19, 5), (19, 11))],
             [((4, 3), (4, 17)), ((4, 17), (18, 17)), ((18, 9), (18, 17)), ((9, 3), (18, 3))],
             [((3, 4), (19, 4)), ((19, 4), (19, 17)), ((9, 17), (19, 17))])[variant]
    for start, end in lines:
        length = abs(end[0] - start[0]) + abs(end[1] - start[1])
        sx, sz = (end[0] > start[0]), (end[1] > start[1])
        for i in range(length + 1):
            x, z = start[0] + i * sx, start[1] + i * sz
            height = max(1, int(4 + 2 * math.sin(i * .46 + variant) + rng.random() * 2))
            opening = (variant == 0 and z == 5 and 9 <= x <= 13)
            for y in range(1, height + 1):
                if opening and y < 5:
                    continue
                s.put(x, y, z, 'asterion:shale_bricks' if y == 1 else 'asterion:ancient_bricks')
            if not opening:
                s.put(x, height + 1, z, 'asterion:ancient_brick_stairs', facing='north' if sx else 'west')
            if i % 5 == 0 and not opening:
                s.put(x + (1 if sz else 0), 1, z + (1 if sx else 0), 'asterion:shale_brick_stairs', facing='south' if sx else 'east')
    if variant == 0:
        s.box((8, 5, 5), (14, 5, 5), 'asterion:ancient_brick_slab', type='top')
        s.box((8, 6, 5), (15, 6, 5), 'asterion:ancient_bricks')
    elif variant == 1:
        for x in range(5, 10):
            for z in range(13, 17):
                s.put(x, 5 + (x - 5) // 2, z, 'asterion:shale_brick_stairs', facing='east')
    else:
        s.box((16, 1, 8), (17, 1, 13), 'asterion:ancient_stone_slab')
    for _ in range(24):
        x, z = rng.randrange(5, 18), rng.randrange(6, 16)
        if abs(x - 11) <= 2 or abs(z - 10) <= 1:
            continue
        s.put(x, 1, z, 'asterion:ancient_moss_carpet' if rng.random() < .6 else 'asterion:shale_slab')
    s.save('ruins/' + name)


if __name__ == '__main__':
    refuge()
    for i, name in enumerate(('gatehouse', 'collapsed_dwelling', 'courtyard')):
        ruin(name, i)
