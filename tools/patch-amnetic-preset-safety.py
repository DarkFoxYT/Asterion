"""Restore the documented local-preset security fix to supplied renderer binaries."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile
import argparse

ROOT = Path(__file__).resolve().parents[1]
JDK = Path('C:/Program Files/Eclipse Adoptium/jdk-25.0.2.10-hotspot/bin')
SOURCE = ROOT / 'tools/amnetic-preset-safety'
OUTPUT = ROOT / 'build/amnetic-preset-safety'
CLASS = 'com/meekdev/amnetic/client/particle/editor/'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jars', nargs='+', type=Path, help='Patch only the named renderer jars.')
    args = parser.parse_args()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    subprocess.run([str(JDK / 'javac.exe'), '--release', '17', '-d', str(OUTPUT),
                    str(SOURCE / 'EffectPresetFiles.java'), str(SOURCE / 'SafePresetIO.java'),
                    str(SOURCE / 'tests/EffectPresetFilesRegression.java'),
                    str(SOURCE / 'tests/SafePresetIORegression.java')], check=True)
    subprocess.run([str(JDK / 'java.exe'), '-cp', str(OUTPUT), CLASS.replace('/', '.') + 'EffectPresetFilesRegression'], check=True)
    subprocess.run([str(JDK / 'java.exe'), '-cp', str(OUTPUT), CLASS.replace('/', '.') + 'SafePresetIORegression'], check=True)
    asm = next((Path.home() / '.gradle/caches/modules-2/files-2.1/org.ow2.asm/asm/9.9.1').glob('*/asm-9.9.1.jar'))
    classpath = os.pathsep.join((str(OUTPUT), str(asm)))
    subprocess.run([str(JDK / 'javac.exe'), '-cp', classpath, '-d', str(OUTPUT), str(SOURCE / 'PatchEffectIO.java')], check=True)
    inputs = sorted({p.resolve() for p in args.jars}) if args.jars else sorted({
        p for base in (ROOT / 'ports/1.5/libs', ROOT / 'ports/1.5/ports/26.1.2/libs')
        for p in base.glob('amnetic*.jar')})
    report_path = OUTPUT / 'report.json'
    report = json.loads(report_path.read_text()) if report_path.exists() else []
    for path in inputs:
        with zipfile.ZipFile(path) as archive:
            if CLASS + 'EffectIO.class' not in archive.namelist():
                continue
            original = archive.read(CLASS + 'EffectIO.class')
            # Idempotent: do not insert the name check again.
            if (b'SafePresetIO' in original or b'EffectPresetFiles' in original) and CLASS + 'EffectPresetFiles.class' in archive.namelist():
                print('Already safe:', path.relative_to(ROOT))
                continue
            before = hashlib.sha256(path.read_bytes()).hexdigest()
            class_in = OUTPUT / 'EffectIO-input.class'
            class_out = OUTPUT / 'EffectIO-output.class'
            class_in.write_bytes(original)
            subprocess.run([str(JDK / 'java.exe'), '-cp', classpath,
                            'PatchEffectIO', str(class_in), str(class_out)], check=True)
            replacement = {CLASS + 'EffectIO.class': class_out.read_bytes(),
                           CLASS + 'EffectPresetFiles.class': (OUTPUT / (CLASS + 'EffectPresetFiles.class')).read_bytes(),
                           CLASS + 'SafePresetIO.class': (OUTPUT / (CLASS + 'SafePresetIO.class')).read_bytes()}
            backup = OUTPUT / 'originals' / path.relative_to(ROOT)
            backup.parent.mkdir(parents=True, exist_ok=True)
            if not backup.exists():
                backup.write_bytes(path.read_bytes())
            with tempfile.NamedTemporaryFile(dir=path.parent, suffix='.jar', delete=False) as tmp:
                temporary = Path(tmp.name)
            try:
                with zipfile.ZipFile(temporary, 'w', compression=zipfile.ZIP_DEFLATED) as result:
                    for entry in archive.infolist():
                        result.writestr(entry, replacement.pop(entry.filename, archive.read(entry.filename)))
                    for name, data in replacement.items():
                        entry = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                        entry.compress_type = zipfile.ZIP_DEFLATED
                        result.writestr(entry, data)
                with zipfile.ZipFile(temporary) as result:
                    assert result.testzip() is None
                # Windows requires the reader to be closed before replacing.
                archive.close()
                temporary.replace(path)
            finally:
                temporary.unlink(missing_ok=True)
        report.append({'file': str(path.relative_to(ROOT)), 'before': before,
                       'after': hashlib.sha256(path.read_bytes()).hexdigest()})
        print('Patched:', path.relative_to(ROOT))
    report_path.write_text(json.dumps(report, indent=2) + '\n')


if __name__ == '__main__':
    main()
