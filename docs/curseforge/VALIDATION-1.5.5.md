# Asterion 1.5.5 validation

Validated on Windows on 2026-10-21. The release covers Minecraft 1.20.1, 1.21.1 and 26.1.2 on Fabric, Quilt, Forge and NeoForge. Fabric/Quilt share one artifact on 1.20.1 and 1.21.1: 12 local filenames represent 10 distinct uploads.

## Runtime coverage

| Minecraft | Loader | Checks |
| --- | --- | --- |
| 1.20.1 | Fabric | Packaged production client, world load and gameplay/render smoke scenario; 14 server GameTests |
| 1.20.1 | Quilt 0.30.1 | Packaged production client, world load and gameplay/render smoke scenario; 14 server GameTests |
| 1.20.1 | Forge 47.4.28 | Installed production runtime, title screen/client Mixins, all eight entity attribute suppliers |
| 1.20.1 | NeoForge 47.1.106 | Installed production runtime, title screen/client Mixins, all eight entity attribute suppliers |
| 1.21.1 | Fabric | Packaged production client and gameplay/render smoke scenario; Sodium 0.8.13 compatibility; 14 server GameTests |
| 1.21.1 | Quilt 0.30.1 | Packaged production client, world load and gameplay/render smoke scenario; 14 server GameTests |
| 1.21.1 | Forge 52.1.20 | Native launcher startup with built mod, title screen/client Mixins, all eight entity attribute suppliers |
| 1.21.1 | NeoForge 21.1.264 | Packaged dedicated server and client; Wither Reincarnated 1.0.5 and Sodium 0.8.13; world join and gameplay/render smoke scenario |
| 26.1.2 | Fabric | Packaged production client, world load, Minotaur rendering, procedural animation, finite ragdoll physics/recovery; Sodium 0.9.2 |
| 26.1.2 | Quilt 0.31.0-beta.4 | Packaged jar in Quilt client; world load, Minotaur rendering, procedural animation, finite ragdoll physics/recovery |
| 26.1.2 | Forge 64.1.5 | Native launcher startup with built mod, title screen/client Mixins, all eight entity attribute suppliers |
| 26.1.2 | NeoForge 26.1.2.109 | Packaged dedicated server and client; world load, Minotaur rendering, procedural animation, finite ragdoll physics/recovery; Sodium 0.9.2 |

The 1.20.1/1.21.1 gameplay scenario checks GUI alpha masks, gear scaling, player anchors, animated body picking, ragdoll item-use rejection, corpse rendering, procedural animation, debris, axe audio and flamethrower networking.

## Reproduced failures and fixes

- Forge 1.20.1 production failed with unmapped client fields/methods. Added production remapping and complete Mixin reference maps, including MixinExtras annotations and the embedded renderer's existing map.
- Legacy Forge/NeoForge cannot inject into interface methods. Heavy-water bucket pickup now uses a concrete class hook on those two targets. Modern targets use a public default handler.
- NeoForge 1.21.1 reached the title but disconnected on world join through a placeholder Forgified Fabric API codec. A narrow Asterion-only codec fallback passed the same scenario with Wither Reincarnated and Sodium.
- Quilt 1.20.1 reproduced a MixinExtras 0.5.4 annotation parsing crash. Bundling 0.5.5 fixed production client tests on both 1.20.1 and 1.21.1.
- Quilt 26.1.2 initially encountered a corrupt transform cache in the existing test profile. Preserving that cache and rebuilding it resolved the loader error; no game-code change was needed.

## Shader regression coverage (2026-10-21–22)

- NeoForge 1.21.1: packaged release with Iris 1.8.14-beta.1, Sodium 0.8.13, Complementary Unbound r5.9.3 and Wither Reincarnated. Framebuffer/viewport restoration, gameplay/render scenario and Overworld → labyrinth → Overworld shader pipeline checks passed (`ports/1.5/build/release-1.5.5-shader-isolation-final.log`).
- NeoForge 26.1.2: packaged release with Iris 1.11.4, Sodium 0.9.2 and Complementary Unbound r5.9.3 passed the same framebuffer and dimension pipeline checks (`ports/1.5/ports/26.1.2/build/release-1.5.5-shader26-final.log`).
- The regression registers counters on every Amnetic world rendering stage. They must remain unchanged while an external shaderpack renders outside the maze, run inside the maze, and stop again after returning to the Overworld. The selected Iris pack must resume after the return.
- Authored maze atmosphere passed three quality-level image checks with the external pack selected, including dust coverage, disabled TAA and absence of OpenGL errors (`ports/1.5/build/release-1.5.5-shader-atmosphere.log`).
- Final NeoForge 1.21.1 release also passed the full gameplay/cutscene scenario with Complementary (`ports/1.5/build/release-1.5.5-shader-overworld-visual.log`) and a separate dimension round-trip with an above-ground sky screenshot (`ports/1.5/build/release-1.5.5-shader-overworld-sky.log`). Screenshots are `ports/1.5/neoforge/build/run/packaged-smoke-client/screenshots/asterion-shader-overworld.png` and `asterion-shader-terrain.png` in that directory.
- Fabric 1.20.1 (Iris 1.7.6 / Sodium 0.5.12-beta.2) and 1.21.1 (Iris 1.8.14-beta.1 / Sodium 0.8.12-beta.1) passed framebuffer restoration, gameplay/render checks and shader dimension round-trips with Complementary r5.9.3 (`ports/1.5/build/release-1.5.5-fabric-shader-final.log`). The first 1.20.1 attempt reproduced a Sodium texture interpolation Mixin conflict; the legacy plugin now recognizes Sodium's original package name and yields that hook.
- Fabric 26.1.2 with Iris 1.11.4, Sodium 0.9.2 and Complementary r5.9.3 passed the packaged production gameplay/render scenario, framebuffer restoration and dimension round-trip (`ports/1.5/ports/26.1.2/build/release-1.5.5-fabric26-shader-final.log`).
- Optional-loader absence checks passed on final Fabric and Quilt 1.20.1/1.21.1 clients (`release-1.5.5-fabric-final-clients.log`, `release-1.5.5-quilt-final-clients.log` under `ports/1.5/build`). Installed Forge/NeoForge 1.20.1 production startup checks also passed (`build/production-{forge,neoforge}-1.20.1/runtime-final-shader-optional.log`).
- After adding the legacy Sodium guard, Quilt 1.20.1 gameplay/render and Forge 1.21.1 native startup passed again (`ports/1.5/build/release-1.5.5-final-legacy-quilt-forge21.log`). Both installed legacy Forge runtimes passed with the final archives (`build/production-{forge,neoforge}-1.20.1/runtime-final-legacy-sodium.log`).
- Forge 26.1.2 native startup passed with the final shader hooks (`ports/1.5/ports/26.1.2/build/release-1.5.5-forge26-final-startup.log`). The final 12-file archive audit passed (`ports/1.5/build/release-1.5.5-audit-shader-final.log`); 10 distinct upload copies are frozen by SHA-256 under `build/curseforge-1.5.5-shader-staging/`.
- Extended installed Forge 47.4.28 and legacy NeoForge 47.1.106 tests with Oculus 1.8.0, Embeddium 0.3.31 and Complementary r5.9.3. Fresh-world generation, Overworld → maze → Overworld shader state, native heavy-water textures/tint and framebuffer restoration passed (`build/production-{forge,neoforge}-1.20.1/runtime-oculus-dimension-final.log`). This exposed and fixed duplicate native/Fabric biome registrations, a disabled ragdoll model-cube accessor, and Oculus's different dimension-ID string representation. The 1.20.1 shader hook now compares the identifier value directly. Fabric 1.20.1 passed again with that hook (`ports/1.5/build/release-1.5.5-fabric20-legacy-iris-final.log`).
- Actual OptiFine I6 on Forge 1.20.1 is not validated as supported. Its overwritten classes conflict with the embedded Fabric API bridge (resource reload, fluid rendering, shader imports and model-baker fields), as well as newer Forge resource-location factories. Experimental bridge workarounds were excluded from the release. These failures are separate from the reported NeoForge/Iris rendering issue.

## Artifact checks and limits

`ports/1.5/gradle/verify-release.py` checks version metadata, Java class levels, referenced Mixin classes, heavy-water handler visibility, legacy field mappings/reference maps, bounded modern NeoForge shared-library versions, embedded Assimp server packaging, and absence of test harness classes. SHA-256 receipts are stored in `ports/1.5/build/release-1.5.5-manifest.json`.

These checks cover the reported failures and representative startup/world scenarios. They do not guarantee compatibility with every modpack, graphics driver, or untested loader/library combination. Forge 1.21.1 and 26.1.2 startup checks use their native development launchers; 1.20.1 legacy checks additionally use installed production runtimes.
