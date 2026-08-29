package net.krodark.asterion.client.ragdoll;

import com.mojang.blaze3d.vertex.PoseStack;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Client-only rigid debris. These are transient GeckoLib render objects, deliberately not blocks
 * or entities, and use the same oriented-box/SAT collision approach as the ragdoll system.
 */
public final class PhysicsDebrisSystem {
    private static final DebrisGeoRenderer RENDERER = new DebrisGeoRenderer();
    private static final List<Piece> PIECES = new ArrayList<>();
    private static final Vec3 MODEL_HALF_EXTENTS = new Vec3(1.50, 2.375, 1.00);
    private static final Vec3 MODEL_CENTER = new Vec3(5.0 / 16.0, 32.0 / 16.0, -2.0 / 16.0);
    private static ClientLevel trackedLevel;
    private static long lastAmbientTick = Long.MIN_VALUE;

    private PhysicsDebrisSystem() { }

    public static void clear() {
        PIECES.clear();
        trackedLevel = null;
        lastAmbientTick = Long.MIN_VALUE;
    }

    public static void spawnRumble(Vec3 center, float radius, float intensity, long seed) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null
                || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)) return;
        double distance = client.player.position().distanceTo(center);
        if (distance > Math.max(24.0, radius + 28.0)) return;
        Random random = new Random(seed ^ client.level.getGameTime() * 0x9E3779B97F4A7C15L);
        int count = Mth.clamp(2 + Math.round(intensity * 7.0F), 2, 10);
        for (int i = 0; i < count; i++) spawnFromCeiling(client.level, center, radius, intensity, random);
    }

    public static void spawnAmbientRumble(Minecraft client, float intensity, long seed) {
        if (client.level == null || client.player == null) return;
        long tick = client.level.getGameTime();
        int interval = Math.max(4, 11 - Math.round(intensity * 6.0F));
        if (tick == lastAmbientTick || Math.floorMod(tick + seed, interval) != 0) return;
        lastAmbientTick = tick;
        spawnRumble(client.player.position().add(0, 4, 0), 13.0F, intensity * 0.78F,
                seed ^ tick * 31L);
    }

    public static void tick(Minecraft client) {
        if (client.level == null || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)) {
            clear();
            return;
        }
        if (trackedLevel != client.level) {
            PIECES.clear();
            trackedLevel = client.level;
        }
        int substeps = Mth.clamp(2 + AsterionConfig.INSTANCE.ragdollPhysicsQuality, 2, 4);
        Iterator<Piece> iterator = PIECES.iterator();
        while (iterator.hasNext()) {
            Piece piece = iterator.next();
            piece.previousPosition = piece.position;
            piece.previousOrientation.set(piece.orientation);
            piece.age++;
            boolean shattered = false;
            for (int step = 0; step < substeps && !shattered; step++)
                shattered = simulateStep(client.level, piece, 1.0 / substeps);
            if (shattered || piece.age > piece.lifetime
                    || (client.player != null && piece.position.distanceToSqr(client.player.position()) > 128 * 128)) {
                if (!shattered && piece.age <= piece.lifetime) burst(client.level, piece, new Vec3(0, 1, 0), 0.25);
                iterator.remove();
            }
        }
    }

    public static void submit(PoseStack poses, LevelRenderState state, SubmitNodeCollector collector) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || PIECES.isEmpty()) return;
        Vec3 camera = state.cameraRenderState.pos;
        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (Piece piece : PIECES) {
            Vec3 position = piece.previousPosition.lerp(piece.position, partialTick);
            if (position.distanceToSqr(camera) > 96 * 96) continue;
            Quaternionf rotation = new Quaternionf(piece.previousOrientation).slerp(piece.orientation, partialTick);
            poses.pushPose();
            poses.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z);
            poses.mulPose(rotation);
            poses.scale(piece.scale, piece.scale, piece.scale);
            poses.translate(-MODEL_CENTER.x, -MODEL_CENTER.y, -MODEL_CENTER.z);
            int light = LevelRenderer.getLightCoords(client.level, BlockPos.containing(position));
            RENDERER.performRenderPass(piece.visual, null, poses, collector, state.cameraRenderState,
                    light, partialTick);
            poses.popPose();
        }
    }

    private static void spawnFromCeiling(ClientLevel level, Vec3 center, float radius,
                                         float intensity, Random random) {
        int cap = 32 + AsterionConfig.INSTANCE.ragdollPhysicsQuality * 16;
        if (PIECES.size() >= cap) return;
        double spread = Math.max(3.0, Math.min(14.0, radius * 0.65));
        double x = center.x + (random.nextDouble() * 2.0 - 1.0) * spread;
        double z = center.z + (random.nextDouble() * 2.0 - 1.0) * spread;
        int startY = Mth.floor(center.y) + 2;
        BlockPos ceiling = null;
        for (int y = startY; y <= startY + 22; y++) {
            BlockPos candidate = BlockPos.containing(x, y, z);
            if (!level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
                    && level.getBlockState(candidate.below()).getCollisionShape(level, candidate.below()).isEmpty()) {
                ceiling = candidate;
                break;
            }
        }
        float scale = 0.10F + random.nextFloat() * random.nextFloat() * 0.24F;
        Vec3 half = MODEL_HALF_EXTENTS.scale(scale * 0.88);
        double y = ceiling == null
                ? center.y + 5.0 + random.nextDouble() * 5.0
                : ceiling.getY() - half.y - 0.035;
        Piece piece = new Piece(new Vec3(x, y, z), scale, random);
        piece.velocity = new Vec3((random.nextDouble() - 0.5) * (0.10 + intensity * 0.12),
                -0.035 - random.nextDouble() * (0.09 + intensity * 0.12),
                (random.nextDouble() - 0.5) * (0.10 + intensity * 0.12));
        if (!isWorldClear(level, piece, piece.position)) {
            for (int i = 1; i <= 8 && !isWorldClear(level, piece, piece.position); i++)
                piece.position = piece.position.add(0, -0.08, 0);
            piece.previousPosition = piece.position;
        }
        if (!isWorldClear(level, piece, piece.position)) return;
        PIECES.add(piece);
        if (ceiling != null) ceilingDust(level, ceiling, random, intensity);
    }

    private static boolean simulateStep(ClientLevel level, Piece piece, double dt) {
        piece.velocity = piece.velocity.add(0, -0.055 * dt, 0).scale(Math.pow(0.994, dt));
        if (piece.velocity.y < -2.8) piece.velocity = new Vec3(piece.velocity.x, -2.8, piece.velocity.z);
        piece.angularVelocity.mul((float) Math.pow(0.992, dt));
        Quaternionf oldRotation = new Quaternionf(piece.orientation);
        piece.orientation.rotateXYZ(piece.angularVelocity.x * (float) dt,
                piece.angularVelocity.y * (float) dt, piece.angularVelocity.z * (float) dt).normalize();
        if (!isWorldClear(level, piece, piece.position)) {
            piece.orientation.set(oldRotation);
            piece.angularVelocity.mul(-0.28F);
        }

        Vec3 motion = piece.velocity.scale(dt);
        int sweeps = Mth.clamp((int) Math.ceil(motion.length() / Math.max(0.035, piece.scale * 0.22)), 1, 8);
        Vec3 increment = motion.scale(1.0 / sweeps);
        for (int sweep = 0; sweep < sweeps; sweep++) {
            Vec3 normal = move(level, piece, increment);
            if (normal == null) continue;
            double impactSpeed = Math.max(0.0, -piece.velocity.dot(normal));
            piece.impacts++;
            if (impactSpeed > 0.30 + piece.scale * 0.65 || piece.impacts >= 3) {
                burst(level, piece, normal, impactSpeed);
                return true;
            }
            piece.velocity = RagdollMath.reflect(piece.velocity, normal, 0.22, 0.34);
            Vec3 torque = normal.cross(piece.velocity).scale(0.28);
            piece.angularVelocity.add((float) torque.x, (float) torque.y, (float) torque.z);
        }
        return false;
    }

    private static Vec3 move(ClientLevel level, Piece piece, Vec3 delta) {
        Vec3 normal = null;
        if (Math.abs(delta.x) > 1.0e-8) normal = moveAxis(level, piece, new Vec3(delta.x, 0, 0),
                delta.x > 0 ? new Vec3(-1, 0, 0) : new Vec3(1, 0, 0), normal);
        if (Math.abs(delta.y) > 1.0e-8) normal = moveAxis(level, piece, new Vec3(0, delta.y, 0),
                delta.y > 0 ? new Vec3(0, -1, 0) : new Vec3(0, 1, 0), normal);
        if (Math.abs(delta.z) > 1.0e-8) normal = moveAxis(level, piece, new Vec3(0, 0, delta.z),
                delta.z > 0 ? new Vec3(0, 0, -1) : new Vec3(0, 0, 1), normal);
        return normal;
    }

    private static Vec3 moveAxis(ClientLevel level, Piece piece, Vec3 delta, Vec3 fallback, Vec3 previous) {
        Vec3 start = piece.position;
        Vec3 candidate = start.add(delta);
        Collision collision = collisionAt(level, piece, candidate);
        if (collision == null) {
            piece.position = candidate;
            return previous;
        }
        piece.position = furthestClear(level, piece, start, candidate);
        Vec3 outward = collision.normal.scale(-1.0);
        return outward.dot(fallback) < 0.20 ? fallback : outward;
    }

    private static Vec3 furthestClear(ClientLevel level, Piece piece, Vec3 clear, Vec3 blocked) {
        Vec3 low = clear, high = blocked;
        for (int i = 0; i < 8; i++) {
            Vec3 middle = low.lerp(high, 0.5);
            if (isWorldClear(level, piece, middle)) low = middle;
            else high = middle;
        }
        return low;
    }

    private static void ceilingDust(ClientLevel level, BlockPos ceiling, Random random, float intensity) {
        BlockState state = level.getBlockState(ceiling);
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, state);
        int count = 4 + Math.round(intensity * 5.0F);
        for (int i = 0; i < count; i++) level.addParticle(dust,
                ceiling.getX() + random.nextDouble(), ceiling.getY() - 0.03,
                ceiling.getZ() + random.nextDouble(),
                (random.nextDouble() - 0.5) * 0.035, -0.015 - random.nextDouble() * 0.055,
                (random.nextDouble() - 0.5) * 0.035);
    }

    private static void burst(ClientLevel level, Piece piece, Vec3 normal, double impactSpeed) {
        BlockPos hitPos = BlockPos.containing(piece.position.subtract(normal.scale(0.12)));
        BlockState hitState = level.getBlockState(hitPos);
        if (hitState.isAir()) hitState = level.getBlockState(hitPos.below());
        if (hitState.isAir()) return;
        Random random = new Random(piece.seed ^ piece.age * 131L);
        BlockParticleOption fragments = new BlockParticleOption(ParticleTypes.BLOCK, hitState);
        int count = Mth.clamp(10 + Math.round(piece.scale * 54.0F + (float) impactSpeed * 12.0F), 12, 38);
        RagdollMath.Basis basis = RagdollMath.directionBasis(normal);
        for (int i = 0; i < count; i++) {
            double outward = 0.05 + random.nextDouble() * (0.09 + impactSpeed * 0.16);
            double tangentA = (random.nextDouble() - 0.5) * (0.16 + impactSpeed * 0.12);
            double tangentB = (random.nextDouble() - 0.5) * (0.16 + impactSpeed * 0.12);
            Vec3 velocity = normal.scale(outward).add(basis.tangent().scale(tangentA))
                    .add(basis.bitangent().scale(tangentB));
            level.addParticle(fragments,
                    piece.position.x + normal.x * piece.scale * 0.25,
                    piece.position.y + normal.y * piece.scale * 0.25,
                    piece.position.z + normal.z * piece.scale * 0.25,
                    velocity.x, velocity.y, velocity.z);
        }
        level.addParticle(ParticleTypes.POOF, piece.position.x, piece.position.y, piece.position.z,
                normal.x * 0.04, Math.max(0.025, normal.y * 0.04), normal.z * 0.04);
        level.playLocalSound(piece.position.x, piece.position.y, piece.position.z,
                SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS,
                Mth.clamp(0.18F + piece.scale * 0.85F, 0.2F, 0.65F),
                0.72F + random.nextFloat() * 0.24F, false);
    }

    private static AABB boundsAt(Piece piece, Vec3 center) {
        Vec3 half = MODEL_HALF_EXTENTS.scale(piece.scale * 0.88);
        Vector3f x = piece.orientation.transform(new Vector3f((float) half.x, 0, 0));
        Vector3f y = piece.orientation.transform(new Vector3f(0, (float) half.y, 0));
        Vector3f z = piece.orientation.transform(new Vector3f(0, 0, (float) half.z));
        double hx = Math.abs(x.x) + Math.abs(y.x) + Math.abs(z.x);
        double hy = Math.abs(x.y) + Math.abs(y.y) + Math.abs(z.y);
        double hz = Math.abs(x.z) + Math.abs(y.z) + Math.abs(z.z);
        return new AABB(center.x - hx, center.y - hy, center.z - hz,
                center.x + hx, center.y + hy, center.z + hz);
    }

    private static boolean isWorldClear(ClientLevel level, Piece piece, Vec3 center) {
        return collisionAt(level, piece, center) == null;
    }

    private static Collision collisionAt(ClientLevel level, Piece piece, Vec3 center) {
        AABB broad = boundsAt(piece, center).deflate(0.00035);
        Collision deepest = null;
        for (var shape : level.getBlockCollisions(null, broad)) {
            for (AABB box : shape.toAabbs()) {
                Collision collision = satContact(center, MODEL_HALF_EXTENTS.scale(piece.scale * 0.88),
                        axes(piece), box.getCenter(),
                        new Vec3(box.getXsize() * 0.5, box.getYsize() * 0.5, box.getZsize() * 0.5),
                        WORLD_AXES);
                if (collision != null && (deepest == null || collision.depth > deepest.depth)) deepest = collision;
            }
        }
        return deepest;
    }

    private static final Vec3[] WORLD_AXES = {
            new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)
    };

    private static Vec3[] axes(Piece piece) {
        Vector3f x = piece.orientation.transform(new Vector3f(1, 0, 0));
        Vector3f y = piece.orientation.transform(new Vector3f(0, 1, 0));
        Vector3f z = piece.orientation.transform(new Vector3f(0, 0, 1));
        return new Vec3[] {new Vec3(x.x, x.y, x.z), new Vec3(y.x, y.y, y.z), new Vec3(z.x, z.y, z.z)};
    }

    private static Collision satContact(Vec3 centerA, Vec3 halfA, Vec3[] axesA,
                                        Vec3 centerB, Vec3 halfB, Vec3[] axesB) {
        Vec3 delta = centerB.subtract(centerA);
        Vec3 bestAxis = null;
        double bestDepth = Double.POSITIVE_INFINITY;
        Vec3[] candidates = new Vec3[15];
        System.arraycopy(axesA, 0, candidates, 0, 3);
        System.arraycopy(axesB, 0, candidates, 3, 3);
        int index = 6;
        for (Vec3 a : axesA) for (Vec3 b : axesB) candidates[index++] = a.cross(b);
        for (Vec3 raw : candidates) {
            double lengthSqr = raw.lengthSqr();
            if (lengthSqr < 1.0e-10) continue;
            Vec3 axis = raw.scale(1.0 / Math.sqrt(lengthSqr));
            double overlap = projectionRadius(halfA, axesA, axis)
                    + projectionRadius(halfB, axesB, axis) - Math.abs(delta.dot(axis));
            if (overlap <= 0.00045) return null;
            if (overlap < bestDepth) {
                bestDepth = overlap;
                bestAxis = delta.dot(axis) < 0 ? axis.scale(-1) : axis;
            }
        }
        return bestAxis == null ? null : new Collision(bestAxis, bestDepth);
    }

    private static double projectionRadius(Vec3 half, Vec3[] axes, Vec3 direction) {
        return half.x * Math.abs(axes[0].dot(direction))
                + half.y * Math.abs(axes[1].dot(direction))
                + half.z * Math.abs(axes[2].dot(direction));
    }

    private static final class Piece {
        private final DebrisPhysicsObject visual = new DebrisPhysicsObject();
        private final Quaternionf orientation;
        private final Quaternionf previousOrientation;
        private final Vector3f angularVelocity;
        private final float scale;
        private final int lifetime;
        private final long seed;
        private Vec3 position;
        private Vec3 previousPosition;
        private Vec3 velocity = Vec3.ZERO;
        private int age;
        private int impacts;

        private Piece(Vec3 position, float scale, Random random) {
            this.position = position;
            this.previousPosition = position;
            this.scale = scale;
            this.seed = random.nextLong();
            this.lifetime = 100 + random.nextInt(121);
            this.orientation = new Quaternionf().rotationXYZ(random.nextFloat() * Mth.TWO_PI,
                    random.nextFloat() * Mth.TWO_PI, random.nextFloat() * Mth.TWO_PI);
            this.previousOrientation = new Quaternionf(orientation);
            this.angularVelocity = new Vector3f((random.nextFloat() - 0.5F) * 0.42F,
                    (random.nextFloat() - 0.5F) * 0.42F,
                    (random.nextFloat() - 0.5F) * 0.42F);
        }
    }

    private record Collision(Vec3 normal, double depth) { }
}
