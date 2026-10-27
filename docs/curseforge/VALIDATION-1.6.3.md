# Asterion 1.6.3 texture hotfix

The user requested fixes for Mazesteel chains on Minecraft 1.20.1, 1.21.11 and
26.3, intermittent armor textures on 1.20.1, and related resource-loading faults.
They explicitly selected publication as a new 1.6.3 hotfix. Existing published
1.6.2 files remain intact.

## Changes

- The chain, bars, foliage and torch-effect models declare cutout rendering in
  all three maintained source trees. Minecraft 1.21.11 Fabric/Quilt additionally
  register those blocks with the terrain cutout layer; native loaders use their
  model declarations. Minecraft 26.3 derives transparency from the texture.
- Removed eight zero-area faces from the chain's crossed planes. Its four
  visible faces, UVs, rotations and original PNG remain unchanged.
- Forge 1.20.1/1.21.1 and NeoForge 1.20.1 now stage all eight armor PNGs into
  `textures/models/armor/*_layer_1.png` and `*_layer_2.png`, as required by worn
  and corpse rendering. These aliases were missing from the affected releases.
- Added an archive-level texture audit: model parents, blockstate/item model
  references, direct texture references, PNG decoding, animation frame bounds,
  chain geometry/cutout, and exact armor aliases. A texture slot named `model`
  is correctly treated as a texture reference, not a parent model.

## Validation

The source artwork is preserved; no textures were repainted or downscaled.
`ports/1.5/gradle/verify-texture-assets.py` checks the shipped archives rather
than just source files. Its regression check rejects the previous 1.21.11
Fabric archive for missing chain cutout metadata:
`build/release-1.6.3-texture-regression-before.log`.

The opt-in `textureSmoke` fixture places chains on all three axes and armor
stands wearing all four Asterion armor sets. It decodes the runtime armor and
chain resources before and after a complete resource-pack reload, waits for
post-reload rendering, saves a screenshot, and exits. This helper stays outside
release files. The strict archive audit also checks that the new 1.21.11
Fabric/Quilt terrain-layer registration is present in compiled code.

## Final results

All 20 final archives pass the combined release, dependency-closure and texture
checks. This includes their references to the actual vanilla models/textures for
each Minecraft version, every authored PNG and animation frame, and exact armor
aliases. All required mod libraries remain embedded. Evidence:
`build/release-1.6.3-final-release-audit.log`,
`build/curseforge/release-1.6.3-dependencies.json`,
`build/curseforge/release-1.6.3-textures.json`,
`build/curseforge/release-1.6.3-manifest.json`.

| Final runtime targets | Evidence |
| --- | --- |
| 1.20.1 Fabric production and NeoForge development runtime | `build/release-1.6.3-texture-legacy-final.log` |
| 1.21.11/26.3 Fabric and Quilt production; 1.21.11 NeoForge packaged | `build/release-1.6.3-texture-modern-runtime.log` |
| 1.21.11 Forge development runtime | `build/release-1.6.3-texture-forge-reload.log` |
| Corrected 26.3 NeoForge packaged and Forge development runtime | `build/release-1.6.3-native-bloom-fix.log` |

Every target reports before/after resource-reload PASS. The Forge runs use a
separate test helper with matching development classes; production archives are
audited independently. Both Forge versions additionally passed the floor/corpse
and full-resolution shader assertions in the first modern runtime run. That
first Forge run selected the ordinary scenario because its launcher did not
forward the custom fixture flag; the dedicated reload runs above explicitly
select the texture fixture in its test entrypoint. No test helper ships.

The 26.3 Forge and NeoForge screenshots caught an additional native bloom bug:
the GL path's zero-threshold skip sentinel instead made the native prefilter
bloom ordinary terrain. Native bloom now skips an unrequested scene fallback
when there is no emissive G-buffer, and retains the actual configured threshold
for real emitters. It does not disable intended emissive bloom globally.
The policy injection requires one matching native method so it cannot silently
miss a future renderer change.

`build/release-1.6.3-bloom-regression-before.log` records the original fixture's
100% washed-out gray floor. The corrected screenshots were visually inspected.
The pixel regression then passed all ten final screenshots: gray floor detail
remains and the sampled floor has 0% overexposed white pixels. Evidence:
`build/release-1.6.3-rendering-visual-audit.log`. Example screenshots:
`ports/1.5/versions/1.20.1-neoforge/build/run/native-client-smoke/screenshots/asterion-textures-after-reload.png`
and
`ports/1.5/ports/1.6.2/versions/26.3-forge/run/automated-port-smoke/screenshots/textures-after-reload.png`.

## Publication

All 18 unique files are approved and public, covering 20 variants. Fabric and
Quilt share identical files on 1.20.1 and 1.21.1. Each upload has the exact
Minecraft, Java and loader tags, client/server environment, Release type,
automatic publication, embedded GeckoLib relation and final four-bullet hotfix
changelog. The first 15 uploads had their notes updated when the additional
native bloom fix was validated. The public 1.20.1 Fabric and 26.3 Forge pages
were checked for the final notes.

| Minecraft | Fabric | Quilt | Forge | NeoForge |
| --- | --- | --- | --- | --- |
| 1.20.1 | 9342730 | 9342730 | 9342748 | 9342749 |
| 1.21.1 | 9342756 | 9342756 | 9342759 | 9342762 |
| 26.1.2 | 9342780 | 9342782 | 9342783 | 9342790 |
| 1.21.11 | 9342794 | 9342835 | 9342891 | 9342839 |
| 26.3 | 9342796 | 9342837 | 9342895 | 9342894 |

Public file URLs use
`https://www.curseforge.com/minecraft/mc-mods/asterion/files/<file ID>`.
Receipts and staged SHA-256 values:
`build/curseforge/release-1.6.3-publication.json`.
Final checks match all 20 current local archives to their 18 approved upload
receipts, exact tags, and final changelog hash. Approval screenshot:
`build/curseforge/release-1.6.3-upload-proof.jpg`.

Previously published 1.6.2 files were not edited or archived for this hotfix.
No source commit or push was performed in this task.

