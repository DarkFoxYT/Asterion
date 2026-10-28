"""Stage the matching renderer with native metadata; preserve its rendering classes."""
from pathlib import Path
import io, json, sys, zipfile

if sys.argv[1] == '--port-26.2':
    # Rendering and mixins come from the tested 26.2 renderer. Only the
    # loader bootstrap is shared with the neighbouring native release.
    renderer, donor, target = map(Path, sys.argv[2:5])
    platform = sys.argv[5]
    version = '1.0.0-asterion.4+26.2'
    bootstrap = ('com/meekdev/amnetic/Amnetic.class',
                 'com/meekdev/amnetic/platform/Platform.class',
                 'com/meekdev/amnetic/client/AmneticClient.class',
                 'com/meekdev/amnetic/client/ui/AmneticEditorBridge.class')
    with zipfile.ZipFile(renderer) as original, zipfile.ZipFile(donor) as native:
        entries = {n: original.read(n) for n in original.namelist()
                   if not n.endswith('/') and not n.startswith('META-INF/jars/')
                   and n not in {'fabric.mod.json', 'com/meekdev/amnetic/client/AmneticClient$1.class',
                                 'com/meekdev/amnetic/client/AmneticDataGenerator.class'}}
        for name in native.namelist():
            if name in bootstrap or name.startswith(('com/meekdev/amnetic/ForgeClientEvents',
                                                     'com/meekdev/amnetic/NeoForgeClientEvents')):
                entries[name] = native.read(name)
        # 26.3 renamed the keyboard enum; the native key registration itself
        # otherwise has the same descriptor on 26.2.
        bridge = 'com/meekdev/amnetic/client/ui/AmneticEditorBridge.class'
        entries[bridge] = entries[bridge].replace(b'\x00\x08KEYBOARD', b'\x00\x06KEYSYM')
        metadata = json.loads(native.read('META-INF/jarjar/metadata.json'))
        metadata['jars'] = [j for j in metadata['jars'] if j['identifier']['artifact'] != 'lwjgl-tinyfd']
        for jar in metadata['jars']:
            if jar['identifier']['group'] == 'org.lwjgl':
                jar['version'] = {'range': '[3.4.1,3.5)', 'artifactVersion': '3.4.1'}
                jar['path'] = jar['path'].replace('3.4.3', '3.4.1')
            path = jar['path']
            nested = original.read('META-INF/jars/' + path.rsplit('/', 1)[1])
            output = io.BytesIO()
            with zipfile.ZipFile(io.BytesIO(nested)) as source, zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as dest:
                for item in source.infolist():
                    if item.filename != 'fabric.mod.json': dest.writestr(item, source.read(item))
            entries[path] = output.getvalue()
        # Make all supported platform binaries available without a Fabric loader.
        for name in original.namelist():
            if name.startswith('META-INF/jars/lwjgl-assimp-') and '-natives-' in name:
                with zipfile.ZipFile(io.BytesIO(original.read(name))) as binaries:
                    for item in binaries.namelist():
                        if item.endswith(('.dll', '.so', '.dylib')): entries[item] = binaries.read(item)
        entries['META-INF/jarjar/metadata.json'] = json.dumps(metadata).encode()
        native_meta = 'META-INF/' + ('mods.toml' if platform == 'forge' else 'neoforge.mods.toml')
        text = native.read(native_meta).decode().replace('26.3', '26.2')
        text = text.replace('[66.0.9,)', '[65.1.5,)').replace('[26.2.0.48-beta,)', '[26.2.0.93,)')
        entries[native_meta] = text.encode()
        entries['META-INF/MANIFEST.MF'] = ('Manifest-Version: 1.0\r\n'
                'Automatic-Module-Name: com.meekdev.amnetic\r\n'
                f'Implementation-Version: {version}\r\n'
                + ('MixinConfigs: amnetic.mixins.json\r\n' if platform == 'forge' else '')
                + '\r\n').encode()
        entries['pack.mcmeta'] = json.dumps({'pack': {'description': 'Amnetic resources',
                'min_format': [88, 0], 'max_format': [121, 0]}}).encode()
        config = json.loads(entries['amnetic.mixins.json'])
        config['compatibilityLevel'] = 'JAVA_21'
        entries['amnetic.mixins.json'] = json.dumps(config).encode()
        for name, data in entries.items():
            if name.endswith('.class') and b'net/fabricmc/' in data:
                raise ValueError('Fabric dependency remains in native renderer: ' + name)
        target.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as result:
            for name, data in entries.items(): result.writestr(name, data)
    raise SystemExit(0)

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
if mc == '26.3':
    # The supplied Fabric renderer already has the backend-independent post
    # pipeline. Keep that exact implementation across all native loaders.
    companion = source.with_name('amnetic-' + version + '.jar')
    prefixes = ('com/meekdev/amnetic/client/post/internal/PostPipeline',
                'com/meekdev/amnetic/client/post/internal/NativePostPipeline',
                'com/meekdev/amnetic/client/render/NativeScreenProgram')
    with zipfile.ZipFile(companion) as native:
        for name in native.namelist():
            if name.endswith('.class') and name.startswith(prefixes):
                data = native.read(name)
                if b'net/fabricmc/' in data:
                    raise ValueError('Loader-specific code in native post pipeline: ' + name)
                replacements[name] = data
if bootstrap != '-':
    excluded.add('com/meekdev/amnetic/Amnetic.class')
    if mc == '26.2':
        excluded.add('com/meekdev/amnetic/NeoForgeClientEvents.class')
    else:
        excluded.update({
        'com/meekdev/amnetic/ForgeClientEvents.class', 'com/meekdev/amnetic/ForgeClientEvents$1.class',
        'com/meekdev/amnetic/platform/Platform.class',
        'com/meekdev/amnetic/client/ui/AmneticEditorBridge.class'})
    root = Path(bootstrap)
    replacements.update({p.relative_to(root).as_posix(): p.read_bytes() for p in root.rglob('*.class')})
target.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(source) as original, zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as output:
    if bootstrap != '-' and mc == '1.21.11':
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
