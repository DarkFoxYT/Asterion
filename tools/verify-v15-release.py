"""Check every 1.5 release artifact without loading Minecraft or external libraries."""
import io
import json
from pathlib import Path
import struct
import tomllib
import zipfile


SHELF = Path(__file__).resolve().parents[1] / "modbuilds"
VERSIONS = {"1.20.1": 61, "1.21.1": 65, "26.1.2": 69}
LOADERS = ("fabric", "quilt", "forge", "neoforge")


def verify(path, loader, major):
    with zipfile.ZipFile(path) as jar:
        assert jar.testzip() is None, f"{path.name}: corrupt ZIP entry"
        names = set(jar.namelist())
        if loader in ("fabric", "quilt"):
            metadata = json.loads(jar.read("fabric.mod.json"))
            assert metadata["id"] == "asterion" and metadata["version"] == "1.5"
            nested = [item["file"] for item in metadata.get("jars", [])]
            mixins = [item if isinstance(item, str) else item["config"]
                      for item in metadata.get("mixins", [])]
            for entries in metadata.get("entrypoints", {}).values():
                for entry in entries:
                    value = entry if isinstance(entry, str) else entry["value"]
                    class_path = value.split("::")[0].replace(".", "/") + ".class"
                    assert class_path in names, f"Missing entrypoint {value}"
        else:
            metadata_path = ("META-INF/neoforge.mods.toml"
                             if "META-INF/neoforge.mods.toml" in names else "META-INF/mods.toml")
            metadata = tomllib.loads(jar.read(metadata_path).decode())
            mod = next(item for item in metadata["mods"] if item["modId"] == "asterion")
            assert mod["version"] in ("1.5", "${file.jarVersion}")
            manifest = jar.read("META-INF/MANIFEST.MF").decode().replace("\r\n ", "")
            if mod["version"] == "${file.jarVersion}":
                assert "Implementation-Version: 1.5" in manifest
            nested = [item["path"] for item in json.loads(jar.read("META-INF/jarjar/metadata.json"))["jars"]]
            mixins = [item["config"] for item in metadata.get("mixins", [])]
            for line in manifest.splitlines():
                if line.startswith("MixinConfigs:"):
                    mixins.extend(line.split(":", 1)[1].strip().split(","))
        assert sum("/amnetic" in item.lower() for item in nested) == 1, "Expected one embedded Amnetic"
        for entry in nested:
            with zipfile.ZipFile(io.BytesIO(jar.read(entry))) as dependency:
                assert dependency.testzip() is None, f"Corrupt nested dependency {entry}"
                if loader == "forge" and major == 69 and "/amnetic" in entry.lower():
                    pack = json.loads(dependency.read("pack.mcmeta"))["pack"]
                    assert pack.get("min_format") == [101, 1] and pack.get("max_format") == [101, 1], "Stale Amnetic pack metadata"
        for config in set(mixins):
            mixin = json.loads(jar.read(config))
            compatibility = mixin.get("compatibilityLevel", "JAVA_8")
            assert int(compatibility.removeprefix("JAVA_")) <= major - 44, f"{config}: unsupported {compatibility}"
            for side in ("mixins", "client", "server"):
                for name in mixin.get(side, []):
                    class_path = (mixin["package"] + "." + name).replace(".", "/") + ".class"
                    assert class_path in names, f"{config}: missing {class_path}"
        classes = [name for name in names if name.startswith("net/krodark/") and name.endswith(".class")]
        assert classes, "No Asterion classes"
        for name in classes:
            bytecode = jar.read(name)
            assert struct.unpack(">H", bytecode[6:8])[0] <= major, f"Unsupported Java bytecode: {name}"
        assert not any(name.startswith("net/krodark/asterion/test/") for name in names), "Test harness shipped"
        print(f"PASS {path.name}: {len(classes)} classes, {len(nested)} embedded dependencies")


if __name__ == "__main__":
    failures = []
    for version, major in VERSIONS.items():
        for loader in LOADERS:
            path = SHELF / f"Asterion-1.5-{loader}-mc{version}.jar"
            try:
                verify(path, loader, major)
            except (AssertionError, KeyError, OSError, ValueError, zipfile.BadZipFile) as error:
                failures.append(f"{path.name}: {error}")
    if failures:
        raise SystemExit("\n".join(failures))
    print("All 12 Asterion 1.5 release artifacts passed.")
