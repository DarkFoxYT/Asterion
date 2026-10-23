"""Export the unlit inventory model from the geometry used by the placed obelisk."""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--check', action='store_true')
args = parser.parse_args()
for port in (ROOT, ROOT / 'ports/26.1.2'):
    assets = port / 'src/main/resources/assets/asterion'
    geometry = json.loads((assets / 'geckolib/models/block/respawn_obelisk.geo.json').read_text())['minecraft:geometry'][0]
    width = geometry['description']['texture_width']
    height = geometry['description']['texture_height']
    elements = []
    for bone in geometry['bones']:
        assert not any(bone.get('rotation', ())), 'Rotated geometry needs a matching item exporter'
        if bone['name'] == 'glow':
            continue  # The placed obelisk shows this layer only while activated.
        for cube in bone.get('cubes', ()):
            assert not any(cube.get('rotation', ())), 'Rotated cubes need a matching item exporter'
            inflate = cube.get('inflate', 0)
            origin, size = cube['origin'], cube['size']
            minimum = [round((origin[i] - inflate) / 3 + (0 if i == 1 else 8), 6) for i in range(3)]
            maximum = [round((origin[i] + size[i] + inflate) / 3 + (0 if i == 1 else 8), 6) for i in range(3)]
            faces = {}
            for face, uv in cube['uv'].items():
                u, v = uv['uv']
                du, dv = uv['uv_size']
                faces[face] = {'uv': [u * 16 / width, v * 16 / height,
                                      (u + du) * 16 / width, (v + dv) * 16 / height], 'texture': '#obelisk'}
            elements.append({'from': minimum, 'to': maximum, 'faces': faces})
    model = {'parent': 'minecraft:block/block', 'ambientocclusion': False,
             'render_type': 'minecraft:cutout',
             'textures': {'particle': 'asterion:block/respawn_obelisk', 'obelisk': 'asterion:block/respawn_obelisk'},
             'elements': elements,
             'display': {'gui': {'rotation': [15, 225, 0], 'translation': [0, 0, 0], 'scale': [.85, .85, .85]}}}
    path = assets / 'models/item/respawn_obelisk.json'
    if args.check:
        assert json.loads(path.read_text()) == model, f'{path}: item model differs from the placed geometry'
    else:
        path.write_text(json.dumps(model, indent=2) + '\n')
    print(f'{"PASS" if args.check else "Exported"} {path}: {len(elements)} unlit model cubes')
