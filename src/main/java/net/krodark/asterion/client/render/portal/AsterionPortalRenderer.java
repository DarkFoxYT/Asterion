package net.krodark.asterion.client.render.portal;

import com.meekdev.amnetic.client.instanced.InstanceLayout;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.meekdev.amnetic.client.instanced.MeshData;
import com.meekdev.amnetic.client.instanced.RenderState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.client.PerformanceGovernor;
import net.krodark.asterion.network.GatewayPortalPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

public final class AsterionPortalRenderer {
    private static final Identifier CORE_ID = Asterion.id("gateway/portal_core");
    private static final Identifier HALO_ID = Asterion.id("gateway/portal_halo");
    private static final Identifier SHADER = Asterion.id("portal/asterion_portal");
    private static final Identifier PORTAL_IMAGE = Asterion.id("textures/portal/asterion_portal_square.png");
    private static final Identifier OVERWORLD_IMAGE = Asterion.id("textures/portal/overworld_portal_square.png");
    private static final InstanceLayout LAYOUT = InstanceLayout.builder()
            .mat4(1).vec4(5).vec4(6).build();
    private static final float CORE_RADIUS = 1.48F;
    private static final double HORIZONTAL_GROUND_CLEARANCE = 0.075D;
    private static final double WAKE_DISTANCE = 96.0D;
    private static final double DRAW_DISTANCE = 112.0D;
    private static final long OPEN_NANOS = 1_050_000_000L;

    private static long wakeStarted;
    private static ClientLevel portalWorld;
    private static BlockPos gateway;
    private static int surfaceY = Integer.MIN_VALUE;
    private static long visualSeed;

    private record PortalInstance(Matrix4fc transform, Vector4fc portal, Vector4fc effect) { }

    private AsterionPortalRenderer() { }

    public static void register() {
        registerLayer(CORE_ID, false);
        registerLayer(HALO_ID, true);
        ClientTickEvents.END_CLIENT_TICK.register(AsterionPortalRenderer::tickAtmosphere);
    }

    private static void tickAtmosphere(Minecraft client) {
        if (client.level == null || client.level != portalWorld || gateway == null
                || surfaceY == Integer.MIN_VALUE || client.player == null
                || (!client.level.dimension().equals(Level.OVERWORLD)
                && !client.level.dimension().equals(Asterion.ASTERION_LEVEL))) return;
        if (client.level.dimension().equals(Asterion.ASTERION_LEVEL)) return;
        double playerDx = client.player.getX() - (gateway.getX() + 0.5D);
        double playerDz = client.player.getZ() - (gateway.getZ() + 0.5D);
        int particleInterval = switch (Math.min(AsterionConfig.INSTANCE.ambientParticleQuality,
                PerformanceGovernor.quality())) {
            case 0 -> 9;
            case 1 -> 6;
            default -> 3;
        };
        if (playerDx * playerDx + playerDz * playerDz > 36.0D * 36.0D
                || (client.level.getGameTime() % particleInterval) != 0L) return;
        double perimeter = (client.level.getGameTime() * 0.075D
                + (visualSeed & 255L) * 0.013D) % 8.0D;
        int side = Mth.floor(perimeter / 2.0D);
        double along = perimeter % 2.0D - 1.0D;
        double edge = 2.22D;
        double offsetX = switch (side) {
            case 0 -> along * edge;
            case 1 -> edge;
            case 2 -> -along * edge;
            default -> -edge;
        };
        double offsetZ = switch (side) {
            case 0 -> -edge;
            case 1 -> along * edge;
            case 2 -> edge;
            default -> -along * edge;
        };
        client.level.addParticle(ParticleTypes.ASH,
                gateway.getX() + 0.5D + offsetX, surfaceY + 0.08D,
                gateway.getZ() + 0.5D + offsetZ,
                -offsetX * 0.003D, 0.014D, -offsetZ * 0.003D);
    }

    public static void receive(GatewayPortalPayload payload) {
        if (!payload.active()) {
            close();
            return;
        }
        ClientLevel current = net.minecraft.client.Minecraft.getInstance().level;
        if (current == null) return;
        if (portalWorld != current || gateway == null || !gateway.equals(payload.center())) wakeStarted = 0L;
        portalWorld = current;
        gateway = payload.center();
        surfaceY = payload.surfaceY();
        visualSeed = payload.visualSeed();
    }

    public static void close() {
        portalWorld = null;
        gateway = null;
        surfaceY = Integer.MIN_VALUE;
        wakeStarted = 0L;
    }

    public static boolean isOpen() {
        return gateway != null;
    }

    private static void registerLayer(Identifier id, boolean halo) {
        // Each layer owns its staging values; batch.add packs them before they are reused.
        Matrix4f transform = new Matrix4f();
        Vector4f portal = new Vector4f(), effect = new Vector4f();
        PortalInstance submission = new PortalInstance(transform, portal, effect);
        InstancedMesh.<PortalInstance>builder(LAYOUT, (instance, packer) -> packer
                        .putMat4(instance.transform())
                        .putVec4(instance.portal())
                        .putVec4(instance.effect()))
                .geometry(planeMesh())
                .shaders(SHADER, SHADER)
                .extraSampler("PortalSampler", PORTAL_IMAGE, 1, true)
                .extraSampler("OverworldSampler", OVERWORLD_IMAGE, 2, true)
                .phase(InstancePhase.WORLD_LAST)
                .renderState(RenderState.builder()
                        .depthTest(true)
                        .depthWrite(false)
                        .blend(RenderState.BlendMode.ALPHA)
                        .backfaceCulling(false)
                        .build())
                // Explicit capture keeps BOTH layers emissive even below the scene bloom threshold.
                // The portal shader is already fullbright; bloom is only its optional soft fringe.
                .emissive(halo ? 1.85F : 1.35F)
                .onRender((ctx, batch) -> {
                    ClientLevel world = ctx.world();
                    if (world == null || world != portalWorld || gateway == null
                            || (!world.dimension().equals(Level.OVERWORLD)
                            && !world.dimension().equals(Asterion.ASTERION_LEVEL))) return;
                    boolean vertical = world.dimension().equals(Asterion.ASTERION_LEVEL);

                    Vec3 camera = ctx.cameraPos();
                    double dx = camera.x - (gateway.getX() + 0.5D);
                    double dz = camera.z - (gateway.getZ() + 0.5D);
                    double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
                    if (horizontalDistance > DRAW_DISTANCE) {
                        if (horizontalDistance > DRAW_DISTANCE + 24.0D) wakeStarted = 0L;
                        return;
                    }

                    if (surfaceY == Integer.MIN_VALUE) return;
                    long now = System.nanoTime();
                    if (horizontalDistance <= WAKE_DISTANCE && wakeStarted == 0L) wakeStarted = now;
                    if (wakeStarted == 0L) return;

                    float elapsed = Math.min(1.0F, (now - wakeStarted) / (float) OPEN_NANOS);
                    float reveal = smootherStep(elapsed);
                    float proximity = 1.0F - smootherStep((float) ((horizontalDistance - 80.0D) / 32.0D));
                    reveal *= proximity;
                    if (reveal <= 0.002F) return;

                    double cx = gateway.getX() + 0.5D;
                    // Horizontal portals need enough clearance to stay above the floor's
                    // depth surface. The old 0.012 offset z-fought and visibly sank into
                    // full blocks at shallow camera angles. Vertical arena portals retain
                    // their centered height.
                    double cy = surfaceY + (vertical ? 0.012D : HORIZONTAL_GROUND_CLEARANCE);
                    double cz = gateway.getZ() + 0.5D;
                    float pulse = 1.0F + (float) Math.sin(now * 0.0000000024D) * 0.006F;
                    float radius = CORE_RADIUS * pulse;
                    float openingScale = 0.08F + 0.92F * (1.0F - (float) Math.pow(1.0F - reveal, 3.0D));
                    float layerScale = halo ? 1.28F : 1.0F;
                    // Test before allocating transforms/uniform vectors; cover the square's corners.
                    float boundsRadius = (vertical ? 2.92F : radius * 1.414214F)
                            * openingScale * layerScale + 0.02F;
                    if (!batch.visible(cx, cy, cz, boundsRadius)) return;
                    transform.translation((float)(cx - camera.x),
                                    (float)(cy + (halo ? 0.006D : 0.0D) - camera.y),
                                    (float)(cz + (vertical && halo ? .012D : 0D) - camera.z));
                    if(vertical) transform.scale(1.5F*openingScale*layerScale,
                            2.5F*openingScale*layerScale,1F);
                    else transform.rotateX((float)Math.PI/2F).scale(radius * openingScale * layerScale,
                            radius * openingScale * layerScale, 1.0F);
                    double viewDepth = vertical ? Math.max(1.25D, Math.abs(camera.z - cz))
                            : Math.max(1.25D, Math.abs(camera.y - cy));
                    float viewX = (float) Mth.clamp(dx / viewDepth, -1.6D, 1.6D);
                    float viewY = vertical
                            ? (float) Mth.clamp((camera.y - cy) / viewDepth, -1.6D, 1.6D)
                            : (float) Mth.clamp(dz / viewDepth, -1.6D, 1.6D);
                    float flowTime = (now % 240_000_000_000L) * 0.000000001F;
                    portal.set((float) cx, (float) cy, vertical ? 1.0F : 0.0F, reveal);
                    effect.set(viewX, viewY, flowTime, halo ? 1.0F : 0.0F);
                    batch.add(submission);
                })
                .register(id);
    }

    private static float smootherStep(float value) {
        float x = Math.max(0.0F, Math.min(1.0F, value));
        return x * x * x * (x * (x * 6.0F - 15.0F) + 10.0F);
    }

    private static MeshData planeMesh() {
        return MeshData.of(new float[] {
                -1.0F, -1.0F, 0.0F,  1.0F, -1.0F, 0.0F,
                 1.0F,  1.0F, 0.0F, -1.0F,  1.0F, 0.0F
        }, new int[] {0, 1, 2, 0, 2, 3});
    }

}
