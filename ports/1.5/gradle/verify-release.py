"""Audit every 1.5.3 distribution before uploading; uses Python's standard library."""
import hashlib
import io
import json
import pathlib
import re
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[3]
VERSION = "1.5.3"
rows = []


def require(value, message):
    if not value:
        raise AssertionError(message)


def inspect_nested(archive, label):
    for name in archive.namelist():
        if name.endswith("WindowGlContextMixin.class"):
            code = archive.read(name)
            require(b"WrapOperation" in code and b"injection/Redirect" not in code,
                    f"{label}: renderer still has the incompatible window Redirect")
        if not name.endswith(".jar"):
            continue
        with zipfile.ZipFile(io.BytesIO(archive.read(name))) as nested:
            if "lwjgl-assimp-" in name and "-natives-" not in name:
                require(not any(n.endswith("module-info.class") for n in nested.namelist()),
                        f"{label}/{name}: explicit Assimp module still requires client LWJGL")
                require(b"Automatic-Module-Name: org.lwjgl.assimp" in nested.read("META-INF/MANIFEST.MF"),
                        f"{label}/{name}: Assimp module identity changed")
            inspect_nested(nested, f"{label}/{name}")


for mc, java_major in [("1.20.1", 61), ("1.21.1", 65), ("26.1.2", 69)]:
    for loader in ["fabric", "quilt", "forge", "neoforge"]:
        path = ROOT / "modbuilds" / f"Asterion-{VERSION}-{loader}-mc{mc}.jar"
        require(path.is_file(), f"Missing release: {path.name}")
        with zipfile.ZipFile(path) as jar:
            require(jar.testzip() is None, f"Bad ZIP checksums: {path.name}")
            names = set(jar.namelist())
            require(not any(n.startswith("net/krodark/asterion/test/") for n in names),
                    f"Test harness leaked into {path.name}")
            for name in names:
                if name.startswith("net/krodark/asterion/") and name.endswith(".class"):
                    major = int.from_bytes(jar.read(name)[6:8], "big")
                    require(major <= java_major, f"{path.name}: {name} requires a newer Java runtime")
            config = json.loads(jar.read("asterion.mixins.json"))
            for group in ["mixins", "client", "server"]:
                for mixin in config.get(group, []):
                    entry = config["package"].replace(".", "/") + "/" + mixin.replace(".", "/") + ".class"
                    require(entry in names, f"{path.name}: missing mixin {mixin}")
            require("RagdollUseGuardMixin" in config["mixins"], f"{path.name}: missing server item-use guard")
            if loader in ("fabric", "quilt"):
                metadata = json.loads(jar.read("fabric.mod.json"))
                require(metadata["version"] == VERSION, f"Wrong version: {path.name}")
                if mc == "1.21.1":
                    require(metadata.get("custom", {}).get("asterion:integrated_amnetic"),
                            f"{path.name}: 1.5.2 renderer integration was lost")
                    require("amnetic" not in metadata["depends"], f"{path.name}: obsolete standalone renderer dependency")
                    require("META-INF/asterion/amnetic-integration.txt" in names, f"{path.name}: missing integration provenance")
                    require(not any(n.startswith(("com/meekdev/amnetic/client/ui/",
                                                  "com/meekdev/amnetic/client/particle/editor/")) for n in names),
                            f"{path.name}: removed editor feature was reintroduced")
            else:
                metadata_path = "META-INF/neoforge.mods.toml" if mc != "1.20.1" and loader == "neoforge" else "META-INF/mods.toml"
                metadata = jar.read(metadata_path).decode()
                require(re.search(r'^version\s*=\s*"1\.5\.3"', metadata, re.M), f"Wrong version: {path.name}")
            if mc != "26.1.2":
                require(config.get("plugin") == "net.krodark.asterion.mixin.AsterionMixinPlugin",
                        f"{path.name}: Sodium interpolation guard missing")
            inspect_nested(jar, path.name)
        rows.append(dict(file=path.name, minecraft=mc, loader=loader, bytes=path.stat().st_size,
                         sha256=hashlib.file_digest(path.open("rb"), "sha256").hexdigest()))
        print(f"PASS {path.name}")

output = ROOT / "ports/1.5/build/release-1.5.3-manifest.json"
output.write_text(json.dumps(dict(version=VERSION, files=rows), indent=2) + "\n")
print(f"Verified {len(rows)} release JARs; manifest: {output}")
