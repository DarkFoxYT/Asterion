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
for minecraft in ('1.20.1', '1.21.1', '26.1.2', '1.21.11', '26.3'):
    for loader in ('Fabric', 'Quilt', 'Forge', 'NeoForge'):
        jar = ROOT / 'modbuilds' / f'Asterion-{VERSION}-{loader.lower()}-mc{minecraft}.jar'
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

changelog = ROOT / f'docs/curseforge/CHANGELOG-{VERSION}.md'
output = ROOT / f'build/curseforge/release-{VERSION}-manifest.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(dict(version=VERSION, projectId=1671346,
    changelog=str(changelog), changelogSha256=hashlib.sha256(changelog.read_bytes()).hexdigest(),
    instructions='Upload each entry once with every listed loader tag after runtime validation.',
    files=list(unique.values())), indent=2) + '\n', encoding='utf-8')
print(f'Prepared {len(unique)} unique uploads for 20 variants: {output}')

