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
    private static long soundBudgetTick = Long.MIN_VALUE;
    private static final List<Vec3> SOUND_POSITIONS = new ArrayList<>();

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
                Quaternionf rotation = piece.interpolatedRotation.set(piece.previousRotation)
                        .slerp(piece.rotation, partial);
                poses.pushPose();
                poses.translate(position.x, position.y, position.z);
                poses.mulPose(rotation);
                poses.scale(piece.scale, piece.scale, piece.scale);
                Vec3 center = modelCenter(piece.variant);
                poses.translate(-center.x, -center.y, -center.z);
                long tick = client.level.getGameTime();
                if (!piece.sleeping || piece.cachedLight < 0 || tick - piece.lightSampleTick >= 20) {
                    piece.cachedLight = LevelRenderer.getLightColor(client.level, BlockPos.containing(piece.position));
                    piece.lightSampleTick = tick;
                }
                RENDERER.render(poses, piece.visual, context.consumers(), null, null, piece.cachedLight, partial);
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
                Piece fragment = new Piece(hinge, 3 + random.nextInt(4),
                        .15F + random.nextFloat() * .18F, random, 240);
                fragment.velocity = inward.scale(.25D + random.nextDouble() * .3D)
                        .add(across.scale(side * (.08D + random.nextDouble() * .12D)))
                        .add((random.nextDouble() - .5D) * .08D,
                                .08D + random.nextDouble() * .18D,
                                (random.nextDouble() - .5D) * .08D);
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
            if (piece.soundCooldown > 0) piece.soundCooldown--;
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
        if (piece.velocity.y < -2.8D)
            piece.velocity = new Vec3(piece.velocity.x, -2.8D, piece.velocity.z);

        piece.scratchRotation.set(piece.rotation);
        piece.rotation.rotateXYZ(piece.spin.x * (float)dt, piece.spin.y * (float)dt,
                piece.spin.z * (float)dt).normalize();
        if (collisionAt(level, piece, piece.position) != null) {
            piece.rotation.set(piece.scratchRotation);
            piece.spin.mul(-.24F);
        } else piece.spin.mul((float)Math.pow(.992F, dt));

        Vec3 motion = piece.velocity.scale(dt);
        int sweeps = Mth.clamp((int)Math.ceil(motion.length()
                / Math.max(.045D, piece.smallestExtent() * .65D)), 1, 10);
        Vec3 increment = motion.scale(1.0D / sweeps);
        boolean collided = false;
        boolean supported = false;
        double strongestImpact = 0;
        for (int sweep = 0; sweep < sweeps; sweep++) {
            Vec3 normal = move(level, piece, increment);
            if (normal == null) continue;
            collided = true;
            supported |= normal.y > .55D;
            double into = piece.velocity.dot(normal);
            double impact = Math.max(0, -into);
            strongestImpact = Math.max(strongestImpact, impact);
            if (into < 0) {
                Vec3 tangent = piece.velocity.subtract(normal.scale(into));
                piece.velocity = tangent.scale(1.0D - friction(piece.variant))
                        .add(normal.scale(impact * restitution(piece.variant)));
                Vec3 torque = normal.cross(tangent).scale(.18D + piece.scale * .12D);
                piece.spin.add((float)torque.x, (float)torque.y, (float)torque.z);
                if (normal.y > .55D) applyRollingContact(piece, normal);
            }
        }
        if (collided) {
            if (strongestImpact > .24D && piece.soundCooldown <= 0
                    && claimLocalSound(level, piece.position)) {
                var sound = switch (piece.soundVariant) {
                    case 0 -> Asterion.DEBRIS_1;
                    case 1 -> Asterion.DEBRIS_2;
                    default -> Asterion.DEBRIS_3;
                };
                level.playLocalSound(piece.position.x, piece.position.y, piece.position.z, sound,
                        SoundSource.BLOCKS, Mth.clamp((.12F + piece.scale * .58F)
                                * (float)Mth.clamp(strongestImpact * 1.7D, .45D, 1.0D), .10F, .72F),
                        Mth.clamp((1.13F - piece.scale * .25F) * piece.impactPitch, .48F, 1.32F), false);
                piece.soundCooldown = 13 + piece.soundVariant * 3;
                level.addParticle(ParticleTypes.POOF, piece.position.x, piece.position.y, piece.position.z, 0, .02D, 0);
            }
        }
        if (!supported && piece.velocity.lengthSqr() < .012D)
            supported = collisionAt(level, piece, piece.position.add(0, -.055D, 0)) != null;
        if (supported) {
            piece.velocity = new Vec3(piece.velocity.x * .88D, piece.velocity.y, piece.velocity.z * .88D);
            piece.spin.mul(.86F);
        }
        if (supported && piece.velocity.lengthSqr() < .0018D && piece.spin.lengthSquared() < .0018F) {
            if (++piece.restTicks > 16) {
                piece.sleeping = true;
                piece.velocity = Vec3.ZERO;
                piece.spin.zero();
            }
        } else piece.restTicks = 0;
    }

    private static Vec3 move(ClientLevel level, Piece piece, Vec3 delta) {
        Vec3 start = piece.position;
        Vec3 candidate = start.add(delta);
        Collision collision = collisionAt(level, piece, candidate);
        if (collision == null) {
            piece.position = candidate;
            return null;
        }
        Vec3 low = start, high = candidate;
        for (int i = 0; i < 7; i++) {
            Vec3 middle = low.lerp(high, .5D);
            if (collisionAt(level, piece, middle) == null) low = middle;
            else high = middle;
        }
        piece.position = low;
        return collision.normal.scale(-1.0D);
    }

    private static void applyRollingContact(Piece piece, Vec3 normal) {
        Vec3 tangent = piece.velocity.subtract(normal.scale(piece.velocity.dot(normal)));
        if (tangent.lengthSqr() < 1.0E-6D) return;
        double radius = Math.max(.04D, Math.min(piece.half.x, piece.half.z));
        Vec3 axis = normal.cross(tangent).scale(1.0D / radius);
        Vector3f target = new Vector3f((float)axis.x, (float)axis.y, (float)axis.z);
        piece.spin.lerp(target, piece.variant >= 5 ? .42F : .24F);
    }

    private static void ensureLevel(ClientLevel level) {
        if (trackedLevel == level) return;
        PIECES.clear();
        trackedLevel = level;
        lastAmbientTick = Long.MIN_VALUE;
        soundBudgetTick = Long.MIN_VALUE;
        SOUND_POSITIONS.clear();
    }

    private static boolean clear(ClientLevel level, Piece piece, Vec3 center) {
        return collisionAt(level, piece, center) == null;
    }

    private static Collision collisionAt(ClientLevel level, Piece piece, Vec3 center) {
        piece.updateGeometry();
        Vec3 broadHalf = piece.boundsHalf;
        AABB broad = new AABB(center.x - broadHalf.x, center.y - broadHalf.y, center.z - broadHalf.z,
                center.x + broadHalf.x, center.y + broadHalf.y, center.z + broadHalf.z).deflate(.00035D);
        Collision deepest = null;
        for (var shape : level.getBlockCollisions(null, broad)) for (AABB box : shape.toAabbs()) {
            Collision contact = satContact(piece, center, box);
            if (contact != null && (deepest == null || contact.depth > deepest.depth)) deepest = contact;
        }
        return deepest;
    }

    private static Collision satContact(Piece piece, Vec3 center, AABB box) {
        Vec3 delta = box.getCenter().subtract(center);
        Vec3 boxHalf = new Vec3(box.getXsize() * .5D, box.getYsize() * .5D, box.getZsize() * .5D);
        Vec3 best = null;
        double depth = Double.POSITIVE_INFINITY;
        for (Vec3 axis : piece.satAxes) {
            if (axis == null) continue;
            double overlap = projection(piece.half, piece.axes, axis)
                    + projection(boxHalf, WORLD_AXES, axis) - Math.abs(delta.dot(axis));
            if (overlap <= .00045D) return null;
            if (overlap < depth) {
                depth = overlap;
                best = delta.dot(axis) < 0 ? axis.scale(-1) : axis;
            }
        }
        return best == null ? null : new Collision(best, depth);
    }

    private static double projection(Vec3 half, Vec3[] axes, Vec3 direction) {
        return half.x * Math.abs(axes[0].dot(direction))
                + half.y * Math.abs(axes[1].dot(direction))
                + half.z * Math.abs(axes[2].dot(direction));
    }

    private static final Vec3[] WORLD_AXES = {
            new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)
    };
    private record Collision(Vec3 normal, double depth) {}

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

    private static boolean claimLocalSound(ClientLevel level, Vec3 position) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.player.distanceToSqr(position) > 56.0D * 56.0D) return false;
        long tick = level.getGameTime();
        if (tick != soundBudgetTick) {
            soundBudgetTick = tick;
            SOUND_POSITIONS.clear();
        }
        int limit = 2 + Math.max(0, net.krodark.asterion.AsterionConfig.INSTANCE.ragdollPhysicsQuality);
        if (SOUND_POSITIONS.size() >= limit
                || SOUND_POSITIONS.stream().anyMatch(other -> other.distanceToSqr(position) < 6.25D)) return false;
        SOUND_POSITIONS.add(position);
        return true;
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
    private static double restitution(int variant) { return variant == 7 ? .06D : variant >= 5 ? .24D : .14D; }
    private static double friction(int variant) { return variant == 7 ? .42D : variant >= 5 ? .20D : .31D; }
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
        final int soundVariant;
        final float impactPitch;
        final Vec3 half;
        final Quaternionf rotation = new Quaternionf();
        final Quaternionf previousRotation = new Quaternionf();
        final Quaternionf interpolatedRotation = new Quaternionf();
        final Quaternionf scratchRotation = new Quaternionf();
        final Quaternionf geometryRotation = new Quaternionf(Float.NaN, Float.NaN, Float.NaN, Float.NaN);
        final Vec3[] axes = new Vec3[3];
        final Vec3[] satAxes = new Vec3[15];
        final Vector3f spin;
        Vec3 boundsHalf;
        Vec3 position, previous, velocity = Vec3.ZERO;
        int age, restTicks, soundCooldown;
        int cachedLight = -1;
        long lightSampleTick = Long.MIN_VALUE;
        boolean sleeping, arena;
        Piece(Vec3 position, int variant, float scale, Random random, int life) {
            this.position = this.previous = position;
            this.variant = Mth.clamp(variant, 1, 7);
            this.visual = new DebrisObject(this.variant);
            this.scale = scale;
            this.half = halfExtents(this.variant).scale(scale);
            this.life = life;
            soundVariant = random.nextInt(3);
            impactPitch = .82F + random.nextFloat() * .36F;
            soundCooldown = random.nextInt(6);
            rotation.rotationXYZ(random.nextFloat() * Mth.TWO_PI, random.nextFloat() * Mth.TWO_PI,
                    random.nextFloat() * Mth.TWO_PI);
            previousRotation.set(rotation);
            spin = new Vector3f((random.nextFloat() - .5F) * .34F,
                    (random.nextFloat() - .5F) * .34F, (random.nextFloat() - .5F) * .34F);
        }

        double smallestExtent() { return Math.min(half.x, Math.min(half.y, half.z)); }

        void updateGeometry() {
            if (geometryRotation.equals(rotation)) return;
            geometryRotation.set(rotation);
            Vector3f x = rotation.transform(new Vector3f(1, 0, 0));
            Vector3f y = rotation.transform(new Vector3f(0, 1, 0));
            Vector3f z = rotation.transform(new Vector3f(0, 0, 1));
            axes[0] = new Vec3(x.x, x.y, x.z);
            axes[1] = new Vec3(y.x, y.y, y.z);
            axes[2] = new Vec3(z.x, z.y, z.z);
            System.arraycopy(axes, 0, satAxes, 0, 3);
            System.arraycopy(WORLD_AXES, 0, satAxes, 3, 3);
            int index = 6;
            for (Vec3 axis : axes) for (Vec3 world : WORLD_AXES) {
                Vec3 cross = axis.cross(world);
                double lengthSqr = cross.lengthSqr();
                satAxes[index++] = lengthSqr < 1.0E-10D ? null
                        : cross.scale(1.0D / Math.sqrt(lengthSqr));
            }
            boundsHalf = new Vec3(projection(half, axes, WORLD_AXES[0]),
                    projection(half, axes, WORLD_AXES[1]), projection(half, axes, WORLD_AXES[2]));
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
