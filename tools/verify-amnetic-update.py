"""Verify each 2.0/26.1.2 package embeds the exact patched Amnetic artifact."""
import hashlib
import argparse
import io
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def check_rendering_resources(archive, native=False):
    for name in ['assets/amnetic/shaders/bloom/composite.fsh',
                 'assets/asterion/shaders/particle/firefly_core.fsh',
                 'assets/asterion/shaders/include/scene_depth.glsl']:
        source = (ROOT / 'src/main/resources' / name).read_text().replace('\r\n', '\n')
        packaged = archive.read(name).decode().replace('\r\n', '\n')
        assert packaged == source, f'{archive.filename}: stale rendering resource {name}'
    if native:
        shader = archive.read('assets/asterion/shaders/post/underworld/river_atmosphere.fsh')
        assert b'#define ASTERION_REVERSED_DEPTH' in shader, f'{archive.filename}: conventional native depth'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--native', action='store_true', help='Check the 26.2/26.3 Fabric and Quilt packages')
    args = parser.parse_args()
    if args.native:
        manifest = json.loads((ROOT / 'libs/amnetic-native-build.json').read_text(encoding='utf-8-sig'))
        for artifact in manifest['artifacts']:
            for loader in artifact['loaders']:
                package = ROOT / f"modbuilds/Asterion-2.0.0-{loader}-mc{artifact['minecraft']}.jar"
                with zipfile.ZipFile(package) as archive:
                    check_rendering_resources(archive, native=True)
                    assert archive.testzip() is None, f'{package.name}: corrupt archive'
                    nested = [name for name in archive.namelist() if name.startswith('META-INF/jars/amnetic') and name.endswith('.jar')]
                    assert len(nested) == 1, (package.name, nested)
                    data = archive.read(nested[0])
                    assert hashlib.sha256(data).hexdigest() == artifact['sha256'], f'{package.name}: stale native Amnetic'
                    with zipfile.ZipFile(io.BytesIO(data)) as renderer:
                        assert renderer.testzip() is None, f'{package.name}: corrupt renderer'
                        for name in ['client/post/internal/NativePostPipeline', 'client/instanced/internal/NativeInstanceRenderer',
                                     'client/render/NativeScreenProgram', 'client/particle/editor/SafePresetIO']:
                            assert f'com/meekdev/amnetic/{name}.class' in renderer.namelist(), (package.name, name)
                print(f'PASS {package.name}: exact native renderer, GPU post/particle paths, preset safety, archive integrity')
        return
    manifest = json.loads((ROOT / 'libs/amnetic-build.json').read_text(encoding='utf-8-sig'))
    hashes = {
        'fabric': manifest['fabricQuiltSha256'],
        'quilt': manifest['fabricQuiltSha256'],
        'forge': manifest['forgeSha256'],
        'neoforge': manifest['neoForgeSha256'],
    }
    for loader, expected in hashes.items():
        package = ROOT / f'modbuilds/Asterion-2.0.0-{loader}-mc26.1.2.jar'
        with zipfile.ZipFile(package) as archive:
            check_rendering_resources(archive)
            nested = [name for name in archive.namelist()
                      if name.startswith(('META-INF/jars/amnetic', 'META-INF/jarjar/amnetic'))
                      and name.endswith('.jar')]
            assert len(nested) == 1, (package.name, nested)
            data = archive.read(nested[0])
            assert hashlib.sha256(data).hexdigest() == expected, f'{package.name}: stale Amnetic'
            with zipfile.ZipFile(io.BytesIO(data)) as renderer:
                assert renderer.testzip() is None, f'{package.name}: corrupt renderer'
                prefix = 'com/meekdev/amnetic/'
                hook = renderer.read(prefix + 'mixin/WindowGlContextMixin.class')
                assert b'WrapOperation' in hook and b'Operation' in hook, f'{package.name}: window hook'
                editor = prefix + 'client/particle/editor/'
                effect_io = renderer.read(editor + 'EffectIO.class')
                assert b'SafePresetIO' in effect_io, f'{package.name}: unsafe presets'
                assert editor + 'SafePresetIO.class' in renderer.namelist()
                assert editor + 'EffectPresetFiles.class' in renderer.namelist()
        print(f'PASS {package.name}: exact renderer hash, window hook, preset safety, archive integrity')


if __name__ == '__main__':
    main()
