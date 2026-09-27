package net.krodark.asterion.port.client.particle;

import com.meekdev.amnetic.client.instanced.InstanceLayout;
import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.MeshData;
import com.meekdev.amnetic.client.instanced.RenderState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.WeakHashMap;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.port.client.PortEmissiveConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.particle.SpriteSet;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;


public abstract class AnimatedEmissiveParticle extends TextureSheetParticle {
    public static final ResourceLocation MESH_ID = Asterion.id("animated_emissive_particles");
    private static final ResourceLocation PARTICLE_ATLAS_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/atlas/particles.png");
    private static boolean gpuFrame;
    public static int trackedCount() { return ACTIVE.size(); }
    public static void setGpuFrame(boolean enabled) { gpuFrame = enabled; }

    private static final Set<AnimatedEmissiveParticle> ACTIVE =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final ArrayList<AnimatedEmissiveParticle> VISIBLE = new ArrayList<>();
    private static final Comparator<AnimatedEmissiveParticle> BACK_TO_FRONT =
            Comparator.comparingDouble((AnimatedEmissiveParticle p) -> p.distanceSquared).reversed();
    private static boolean initialized;
    protected final SpriteSet sprites;
    private long lastTick;
    private double distanceSquared;
    private float renderX, renderY, renderZ, renderSize;

    protected AnimatedEmissiveParticle(ClientLevel level, double x, double y, double z,
                                      double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        setSprite(sprites.get(0, 1));
        this.sprites = sprites;
        lastTick = level.getGameTime();
        ACTIVE.add(this);
    }

    private float renderRoll;
    public static void initialize() {
        if (initialized) return;
        InstanceLayout layout = InstanceLayout.builder().vec3(2).float1(3).vec4(4).vec4(5).float1(6).build();
        InstancedMesh.<AnimatedEmissiveParticle>builder(layout, (p, out) -> out
                .putVec3(p.renderX, p.renderY, p.renderZ).putFloat(p.renderSize)
                .putVec4(p.rCol, p.gCol, p.bCol, p.alpha)
                .putVec4(p.getU0(), p.getV0(), p.getU1() - p.getU0(), p.getV1() - p.getV0()).putFloat(p.renderRoll))
                .geometry(MeshData.texturedQuad())
                .shaders(Asterion.id("particle/animated_emissive"),
                        ResourceLocation.fromNamespaceAndPath("amnetic", "particle/default_textured"))

                .extraSampler("TextureSampler", PARTICLE_ATLAS_TEXTURE, 0, false)
                .renderState(RenderState.builder().depthTest(true).depthWrite(false)
                        .backfaceCulling(false).blend(RenderState.BlendMode.ALPHA).build())

                .gpuCull()
                .phase(InstancePhase.WORLD_LAST)
                .emissive(PortEmissiveConfig.beetleFireStrength())
                .onRender((context, batch) -> {
                    VISIBLE.clear();
                    boolean culling = net.krodark.asterion.AsterionConfig.INSTANCE.potatoParticleCulling;
                    var config = net.krodark.asterion.AsterionConfig.INSTANCE;
                    int range = culling ? Math.min(64, config.emissiveParticleDistance) : config.emissiveParticleDistance;
                    int limit = culling ? Math.min(2048, config.maxEmissiveParticles) : config.maxEmissiveParticles;
                    var world = context.world();
                    if (world == null) { ACTIVE.clear(); return; }
                    var camera = context.cameraPos();
                    float partialTick = context.deltaTick();
                    var iterator = ACTIVE.iterator();
                    while (iterator.hasNext()) {
                        var particle = iterator.next();
                        if (!particle.isAlive() || particle.level != world
                                || world.getGameTime() - particle.lastTick > 1) {
                            iterator.remove();
                            continue;
                        }
                        if (!particle.isEmissive()) continue;
                        particle.renderX = (float)(Mth.lerp(partialTick, particle.xo, particle.x) - camera.x);
                        particle.renderY = (float)(Mth.lerp(partialTick, particle.yo, particle.y) - camera.y);
                        particle.renderZ = (float)(Mth.lerp(partialTick, particle.zo, particle.z) - camera.z);
                        particle.distanceSquared = particle.renderX * particle.renderX
                                + particle.renderY * particle.renderY + particle.renderZ * particle.renderZ;
                        if (particle.distanceSquared > range * range) continue;
                        particle.renderSize = particle.getQuadSize(partialTick) * 2.0F;
                        particle.renderRoll = Mth.lerp(partialTick, particle.oRoll, particle.roll);

                        float radius = Math.abs(particle.renderSize) * 0.707107F + 0.01F;
                        if (!gpuFrame && !batch.visible(camera.x + particle.renderX, camera.y + particle.renderY,
                                camera.z + particle.renderZ, radius)) continue;
                        VISIBLE.add(particle);
                    }
                    VISIBLE.sort(BACK_TO_FRONT);

                    for (int i = Math.max(0, VISIBLE.size() - limit); i < VISIBLE.size(); i++) {
                        var p = VISIBLE.get(i);
                        if (gpuFrame) batch.add(p, p.renderX, p.renderY, p.renderZ,
                                Math.abs(p.renderSize) * .707107F + .01F);
                        else batch.add(p);
                    }
                    VISIBLE.clear();
                }).register(MESH_ID);
        initialized = true;
    }

    protected boolean isEmissive() { return true; }

    protected final void markTicked() { lastTick = level.getGameTime(); }

    @Override
    public void setSpriteFromAge(SpriteSet sprites) {

        if (isAlive()) setSprite(sprites.get(Math.min(7, age * 8 / Math.max(1, lifetime)), 7));
    }

    @Override
    public void tick() {
        markTicked();
        super.tick();
        setSpriteFromAge(sprites);
    }

    @Override
    public void render(VertexConsumer state, Camera camera, float partialTick) {
        if (!isEmissive()) super.render(state, camera, partialTick);
    }

    @Override
    public net.minecraft.client.particle.ParticleRenderType getRenderType() { return net.minecraft.client.particle.ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    @Override
    public void remove() {
        ACTIVE.remove(this);
        super.remove();
    }
}
