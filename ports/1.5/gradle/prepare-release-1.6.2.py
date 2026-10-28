"""Audit and group a complete maintained release for CurseForge uploads.

Build both ports/1.5:prepareCurseForgeRelease and
ports/1.5/ports/1.6.2:verifyExtraPortsWithoutLaunching first.
Runtime validation remains a separate prerequisite for publication.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[3]
parser = argparse.ArgumentParser()
parser.add_argument('--version', default='1.6.3')
args = parser.parse_args()
VERSION = args.version
subprocess.run([sys.executable, str(ROOT / 'ports/1.5/gradle/verify-release.py'),
                '--version', VERSION], check=True)
subprocess.run([sys.executable, str(ROOT / 'ports/1.5/ports/1.6.2/tools/verify-extra-ports.py'), '--version', VERSION],
               check=True)
subprocess.run([sys.executable, str(ROOT / 'ports/1.5/gradle/verify-embedded-dependencies.py'), '--version', VERSION],
               check=True)

subprocess.run([sys.executable, str(ROOT / 'ports/1.5/gradle/verify-texture-assets.py'),
                '--version', VERSION], check=True)

unique = {}
for minecraft in ('1.20.1', '1.21.1', '26.1.2', '1.21.11', '26.2', '26.3'):
    for loader in ('Fabric', 'Quilt', 'Forge', 'NeoForge'):
        jar = ROOT / 'modbuilds' / f'Asterion-{VERSION}-{loader.lower()}-mc{minecraft}.jar'
        if tuple(map(int, VERSION.split('.'))) >= (1, 6, 4):
            with zipfile.ZipFile(jar) as archive:
                names = set(archive.namelist())
                for name, maximum in (('swifty_water', 3), ('minos_killer', 3), ('celestial_curse', 2), ('durability_steal', 5)):
                    if minecraft != '1.20.1':
                        enchantment = json.loads(archive.read(f'data/asterion/enchantment/{name}.json'))
                        assert enchantment['max_level'] == maximum, f'{jar.name}: wrong {name} levels'
                    assert 'net/krodark/asterion/game/EnchantmentGameplay.class' in names
                folder = 'advancements' if minecraft == '1.20.1' else 'advancement'
                favor = json.loads(archive.read(f'data/asterion/{folder}/queens_favor.json'))
                assert 'asterion:flamethrower' in favor['rewards']['recipes'], f'{jar.name}: missing Queen recipe reward'
        with jar.open('rb') as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        entry = unique.setdefault(digest, dict(file=str(jar), sha256=digest,
                minecraft=minecraft, java=17 if minecraft == '1.20.1' else
                25 if minecraft.startswith('26.') else 21,
                loaders=[], equivalentFiles=[]))
        if entry['minecraft'] != minecraft:
            raise ValueError('Identical archives declare different Minecraft versions')
        entry['loaders'].append(loader)
        entry['equivalentFiles'].append(jar.name)

changelog = ROOT / f'docs/curseforge/CHANGELOG-{VERSION}.txt'
output = ROOT / f'build/curseforge/release-{VERSION}-manifest.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(dict(version=VERSION, projectId=1671346,
    changelog=str(changelog), changelogSha256=hashlib.sha256(changelog.read_bytes()).hexdigest(),
    instructions='Upload each entry once with every listed loader tag after runtime validation.',
    files=list(unique.values())), indent=2) + '\n', encoding='utf-8')
print(f'Prepared {len(unique)} unique uploads for 24 variants: {output}')

