"""Audit the eight production archives without starting Minecraft.

Usage: python tools/verify-extra-ports.py [repository modbuilds directory]
"""
import argparse
import hashlib
from collections import Counter
import io
import json
from pathlib import Path
import re
import struct
import sys
import tomllib
import zipfile

PROJECT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('shelf', nargs='?', type=Path, default=PROJECT.parents[3] / 'modbuilds')
parser.add_argument('--version', default=re.search(r'^mod_version=(.+)$', (PROJECT / 'gradle.properties').read_text(), re.M).group(1))
args = parser.parse_args()
SHELF, VERSION = args.shelf, args.version
TARGETS = {'1.21.11': (65, [75, 0]), '26.3': (69, [97, 1])}
LOADERS = ('fabric', 'quilt', 'forge', 'neoforge')


def verify_native_pack(pack, mc, loader):
    assert 'supported_formats' not in pack, 'Deprecated native pack metadata'
    if loader == 'neoforge':
        assert set(pack) == {'description'}, 'NeoForge must supply type-specific formats'
    else:
        minimum, maximum = ([82, 0], [94, 1]) if mc == '1.21.11' else ([97, 1], [121, 0])
        assert pack['min_format'] == minimum and pack['max_format'] == maximum, 'Wrong Forge asset/data pack range'


def read_json(jar, name):
    return json.loads(jar.read(name))


def nested_paths(jar, loader):
    if loader in ('fabric', 'quilt'):
        return [item['file'] for item in read_json(jar, 'fabric.mod.json').get('jars', [])]
    return [item['path'] for item in read_json(jar, 'META-INF/jarjar/metadata.json')['jars']]


def mixins(jar):
    for name in jar.namelist():
        if name.endswith('.mixins.json'):
            config = read_json(jar, name)
            for side in ('mixins', 'client', 'server'):
                for entry in config.get(side, []):
                    path = (config['package'] + '.' + entry).replace('.', '/') + '.class'
                    assert path in jar.namelist(), f'{name}: missing mixin {path}'


def no_old_loot_fields(value):
    if isinstance(value, list):
        for item in value:
            no_old_loot_fields(item)
    elif isinstance(value, dict):
        assert not {'functions', 'function', 'conditions'} & value.keys(), f'Unmigrated loot: {value}'
        assert not isinstance(value.get('condition'), str) or value['condition'].startswith('asterion:'), value
        for item in value.values():
            no_old_loot_fields(item)


def primitive_values(value):
    if isinstance(value, dict):
        return sum((primitive_values(v) for v in value.values()), Counter())
    if isinstance(value, list):
        return sum((primitive_values(v) for v in value), Counter())
    if value == 'minecraft:block_state_property':
        value = 'minecraft:match_block'
    return Counter({(type(value).__name__, value): 1})


def verify(path, mc, loader):
    major, pack_format = TARGETS[mc]
    with zipfile.ZipFile(path) as jar:
        assert jar.testzip() is None, 'Corrupt archive'
        names = set(jar.namelist())
        assert len(names) == len(jar.namelist()), 'Duplicate ZIP entries'
        if loader in ('fabric', 'quilt'):
            assert 'pack.mcmeta' not in names, 'Fabric must generate separate asset/data pack metadata'
        else:
            pack = read_json(jar, 'pack.mcmeta')['pack']
            verify_native_pack(pack, mc, loader)
        if loader in ('fabric', 'quilt'):
            metadata = read_json(jar, 'fabric.mod.json')
            assert metadata['id'] == 'asterion' and metadata['version'] == VERSION
            assert metadata['depends']['minecraft'] == mc
            for entries in metadata['entrypoints'].values():
                for entry in entries:
                    value = entry if isinstance(entry, str) else entry['value']
                    assert value.split('::')[0].replace('.', '/') + '.class' in names, value
            if mc == '1.21.11':
                assert metadata['accessWidener'] in names, 'Missing access widener'
        else:
            meta_path = 'META-INF/mods.toml' if loader == 'forge' else 'META-INF/neoforge.mods.toml'
            metadata = tomllib.loads(jar.read(meta_path).decode())
            assert next(m for m in metadata['mods'] if m['modId'] == 'asterion')['version'] == VERSION
            assert next(d for d in metadata['dependencies']['asterion'] if d['modId'] == 'minecraft')['versionRange'] == f'[{mc}]'
            assert not any(n.startswith('net/fabricmc/') for n in names), 'Public Fabric adapter packages remain'
            assert any(n.startswith('net/krodark/asterion/nativeapi/') for n in names), 'Native adapters missing'
        for name in names:
            if name.endswith('.json'):
                value = read_json(jar, name)
                if name.startswith('assets/asterion/post_effect/'):
                    for render_pass in value.get('passes', []):
                        for sample in render_pass.get('inputs', []):
                            scene_input = sample.get('sampler_name') == 'Scene'
                            final_copy = render_pass.get('fragment_shader') == 'minecraft:post/blit'
                            if scene_input or final_copy:
                                assert not sample.get('bilinear', False), f'Filtered world image: {name}'
                if mc == '26.3' and '/biome_modifier/' in name and value.get('type', '').endswith(':add_spawns'):
                    spawns = value['spawners'] if isinstance(value['spawners'], list) else [value['spawners']]
                    for spawn in spawns:
                        assert 'count' in spawn and not {'minCount', 'maxCount'} & spawn.keys(), f'Old spawn count schema: {name}'
            if name.endswith('.class'):
                code = jar.read(name)
                assert struct.unpack('>H', code[6:8])[0] <= major, f'Wrong Java target: {name}'
                assert not name.startswith('net/krodark/asterion/test/'), 'Test code shipped'
                assert not name.startswith('net/krodark/asterion/dev/'), 'Development code shipped'
                if loader in ('forge', 'neoforge'):
                    assert b'net/fabricmc/' not in code, f'Unisolated Fabric API link: {name}'
        mixins(jar)
        if mc == '1.21.11' and loader in ('fabric', 'quilt'):
            client = jar.read('net/krodark/asterion/client/AsterionClient.class')
            # Vanilla enum fields are remapped in production Fabric archives.
            assert b'BlockRenderLayerMap' in client and b'putBlocks' in client and b'MAZESTEEL_CHAIN' in client, 'Transparent terrain layer registration missing'
        if mc == '26.3' and loader in ('forge', 'neoforge'):
            bloom = jar.read('net/krodark/asterion/mixin/AmneticBloomPerformanceMixin.class')
            assert b'asterion$nativeBloomPolicy' in bloom and b'hasEmissive' in bloom and b'renderNative' in bloom, 'Native bloom fallback policy missing'
        nested = nested_paths(jar, loader)
        assert len(nested) == len(set(nested)), 'Duplicate bundled dependency declarations'
        renderer = [n for n in nested if '/amnetic' in n]
        assert len(renderer) == 1, 'Expected one matching renderer'
        assert any('/geckolib' in n for n in nested), 'GeckoLib not bundled'
        for entry in nested:
            assert entry in names, f'Missing bundled dependency {entry}'
            with zipfile.ZipFile(io.BytesIO(jar.read(entry))) as dependency:
                assert dependency.testzip() is None, f'Corrupt dependency {entry}'
                if entry not in renderer:
                    continue
                pack = read_json(dependency, 'pack.mcmeta')['pack']
                if loader in ('forge', 'neoforge'):
                    verify_native_pack(pack, mc, loader)
                    if loader == 'neoforge' and mc == '1.21.11':
                        assert 'NeoForgeTextureMixin' in read_json(dependency, 'amnetic.mixins.json')['client'], 'Validated GPU texture bridge missing'
                        bridge = dependency.read('com/meekdev/amnetic/mixin/NeoForgeTextureMixin.class')
                        assert b'getRealTexture' in bridge and b'glId' in bridge, 'Validated GPU textures cannot reach OpenGL'
                    if loader == 'forge' and mc == '26.3':
                        assert not any('lwjgl-tinyfd-' in n and n.endswith('.jar') for n in dependency.namelist()), 'Forge renderer duplicates vanilla TinyFD'
                        assert read_json(dependency, 'amnetic.mixins.json')['compatibilityLevel'] == 'JAVA_21', 'Forge Mixin compatibility level is unsupported'
                    assert not any(n.endswith('.class') and (n.startswith('org/lwjgl/') or
                        (n.startswith('META-INF/versions/') and '/org/lwjgl/' in n))
                        for n in dependency.namelist()), 'Renderer shadows Minecraft LWJGL core'
                else:
                    assert pack['min_format'] == pack_format and pack['max_format'] == pack_format, pack
                assert 'supported_formats' not in pack, 'Deprecated renderer pack metadata'
                if loader in ('fabric', 'quilt'):
                    renderer_meta = read_json(dependency, 'fabric.mod.json')
                    assert renderer_meta['version'].endswith('+' + mc), 'Wrong renderer version'
                else:
                    renderer_meta = tomllib.loads(dependency.read('META-INF/mods.toml' if loader == 'forge' else 'META-INF/neoforge.mods.toml').decode())
                    assert next(m for m in renderer_meta['mods'] if m['modId'] == 'amnetic')['version'].endswith('+' + mc)
                effect_io = dependency.read('com/meekdev/amnetic/client/particle/editor/EffectIO.class')
                assert b'SafePresetIO' in effect_io and b'validateName' in effect_io, 'Preset safety fix missing'
                mixins(dependency)
                for name in dependency.namelist():
                    if name.endswith('.class'):
                        assert struct.unpack('>H', dependency.read(name)[6:8])[0] <= major, f'Wrong renderer Java target: {name}'
        assert 'data/asterion/dimension/labyrinth.json' in names
        for forbidden in ('limbo', 'underworld', 'asterion_dimension'):
            assert f'data/asterion/dimension/{forbidden}.json' not in names, f'Unwanted dimension {forbidden}'
        for forbidden in ('limbo', 'underworld'):
            assert f'data/asterion/dimension_type/{forbidden}.json' not in names
        assert 'data/asterion/worldgen/biome/limbo.json' not in names
        assert not any('/port_test_' in name for name in names), 'Test datapack shipped'
        assert 'net/krodark/asterion/item/AfterblowItem.class' in names, 'Afterblow missing'
        # Keep the authored textures byte-for-byte. Generated equipment/forge
        # models may add assets, but must not replace the original artwork.
        textures = PROJECT / 'src/main/resources/assets/asterion/textures'
        texture_count = 0
        for texture in textures.rglob('*.png'):
            name = 'assets/asterion/textures/' + texture.relative_to(textures).as_posix()
            assert name in names and jar.read(name) == texture.read_bytes(), f'Texture changed or missing: {name}'
            texture_count += 1
        if mc == '26.3':
            for name in names:
                if name.startswith('data/asterion/worldgen/biome/') and name.endswith('.json'):
                    biome = read_json(jar, name)
                    assert 'spawners' not in biome, f'Old spawn data: {name}'
                    spawn = biome['attributes']['minecraft:gameplay/natural_mob_spawns']
                    assert spawn['modifier'] == 'override' and spawn['argument']['spawns_by_category'] == {}, name
                assert not any('/worldgen/configured_feature/' in n and not n.endswith('/') for n in names), 'Old feature registry resources remain'
            assert len([n for n in names if n.startswith('data/asterion/worldgen/feature/') and n.endswith('.json')]) == 12
            for name in names:
                if name.startswith('data/asterion/loot_table/') and name.endswith('.json'):
                    migrated = read_json(jar, name)
                    no_old_loot_fields(migrated)
                    baseline = json.loads((PROJECT / 'src/main/resources' / name).read_text(encoding='utf-8'))
                    before, after = primitive_values(baseline), primitive_values(migrated)
                    before.pop(('str', 'minecraft:all_of'), None)
                    after.pop(('str', 'minecraft:all_of'), None)
                    assert before == after, f'Loot values changed: {name}'
                if name.startswith('data/asterion/advancement/') and name.endswith('.json'):
                    for criterion in read_json(jar, name).get('criteria', {}).values():
                        if criterion['trigger'] == 'minecraft:recipe_unlocked':
                            assert 'recipes' in criterion['conditions'], f'Old recipe criterion: {name}'
                        if criterion['trigger'] == 'minecraft:player_killed_entity':
                            entity = criterion.get('conditions', {}).get('entity')
                            if entity:
                                assert entity['type'] == 'minecraft:entity_properties'
                                assert 'minecraft:entity_type' in entity['predicate']
        return {'file': path.name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                'bytes': path.stat().st_size, 'textures_unchanged': texture_count}


if __name__ == '__main__':
    results = []
    for mc in TARGETS:
        for loader in LOADERS:
            path = SHELF / f'Asterion-{VERSION}-{loader}-mc{mc}.jar'
            results.append(verify(path, mc, loader))
            print('PASS:', path.name)
    output = PROJECT / 'build/extra-ports-artifact-audit.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(results, indent=2) + '\n', encoding='utf-8')
    print('PASS: all eight production archives; metadata, nesting, Java targets, data migration and authored textures')
