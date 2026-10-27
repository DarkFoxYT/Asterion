"""Check every required mod dependency inside the 20 self-contained releases."""
import io
import argparse
import json
from pathlib import Path
import sys
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[3]
BUILTINS = {'minecraft', 'java', 'fabricloader', 'quilt_loader', 'forge', 'neoforge'}
rows = []
parser = argparse.ArgumentParser()
parser.add_argument('--version', default='1.6.3')
parser.add_argument('--minecraft')
parser.add_argument('--loader', choices=('fabric', 'quilt', 'forge', 'neoforge'))
args = parser.parse_args()

for minecraft in ('1.20.1', '1.21.1', '26.1.2', '1.21.11', '26.3'):
    for loader in ('fabric', 'quilt', 'forge', 'neoforge'):
        if args.minecraft and args.minecraft != minecraft or args.loader and args.loader != loader:
            continue
        path = ROOT / 'modbuilds' / f'Asterion-{args.version}-{loader}-mc{minecraft}.jar'
        provided, required, archives, classes, native_files = set(), [], [], set(), set()

        def visit(content, label):
            with zipfile.ZipFile(io.BytesIO(content)) as jar:
                names = set(jar.namelist())
                assert jar.testzip() is None, f'Corrupt dependency: {label}'
                assert not any(n.startswith('net/krodark/asterion/test/') for n in names), f'{label}: temporary test harness shipped'
                classes.update(n for n in names if n.endswith('.class'))
                native_files.update(n for n in names if 'assimp' in n.lower() and n.endswith(('.dll', '.so', '.dylib')))
                renderer_entrypoint = 'com/meekdev/amnetic/Amnetic.class'
                if renderer_entrypoint in names:
                    assert b'bundled dependency' not in jar.read(renderer_entrypoint), f'{label}: misleading standalone-renderer warning remains'
                declared = []
                if loader in ('fabric', 'quilt') and 'fabric.mod.json' in names:
                    meta = json.loads(jar.read('fabric.mod.json'))
                    provided.add(meta['id'])
                    provided.update(meta.get('provides', []))
                    required.extend((label, dep) for dep in meta.get('depends', {}))
                    declared.extend(entry['file'] for entry in meta.get('jars', []))
                for metadata_path in ('META-INF/mods.toml', 'META-INF/neoforge.mods.toml'):
                    if loader in ('fabric', 'quilt') or metadata_path not in names:
                        continue
                    meta = tomllib.loads(jar.read(metadata_path).decode('utf8'))
                    provided.update(mod['modId'] for mod in meta.get('mods', []))
                    for deps in meta.get('dependencies', {}).values():
                        required.extend((label, dep['modId']) for dep in deps
                                        if dep.get('mandatory', True) and dep.get('type', 'required') == 'required')
                if loader not in ('fabric', 'quilt') and 'META-INF/jarjar/metadata.json' in names:
                    meta = json.loads(jar.read('META-INF/jarjar/metadata.json'))
                    declared.extend(entry['path'] for entry in meta['jars'])
                for entry in declared:
                    assert entry in names, f'Undelivered nested dependency: {label}/{entry}'
                # Inspect every nested library, including shaded/legacy libraries
                # which do not carry loader registration metadata themselves.
                for name in sorted(n for n in names if n.endswith('.jar')):
                    archives.append(label + '/' + name)
                    visit(jar.read(name), label + '/' + name)

        visit(path.read_bytes(), path.name)
        missing = [(label, dep) for label, dep in required if dep not in provided | BUILTINS]
        assert not missing, f'{path.name}: required external mods remain: {missing}'
        assert 'geckolib' in provided, f'{path.name}: GeckoLib is not embedded'
        assert any(n.startswith('com/meekdev/amnetic/') for n in classes), f'{path.name}: renderer missing'
        assert any('/assimp/' in n for n in classes), f'{path.name}: model importer missing'
        for extension in ('.dll', '.so', '.dylib'):
            assert any(n.endswith(extension) for n in native_files), f'{path.name}: model importer native {extension} missing'
        if minecraft == '1.20.1':
            assert any(n.startswith('com/eliotlash/') for n in classes), f'{path.name}: animation expression library missing'
        if minecraft == '1.21.1':
            assert any(n.startswith(('com/eliotlash/', 'com/danrus/bb4j/')) for n in classes), f'{path.name}: animation expression library missing'
        rows.append(dict(file=path.name, providedMods=sorted(provided),
                         requiredMods=sorted({dep for _, dep in required}), embeddedArchives=archives,
                         importerNatives=sorted(native_files)))
        print(f'PASS {path.name}: {len(provided)} mod IDs, {len(archives)} embedded libraries; no required external mods')

suffix = '-' + '-'.join(v for v in (args.minecraft, args.loader) if v) if args.minecraft or args.loader else ''
output = ROOT / f'build/curseforge/release-{args.version}-dependencies{suffix}.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(rows, indent=2) + '\n', encoding='utf8')
