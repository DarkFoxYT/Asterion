"""Stage the matching renderer with native metadata; preserve its rendering classes."""
from pathlib import Path
import json, sys, zipfile

source, target = map(Path, sys.argv[1:3])
mc, version, loader, major, minor, bootstrap = sys.argv[3:]
# NeoForge fills in the current version separately for assets and server data.
pack = {'description': 'Amnetic resources'}
replacements = {
    'pack.mcmeta': json.dumps({'pack': pack}).encode(),
    'META-INF/neoforge.mods.toml': f'''modLoader="javafml"
loaderVersion="[4,)"
license="All-Rights-Reserved"
[[mods]]
modId="amnetic"
version="{version}"
displayName="Amnetic"
description="Custom rendering library for Asterion."
[[dependencies.amnetic]]
modId="neoforge"
type="required"
versionRange="[{loader},)"
side="CLIENT"
[[dependencies.amnetic]]
modId="minecraft"
type="required"
versionRange="[{mc}]"
side="CLIENT"
[[mixins]]
config="amnetic.mixins.json"
'''.encode(),
    'META-INF/MANIFEST.MF': ('Manifest-Version: 1.0\r\nAutomatic-Module-Name: com.meekdev.amnetic\r\n'
        f'Implementation-Version: {version}\r\n\r\n').encode(),
}
excluded = {'META-INF/mods.toml'}
if bootstrap != '-':
    excluded.update({'com/meekdev/amnetic/Amnetic.class',
        'com/meekdev/amnetic/ForgeClientEvents.class', 'com/meekdev/amnetic/ForgeClientEvents$1.class',
        'com/meekdev/amnetic/platform/Platform.class',
        'com/meekdev/amnetic/client/ui/AmneticEditorBridge.class'})
    root = Path(bootstrap)
    replacements.update({p.relative_to(root).as_posix(): p.read_bytes() for p in root.rglob('*.class')})
target.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(source) as original, zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as output:
    if bootstrap != '-':
        mixins = json.loads(original.read('amnetic.mixins.json'))
        mixins['client'].append('NeoForgeTextureMixin')
        replacements['amnetic.mixins.json'] = json.dumps(mixins, indent=2).encode()
    for entry in original.infolist():
        # Minecraft owns LWJGL core. A shaded copy in the renderer creates a
        # second SharedLibrary type and breaks native modules such as FreeType.
        if entry.filename.endswith('.class') and (entry.filename.startswith('org/lwjgl/')
                or (entry.filename.startswith('META-INF/versions/') and '/org/lwjgl/' in entry.filename)):
            continue
        if entry.filename not in excluded and entry.filename not in replacements:
            data = original.read(entry)
            if bootstrap != '-' and entry.filename.endswith('.class') and b'net/minecraftforge/' in data:
                raise ValueError(f'Unported Forge dependency in {entry.filename}')
            output.writestr(entry, data)
    for name, data in replacements.items(): output.writestr(name, data)
