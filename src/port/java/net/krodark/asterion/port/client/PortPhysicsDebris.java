package net.krodark.asterion.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.MinotaurDoorMotion;
import net.krodark.asterion.event.RumbleSources;
import net.krodark.asterion.network.ArenaDebrisPayload;
import net.krodark.asterion.network.DoorBreakPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Rigid, depth-tested Minotaur door slabs and arena rubble for the 1.21.1 port. */
@SuppressWarnings("deprecation")
public final class PortPhysicsDebris {
    private static final List<Piece> PIECES = new ArrayList<>();
    private static final GeoObjectRenderer<DebrisObject> RENDERER = new GeoObjectRenderer<>(new DebrisModel());
    private static ClientLevel trackedLevel;
    private static long lastAmbientTick = Long.MIN_VALUE;

    private PortPhysicsDebris() {}

    public static void initialize() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (PIECES.isEmpty() || context.matrixStack() == null || context.consumers() == null) return;
            Minecraft client = Minecraft.getInstance();
            if (client.level == null) return;
            Vec3 camera = context.camera().getPosition();
            float partial = client.getTimer().getGameTimeDeltaPartialTick(true);
            PoseStack poses = context.matrixStack();
            for (Piece piece : PIECES) {
                double distanceSqr = piece.position.distanceToSqr(camera);
                if (distanceSqr > 96 * 96) continue;
                Vec3 position = piece.previous.lerp(piece.position, partial).subtract(camera);
                Quaternionf rotation = new Quaternionf(piece.previousRotation).slerp(piece.rotation, partial);
                poses.pushPose();
                poses.translate(position.x, position.y, position.z);
                poses.mulPose(rotation);
                poses.scale(piece.scale, piece.scale, piece.scale);
                Vec3 center = modelCenter(piece.variant);
                poses.translate(-center.x, -center.y, -center.z);
                int light = LevelRenderer.getLightColor(client.level, BlockPos.containing(piece.position));
                RENDERER.render(poses, piece.visual, context.consumers(), null, null, light, partial);
                poses.popPose();
            }
        });
    }

    public static void spawnDoors(DoorBreakPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || !payload.facing().getAxis().isHorizontal()
                || client.player.distanceToSqr(Vec3.atCenterOf(payload.root())) > 96 * 96) return;
        ensureLevel(client.level);
        Random random = new Random(payload.seed());
        float angle = Float.isFinite(payload.angle())
                ? Mth.clamp(payload.angle(), 0, MinotaurDoorMotion.OPEN_ANGLE) : 0;
        Vec3 inward = Vec3.atLowerCornerOf(payload.facing().getOpposite().getNormal());
        Vec3 across = Vec3.atLowerCornerOf(payload.facing().getClockWise().getNormal());
        for (int side : new int[]{-1, 1}) {
            Piece door = new Piece(MinotaurDoorMotion.leafCenter(payload.root(), payload.facing(), side, angle),
                    7, 1.0F, random, 1000);
            door.rotation.rotationY(MinotaurDoorMotion.yaw(payload.facing()) + side * angle);
            door.previousRotation.set(door.rotation);
            door.velocity = inward.scale(1.55D + random.nextDouble() * .18D)
                    .add(across.scale(side * .22D)).add(0, .48D + random.nextDouble() * .10D, 0);
            door.spin.set((float)(across.x * .13D), side * .10F, (float)(across.z * .13D));
            PIECES.add(door);
            for (double hingeY : new double[]{1.0D, 4.0D}) for (int chip = 0; chip < 3; chip++) {
                Vec3 hinge = MinotaurDoorMotion.toWorld(payload.root(), payload.facing(),
                        new Vec3(side * 3.0D, hingeY, 0));
                Piece fragment = new Piece(hinge, 3, .18F + random.nextFloat() * .12F, random, 240);
                fragment.velocity = inward.scale(.25D + random.nextDouble() * .3D)
                        .add(across.scale(side * (.08D + random.nextDouble() * .12D))).add(0, .12D, 0);
                PIECES.add(fragment);
            }
        }
        for (int i = 0; i < 80; i++) {
            double side = random.nextBoolean() ? 3.0D : -3.0D;
            Vec3 p = Vec3.atBottomCenterOf(payload.root()).add(across.scale(side + (random.nextDouble() - .5D) * .5D))
                    .add(inward.scale(random.nextDouble() * .4D)).add(0, 1 + random.nextDouble() * 3.4D, 0);
            client.level.addParticle(Asterion.DOOR_SMOKE, p.x, p.y, p.z,
                    inward.x * (.05D + random.nextDouble() * .12D), .02D + random.nextDouble() * .05D,
                    inward.z * (.05D + random.nextDouble() * .12D));
        }
        trim();
    }

    public static void spawnArenaDebris(ArenaDebrisPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;
        ensureLevel(client.level);
        if (payload.fragments().isEmpty()) {
            PIECES.removeIf(piece -> piece.variant == 7 || piece.arena);
            return;
        }
        Random random = new Random(payload.seed());
        for (ArenaDebrisPayload.Fragment fragment : payload.fragments()) {
            if (!finite(fragment.position()) || !finite(fragment.velocity())
                    || fragment.position().distanceToSqr(client.player.position()) > 96 * 96) continue;
            int variant = random.nextInt(12) == 0 ? 1 : 2 + random.nextInt(5);
            Piece piece = new Piece(fragment.position(), variant, fragment.scale(), random, 180 + random.nextInt(150));
            piece.arena = true;
            piece.velocity = fragment.velocity().lengthSqr() > 9
                    ? fragment.velocity().normalize().scale(3) : fragment.velocity();
            Vec3 spawn = findClearSpawn(client.level, piece, piece.position);
            if (spawn != null) {
                piece.position = piece.previous = spawn;
                PIECES.add(piece);
                emitDust(client.level, spawn, new Vec3(0, 1, 0), random, 3);
            }
        }
        trim();
    }

    public static void spawnRumble(Vec3 center, float radius, float intensity, long seed) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.player.position().distanceTo(center) > radius + 28)
            return;
        ensureLevel(client.level);
        Random random = new Random(seed ^ client.level.getGameTime() * 0x9E3779B97F4A7C15L);
        int count = Mth.clamp(2 + Math.round(intensity * 4), 2, 7);
        for (int i = 0; i < count; i++) {
            RumbleSources.Source source = RumbleSources.find(client.level, client.player.position(), random);
            if (source == null) continue;
            Vec3 position = source.position();
            Piece piece = new Piece(position, 3 + random.nextInt(4), .12F + random.nextFloat() * .20F,
                    random, 150 + random.nextInt(120));
            piece.arena = true;
            piece.velocity = source.normal().scale(.05D + random.nextDouble() * .10D)
                    .add((random.nextDouble() - .5D) * .08D,
                            .04D + random.nextDouble() * .22D * intensity,
                            (random.nextDouble() - .5D) * .08D);
            if (clear(client.level, piece, position)) {
                PIECES.add(piece);
                emitDust(client.level, position, source.normal(), random, 5 + Math.round(intensity * 3));
            }
        }
        trim();
    }

    public static void spawnAmbientRumble(Minecraft client, float intensity, long seed) {
        if (client.level == null || client.player == null) return;
        long tick = client.level.getGameTime();
        int interval = Math.max(4, 11 - Math.round(intensity * 6.0F));
        if (tick == lastAmbientTick || Math.floorMod(tick + seed, interval) != 0) return;
        lastAmbientTick = tick;
        spawnRumble(client.player.position(), 13.0F, intensity * .78F, seed ^ tick * 31L);
    }

    public static void tick(Minecraft client) {
        if (client.level == null) { PIECES.clear(); trackedLevel = null; return; }
        ensureLevel(client.level);
        Iterator<Piece> iterator = PIECES.iterator();
        while (iterator.hasNext()) {
            Piece piece = iterator.next();
            piece.previous = piece.position;
            piece.previousRotation.set(piece.rotation);
            if (!piece.sleeping) {
                int substeps = Mth.clamp(1 + net.krodark.asterion.AsterionConfig.INSTANCE.ragdollPhysicsQuality, 1, 3);
                for (int step = 0; step < substeps && !piece.sleeping; step++)
                    simulate(client.level, piece, 1.0D / substeps);
            }
            if (++piece.age > piece.life || client.player != null
                    && piece.position.distanceToSqr(client.player.position()) > 128 * 128) iterator.remove();
        }
    }

    private static void simulate(ClientLevel level, Piece piece, double dt) {
        piece.velocity = piece.velocity.add(0, -.075D * gravity(piece.variant) * dt, 0)
                .scale(Math.pow(.992D, dt));
        Vec3 start = piece.position;
        double impact = piece.velocity.length();
        boolean collided = false;
        Vec3 x = start.add(piece.velocity.x * dt, 0, 0);
        if (clear(level, piece, x)) start = x; else { piece.velocity = piece.velocity.multiply(-.22D, 1, 1); collided = true; }
        Vec3 y = start.add(0, piece.velocity.y * dt, 0);
        if (clear(level, piece, y)) start = y; else { piece.velocity = piece.velocity.multiply(1, -.18D, 1); collided = true; }
        Vec3 z = start.add(0, 0, piece.velocity.z * dt);
        if (clear(level, piece, z)) start = z; else { piece.velocity = piece.velocity.multiply(1, 1, -.22D); collided = true; }
        piece.position = start;
        piece.rotation.rotateXYZ(piece.spin.x * (float)dt, piece.spin.y * (float)dt,
                piece.spin.z * (float)dt).normalize();
        piece.spin.mul(collided ? .72F : (float)Math.pow(.988F, dt));
        if (collided) {
            piece.velocity = piece.velocity.multiply(.78D, 1, .78D);
            if (impact > .24D && piece.soundCooldown-- <= 0) {
                var sound = switch (piece.variant % 3) {
                    case 0 -> Asterion.DEBRIS_1;
                    case 1 -> Asterion.DEBRIS_2;
                    default -> Asterion.DEBRIS_3;
                };
                level.playLocalSound(piece.position.x, piece.position.y, piece.position.z, sound,
                        SoundSource.BLOCKS, Mth.clamp(.15F + piece.scale * .7F, .15F, .8F),
                        Mth.clamp(1.15F - piece.scale * .28F, .52F, 1.18F), false);
                piece.soundCooldown = 8;
                level.addParticle(ParticleTypes.POOF, piece.position.x, piece.position.y, piece.position.z, 0, .02D, 0);
            }
        } else if (piece.soundCooldown > 0) piece.soundCooldown--;
        if (collided && piece.velocity.lengthSqr() < .0012D && piece.spin.lengthSquared() < .0012F) {
            if (++piece.restTicks > 12) piece.sleeping = true;
        } else piece.restTicks = 0;
    }

    private static void ensureLevel(ClientLevel level) {
        if (trackedLevel == level) return;
        PIECES.clear();
        trackedLevel = level;
        lastAmbientTick = Long.MIN_VALUE;
    }

    private static boolean clear(ClientLevel level, Piece piece, Vec3 center) {
        Vec3 half = halfExtents(piece.variant).scale(piece.scale);
        return level.noCollision(new AABB(center.x - half.x, center.y - half.y, center.z - half.z,
                center.x + half.x, center.y + half.y, center.z + half.z).deflate(.001D));
    }

    private static Vec3 findClearSpawn(ClientLevel level, Piece piece, Vec3 origin) {
        for (double lift : new double[]{0, .18D, .36D, .65D, 1.0D}) {
            Vec3 candidate = origin.add(0, lift, 0);
            if (clear(level, piece, candidate)) return candidate;
        }
        return null;
    }

    private static void emitDust(ClientLevel level, Vec3 position, Vec3 normal, Random random, int count) {
        for (int i = 0; i < count; i++) {
            Vec3 spread = position.add((random.nextDouble() - .5D) * .55D,
                    (random.nextDouble() - .5D) * .25D, (random.nextDouble() - .5D) * .55D);
            level.addParticle(Asterion.ANCIENT_WALL_DUST, spread.x, spread.y, spread.z,
                    normal.x * .012D, normal.y * .012D, normal.z * .012D);
        }
    }

    private static Vec3 halfExtents(int variant) {
        return switch (variant) {
            case 1 -> new Vec3(1.32D, 2.1D, .66D);
            case 2 -> new Vec3(.45D, .90D, .23D);
            case 7 -> new Vec3(1.75D, 2.5D, .25D);
            default -> variant >= 5 ? new Vec3(.23D, .23D, .23D) : new Vec3(.44D, .44D, .23D);
        };
    }

    private static Vec3 modelCenter(int variant) {
        return switch (variant) {
            case 1 -> new Vec3(5.0D / 16.0D, 2.0D, -6.0D / 16.0D);
            case 2 -> new Vec3(0, 1, 0);
            case 5 -> new Vec3(.25D, .75D, 0);
            case 7 -> new Vec3(.6D / 16.0D, 2.5D, 0);
            default -> new Vec3(0, variant == 6 ? .25D : .5D, 0);
        };
    }

    private static double gravity(int variant) { return variant == 7 ? 1.35D : variant >= 5 ? .72D : 1.0D; }
    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
    private static void trim() {
        int quality = net.krodark.asterion.AsterionConfig.INSTANCE.ragdollPhysicsQuality;
        int limit = quality <= 0 ? 72 : quality == 1 ? 128 : 192;
        while (PIECES.size() > limit) PIECES.removeFirst();
    }

    private static final class Piece {
        final DebrisObject visual;
        final int variant, life;
        final float scale;
        final Quaternionf rotation = new Quaternionf();
        final Quaternionf previousRotation = new Quaternionf();
        final Vector3f spin;
        Vec3 position, previous, velocity = Vec3.ZERO;
        int age, restTicks, soundCooldown;
        boolean sleeping, arena;
        Piece(Vec3 position, int variant, float scale, Random random, int life) {
            this.position = this.previous = position;
            this.variant = Mth.clamp(variant, 1, 7);
            this.visual = new DebrisObject(this.variant);
            this.scale = scale;
            this.life = life;
            rotation.rotationXYZ(random.nextFloat() * Mth.TWO_PI, random.nextFloat() * Mth.TWO_PI,
                    random.nextFloat() * Mth.TWO_PI);
            previousRotation.set(rotation);
            spin = new Vector3f((random.nextFloat() - .5F) * .34F,
                    (random.nextFloat() - .5F) * .34F, (random.nextFloat() - .5F) * .34F);
        }
    }

    private static final class DebrisObject implements GeoAnimatable {
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
        private final int variant;
        private DebrisObject(int variant) { this.variant = variant; }
        @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
        @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
        @Override public double getTick(Object related) {
            ClientLevel level = Minecraft.getInstance().level;
            return level == null ? 0 : level.getGameTime();
        }
    }

    private static final class DebrisModel extends GeoModel<DebrisObject> {
        @Override public ResourceLocation getModelResource(DebrisObject object) {
            return Asterion.id("geo/physics/" + (object.variant == 7
                    ? "minotaur_door_debirs" : "debris" + object.variant) + ".geo.json");
        }
        @Override public ResourceLocation getTextureResource(DebrisObject object) {
            return Asterion.id("textures/physics/" + (object.variant == 7
                    ? "minotaur_door_debris" : "debris" + object.variant) + ".png");
        }
        @Override public ResourceLocation getAnimationResource(DebrisObject object) { return null; }
    }
}
