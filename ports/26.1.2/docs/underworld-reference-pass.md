# Underworld reference pass — Minecraft 26.1.2

Implemented on the `underworld` checkout using the supplied fog, tunnel and water reference images.

## Appearance

- The first three blocks remain free of post-process haze. Ground mist occupies the nearby 30 blocks, with a soft fade from 24 to 30 blocks and dark distance extinction beyond it.
- A dim, textured green canopy hangs 15–31 blocks above the river datum, separate from the ground mist. Both layers stop sampling at 30 blocks and respect scene depth.
- The nearby mist retains shared wave-height samples. Camera immersion height is supplied once per frame instead of recalculated for every fragment.
- Water retains the resource-pack/vanilla texture, sampled at texel centers, with an opaque milky gray-green body, restrained reflections and muted broken foam. Existing wave motion, hull cutout and persistent wakes remain.

## Terrain

- Wider steep-sided vaults vary into much taller chambers.
- A winding mud strip crosses the shale entrance; wet banks use mud and isolated puddles sit away from the route.
- Shale spikes grow from rock clusters. Hanging formations vary in length and leave passage clearance.
- Thin falling-water ribbons occur against the tunnel wall. They use bounded falling water states rather than scheduling large cascades of fluid updates.
- Layered buttresses frame the sea entrance, and stacked shale-brick ruins with recessed openings protrude from alternating tunnel walls.
- World-coordinate placement keeps features deterministic across chunk boundaries; the approach, landing and ferry lane remain protected.

These generation changes affect new chunks. Existing saved terrain is not erased or regenerated. A fresh test world is the clearest way to see the complete entrance layout.

## Assets and scope

The supplied images are art direction, not texture sheets. This pass uses the existing shale, shaded shale, brick and formation blocks plus vanilla mud and water. No custom texture sheets or new block registrations were added. The wall dwellings are procedural ruins, not a completed inhabited city. The linked ShaderToy page could not be fetched; no third-party shader code was copied.

## Validation

- Both Fabric and NeoForge distributable jars build.
- Terrain tests cover 64 seeds for spawn, bank, landing, ferry clearance and open sea; additional sampling checks rooted spikes, formation clearance, mud, waterfalls and chamber height.
- Actual OpenGL compilation, water preview renders, CPU/GPU wave and hull agreement, wake persistence and relog data serialization pass.
- `build/reports/amnetic-underworld-reference.csv` records synthetic GPU timings using actual Amnetic framebuffers and its profiler. These are isolated pass costs, not game FPS.
- A Fabric launch reached resource loading and a local world with the prior startup fix intact. Full visual comparison, multiplayer and NeoForge runtime validation remain manual.
- The test launch selected the AMD Radeon 610M, despite an RTX 5070 Laptop GPU also being present. That device choice is separate from shader quality and materially affects performance.

The original dust restoration is unchanged by this Underworld-specific pass.
