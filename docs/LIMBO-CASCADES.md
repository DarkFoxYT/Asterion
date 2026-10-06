# Limbo cascades

Limbo uses concentric rings centred at (0, 16018). The outer spider caves
surround a 16,000-block-radius ocean: Styx (Y 47), Phlegethon (29), Lethe
(9), Acheron (-13), and central Cocytus (-37). Each sea retains its 3,200-block
radial width, 320-block palette blend half-width and original colours. Four
18-, 20-, 22- and 24-block drops flow inward at radii 12,800, 9,600, 6,400 and 3,200. The
existing spawn, entrance and dock remain on the north approach. The normal
paid ferry crossing retains its existing endpoint and distance; the larger
rings remain available for exploration.

The outer cave biome contains only territorial spider spawns. Their spawn
rules reject positions within 96 blocks of another Limbo spider, bounding
local density even though the spiders persist. Web searches cover the whole
cave region. Dry cave tiles are excluded from ocean topology scans.

Existing chunks retain their terrain;
this patch does not regenerate or erase a saved world. Restart the client and
server to load the update, then explore newly generated sea areas.

Waterfall bodies are projected onto the circular contour when caching the mesh,
while the lips join the actual block surface and ease into that smooth shape.
Water rolls forward from
the lip along a gravity-shaped arc, projects roughly 4.4 blocks into the lower
sea, and has two moving shells nearby. Twelve vertical subdivisions concentrate
detail at the rolled lip; distant geometry uses six or three subdivisions.
The material reuses Minecraft's water texture and Limbo's shared palette, with
broad turbulent white ribbons and accelerating flow coordinates. Physical
droplets follow the same arc and launch forward into white spray. The sheet
breaks into separate streams near its foot, with fine GPU aerosol flecks in
the mist. The flat
native rim faces are replaced in vanilla and Sodium rendering.

White lip foam and churning landing foam connect to the water surface.
Wave crests receive broken white foam, including
the regional water styles. Waves and buoyancy use the same quantized weather
strength to keep their displacement aligned.

World-space volumetric impact mist and raised turbulent foam reuse the existing
reduced-resolution atmosphere pass. A targeted radial/height interval bounds
8–16 front-to-back samples, with 3D animated density, depth occlusion,
underwater suppression and early rejection away from the rims. The camera can
move inside the plume. Lightweight translucent veils retain fine aerosol glints
at a 96-block range and a 192-face frame budget; their opacity is reduced to
avoid doubling the volume. No additional fullscreen pass or mist textures.

The exact entrance gate template is copied to the centre (0, 16018), at Y -35,
on a flat dry island. Nearby random monoliths are excluded from its courtyard.
Both gates are placed per chunk, with identical orientation and clipping.

Cached waterfall topology is drawn in one curtain batch before the surface
batch, followed by one mist batch. Nearby physical spray is capped at eight
spawns per tick and 160 live particles, with short lifetimes and a 48-block
range. Custom water rendering yields to active shader packs; native waterfall
faces remain available as a fallback.

The ferry follows the lower water heights, accelerates downward over a drop,
and produces an impact on landing. It cannot float uphill through a terrace
waterfall. Exit height checks use the local water height. Wakes and shoreline effects follow the
local surface. Vanilla boats retain their normal airborne gravity.

## Validation

Run with the Java 25 runtime and the opt-in validation script:

```powershell
.\gradlew.bat -I tools/underworld-validation.gradle :26.1.2-fabric:checkUnderworldShaders :26.1.2-fabric:checkLimboCascades :26.1.2-fabric:checkCascadeVisuals :26.1.2-fabric:checkLimboWater :26.1.2-fabric:checkUnderworldTerrain :26.1.2-fabric:checkLimboSeas
```

Checks also render the actual volumetric shader with the camera inside all four
plumes, confirm foreground depth occlusion and reject distant spray, and verify
the entire centre gate footing.

Checks cover driver compilation/linking of water, curtain and mist shaders;
all four terrain transitions around the circumference; 48 contour crossings
and 384 floor samples; spider cave coverage in twelve directions;
ferry free-fall and landing; cached waterfall faces and unloaded neighbors;
CPU/GPU wave agreement; existing terrain/sea regressions; and 76 upper/lower
shoreline positions against the rendered per-tier depth grid, preventing
boat/wave mismatches at the falls. Cave pools retain native water rather than
acquiring ocean waves. Material previews render the real curtain shaders and
water texture for all five palettes,
including an angled silhouette preview and a real depth-tested mist blend.
Fabric and Quilt builds and Forge compilation also pass. These checks do not
measure in-world FPS or replace a multiplayer playtest.
