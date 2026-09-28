package net.krodark.asterion.client.particle;

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
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.client.render.post.AsterionPostEffects;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

/** A shader-shaped camera-facing haze plane; the registered sprite is never sampled. */
public final class GroundFogParticle extends SingleQuadParticle {
    private static final Set<GroundFogParticle> ACTIVE =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final ArrayList<GroundFogParticle> VISIBLE = new ArrayList<>();
    private static final Comparator<GroundFogParticle> BACK_TO_FRONT =
            Comparator.comparingDouble((GroundFogParticle p) -> p.distanceSquared).reversed();
    private static boolean initialized;

    private final float opacity;
    private final float baseSize;
    private final float texturePhase;
    private final float textureSpin;
    private long lastTick;
    private double distanceSquared;
    private float renderX, renderY, renderZ, renderSize, renderAlpha, renderAngle;

    private GroundFogParticle(ClientLevel level, double x, double y, double z,
                              double vx, double vy, double vz,
                              SpriteSet sprites, RandomSource random) {
        super(level, x, y, z, vx, vy, vz, sprites.first());
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.friction = .985F;
        this.gravity = 0F;
        this.hasPhysics = false;
        this.lifetime = 170 + random.nextInt(90);
        this.baseSize = 5F + random.nextFloat() * 2F;
        this.quadSize = baseSize;
        boolean limbo = level.dimension().equals(Asterion.LIMBO_LEVEL);
        float strength = limbo ? AsterionConfig.INSTANCE.limboHazeStrength
                : AsterionConfig.INSTANCE.labyrinthHazeStrength;
        this.opacity = (.32F + random.nextFloat() * .145F) * Math.min(1.2F, strength);
        if (limbo) {
            setColor(.30F, .40F, .35F);
        } else {
            Vector3f tint = AsterionPostEffects.ambientDustColor();
            setColor(tint.x, tint.y, tint.z);
        }
        setAlpha(0F);
        texturePhase = random.nextFloat() * Mth.TWO_PI;
        textureSpin = (random.nextBoolean() ? 1F : -1F) * (.004F + random.nextFloat() * .004F);
        lastTick = level.getGameTime();
        ACTIVE.add(this);
    }

    public static Particle create(ClientLevel level, double x, double y, double z,
                                  double vx, double vy, double vz,
                                  SpriteSet sprites, RandomSource random) {
        return new GroundFogParticle(level, x, y, z, vx, vy, vz, sprites, random);
    }

    public static void initialize() {
        if (initialized) return;
        InstancedMesh.<GroundFogParticle>builder(InstanceLayout.TEXTURED_BILLBOARD, (p, out) -> out
                .putVec3(p.renderX, p.renderY, p.renderZ).putFloat(p.renderSize)
                .putVec4(p.rCol, p.gCol, p.bCol, p.renderAlpha)
                // The procedural texture uses the unused atlas rectangle for rotation and seed.
                .putVec4(p.renderAngle, p.texturePhase, 0F, 0F))
                .geometry(MeshData.texturedQuad())
                .shaders(Asterion.id("particle/ground_fog"), Asterion.id("particle/ground_fog"))
                .renderState(RenderState.builder().depthTest(true).depthWrite(false)
                        .backfaceCulling(false).blend(RenderState.BlendMode.ALPHA).build())
                .phase(InstancePhase.WORLD_LAST)
                .onRender((context, batch) -> {
                    VISIBLE.clear();
                    var world = context.world();
                    if (world == null) { ACTIVE.clear(); return; }
                    var camera = context.cameraPos();
                    float partialTick = context.deltaTick();
                    Vector3f biomeDust = AsterionPostEffects.ambientDustColor();
                    double maxDistance = Math.max(32D, (net.minecraft.client.Minecraft.getInstance()
                            .options.getEffectiveRenderDistance() - 1) * 16D);
                    var iterator = ACTIVE.iterator();
                    while (iterator.hasNext()) {
                        GroundFogParticle p = iterator.next();
                        if (!p.isAlive() || p.level != world || world.getGameTime() - p.lastTick > 1) {
                            iterator.remove();
                            continue;
                        }
                        p.renderX = (float)(Mth.lerp(partialTick, p.xo, p.x) - camera.x);
                        p.renderY = (float)(Mth.lerp(partialTick, p.yo, p.y) - camera.y);
                        p.renderZ = (float)(Mth.lerp(partialTick, p.zo, p.z) - camera.z);
                        p.distanceSquared = p.renderX * p.renderX + p.renderY * p.renderY
                                + p.renderZ * p.renderZ;
                        if (p.distanceSquared > maxDistance * maxDistance) continue;
                        if (world.dimension().equals(Asterion.ASTERION_LEVEL)) {
                            p.setColor(biomeDust.x, biomeDust.y, biomeDust.z);
                        }
                        float distanceFade = Mth.clamp((float)((Math.sqrt(p.distanceSquared) - 6D) / 16D), 0F, 1F);
                        p.renderAlpha = p.alpha * distanceFade;
                        if (p.renderAlpha <= .001F) continue;
                        p.renderSize = p.getQuadSize(partialTick) * 2F;
                        p.renderAngle = p.texturePhase + (p.age + partialTick) * p.textureSpin;
                        if (!batch.visible(camera.x + p.renderX, camera.y + p.renderY,
                                camera.z + p.renderZ, p.renderSize * .707107F)) continue;
                        VISIBLE.add(p);
                    }
                    VISIBLE.sort(BACK_TO_FRONT);
                    for (int i = 0; i < Math.min(240, VISIBLE.size()); i++) {
                        batch.add(VISIBLE.get(i));
                    }
                    VISIBLE.clear();
                }).register(Asterion.id("ground_fog_planes"));
        initialized = true;
    }

    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        // The instanced world pass renders the plane with its own fragment shader.
    }

    @Override
    public void tick() {
        lastTick = level.getGameTime();
        super.tick();
        if (removed) return;
        if (age % 8 == 0 && !level.getBlockState(BlockPos.containing(x, y, z)).isAir()) {
            remove();
            return;
        }
        xd += (random.nextFloat() - .5F) * .0007F;
        zd += (random.nextFloat() - .5F) * .0007F;
        yd = Mth.clamp(yd, -.002D, .004D);
        float life = age / (float)lifetime;
        float appear = Mth.clamp(age / 32F, 0F, 1F);
        float disappear = Mth.clamp((1F - life) / .35F, 0F, 1F);
        quadSize = baseSize * (1F + life * .12F);
        setAlpha(opacity * appear * disappear);
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    public void remove() {
        ACTIVE.remove(this);
        super.remove();
    }
}
