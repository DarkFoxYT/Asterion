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
        for excluded in ("data/asterion/dimension/limbo.json", "data/asterion/dimension/underworld.json", "data/asterion/dimension_type/limbo.json", "data/asterion/dimension_type/underworld.json", "data/asterion/worldgen/biome/limbo.json"):
            assert excluded not in names, f"Excluded 1.5 dimension shipped: {excluded}"
        for shared in ("entity/PhysicsChainEntity", "entity/CentipedeSegmentEntity", "physics/SegmentedChain", "physics/VoxelCollisionCache", "physics/SwordAttackMotion", "game/FinaleTimeline"):
            assert f"net/krodark/asterion/{shared}.class" in names, f"Missing shared improvement: {shared}"
        client_root = "client" if major == 69 else "port/client"
        for feature in (("particle/GroundFogParticle", "render/entity/PhysicsChainRenderer", "cinematic/BossCreditsScreen") if major == 69 else ("particle/GroundFogParticle", "PortPhysicsChainRenderer", "PortBossCreditsScreen", "PortPostBuffers")):
            assert f"net/krodark/asterion/{client_root}/{feature}.class" in names, f"Missing client feature: {feature}"
        recipe_path = "data/asterion/recipes/physics_chain.json" if major == 61 else "data/asterion/recipe/physics_chain.json"
        recipe = json.loads(jar.read(recipe_path))
        if major < 69:
            assert recipe["key"]["C"] == {"item": "asterion:mazesteel_chain"}, "Invalid legacy chain ingredient"
        result_key = "item" if major == 61 else "id"
        assert recipe["result"][result_key] == "asterion:physics_chain", "Invalid chain recipe result"
        for resource in ("particles/ground_fog.json", "shaders/particle/ground_fog.fsh", "shaders/post/dimension/volume_integrate.fsh"):
            assert f"assets/asterion/{resource}" in names, f"Missing haze resource: {resource}"
        if major < 69:
            for name in ("dead_sun", "dusty_air", "dusty_air_high"):
                effect = json.loads(jar.read(f"assets/asterion/post_effect/dimension/{name}.json"))
                for render_pass in effect["passes"]:
                    for shader_key, extension in (("vertex_shader", "vsh"), ("fragment_shader", "fsh")):
                        namespace, shader = render_pass[shader_key].split(":", 1)
                        if namespace == "minecraft":
                            assert shader_key == "vertex_shader" and shader == "core/screenquad", f"Unsupported legacy shader: {render_pass[shader_key]}"
                        else:
                            assert namespace == "asterion", f"Unexpected shader namespace: {namespace}"
                            assert f"assets/asterion/shaders/{shader}.{extension}" in names, f"Missing shader {shader}"
        hint_mixin = "LimboDeathScreenMixin" if major == 69 else "PortDeathScreenMixin"
        hints = jar.read(f"net/krodark/asterion/mixin/{hint_mixin}.class")
        assert b"tip.asterion.death.chain" in hints, "Death-screen tips missing"
        assert b"asterion$automaticPassage" not in hints, "Limbo handoff enabled in 1.5"
        sound_events = json.loads(jar.read("assets/asterion/sounds.json"))
        for event, definition in sound_events.items():
            for sound in definition.get("sounds", []):
                if isinstance(sound, dict) and sound.get("type") == "event":
                    continue
                name = sound if isinstance(sound, str) else sound.get("name", "")
                namespace, _, asset = name.partition(":")
                if namespace == "asterion":
                    assert f"assets/asterion/sounds/{asset}.ogg" in names, f"Missing audio for {event}: {asset}"
        if major < 69:
            assert "assets/asterion/models/item/physics_chain.json" in names, "Missing legacy chain item model"
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
