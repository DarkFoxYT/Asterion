# Limbo cascades

Newly generated Limbo sea terrain descends through Styx (Y 47), Phlegethon
(29), Lethe (11), Acheron (-7), and Cocytus (-25). Four 18-block drops follow
the existing irregular biome boundaries. Existing chunks retain their terrain;
this patch does not regenerate or erase a saved world. Restart the client and
server to load the update, then explore newly generated sea areas.

The waterfall curtains reuse Minecraft's water texture and Limbo's shared sea
palette. Animated falling streams, white lip foam and churning landing foam
connect to the water surface. Wave crests receive broken white foam, including
the regional water styles. Waves and buoyancy use the same quantized weather
strength to keep their displacement aligned.

Local, translucent GPU mist veils drift above waterfall bases. They use two
noise octaves, depth testing without depth writes, a 96-block range, and a
192-face frame budget. They do not require a fullscreen pass or mist textures.
Cached waterfall topology is drawn in one curtain batch before the surface
batch, followed by one mist batch. Nearby physical spray is capped at eight
spawns per tick and 160 live particles, with short lifetimes and a 48-block
range. Custom water rendering yields to active shader packs; native waterfall
faces remain available as a fallback.

The ferry follows the lower water heights, accelerates downward over a drop,
and produces an impact on landing. Wakes and shoreline effects follow the
local surface. Vanilla boats retain their normal airborne gravity.

## Validation

Run with the Java 25 runtime and the opt-in validation script:

```powershell
.\gradlew.bat -I tools/underworld-validation.gradle :26.1.2-fabric:checkUnderworldShaders :26.1.2-fabric:checkLimboCascades :26.1.2-fabric:checkCascadeVisuals :26.1.2-fabric:checkLimboWater :26.1.2-fabric:checkUnderworldTerrain :26.1.2-fabric:checkLimboSeas
```

Checks cover driver compilation/linking of water, curtain and mist shaders;
all four terrain transitions; 20 contour crossings and 160 floor samples;
ferry free-fall and landing; cached waterfall faces and unloaded neighbors;
CPU/GPU wave agreement; and existing terrain/sea regressions. Material previews
render the real curtain shaders and water texture for all five palettes.
Fabric and Quilt builds and Forge compilation also pass. These checks do not
measure in-world FPS or replace a multiplayer playtest.
