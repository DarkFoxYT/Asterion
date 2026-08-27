package net.krodark.labyrinth.client.render.portal;

import com.meekdev.amnetic.client.instanced.InstanceLayout;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.meekdev.amnetic.client.instanced.MeshData;
import com.meekdev.amnetic.client.instanced.RenderState;
import net.krodark.labyrinth.Labyrinth;
import net.krodark.labyrinth.network.GatewayPortalPayload;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;


/**
 * Camera-aware view into the Labyrinth at the generated overworld gateway.
 *
 * <p>The server supplies the actual generated maze topology around the shared arrival. The client
 * turns it into a height/albedo texture and the shader relief-maps it from the real camera, providing
 * a stable top-down view with view-dependent parallax.</p>
 */
public final class LabyrinthPortalRenderer {
    private static final Identifier CORE_ID = Labyrinth.id("gateway/portal_core");
    private static final Identifier SHADER = Labyrinth.id("portal/labyrinth_portal");
    /** Replace this resource with the final 2048x2048 portal artwork. */
    private static final Identifier PORTAL_IMAGE = Labyrinth.id("textures/portal/labyrinth_portal.png");
    private static final InstanceLayout LAYOUT = InstanceLayout.builder()
            .mat4(1).vec4(5).vec4(6).build();
    private static final float CORE_RADIUS = 2.48F;
    private static final double WAKE_DISTANCE = 52.0D;
    private static final double DRAW_DISTANCE = 112.0D;
    private static final long OPEN_NANOS = 1_050_000_000L;

    private static long wakeStarted;
    private static ClientLevel portalWorld;
    private static BlockPos gateway;
    private static int surfaceY = Integer.MIN_VALUE;
    private static long visualSeed;

    private record PortalInstance(Matrix4fc transform, Vector4fc portal, Vector4fc effect) { }

    private LabyrinthPortalRenderer() { }

    public static void register() {
        registerLayer(CORE_ID, false);
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
        InstancedMesh.<PortalInstance>builder(LAYOUT, (instance, packer) -> packer
                        .putMat4(instance.transform())
                        .putVec4(instance.portal())
                        .putVec4(instance.effect()))
                .geometry(planeMesh())
                .shaders(SHADER, SHADER)
                .extraSampler("PortalSampler", PORTAL_IMAGE, 1, true)
                .phase(InstancePhase.WORLD_LAST)
                .renderState(RenderState.builder()
                        .depthTest(true)
                        .depthWrite(false)
                        .blend(RenderState.BlendMode.ALPHA)
                        .backfaceCulling(false)
                        .build())
                .emissive(halo ? 1.85F : 1.35F)
                .onRender((ctx, batch) -> {
                    ClientLevel world = ctx.world();
                    if (world == null || world != portalWorld || gateway == null
                            || !world.dimension().equals(Level.OVERWORLD)) return;

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
                    double cy = surfaceY + 0.012D;
                    double cz = gateway.getZ() + 0.5D;
                    float pulse = 1.0F + (float) Math.sin(now * 0.0000000024D) * 0.006F;
                    float radius = CORE_RADIUS * pulse;
                    float openingScale = 0.08F + 0.92F * (1.0F - (float) Math.pow(1.0F - reveal, 3.0D));
                    Matrix4f transform = ctx.worldToModel(cx, cy, cz)
                            .scale(radius * openingScale, 1.0F, radius * openingScale);
                    double cameraHeight = Math.max(1.25D, Math.abs(camera.y - cy));
                    float viewX = (float) Mth.clamp(dx / cameraHeight, -1.6D, 1.6D);
                    float viewZ = (float) Mth.clamp(dz / cameraHeight, -1.6D, 1.6D);
                    batch.add(new PortalInstance(transform,
                            new Vector4f((float) cx, (float) cy, (float) cz, reveal),
                            new Vector4f(viewX, viewZ, reveal, radius)));
                })
                .register(id);
    }

    private static float smootherStep(float value) {
        float x = Math.max(0.0F, Math.min(1.0F, value));
        return x * x * x * (x * (x * 6.0F - 15.0F) + 10.0F);
    }

    private static MeshData planeMesh() {
        return MeshData.of(new float[] {
                -1.0F, 0.0F, -1.0F,  1.0F, 0.0F, -1.0F,
                 1.0F, 0.0F,  1.0F, -1.0F, 0.0F,  1.0F
        }, new int[] {0, 1, 2, 0, 2, 3});
    }

}
