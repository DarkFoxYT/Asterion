# Asterion 1.5.7 validation

Validated on 22 October 2026. Release files are in `modbuilds`; SHA-256 values and the ten unique upload entries are in `ports/1.5/build/curseforge/release-manifest.json`.

- All twelve Minecraft/loader distributions build and pass `ports/1.5/gradle/verify-release.py`, including nested renderer checks, Java versions, native dependencies, retired combat-code removal and the actual obelisk item model.
- All 16 required game tests pass on Minecraft 1.20.1 and 1.21.1. Retired items are absent from all three creative tabs; legacy gates and charged altars have no collision, visible model, light or ticker. The active obelisk remains in the rune tab.
- Dedicated-server scenarios pass on NeoForge 1.20.1, 1.21.1 and 26.1.2, and Forge 1.21.1: boss authority, ragdoll recovery, invalid poses, bounded relay history, deferred respawn callbacks, original-player identity and Queen Beetle quest preservation.
- Packaged Fabric and Quilt clients pass on 1.20.1 and 1.21.1: composed layer visibility, 720 repeated legacy-item damage callbacks without attribute changes, and Afterblow parry, durability, stored damage and a single-use damage bonus.
- The obelisk inventory preview was captured and inspected in game. Both source trees pass `generate-obelisk-item-model.py --check`; the placed model and its effects were not changed.
- Production Forge 47.4.28 on 1.20.1 passes with Majrusz Library 7.0.8, Oculus, Embeddium and Complementary Unbound: startup, composed visibility, Afterblow, repeated damage callbacks, separate draw/read framebuffer restoration, and shader-pack disable in the Labyrinth followed by restoration on exit. The unpatched build reproduced the original redirect conflict.
- Production NeoForge 47.1.106 on 1.20.1 passes the same renderer, Afterblow, repeated damage, framebuffer and dimension shader checks with Oculus/Embeddium after restoring the missing bundled-renderer Mixin manifest entry.
- Packaged NeoForge 26.1.2 passes with Iris, Sodium and Complementary: all 26 custom fluid states retain the expected tint, framebuffer restoration, world load, procedural animation, finite ragdoll recovery and shader dimension isolation.

The Forge player-tracking adapter is compiled and audited on both 1.21.1 and 26.1.2; Forge 1.21.1 also passes the dedicated-server runtime scenario that exposed the missing class. This coverage does not establish compatibility with every modpack or shader pack, or an FPS improvement.

Key logs (workspace-relative):

- `ports/1.5/build/release-1.5.7-content-build.log`
- `ports/1.5/build/release-1.5.7-audit.log`
- `ports/1.5/build/release-1.5.7-runtime.log` (NeoForge 1.20.1 and 1.21.1 server checks)
- `ports/1.5/build/release-1.5.7-afterblow-runtime.log` (corrected Forge server and Fabric clients)
- `ports/1.5/build/release-1.5.7-quilt-runtime.log`
- `build/production-forge-1.20.1/runtime-1.5.7-majrusz-shaders.log`
- `build/production-neoforge-1.20.1/runtime-1.5.7.log`
- `ports/1.5/ports/26.1.2/build/release-1.5.7-native-runtime.log`

## CurseForge publication

All ten unique release files were uploaded and approved on CurseForge. Their Minecraft, loader, Java and client/server tags were verified against the release manifest. Publication was confirmed on 23 October 2026.

| Minecraft | Loaders | Approved file |
| --- | --- | --- |
| 1.20.1 | Fabric / Quilt | [9293228](https://authors.curseforge.com/#/projects/1671346/files/9293228) |
| 1.21.1 | Fabric / Quilt | [9293111](https://authors.curseforge.com/#/projects/1671346/files/9293111) |
| 1.20.1 | NeoForge | [9293013](https://authors.curseforge.com/#/projects/1671346/files/9293013) |
| 1.20.1 | Forge | [9292890](https://authors.curseforge.com/#/projects/1671346/files/9292890) |
| 1.21.1 | NeoForge | [9292742](https://authors.curseforge.com/#/projects/1671346/files/9292742) |
| 1.21.1 | Forge | [9292543](https://authors.curseforge.com/#/projects/1671346/files/9292543) |
| 26.1.2 | Quilt | [9292391](https://authors.curseforge.com/#/projects/1671346/files/9292391) |
| 26.1.2 | Fabric | [9292197](https://authors.curseforge.com/#/projects/1671346/files/9292197) |
| 26.1.2 | Forge | [9291763](https://authors.curseforge.com/#/projects/1671346/files/9291763) |
| 26.1.2 | NeoForge | [9291581](https://authors.curseforge.com/#/projects/1671346/files/9291581) |

The two shared Fabric/Quilt files cover both loaders on 1.20.1 and 1.21.1. All twelve distribution JARs are represented. Machine-readable publication IDs and SHA-256 hashes are saved in `ports/1.5/build/curseforge/publication-receipt-1.5.7.json`.
