"""Derive optional/embedded upload relations from the actual release jars."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[3]
parser = argparse.ArgumentParser()
parser.add_argument('--version', required=True)
args = parser.parse_args()
catalog = json.loads((ROOT / 'docs/curseforge/RELATION-PROJECTS.json').read_text(encoding='utf-8'))
projects = catalog['projects']
files = []
seen = {}
for path in sorted((ROOT / 'modbuilds').glob(f'Asterion-{args.version}-*-mc*.jar')):
    loader, mc = path.stem.split(f'Asterion-{args.version}-', 1)[1].split('-mc')
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    if digest in seen:
        seen[digest]['loaders'].append(loader)
        continue
    with zipfile.ZipFile(path) as jar:
        names = set(jar.namelist())
        has = lambda suffix: any(n.endswith(suffix) for n in names)
        optional = []
        def add(slug, evidence):
            project = projects[slug]
            if f'{mc}/{loader}' in project['supportedPairs']:
                optional.append({'project': slug, 'modrinth': project['modrinth'], 'curseforge': project.get('curseforge'),
                                 'type': 'optional', 'evidence': evidence})
        if 'fabric.mod.json' in names:
            metadata = json.loads(jar.read('fabric.mod.json'))
            if metadata.get('entrypoints', {}).get('modmenu'):
                add('modmenu', 'Registered Mod Menu entrypoint')
        for slug, suffix in [('jei', '/AsterionJeiPlugin.class'),
                             ('jade', '/AsterionJadePlugin.class'),
                             ('rei', '/AsterionReiClientPlugin.class')]:
            # These shared classes are compiled against Fabric APIs. Native
            # builds may contain them without registering a native integration.
            if loader in ('fabric', 'quilt') and has(suffix):
                add(slug, 'Packaged optional integration')
        if has('/MinimapHudLayout.class'):
            add('xaeros-minimap', 'Objective HUD avoids the minimap')
        if has('/AsterionMixinPlugin.class'):
            for slug in ('sodium', 'embeddium', 'iris', 'oculus'):
                add(slug, 'Optional renderer hooks; external shader packs disabled in Labyrinth')
        if has('/PhysicsCompatibility.class'):
            for slug in ('sable', 'sable-ragdolls'):
                add(slug, 'Defers player physics to Sable when installed')
        if has('/RagdollCompat.class') or has('/ReplayCompatibility.class'):
            for slug in ('flashback', 'replaymod'):
                add(slug, 'Replay-aware ragdoll rendering')
        if mc == '1.20.1' and loader == 'forge':
            add('starcatcher', 'Labyrinth entry regression with Starcatcher 3.3.10 and NeoBackports 1.4.2')
        embedded = [{'project': 'geckolib', 'modrinth': projects['geckolib']['modrinth'], 'type': 'embedded'}]
        if any('fabric-api-' in n and n.endswith('.jar') for n in names):
            slug = 'fabric-api' if loader in ('fabric', 'quilt') else 'forgified-fabric-api'
            embedded.append({'project': slug, 'modrinth': projects[slug]['modrinth'], 'type': 'embedded'})
    for relation in embedded:
        relation['curseforge'] = projects[relation['project']].get('curseforge')
    record = {'file': path.name, 'sha256': digest, 'minecraft': mc,
              'loaders': [loader], 'relations': optional + embedded}
    seen[digest] = record
    files.append(record)
if not files:
    raise SystemExit('No release jars found')
output = ROOT / f'build/curseforge/relations-{args.version}.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps({'version': args.version, 'catalogChecked': catalog['checked'],
                             'files': files}, indent=2) + '\n', encoding='utf-8')
print(f'Prepared relations for {len(files)} unique uploads: {output}')
