"""Audit release distributions before uploading; uses Python's standard library."""
import argparse
import hashlib
import io
import json
import pathlib
import re
import struct
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[3]
VERSION = "1.6.1"
rows = []
parser = argparse.ArgumentParser()
parser.add_argument('--version', default=VERSION)
parser.add_argument('--minecraft', choices=('1.20.1', '1.21.1', '26.1.2'))
parser.add_argument('--loader', choices=('fabric', 'quilt', 'forge', 'neoforge'))
args = parser.parse_args()
VERSION = args.version


def require(value, message):
    if not value:
        raise AssertionError(message)


def class_members(data):
    """Read field/method flags from the shipped class, without loading Mixins."""
    pos = 10
    strings = {}
    index = 1
    while index < int.from_bytes(data[8:10], 'big'):
        tag = data[pos]
        pos += 1
        if tag == 1:
            size = int.from_bytes(data[pos:pos + 2], 'big')
            strings[index] = data[pos + 2:pos + 2 + size].decode('utf-8', errors='replace')
            pos += size + 2
        else:
            pos += {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4,
                    11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}[tag]
            if tag in (5, 6): index += 1
        index += 1
    pos += 6
    interfaces = int.from_bytes(data[pos:pos + 2], 'big')
    pos += 2 + interfaces * 2
    groups = []
    for _ in range(2):
        count = int.from_bytes(data[pos:pos + 2], 'big')
        pos += 2
        members = {}
        for _ in range(count):
            flags, name, descriptor, attributes = struct.unpack_from('>HHHH', data, pos)
            pos += 8
            members[strings[name]] = flags
            for _ in range(attributes):
                length = int.from_bytes(data[pos + 2:pos + 6], 'big')
                pos += 6 + length
        groups.append(members)
    return groups


def inspect_nested(archive, label):
    for name in archive.namelist():
        if name == 'com/meekdev/amnetic/mixin/LivingEntityRendererMixin.class':
            require(b'injection/Redirect' not in archive.read(name),
                    f'{label}: exclusive Amnetic layer redirect still conflicts with other renderer mods')
        if name.endswith("WindowGlContextMixin.class"):
            code = archive.read(name)
            require(b"WrapOperation" in code and b"injection/Redirect" not in code,
                    f"{label}: renderer still has the incompatible window Redirect")
        if not name.endswith(".jar"):
            continue
        with zipfile.ZipFile(io.BytesIO(archive.read(name))) as nested:
            if 'amnetic-forge-' in name and '1.20.1' in name:
                require(b'MixinConfigs: amnetic.mixins.json' in nested.read('META-INF/MANIFEST.MF'),
                        f'{label}/{name}: legacy renderer mixins are not registered')
                config = json.loads(nested.read('amnetic.mixins.json'))
                require(config.get('refmap') in nested.namelist(),
                        f'{label}/{name}: legacy renderer reference map is not enabled')
            if "lwjgl-assimp-" in name and "-natives-" not in name:
                require(not any(n.endswith("module-info.class") for n in nested.namelist()),
                        f"{label}/{name}: explicit Assimp module still requires client LWJGL")
                require(b"Automatic-Module-Name: org.lwjgl.assimp" in nested.read("META-INF/MANIFEST.MF"),
                        f"{label}/{name}: Assimp module identity changed")
            inspect_nested(nested, f"{label}/{name}")


for mc, java_major in [("1.20.1", 61), ("1.21.1", 65), ("26.1.2", 69)]:
    for loader in ["fabric", "quilt", "forge", "neoforge"]:
        if args.minecraft and args.minecraft != mc or args.loader and args.loader != loader:
            continue
        path = ROOT / "modbuilds" / f"Asterion-{VERSION}-{loader}-mc{mc}.jar"
        require(path.is_file(), f"Missing release: {path.name}")
        with zipfile.ZipFile(path) as jar:
            require(jar.testzip() is None, f"Bad ZIP checksums: {path.name}")
            names = set(jar.namelist())
            if VERSION in ('1.6.1', '1.6.2', '1.6.3'):
                ragdoll = 'net/krodark/asterion/' + ('client/ragdoll/' if mc == '26.1.2' else 'port/client/ragdoll/')
                engine = jar.read(ragdoll + 'DismembermentEngine.class')
                require(b'lifetimeTicks' in engine and b'AncientSkeletonEntity' in engine
                        and b'synchronizeHeldItemGrip' in engine and b'skeletonBody' in engine,
                        f'{path.name}: ancient skeleton corpse/gear/physics fixes missing')
                _, event_methods = class_members(jar.read('net/krodark/asterion/event/DeadSunEventSystem.class'))
                require('syncPlayers' in event_methods, f'{path.name}: eclipse recipient filtering missing')
                presentation = ('client/event/DeadSunClientEvents' if mc == '26.1.2' else 'port/client/PortDeadSunEvents')
                _, presentation_methods = class_members(jar.read('net/krodark/asterion/' + presentation + '.class'))
                require('stopEclipseSound' in presentation_methods and 'eclipseExposed' in presentation_methods,
                        f'{path.name}: underground eclipse presentation/audio filtering missing')
                if mc == '26.1.2':
                    require('net/krodark/asterion/mixin/GlCommandEncoderStateMixin.class' in names
                            and 'GlCommandEncoderStateMixin' in json.loads(jar.read('asterion.mixins.json'))['client'],
                            f'{path.name}: vanilla shader state restoration missing')
                _, forge_methods = class_members(jar.read('net/krodark/asterion/worldgen/ForgeDepths.class'))
                require('repairAccess' in forge_methods, f'{path.name}: existing Forge entrance repair missing')
                _, gate_methods = class_members(jar.read('net/krodark/asterion/worldgen/MinotaurArenaEntrances.class'))
                require('inChunk' in gate_methods, f'{path.name}: chunk-clipped gate placement missing')
                if mc != '26.1.2' and loader in ('forge', 'neoforge'):
                    require(b'HumanoidArmorLayer' in jar.read('net/krodark/asterion/port/client/AncientSkeletonRenderer.class'),
                            f'{path.name}: ancient skeleton armor layer missing')
            require(not names.intersection({
                'data/asterion/dimension/limbo.json', 'data/asterion/dimension/underworld.json',
                'data/asterion/dimension_type/limbo.json', 'data/asterion/dimension_type/underworld.json',
                'data/asterion/worldgen/biome/limbo.json'}),
                f'{path.name}: unreleased dimension data leaked into a Labyrinth release')
            if loader == 'forge' and mc != '1.20.1':
                require('net/fabricmc/fabric/api/networking/v1/PlayerLookup.class' in names,
                        f'{path.name}: native player-tracking adapter is missing')
            require('net/krodark/asterion/item/SickenedTwinbladesItem.class' not in names,
                    f'{path.name}: retired weapon implementation is still packaged')
            combat = jar.read('net/krodark/asterion/game/WeaponCombatSystem.class')
            require(b'updateSpeed' not in combat and b'sickened_twinblades_combo_speed' not in combat,
                    f'{path.name}: retired combo/modifier loop is still packaged')
            model = json.loads(jar.read('assets/asterion/models/item/respawn_obelisk.json'))
            require(len(model.get('elements', [])) == 2
                    and model.get('textures', {}).get('obelisk') == 'asterion:block/respawn_obelisk',
                    f'{path.name}: obelisk GUI still uses the approximate block model')
            catalog = json.loads(jar.read('data/asterion/maze_structures.json'))
            require(not any(entry['template'] == 'asterion:rune_gate' for entry in catalog['templates']),
                    f'{path.name}: retired rune gate can still generate')
            for shader_mixin in ('AmneticMainFramebufferMixin', 'AmneticGBufferFramebufferMixin',
                                 'IrisLabyrinthPipelineMixin', 'OptifineLabyrinthShadersMixin',
                                 'AmneticShaderPipelineMixin', 'AmneticShaderPostMixin'):
                require(f'net/krodark/asterion/mixin/{shader_mixin}.class' in names,
                        f'{path.name}: missing shader compatibility hook {shader_mixin}')
            legacy_forge = mc == '1.20.1' and loader in ('forge', 'neoforge')
            if legacy_forge and loader == 'forge':
                require(not any(name.startswith('data/asterion/forge/biome_modifier/') for name in names),
                        f'{path.name}: duplicate native biome modifiers would cause worldgen feature cycles')
            if loader == 'forge' and mc == '1.21.1':
                require('data/asterion/forge/biome_modifier/ocean_ruins.json' in names,
                        f'{path.name}: modern Forge still needs its native biome modifiers')
            pickup_mixin = 'legacy/HeavyWaterPickupMixin' if legacy_forge else 'HeavyWaterPickupMixin'
            pickup_code = jar.read(f'net/krodark/asterion/mixin/{pickup_mixin}.class')
            _, handlers = class_members(pickup_code)
            flags = handlers['asterion$pickUpHeavyWater']
            if legacy_forge:
                require(b'WrapOperation' in pickup_code and b'world/item/BucketItem' in pickup_code,
                        f'{path.name}: legacy pickup must use the class-based bucket hook')
            else:
                require(flags & 1 and not flags & (2 | 0x400),
                        f'{path.name}: heavy-water interface handler must be public with a body')
            require(not any(n.startswith("net/krodark/asterion/test/") for n in names),
                    f"Test harness leaked into {path.name}")
            require(not any(n.startswith('me/lucko/spark/') or '/spark-' in n.lower() for n in names),
                    f'Spark profiler leaked into {path.name}')
            require(b'spareCells' in jar.read('net/krodark/asterion/physics/VoxelCollisionCache.class'),
                    f'{path.name}: missing bounded collision-storage reuse')
            if VERSION in ('1.5.9.5', '1.6', '1.6.1') and mc != '26.1.2':
                light_code = jar.read('net/krodark/asterion/port/client/PortPointLights.class')
                _, light_methods = class_members(light_code)
                require(all(light_methods[method] & 0x20 for method in
                            ('update', 'loaded', 'cull', 'remove', 'clear', 'nearestAttractor')),
                        f'{path.name}: unguarded point-light collection access')
                require(b'copyOf' in light_code, f'{path.name}: culling still uses a live map iterator')
                boss_code = jar.read('net/krodark/asterion/mixin/PortMinotaurBossBarMixin.class')
                require(b'ModifyExpressionValue' in boss_code and b'injection/Redirect' not in boss_code,
                        f'{path.name}: boss-name hook still conflicts with exclusive redirects')
            if mc == '26.1.2':
                require(b'updateRecipes' in jar.read('net/krodark/asterion/client/render/ClientRenderCaches.class'),
                        f'{path.name}: missing recipe-search world-retention fix')
                if VERSION in ('1.6', '1.6.1'):
                    boss_code = jar.read('net/krodark/asterion/mixin/MinotaurBossBarMixin.class')
                    require(b'ModifyExpressionValue' in boss_code and b'injection/Redirect' not in boss_code,
                            f'{path.name}: boss-name hook still uses an exclusive redirect')
            for name in names:
                if name.startswith("net/krodark/asterion/") and name.endswith(".class"):
                    major = int.from_bytes(jar.read(name)[6:8], "big")
                    require(major <= java_major, f"{path.name}: {name} requires a newer Java runtime")
            if VERSION in ('1.6', '1.6.1'):
                _, world_methods = class_members(jar.read('net/krodark/asterion/worldgen/WorldGenerator.class'))
                require('trackExplosionBreak' in world_methods and 'restoresExplosionBlock' in world_methods,
                        f'{path.name}: explosion terrain restoration missing')
                require(any(name.startswith('lambda$tickRestoringBlocks$') for name in world_methods),
                        f'{path.name}: dropped-item restoration fix missing')
                gun_tag = json.loads(jar.read('data/asterion/tags/damage_type/gun_damage.json'))
                require({'id': '#tacz:bullets', 'required': False} in gun_tag['values'],
                        f'{path.name}: optional gun compatibility tag missing')
                require('net/krodark/asterion/game/BossGunDamage.class' in names,
                        f'{path.name}: missing gun resistance')
                explosion = jar.read('net/krodark/asterion/mixin/LabyrinthExplosionMixin.class')
                if mc == '1.20.1':
                    require(b'onBlockExploded' in explosion, f'{path.name}: Forge explosion callback missing')
                require('net/krodark/asterion/compat/MinimapHudLayout.class' in names,
                        f'{path.name}: minimap HUD layout missing')
            config = json.loads(jar.read("asterion.mixins.json"))
            for cleanup in ('AmneticLayerCleanupMixin', 'AmneticParticleContextAccessor', 'AmneticParticleContextCleanupMixin'):
                require(cleanup in config.get('client', []), f'{path.name}: missing {cleanup}')
            require('AmneticZoomFrameMixin' in config.get('client', []),
                    f'{path.name}: missing zoom frame/layer recovery')
            require(('AmneticVanillaShaderCacheMixin' in config.get('client', [])) == (mc != '26.1.2'),
                    f'{path.name}: wrong vanilla shader-cache recovery for this Minecraft version')
            if legacy_forge:
                require('PortModelCubeAccessor' in config.get('client', []),
                        f'{path.name}: ragdoll model-cube accessor is disabled')
            if mc == '1.20.1' and loader in ('forge', 'neoforge'):
                require(config.get('refmap') in names, f'{path.name}: missing production Mixin refmap')
                refmap = json.loads(jar.read(config['refmap']))['mappings']
                require('hasBlockEntity' in refmap.get('net/krodark/asterion/mixin/RuneBlockEntityStateMixin', {}),
                        f'{path.name}: MixinExtras injection mappings are missing')
                for mixin in ('PortCentipedeInteractionMixin', 'PortMinotaurBodyPickingMixin'):
                    fields, _ = class_members(jar.read(f'net/krodark/asterion/mixin/{mixin}.class'))
                    require('f_91011_' in fields and 'rightClickDelay' not in fields,
                            f'{path.name}: {mixin} still uses a development field name')
                    require('m_91277_' in refmap[f'net/krodark/asterion/mixin/{mixin}']['startUseItem'],
                            f'{path.name}: {mixin} injection lacks production method mapping')
            for group in ["mixins", "client", "server"]:
                for mixin in config.get(group, []):
                    entry = config["package"].replace(".", "/") + "/" + mixin.replace(".", "/") + ".class"
                    require(entry in names, f"{path.name}: missing mixin {mixin}")
            require("RagdollUseGuardMixin" in config["mixins"], f"{path.name}: missing server item-use guard")
            require("LabyrinthCloudMixin" in config.get("client", []),
                    f"{path.name}: missing dimension cloud suppression")
            if loader in ("fabric", "quilt"):
                metadata = json.loads(jar.read("fabric.mod.json"))
                require(metadata["version"] == VERSION, f"Wrong version: {path.name}")
                if mc != '26.1.2':
                    require(any('mixinextras-fabric-0.5.5' in entry.get('file', '')
                                for entry in metadata.get('jars', [])),
                            f'{path.name}: missing Quilt-compatible MixinExtras')
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
                require(re.search(r'^version\s*=\s*"' + re.escape(VERSION) + r'"', metadata, re.M), f"Wrong version: {path.name}")
                if loader == 'neoforge':
                    require(not re.search(r'modId\s*=\s*"(?:fabric_api|connector)"', metadata),
                            f'{path.name}: native port still requires a compatibility runtime')
                    require(not any(n.startswith('net/fabricmc/') for n in names),
                            f'{path.name}: private adapters still share Fabric API packages')
                    embedded = json.loads(jar.read('META-INF/jarjar/metadata.json'))['jars']
                    require(not any('forgified' in entry['path'].lower() or 'fabric-api' in entry['path'].lower()
                                    for entry in embedded), f'{path.name}: Fabric compatibility runtime embedded')
                    require(any('geckolib' in entry['identifier']['artifact'] for entry in embedded),
                            f'{path.name}: missing GeckoLib')
            tag_folder = 'items' if mc == '1.20.1' else 'item'
            for material in ('wooden', 'iron', 'gold', 'diamond', 'netherite'):
                tag = json.loads(jar.read(f'data/minecraft/tags/{tag_folder}/{material}_tool_materials.json'))
                require(tag.get('values') and not tag.get('replace'),
                        f'{path.name}: missing additive {material} tool-material fallback')
            if mc != "26.1.2":
                plugin = 'LegacySodiumMixinPlugin' if mc == '1.20.1' else 'AsterionMixinPlugin'
                require(config.get("plugin") == 'net.krodark.asterion.mixin.' + plugin,
                        f"{path.name}: Sodium interpolation guard missing")
            inspect_nested(jar, path.name)
        rows.append(dict(file=path.name, minecraft=mc, loader=loader, bytes=path.stat().st_size,
                         sha256=hashlib.file_digest(path.open("rb"), "sha256").hexdigest()))
        print(f"PASS {path.name}")

suffix = '-' + '-'.join(filter(None, (args.minecraft, args.loader))) if args.minecraft or args.loader else ''
output = ROOT / f"ports/1.5/build/release-{VERSION}{suffix}-manifest.json"
output.write_text(json.dumps(dict(version=VERSION, files=rows), indent=2) + "\n")
print(f"Verified {len(rows)} release JARs; manifest: {output}")
