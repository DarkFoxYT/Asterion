"""Align supplied custom renderer metadata with its actual Minecraft target."""
from pathlib import Path
import hashlib, json, re, zipfile

base = Path(__file__).resolve().parents[1]
provenance = json.loads((base / 'libs/amnetic-build.json').read_text(encoding='utf-8'))
for artifact in provenance['artifacts']:
    path = base / 'libs' / artifact['file']
    mc = path.stem.split('+')[-1]
    version = '1.0.0-asterion.4+' + mc
    major, minor = {'1.21.11': (75, 0), '26.3': (97, 1)}[mc]
    pack = {'description': 'Amnetic resources', 'min_format': [major, minor], 'max_format': [major, minor]}
    if mc == '1.21.11': pack.update(pack_format=major, supported_formats=[major, major])
    updates = {'pack.mcmeta': json.dumps({'pack': pack}).encode()}
    temporary = path.with_suffix('.metadata.tmp')
    try:
        with zipfile.ZipFile(path) as original, zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED) as output:
            for entry in original.infolist():
                data = original.read(entry)
                if entry.filename == 'fabric.mod.json':
                    metadata = json.loads(data)
                    metadata['version'] = version
                    data = json.dumps(metadata, indent=2).encode()
                elif entry.filename in {'META-INF/mods.toml', 'META-INF/neoforge.mods.toml'}:
                    data = re.sub(r'(?m)^version="[^"]*"', 'version="' + version + '"', data.decode()).encode()
                if entry.filename not in updates:
                    output.writestr(entry, data)
            for name, data in updates.items(): output.writestr(name, data)
        with zipfile.ZipFile(temporary) as output:
            if output.testzip() is not None: raise ValueError('Invalid prepared renderer archive')
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)
    artifact['prepared_sha256'] = hashlib.sha256(path.read_bytes()).hexdigest()
provenance['preparation'] = ['matching Minecraft pack format', 'release version metadata', 'preset path safety']
(base / 'libs/amnetic-build.json').write_text(json.dumps(provenance, indent=2) + '\n', encoding='utf-8')
print('Prepared metadata for', len(provenance['artifacts']), 'matching custom renderers')
