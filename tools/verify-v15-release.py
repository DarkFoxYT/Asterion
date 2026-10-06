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
        assert "data/asterion/dimension/labyrinth.json" in names, "Renamed Labyrinth dimension is missing"
        assert "data/asterion/dimension/asterion_dimension.json" not in names, "Retired dimension ID still ships"
        assert "net/krodark/asterion/worldgen/LabyrinthDimensionMigration.class" in names, "World migration is missing"
        assert any(name in names for name in ("assets/asterion/geo/item/flamethrower.geo.json", "assets/asterion/geckolib/models/item/flamethrower.geo.json")), "Flamethrower model is missing"
        if major < 69:
            model = json.loads(jar.read("assets/asterion/models/item/flamethrower.json"))
            assert model.get("parent") == "builtin/entity", "Flamethrower custom model is not enabled"
            for mixin in ("PortHandRenderStateMixin", "PortAfterblowFirstPersonMixin", "FlamethrowerInputMixin"):
                assert f"net/krodark/asterion/mixin/{mixin}.class" in names, f"Missing first-person feature: {mixin}"

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
                if "/amnetic" in entry.lower():
                    prefix = "com/meekdev/amnetic/client/particle/editor/"
                    assert prefix + "EffectPresetFiles.class" in dependency.namelist(), "Missing Amnetic preset safety helper"
                    effect_io = dependency.read(prefix + "EffectIO.class")
                    assert b"SafePresetIO" in effect_io and b"validateName" in effect_io, "Unpatched Amnetic preset file operations"
                    assert b"java/nio/file/Files" not in effect_io, "Amnetic still bypasses safe preset storage"
                    for config in (name for name in dependency.namelist() if name.endswith(".mixins.json")):
                        renderer_mixin = json.loads(dependency.read(config))
                        level = renderer_mixin.get("compatibilityLevel", "JAVA_8")
                        assert int(level.removeprefix("JAVA_")) <= major - 44, f"{entry}/{config}: unsupported {level}"
                        for side in ("mixins", "client", "server"):
                            for name in renderer_mixin.get(side, []):
                                target = (renderer_mixin["package"] + "." + name).replace(".", "/") + ".class"
                                assert target in dependency.namelist(), f"{entry}/{config}: missing {target}"
                if loader in ("fabric", "quilt") and major in (61, 65) and "/amnetic" in entry.lower():
                    renderer_metadata = json.loads(dependency.read("fabric.mod.json"))
                    assert "asterion.9" in renderer_metadata["version"], "Unpatched legacy renderer embedded"
                if loader == "neoforge" and major == 69 and "/amnetic" in entry.lower():
                    renderer_metadata = tomllib.loads(dependency.read("META-INF/neoforge.mods.toml").decode())
                    renderer_version = next(mod["version"] for mod in renderer_metadata["mods"] if mod["modId"] == "amnetic")
                    required = next(mod["versionRange"] for mod in metadata["dependencies"]["asterion"] if mod["modId"] == "amnetic")
                    assert required == f"[{renderer_version},)", "Embedded Amnetic does not satisfy the declared renderer version"
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
        assert "net/krodark/asterion/worldgen/ArenaSurfaceSeam.class" in names, "Missing arena/maze boundary repair"
        assert b"waterproofLever" in jar.read("net/krodark/asterion/worldgen/CatacombProtection.class"), "Missing Labyrinth lever protection"
        assert b"waterproofLever" in jar.read("net/krodark/asterion/mixin/HeavyWaterFlowMixin.class"), "Missing water-flow lever protection hook"
        if loader == "forge" and major == 69:
            assert "net/fabricmc/fabric/api/client/item/v1/ItemTooltipCallback.class" in names, "Missing Forge tooltip event adapter"
        for name in classes:
            bytecode = jar.read(name)
            assert struct.unpack(">H", bytecode[6:8])[0] <= major, f"Unsupported Java bytecode: {name}"
        assert not any(name.startswith("net/krodark/asterion/test/") for name in names), "Test harness shipped"
        if major in (61, 65):
            bloom_patch = jar.read("net/krodark/asterion/mixin/AmneticBloomPerformanceMixin.class")
            assert b"asterion$worldDepthResolution" in bloom_patch, "Missing full-resolution emission depth fix"
        for excluded in ("data/asterion/dimension/limbo.json", "data/asterion/dimension/underworld.json", "data/asterion/dimension_type/limbo.json", "data/asterion/dimension_type/underworld.json", "data/asterion/worldgen/biome/limbo.json"):
            assert excluded not in names, f"Excluded 1.5 dimension shipped: {excluded}"
        for shared in ("entity/PhysicsChainEntity", "entity/CentipedeSegmentEntity", "physics/SegmentedChain", "physics/ChainContact", "physics/VoxelCollisionCache", "physics/SwordAttackMotion", "game/FinaleTimeline"):
            assert f"net/krodark/asterion/{shared}.class" in names, f"Missing shared improvement: {shared}"
        client_root = "client" if major == 69 else "port/client"
        for feature in (("particle/GroundFogParticle", "render/entity/PhysicsChainRenderer", "cinematic/BossCreditsScreen") if major == 69 else ("particle/GroundFogParticle", "PortPhysicsChainRenderer", "PortBossCreditsScreen", "PortPostBuffers")):
            assert f"net/krodark/asterion/{client_root}/{feature}.class" in names, f"Missing client feature: {feature}"
        settings = "client/AsterionSettingsScreen" if major == 69 else "port/client/PortAsterionSettingsScreen"
        settings_code = jar.read(f"net/krodark/asterion/{settings}.class")
        assert b"performanceTargetFps" not in settings_code, "FPS target returned to the essential settings UI"
        sounds = json.loads(jar.read("assets/asterion/sounds.json"))
        english = json.loads(jar.read("assets/asterion/lang/en_us.json"))
        tracks = json.loads(jar.read("assets/asterion/music_tracks.json"))
        for track in tracks:
            namespace, event = track["sound"].split(":", 1)
            assert namespace == "asterion" and event in sounds, "Music track references an unregistered sound"
            label = english[sounds[event]["subtitle"]]
            assert track["title"] in label and track["artist"] in label, "Music label is missing its title or artist"
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
