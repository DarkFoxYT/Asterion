"""Check the actual shipped model graph, texture bytes and version-specific armor paths."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import zipfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
parser = argparse.ArgumentParser()
parser.add_argument('--version', default='1.6.3')
parser.add_argument('--minecraft')
parser.add_argument('--loader')
args = parser.parse_args()
decoded = set()
rows = []

for mc in ('1.20.1', '1.21.1', '26.1.2', '1.21.11', '26.3'):
    if args.minecraft and args.minecraft != mc:
        continue
    with zipfile.ZipFile(Path.home() / '.gradle/caches/fabric-loom' / mc / 'minecraft-client.jar') as vanilla:
        vanilla_names = set(vanilla.namelist())
    for loader in ('fabric', 'quilt', 'forge', 'neoforge'):
        if args.minecraft and args.minecraft != mc or args.loader and args.loader != loader:
            continue
        path = ROOT / 'modbuilds' / f'Asterion-{args.version}-{loader}-mc{mc}.jar'
        with zipfile.ZipFile(path) as jar:
            names = set(jar.namelist())
            prefix = 'assets/asterion/'
            def require(resource, owner):
                assert resource in names, f'{path.name}: {owner} references missing {resource}'

            def model_references(value, owner):
                if isinstance(value, dict):
                    for key, child in value.items():
                        # A texture slot may itself be named "model"; it is not a parent model.
                        if key == 'textures':
                            continue
                        if key in ('parent', 'model') and isinstance(child, str) and child.startswith('asterion:'):
                            require(prefix + 'models/' + child.split(':', 1)[1] + '.json', owner)
                        if key in ('parent', 'model') and isinstance(child, str) and child.startswith('minecraft:') and not child.startswith('minecraft:builtin/'):
                            assert 'assets/minecraft/models/' + child.split(':', 1)[1] + '.json' in vanilla_names, f'{path.name}: missing vanilla parent/model {child} in {owner}'
                        model_references(child, owner)
                elif isinstance(value, list):
                    for child in value:
                        model_references(child, owner)

            images = models = 0
            for name in sorted(names):
                if name.startswith(prefix + 'textures/') and name.endswith('.png.mcmeta'):
                    texture = name[:-7]
                    require(texture, name)
                    animation = json.loads(jar.read(name)).get('animation')
                    if animation is not None:
                        with Image.open(io.BytesIO(jar.read(texture))) as image:
                            width, height = image.size
                        if 'width' in animation or 'height' in animation:
                            frame_width = animation.get('width', width)
                            frame_height = animation.get('height', height)
                        else:
                            frame_width = frame_height = min(width, height)
                        assert frame_width > 0 and frame_height > 0, f'{path.name}: invalid animation size {name}'
                        assert width % frame_width == 0 and height % frame_height == 0, f'{path.name}: incomplete animation frames {name}'
                        count = width // frame_width * (height // frame_height)
                        assert animation.get('frametime', 1) > 0, f'{path.name}: invalid animation speed {name}'
                        for frame in animation.get('frames', []):
                            index = frame if isinstance(frame, int) else frame['index']
                            assert 0 <= index < count, f'{path.name}: animation frame outside PNG {name}'
                            assert isinstance(frame, int) or frame.get('time', 1) > 0, f'{path.name}: invalid frame time {name}'
                if name.startswith(prefix + 'textures/') and name.endswith('.png'):
                    images += 1
                    data = jar.read(name)
                    digest = hashlib.sha256(data).digest()
                    if digest not in decoded:
                        with Image.open(io.BytesIO(data)) as image:
                            image.verify()
                        with Image.open(io.BytesIO(data)) as image:
                            image.load()
                            assert image.width > 0 and image.height > 0, name
                        decoded.add(digest)
                if name.endswith('.json') and any(name.startswith(prefix + folder) for folder in ('models/', 'blockstates/', 'items/')):
                    model = json.loads(jar.read(name))
                    model_references(model, name)
                    if name.startswith(prefix + 'models/'):
                        models += 1
                        for texture in model.get('textures', {}).values():
                            if isinstance(texture, str) and texture.startswith('asterion:'):
                                require(prefix + 'textures/' + texture.split(':', 1)[1] + '.png', name)
                            if isinstance(texture, str) and texture.startswith('minecraft:'):
                                assert 'assets/minecraft/textures/' + texture.split(':', 1)[1] + '.png' in vanilla_names, f'{path.name}: missing vanilla texture {texture} in {name}'

            chain = json.loads(jar.read(prefix + 'models/block/mazesteel_chain.json'))
            assert chain.get('render_type') == 'minecraft:cutout', f'{path.name}: chain uses opaque terrain'
            axes = {'east': (1, 2), 'west': (1, 2), 'up': (0, 2), 'down': (0, 2), 'north': (0, 1), 'south': (0, 1)}
            assert sum(len(e['faces']) for e in chain['elements']) == 4, f'{path.name}: chain has degenerate faces'
            for element in chain['elements']:
                extent = [b - a for a, b in zip(element['from'], element['to'])]
                for face, data in element['faces'].items():
                    assert all(extent[a] != 0 for a in axes[face]), f'{path.name}: zero-area chain face'
                    uv = data['uv']
                    assert uv[0] != uv[2] and uv[1] != uv[3], f'{path.name}: zero-area chain UV'
            for material in ('tarnished_gold', 'celestial_bronze', 'celestial_gold', 'celestial_steel'):
                for source, layer, equipment in (('top', 1, 'humanoid'), ('bottom', 2, 'humanoid_leggings')):
                    authored = jar.read(prefix + f'textures/armor/{material}_{source}.png')
                    runtime = prefix + (f'textures/models/armor/{material}_layer_{layer}.png'
                            if mc in ('1.20.1', '1.21.1') else f'textures/entity/equipment/{equipment}/{material}.png')
                    require(runtime, material)
                    assert jar.read(runtime) == authored, f'{path.name}: armor texture changed during staging'
            rows.append(dict(file=path.name, textures=images, models=models))
            print(f'PASS {path.name}: {images} valid PNGs; {models} model references; chain and all armor layers')

output = ROOT / f'build/curseforge/release-{args.version}-textures.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(rows, indent=2) + '\n')
