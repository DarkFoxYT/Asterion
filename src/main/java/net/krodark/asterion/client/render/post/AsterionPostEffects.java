package net.krodark.asterion.client.render.post;

import com.meekdev.amnetic.client.post.PostEffects;
import com.meekdev.amnetic.client.post.RenderPhase;
import com.meekdev.amnetic.client.camera.AmneticCamera;
import com.mojang.blaze3d.systems.RenderSystem;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.client.event.DeadSunClientEvents;
import net.krodark.asterion.client.DeadSunEntryCinematic;
import net.krodark.asterion.client.BossFinaleOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

public final class AsterionPostEffects {
    private static int biomeTarget;
    private static float overgrowthBlend;
    private static float crimsonBlend;
    private static float catacombBlend;
    private static float arenaBlend;
    private static float caveBlend;
    private static float forgeBlend;
    private static float floodBlend;
    private static final Matrix4f lastInverseViewProjection = new Matrix4f();
    private static Vec3 lastCameraPosition = Vec3.ZERO;
    private static Vec3 lastCameraForward = new Vec3(0.0D, 0.0D, 1.0D);
    private static boolean hasCameraSnapshot;

    private AsterionPostEffects() {
    }

    public static void register() {
        PostEffects.register(Asterion.id("dimension/dead_sun"), config -> config
                .when(() -> isPostProcessingReady() && AsterionConfig.INSTANCE.deadSunEnabled
                        && effectQuality() > 0)
                .phase(RenderPhase.POST_WORLD)
                .priority(10)
                .fade(32, 16)
                .uniform("DustTime", AsterionPostEffects::renderTime)
                .uniform("AsterionStrength", () -> AsterionConfig.INSTANCE.deadSunStrength)
                .uniform("AsterionQuality", AsterionPostEffects::effectQuality)
                .uniformRaw("WorldData", AsterionPostEffects::worldData)
                .uniformVec4("DeadSunData", AsterionPostEffects::deadSunData)
                .uniformVec4("DeadSunTuning", AsterionPostEffects::deadSunTuning)
                .uniform("EclipseData", DeadSunClientEvents::eclipseStrength)
                .uniform("WorldDarkness", DeadSunClientEvents::darknessStrength)
                .uniform("EntryRadiance", AsterionPostEffects::radiance)
                .uniform("FinaleProgress", BossFinaleOverlay::sunDetonationStrength)
                .uniform("DeadSunOpacity", () -> AsterionConfig.INSTANCE.deadSunOpacity)
                .uniformVec3("DeadSunCoreColor", () -> new Vector3f(AsterionConfig.INSTANCE.deadSunCoreR,
                        AsterionConfig.INSTANCE.deadSunCoreG, AsterionConfig.INSTANCE.deadSunCoreB))
                .uniformVec3("DeadSunCoronaColor", () -> new Vector3f(AsterionConfig.INSTANCE.deadSunCoronaR,
                        AsterionConfig.INSTANCE.deadSunCoronaG, AsterionConfig.INSTANCE.deadSunCoronaB)));

        PostEffects.register(Asterion.id("dimension/dusty_air"), config -> config
                .when(() -> isPostProcessingReady() && AsterionConfig.INSTANCE.dustyAirEnabled
                        && effectQuality() == 1)
                .phase(RenderPhase.POST_WORLD)
                .priority(20)
                .fade(24, 16)
                .uniform("DustTime", AsterionPostEffects::renderTime)
                .uniform("AsterionStrength", () -> AsterionConfig.INSTANCE.dustyAirStrength)
                .uniform("AsterionQuality", AsterionPostEffects::effectQuality)
                .uniformVec3("AtmosphereSettings", AsterionPostEffects::atmosphereSettings)
                .uniformVec3("DustColor", AsterionPostEffects::dustColor)
                .uniformVec3("FogColor", AsterionPostEffects::fogColor)
                .uniform("EclipseData", DeadSunClientEvents::eclipseStrength)
                .uniformRaw("WorldData", AsterionPostEffects::worldData));

        // High quality runs the volume at the actual window size. The old fixed
        // 640x360 target was then enlarged over the scene and made the fog visibly blocky.
        PostEffects.register(Asterion.id("dimension/dusty_air_high"), config -> config
                .when(() -> isPostProcessingReady() && AsterionConfig.INSTANCE.dustyAirEnabled
                        && effectQuality() >= 2)
                .phase(RenderPhase.POST_WORLD)
                .priority(20)
                .fade(24, 16)
                .uniform("DustTime", AsterionPostEffects::renderTime)
                .uniform("AsterionStrength", () -> AsterionConfig.INSTANCE.dustyAirStrength)
                .uniform("AsterionQuality", AsterionPostEffects::effectQuality)
                .uniformVec3("AtmosphereSettings", AsterionPostEffects::atmosphereSettings)
                .uniformVec3("DustColor", AsterionPostEffects::dustColor)
                .uniformVec3("FogColor", AsterionPostEffects::fogColor)
                .uniform("EclipseData", DeadSunClientEvents::eclipseStrength)
                .uniformRaw("WorldData", AsterionPostEffects::worldData));

        PostEffects.register(Asterion.id("dimension/dusty_air_fast"), config -> config
                .when(() -> isPostProcessingReady() && AsterionConfig.INSTANCE.dustyAirEnabled
                        && effectQuality() <= 0)
                .phase(RenderPhase.POST_WORLD)
                .priority(20)
                .fade(3, 8)
                .uniform("DustTime", AsterionPostEffects::renderTime)
                .uniform("AsterionStrength", () -> AsterionConfig.INSTANCE.dustyAirStrength)
                .uniform("AsterionQuality", 0)
                .uniformVec3("AtmosphereSettings", AsterionPostEffects::atmosphereSettings)
                .uniformVec3("DustColor", AsterionPostEffects::dustColor)
                .uniformVec3("FogColor", AsterionPostEffects::fogColor)
                .uniform("EclipseData", DeadSunClientEvents::eclipseStrength)
                .uniformRaw("WorldData", AsterionPostEffects::worldData));

        // Under sustained frame pressure these retain the same world-space colors,
        // depth occlusion and animation with fewer full-resolution passes/ray samples.
        PostEffects.register(Asterion.id("dimension/dead_sun_fast"), config -> config
                .when(() -> isPostProcessingReady() && AsterionConfig.INSTANCE.deadSunEnabled
                        && effectQuality() <= 0)
                .phase(RenderPhase.POST_WORLD).priority(10).fade(3, 8)
                .uniform("DustTime", AsterionPostEffects::renderTime)
                .uniform("AsterionStrength", () -> AsterionConfig.INSTANCE.deadSunStrength)
                .uniform("AsterionQuality", 0)
                .uniformRaw("WorldData", AsterionPostEffects::worldData)
                .uniformVec4("DeadSunData", AsterionPostEffects::deadSunData)
                .uniformVec4("DeadSunTuning", AsterionPostEffects::deadSunTuning)
                .uniform("EclipseData", DeadSunClientEvents::eclipseStrength)
                .uniform("WorldDarkness", DeadSunClientEvents::darknessStrength)
                .uniform("EntryRadiance", AsterionPostEffects::radiance)
                .uniform("FinaleProgress", BossFinaleOverlay::sunDetonationStrength)
                .uniform("DeadSunOpacity", () -> AsterionConfig.INSTANCE.deadSunOpacity)
                .uniformVec3("DeadSunCoreColor", () -> new Vector3f(AsterionConfig.INSTANCE.deadSunCoreR,
                        AsterionConfig.INSTANCE.deadSunCoreG, AsterionConfig.INSTANCE.deadSunCoreB))
                .uniformVec3("DeadSunCoronaColor", () -> new Vector3f(AsterionConfig.INSTANCE.deadSunCoronaR,
                        AsterionConfig.INSTANCE.deadSunCoronaG, AsterionConfig.INSTANCE.deadSunCoronaB)));
    }

    private static boolean isInsideAsterion() {
        Minecraft client = Minecraft.getInstance();
        return client.level != null && client.level.dimension().equals(Asterion.ASTERION_LEVEL);
    }

    private static boolean isPostProcessingReady() {
        // Start only once a real camera snapshot exists. Afterwards, a transient missed
        // snapshot reuses the last valid matrices instead of blinking or projecting with identity.
        if (!isInsideAsterion()) {
            hasCameraSnapshot = false;
            return false;
        }
        return AmneticCamera.isReady() || hasCameraSnapshot;
    }

    private static double effectQuality() {
        // Changing post chains at runtime makes both chains fade across one another and
        // intermittently drops the volume. The configured quality remains stable per session.
        return Mth.clamp(AsterionConfig.INSTANCE.cinematicQuality, 0, 2);
    }

    private static double renderTime() {
        return (System.nanoTime() * 0.000000001 % 100000.0) * 20.0;
    }

    private static Vector4f deadSunData() {
        AsterionConfig config = AsterionConfig.INSTANCE;
        Vec3 shake = DeadSunClientEvents.sunOffset();
        Vec3 camera = AmneticCamera.position();
        double dx = camera.x - config.deadSunX;
        double dz = camera.z - config.deadSunZ;
        float distanceScale = 1.0F + Math.min(7.0F,
                (float) Math.sqrt(dx * dx + dz * dz) / 1200.0F);
        return new Vector4f(config.deadSunX + (float) shake.x,
                config.deadSunHeight + (float) shake.y,
                config.deadSunZ + (float) shake.z,
                config.deadSunSize * distanceScale * mix(1.0F, 1.08F, eclipse())
                        * mix(1.0F, 3.8F, BossFinaleOverlay.sunDetonationStrength())
                        * mix(1.0F, 1.16F, DeadSunEntryCinematic.radianceStrength()));
    }

    private static Vector4f deadSunTuning() {
        AsterionConfig config = AsterionConfig.INSTANCE;
        float eclipse = eclipse();
        float finale = BossFinaleOverlay.sunDetonationStrength();
        return new Vector4f(
                config.deadSunBrightness * mix(1.0F, 0.95F, eclipse)
                        * mix(1.0F, 1.18F, DeadSunEntryCinematic.radianceStrength())
                        * mix(1.0F, 0.52F, finale),
                config.shaderAnimationSpeed * mix(1.0F, 0.72F, eclipse),
                config.deadSunCorona * mix(1.0F, 2.15F, eclipse)
                        * mix(1.0F, 1.22F, DeadSunEntryCinematic.radianceStrength())
                        * mix(1.0F, 2.35F, finale),
                config.deadSunDensity * mix(1.0F, 1.55F, eclipse)
                        * mix(1.0F, 1.65F, finale));
    }

    private static Vector3f atmosphereSettings() {
        AsterionConfig config = AsterionConfig.INSTANCE;
        float eclipse = darkness();
        return new Vector3f(
                config.dustDensity * mix(1.0F, 0.82F, overgrowthBlend)
                        * mix(1.0F, 0.92F, crimsonBlend)
                        * mix(1.0F, 1.18F, forgeBlend)
                        * mix(1.0F, 2.80F, eclipse)
                        * mix(1.0F, 1.4F + 2.8F * floodBlend * (1 - arenaBlend), catacombBlend)
                        * mix(1.0F, .45F, arenaBlend) * mix(1.0F, 3.2F, caveBlend),
                config.fogStrength * mix(1.0F, 0.90F, overgrowthBlend)
                        * mix(1.0F, 0.94F, crimsonBlend)
                        * mix(1.0F, 1.12F, forgeBlend)
                        * mix(1.0F, 2.25F, eclipse)
                        * mix(1.0F, 1.1F + .4F * floodBlend * (1 - arenaBlend), catacombBlend)
                        * mix(1.0F, .38F, arenaBlend) * mix(1.0F, 2.1F, caveBlend),
                config.shaderAnimationSpeed);
    }

    private static Vector3f dustColor() {
        AsterionConfig config = AsterionConfig.INSTANCE;
        float eclipse = darkness();
        // Bright ember-orange shafts, kept luminous rather than muddy brown.
        // Pale-Garden-like stone and lichen tint: cool, soft and readable without changing
        // Minecraft's biome tinting itself.
        float red = mix(config.dustR, 0.43F, overgrowthBlend);
        float green = mix(config.dustG, 0.46F, overgrowthBlend);
        float blue = mix(config.dustB, 0.40F, overgrowthBlend);
        red = mix(red, 0.50F, crimsonBlend);
        green = mix(green, 0.70F, crimsonBlend);
        blue = mix(blue, 0.84F, crimsonBlend);
        red = mix(red, 0.92F, forgeBlend);
        green = mix(green, 0.34F, forgeBlend);
        blue = mix(blue, 0.12F, forgeBlend);
        return new Vector3f(
                mix(mix(mix(mix(red, 0.15F, eclipse), .70F, catacombBlend), .82F, arenaBlend), .025F, caveBlend),
                mix(mix(mix(mix(green, 0.018F, eclipse), .81F, catacombBlend), .52F, arenaBlend), .028F, caveBlend),
                mix(mix(mix(mix(blue, 0.012F, eclipse), .90F, catacombBlend), .25F, arenaBlend), .032F, caveBlend));
    }

    private static Vector3f fogColor() {
        AsterionConfig config = AsterionConfig.INSTANCE;
        float eclipse = darkness();
        float red = mix(config.fogR, 0.20F, overgrowthBlend);
        float green = mix(config.fogG, 0.235F, overgrowthBlend);
        float blue = mix(config.fogB, 0.205F, overgrowthBlend);
        red = mix(red, 0.20F, crimsonBlend);
        green = mix(green, 0.34F, crimsonBlend);
        blue = mix(blue, 0.43F, crimsonBlend);
        red = mix(red, 0.22F, forgeBlend);
        green = mix(green, 0.085F, forgeBlend);
        blue = mix(blue, 0.045F, forgeBlend);
        return new Vector3f(
                mix(mix(mix(mix(red, 0.018F, eclipse), .64F, catacombBlend), .20F, arenaBlend), .003F, caveBlend),
                mix(mix(mix(mix(green, 0.003F, eclipse), .76F, catacombBlend), .13F, arenaBlend), .004F, caveBlend),
                mix(mix(mix(mix(blue, 0.002F, eclipse), .86F, catacombBlend), .085F, arenaBlend), .006F, caveBlend));
    }

    public static void setBiome(int biome) {
        biomeTarget = Mth.clamp(biome, 0, 4);
    }

    public static boolean isCrimsonBiome() {
        return biomeTarget == 2 || crimsonBlend > 0.55F;
    }

    public static void tickBiomeAtmosphere(Minecraft client) {
        if (!isInsideAsterion() || client.player == null) {
            catacombBlend = 0;
            arenaBlend = 0;
            caveBlend = 0;
            forgeBlend = 0;
            floodBlend = 0;
        } else {
            Vec3 camera = AmneticCamera.isReady() ? AmneticCamera.position() : client.player.getEyePosition();
            double cameraY = camera.y;
            float caveTarget = Mth.clamp((float)(net.krodark.asterion.worldgen.LabyrinthLevels.CAVE_ROOF_Y - cameraY) / 12F, 0F, 1F);
            caveBlend += (caveTarget - caveBlend) * .04F;
            // Fade at the arena walls and roof so the maze above keeps its own atmosphere.
            float arenaTarget = Mth.clamp((float)(42 - Math.max(Math.abs(camera.x), Math.abs(camera.z))) / 6F, 0F, 1F)
                    * Mth.clamp((float)(net.krodark.asterion.worldgen.AuthoredCatacombs.ARENA_BASE_Y + 47 - cameraY) / 5F, 0F, 1F)
                    * Mth.clamp((float)(cameraY - net.krodark.asterion.worldgen.AuthoredCatacombs.ARENA_BASE_Y) / 4F, 0F, 1F);
            arenaBlend += (arenaTarget - arenaBlend) * .08F;
            int vaultRoof = Math.min(net.krodark.asterion.worldgen.LabyrinthLevels.MAZE_FLOOR_Y - 2,
                    net.krodark.asterion.worldgen.CatacombLayout.roofAt(
                            client.player.getBlockX(), client.player.getBlockZ()));
            float underground = Mth.clamp((float)(vaultRoof + 3 - cameraY) / 4F, 0F, 1F)
                    * Mth.clamp((float)(cameraY
                    - net.krodark.asterion.worldgen.LabyrinthLevels.FORGE_ROOF_Y), 0F, 1F);
            if (biomeTarget == 3) underground = 1F;
            catacombBlend += (underground - catacombBlend) * .04F;
            float forgeTarget = biomeTarget == 4 ? 1F - caveTarget : 0F;
            forgeBlend += (forgeTarget - forgeBlend) * .04F;
            float floodTarget = DeadSunClientEvents.floodStrength();
            floodBlend += (floodTarget - floodBlend) * .025F;
        }
        if (client.level == null || !client.level.dimension().equals(Asterion.ASTERION_LEVEL))
            biomeTarget = 0;
        float target = biomeTarget == 1 ? 1.0F : 0.0F;
        float crimsonTarget = biomeTarget == 2 ? 1.0F : 0.0F;
        // About two seconds of easing at 20 TPS prevents a visible biome seam.
        overgrowthBlend += (target - overgrowthBlend) * 0.026F;
        if (Math.abs(target - overgrowthBlend) < 0.001F) overgrowthBlend = target;
        crimsonBlend += (crimsonTarget - crimsonBlend) * 0.026F;
        if (Math.abs(crimsonTarget - crimsonBlend) < 0.001F) crimsonBlend = crimsonTarget;
    }

    private static float eclipse() {
        return Math.max(0.0F, Math.min(1.0F, DeadSunClientEvents.eclipseStrength()));
    }

    private static float darkness() {
        return Math.max(0.0F, Math.min(1.0F, DeadSunClientEvents.darknessStrength()));
    }

    private static float radiance() {
        return Math.max(DeadSunEntryCinematic.radianceStrength(),
                BossFinaleOverlay.sunDetonationStrength());
    }

    private static float mix(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private static List<UniformValue> worldData() {
        if (AmneticCamera.isReady()) {
            lastInverseViewProjection.set(AmneticCamera.inverseViewProjection());
            lastCameraPosition = AmneticCamera.position();
            lastCameraForward = AmneticCamera.forward();
            hasCameraSnapshot = true;
        }
        float zeroToOne = RenderSystem.getDevice().isZZeroToOne() ? 1.0f : 0.0f;
        return List.of(
                new UniformValue.Matrix4x4Uniform(new Matrix4f(lastInverseViewProjection)),
                new UniformValue.Vec4Uniform(new Vector4f(
                        (float) lastCameraPosition.x, (float) lastCameraPosition.y,
                        (float) lastCameraPosition.z, zeroToOne)),
                new UniformValue.Vec4Uniform(new Vector4f(
                        (float) lastCameraForward.x, (float) lastCameraForward.y,
                        (float) lastCameraForward.z, 0.0f))
        );
    }
}
