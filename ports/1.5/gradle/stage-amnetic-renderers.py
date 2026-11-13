"""Stage matching upstream Amnetic builds and record their provenance."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[3]
parser = argparse.ArgumentParser()
parser.add_argument('checkout', type=Path)
parser.add_argument('--version', default='1.0.0-asterion.10')
args = parser.parse_args()
source = args.checkout.resolve()
commit = subprocess.check_output(['git', '-C', str(source), 'rev-parse', 'HEAD'], text=True).strip()
patch = ROOT / 'ports/1.5/gradle/amnetic-26.2-hand-depth.patch'
source_diff = subprocess.check_output(['git', '-C', str(source), 'diff', '--'], text=True)
if source_diff.strip() != patch.read_text(encoding='utf-8-sig').strip():
    raise SystemExit('Apply the maintained amnetic-26.2-hand-depth.patch before building; unexpected source changes found')
version = args.version
groups = {}

def stage(mc, loader, destination, *, dev=False):
    artifact = 'amnetic' if loader == 'fabric' else f'amnetic-{loader}'
    suffix = '-dev-shadow' if dev else (f'-{loader}' if loader in ('forge', 'neoforge') else '')
    folder = source / ('neoforge' if (mc, loader) == ('26.1.2', 'neoforge') else f'versions/{mc}-{loader}')
    original = folder / f'build/libs/{artifact}-{version}+{mc}{suffix}.jar'
    if not original.is_file():
        raise FileNotFoundError(original)
    destination.parent.mkdir(parents=True, exist_ok=True)
    replacements = {}
    excluded = set()
    with zipfile.ZipFile(original) as jar:
        if mc in ('1.21.11', '26.2', '26.3') and loader in ('fabric', 'quilt'):
            pack_format = {'1.21.11': [75, 0], '26.2': [88, 0], '26.3': [97, 1]}[mc]
            replacements['pack.mcmeta'] = json.dumps({'pack': {'description': 'Amnetic resources',
                    'min_format': pack_format, 'max_format': pack_format}}).encode()
        if loader == 'forge' and mc in ('26.2', '26.3'):
            metadata = json.loads(jar.read('META-INF/jarjar/metadata.json'))
            tinyfd = [j for j in metadata['jars'] if j['identifier'] == {'group': 'org.lwjgl', 'artifact': 'lwjgl-tinyfd'}]
            excluded.update(j['path'] for j in tinyfd)
            metadata['jars'] = [j for j in metadata['jars'] if j not in tinyfd]
            replacements['META-INF/jarjar/metadata.json'] = json.dumps(metadata).encode()
            for name in jar.namelist():
                if name.endswith('mixins.json'):
                    config = json.loads(jar.read(name))
                    config['compatibilityLevel'] = 'JAVA_21'
                    replacements[name] = json.dumps(config).encode()
        if replacements or excluded:
            with zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED) as output:
                for item in jar.infolist():
                    if item.filename not in replacements and item.filename not in excluded:
                        output.writestr(item, jar.read(item))
                for name, content in replacements.items():
                    info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    output.writestr(info, content)
        else:
            shutil.copyfile(original, destination)
    groups.setdefault(destination.parent, []).append({'file': destination.name, 'minecraft': mc,
            'loader': loader, 'developmentMappings': dev,
            'sourceArtifact': original.relative_to(source).as_posix(),
            'sourcePatch': patch.relative_to(ROOT).as_posix() if mc in ('26.2', '26.3') else None,
            'upstreamSha256': hashlib.sha256(original.read_bytes()).hexdigest(),
            'sha256': hashlib.sha256(destination.read_bytes()).hexdigest()})

legacy = ROOT / 'ports/1.5/libs'
for mc in ('1.20.1', '1.21.1'):
    stage(mc, 'fabric', legacy / f'amnetic-{version}+{mc}.jar')
    stage(mc, 'forge', legacy / f'amnetic-forge-{version}+{mc}-forge.jar')
stage('1.21.1', 'fabric', legacy / f'amnetic-{version}+1.21.1-dev.jar', dev=True)
native26 = ROOT / 'ports/1.5/ports/26.1.2/libs'
for loader in ('fabric', 'forge', 'neoforge'):
    artifact = 'amnetic' if loader == 'fabric' else f'amnetic-{loader}'
    stage('26.1.2', loader, native26 / f'{artifact}-{version}+26.1.2.jar')
modern = ROOT / 'ports/1.5/ports/1.6.2/libs'
for mc in ('1.21.11', '26.2', '26.3'):
    for loader in ('fabric', 'forge', 'neoforge', 'quilt'):
        if loader == 'neoforge' and mc == '1.21.11': continue
        if loader == 'quilt' and mc != '26.3': continue
        artifact = 'amnetic' if loader == 'fabric' else f'amnetic-{loader}'
        stage(mc, loader, modern / f'{artifact}-{version}+{mc}.jar')
for folder, artifacts in groups.items():
    provenance = {'upstream': 'https://github.com/Meekiavelique/Amnetic', 'upstreamCommit': commit,
                  'version': version, 'artifacts': artifacts,
                  'preparation': ['Minecraft-specific resource-pack metadata for the extra Fabric/Quilt ports',
                                  'Forge 26.2/26.3 uses Minecraft TinyFD and supported Mixin metadata',
                                  'Native adapters and API isolation are applied by the Asterion build'],
                  'rebuild': f'Apply ports/1.5/gradle/amnetic-26.2-hand-depth.patch, build source targets with -Pmod_version={version}, then run ports/1.5/gradle/stage-amnetic-renderers.py CHECKOUT'}
    (folder / 'amnetic-build.json').write_text(json.dumps(provenance, indent=2) + '\n', encoding='utf-8')
print(f'Staged {sum(map(len, groups.values()))} renderer artifacts from {commit}')
