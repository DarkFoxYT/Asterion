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
import net.minecraft.core.Direction;
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
    private static final VariantProfile[] PROFILES = {
            null,
            new VariantProfile(new Vec3(1.50, 2.375, 0.75), new Vec3(5.0 / 16.0, 2.0, -6.0 / 16.0),
                    1.00F, 1.00, 0.995, 0.12, 0.42, 0.46, 3, 0.82F, false, false, 110, 230),
            new VariantProfile(new Vec3(0.50, 1.00, 0.25), new Vec3(0.0, 1.0, 0.0),
                    0.62F, 0.96, 0.992, 0.16, 0.34, 0.40, 3, 1.08F, false, false, 120, 250),
            new VariantProfile(new Vec3(0.50, 0.50, 0.25), new Vec3(0.0, 0.50, 0.0),
                    0.46F, 0.91, 0.988, 0.20, 0.28, 0.34, 4, 1.25F, false, false, 125, 270),
            new VariantProfile(new Vec3(0.50, 0.50, 0.25), new Vec3(0.0, 0.50, 0.0),
                    0.34F, 0.86, 0.984, 0.24, 0.22, 0.30, 4, 1.42F, false, false, 135, 285),
            new VariantProfile(new Vec3(0.25, 0.25, 0.25), new Vec3(0.25, 0.75, 0.0),
                    0.20F, 0.76, 0.978, 0.29, 0.16, 0.25, 5, 1.72F, false, true, 150, 310),
            new VariantProfile(new Vec3(0.25, 0.25, 0.25), new Vec3(0.0, 0.25, 0.0),
                    0.09F, 0.62, 0.970, 0.36, 0.055, Double.POSITIVE_INFINITY,
                    Integer.MAX_VALUE, 2.15F, true, true, 220, 420)
    };
    private static ClientLevel trackedLevel;
    private static long lastAmbientTick = Long.MIN_VALUE;

    private PhysicsDebrisSystem() { }

    private static VariantProfile profile(int variant) {
        return PROFILES[Mth.clamp(variant, 1, 6)];
    }

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
        spawnAncientWallDust(client.level, center, radius, intensity, random);
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
        List<Piece> fracturedChildren = new ArrayList<>();
        Iterator<Piece> iterator = PIECES.iterator();
        while (iterator.hasNext()) {
            Piece piece = iterator.next();
            piece.previousPosition = piece.position;
            piece.previousOrientation.set(piece.orientation);
            piece.age++;
            boolean shattered = false;
            for (int step = 0; step < substeps && !shattered; step++)
                shattered = simulateStep(client.level, piece, 1.0 / substeps, fracturedChildren);
            if (shattered || piece.age > piece.lifetime
                    || (client.player != null && piece.position.distanceToSqr(client.player.position()) > 128 * 128)) {
                if (!shattered && !piece.unbreakable() && piece.age <= piece.lifetime)
                    burst(client.level, piece, new Vec3(0, 1, 0), 0.25);
                iterator.remove();
            }
        }
        // Never truncate a fracture family: debris 1 always resolves into the complete 2–6 set.
        // Normal ceiling spawning still respects the quality cap on following ticks.
        PIECES.addAll(fracturedChildren);
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
            Vec3 modelCenter = piece.modelCenter();
            poses.translate(-modelCenter.x, -modelCenter.y, -modelCenter.z);
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
        int variant = randomVariant(random);
        float scale = randomScale(variant, random);
        Vec3 half = profile(variant).halfExtents.scale(scale * 0.88);
        double y = ceiling == null
                ? center.y + 5.0 + random.nextDouble() * 5.0
                : ceiling.getY() - half.y - 0.035;
        Piece piece = new Piece(new Vec3(x, y, z), variant, scale, random);
        VariantProfile profile = profile(variant);
        double lightness = 1.0D - profile.massFactor;
        double drift = 1.0D + lightness * 1.05D;
        double fall = 0.72D + profile.massFactor * 0.28D;
        piece.velocity = new Vec3((random.nextDouble() - 0.5) * (0.10 + intensity * 0.12) * drift,
                (-0.035 - random.nextDouble() * (0.09 + intensity * 0.12)) * fall,
                (random.nextDouble() - 0.5) * (0.10 + intensity * 0.12) * drift);
        if (!isWorldClear(level, piece, piece.position)) {
            for (int i = 1; i <= 8 && !isWorldClear(level, piece, piece.position); i++)
                piece.position = piece.position.add(0, -0.08, 0);
            piece.previousPosition = piece.position;
        }
        if (!isWorldClear(level, piece, piece.position)) return;
        PIECES.add(piece);
        if (ceiling != null) ceilingDust(level, ceiling, random, intensity);
    }

    private static int randomVariant(Random random) {
        float roll = random.nextFloat();
        if (roll < 0.24F) return 1;
        if (roll < 0.44F) return 2;
        if (roll < 0.62F) return 3;
        if (roll < 0.78F) return 4;
        if (roll < 0.91F) return 5;
        return 6;
    }

    private static float randomScale(int variant, Random random) {
        float shaped = random.nextFloat() * random.nextFloat();
        return switch (variant) {
            case 1 -> 0.10F + shaped * 0.22F;
            case 2 -> 0.26F + shaped * 0.34F;
            case 3 -> 0.32F + shaped * 0.42F;
            case 4 -> 0.34F + shaped * 0.44F;
            case 5 -> 0.42F + shaped * 0.44F;
            default -> 0.48F + shaped * 0.46F;
        };
    }

    private static boolean simulateStep(ClientLevel level, Piece piece, double dt,
                                        List<Piece> fracturedChildren) {
        piece.velocity = piece.velocity.add(0, -0.055 * piece.gravityFactor() * dt, 0)
                .scale(Math.pow(piece.airRetention(), dt));
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
            if (!piece.unbreakable() && (impactSpeed > piece.breakSpeed()
                    || piece.impacts >= piece.maxImpacts())) {
                if (piece.variant == 1)
                    splitPrimaryDebris(level, piece, normal, impactSpeed, fracturedChildren);
                else
                    burst(level, piece, normal, impactSpeed);
                return true;
            }
            piece.velocity = RagdollMath.reflect(piece.velocity, normal,
                    piece.restitution(), piece.friction());
            if (piece.rolling() && normal.y > 0.55D) applyRollingContact(piece, normal);
            Vec3 torque = normal.cross(piece.velocity).scale(0.28 * piece.spinMultiplier());
            piece.angularVelocity.add((float) torque.x, (float) torque.y, (float) torque.z);
        }
        return false;
    }

    private static void applyRollingContact(Piece piece, Vec3 normal) {
        Vec3 tangentVelocity = piece.velocity.subtract(normal.scale(piece.velocity.dot(normal)));
        if (tangentVelocity.lengthSqr() < 1.0e-6D) {
            piece.angularVelocity.mul(0.94F);
            return;
        }
        double radius = Math.max(0.035D, Math.min(piece.halfExtents().x, piece.halfExtents().z));
        Vec3 rollingAxis = normal.cross(tangentVelocity).scale(1.0D / radius);
        float blend = piece.variant == 6 ? 0.52F : 0.30F;
        Vector3f target = new Vector3f((float) rollingAxis.x, (float) rollingAxis.y,
                (float) rollingAxis.z).mul(piece.spinMultiplier());
        piece.angularVelocity.lerp(target, blend);
        double outwardSpeed = Math.max(0.0D, piece.velocity.dot(normal));
        double rollingRetention = piece.variant == 6 ? 0.975D : 0.925D;
        piece.velocity = tangentVelocity.scale(rollingRetention)
                .add(normal.scale(outwardSpeed * (piece.variant == 6 ? 0.18D : 0.34D)));
    }

    /** Debris 1 is the intact wall cluster. Its terminal impact produces one of every authored
     * child mesh while preserving the parent's center-of-mass velocity. */
    private static void splitPrimaryDebris(ClientLevel level, Piece parent, Vec3 normal,
                                           double impactSpeed, List<Piece> output) {
        burst(level, parent, normal, impactSpeed);
        Random random = new Random(parent.seed ^ parent.age * 0x9E3779B97F4A7C15L);
        RagdollMath.Basis basis = RagdollMath.directionBasis(normal);
        List<Piece> children = new ArrayList<>(5);
        double[] scales = {0.0D, 0.0D, 1.16D, 1.34D, 1.30D, 1.54D, 1.48D};
        for (int variant = 2; variant <= 6; variant++) {
            double angle = (variant - 2) * Mth.TWO_PI / 5.0D + 0.24D;
            Vec3 radial = basis.tangent().scale(Math.cos(angle))
                    .add(basis.bitangent().scale(Math.sin(angle)));
            float childScale = (float) (parent.scale * scales[variant]);
            Vec3 childPosition = parent.position.add(normal.scale(0.055D + (variant - 2) * 0.012D))
                    .add(radial.scale(parent.scale * (0.34D + (variant - 2) * 0.055D)));
            Piece child = new Piece(childPosition, variant, childScale, random);
            child.orientation.set(parent.orientation).rotateXYZ(
                    (random.nextFloat() - 0.5F) * 0.24F,
                    (random.nextFloat() - 0.5F) * 0.24F,
                    (random.nextFloat() - 0.5F) * 0.24F).normalize();
            child.previousOrientation.set(child.orientation);
            child.velocity = parent.velocity.add(normal.scale(0.055D + impactSpeed * 0.10D))
                    .add(radial.scale(0.07D + impactSpeed * (0.055D + variant * 0.006D)));
            if (!isWorldClear(level, child, child.position)) {
                child.position = parent.position.add(normal.scale(0.04D));
                child.orientation.set(parent.orientation);
                child.previousOrientation.set(child.orientation);
            }
            child.previousPosition = child.position;
            children.add(child);
        }

        double totalMass = 0.0D;
        Vec3 relativeMomentum = Vec3.ZERO;
        for (Piece child : children) {
            totalMass += child.mass();
            relativeMomentum = relativeMomentum.add(child.velocity.subtract(parent.velocity).scale(child.mass()));
        }
        Vec3 correction = totalMass <= 1.0e-8D ? Vec3.ZERO : relativeMomentum.scale(1.0D / totalMass);
        for (Piece child : children) child.velocity = child.velocity.subtract(correction);
        output.addAll(children);
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

    private static void spawnAncientWallDust(ClientLevel level, Vec3 center, float radius,
                                             float intensity, Random random) {
        int emitters = Mth.clamp(4 + Math.round(intensity * 5.0F), 4, 10);
        double spread = Math.max(5.0, Math.min(15.0, radius * 0.72));
        for (int emitter = 0; emitter < emitters; emitter++) {
            DustSource source = findUpperWallSource(level, center, spread, random);
            if (source == null) continue;
            int count = 7 + random.nextInt(8) + Math.round(intensity * 4.0F);
            for (int i = 0; i < count; i++) {
                double across = (random.nextDouble() - 0.5D) * 1.15D;
                double depth = (random.nextDouble() - 0.5D) * 0.34D;
                Vec3 position = source.position.add(source.tangent.scale(across))
                        .add(source.normal.scale(depth));
                double outward = 0.002D + random.nextDouble() * 0.012D;
                level.addParticle(Asterion.ANCIENT_WALL_DUST,
                        position.x, position.y - random.nextDouble() * 0.28D, position.z,
                        source.normal.x * outward + (random.nextDouble() - 0.5D) * 0.007D,
                        -0.004D - random.nextDouble() * 0.018D,
                        source.normal.z * outward + (random.nextDouble() - 0.5D) * 0.007D);
            }
        }
    }

    private static DustSource findUpperWallSource(ClientLevel level, Vec3 center,
                                                   double spread, Random random) {
        for (int attempt = 0; attempt < 18; attempt++) {
            int x = Mth.floor(center.x + (random.nextDouble() * 2.0D - 1.0D) * spread);
            int z = Mth.floor(center.z + (random.nextDouble() * 2.0D - 1.0D) * spread);
            int lowY = Mth.floor(center.y) + 3;
            int highY = lowY + 20;

            // Undersides and ceiling seams are visible from inside the maze.
            for (int y = lowY; y <= highY; y++) {
                BlockPos wall = new BlockPos(x, y, z);
                if (isSolid(level, wall) && !isSolid(level, wall.below())) {
                    Direction side = horizontalAirSide(level, wall, random);
                    Vec3 normal = side == null ? new Vec3(0, -1, 0) : side.getUnitVec3();
                    Vec3 tangent = Math.abs(normal.y) > 0.5D ? new Vec3(1, 0, 0)
                            : new Vec3(-normal.z, 0, normal.x);
                    return new DustSource(Vec3.atCenterOf(wall).add(normal.scale(0.52D)), normal, tangent);
                }
            }

            // Open-topped wall columns shed dust over their exposed edges.
            for (int y = highY; y >= lowY; y--) {
                BlockPos wall = new BlockPos(x, y, z);
                if (!isSolid(level, wall) || isSolid(level, wall.above())) continue;
                Direction side = horizontalAirSide(level, wall, random);
                if (side == null) continue;
                Vec3 normal = side.getUnitVec3();
                Vec3 tangent = new Vec3(-normal.z, 0, normal.x);
                return new DustSource(Vec3.atCenterOf(wall).add(normal.scale(0.53D)).add(0, 0.42D, 0),
                        normal, tangent);
            }
        }
        return null;
    }

    private static Direction horizontalAirSide(ClientLevel level, BlockPos wall, Random random) {
        int offset = random.nextInt(4);
        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (int i = 0; i < directions.length; i++) {
            Direction direction = directions[(i + offset) & 3];
            if (!isSolid(level, wall.relative(direction))) return direction;
        }
        return null;
    }

    private static boolean isSolid(ClientLevel level, BlockPos position) {
        return !level.getBlockState(position).getCollisionShape(level, position).isEmpty();
    }

    private static void burst(ClientLevel level, Piece piece, Vec3 normal, double impactSpeed) {
        BlockPos hitPos = BlockPos.containing(piece.position.subtract(normal.scale(0.12)));
        BlockState hitState = level.getBlockState(hitPos);
        if (hitState.isAir()) hitState = level.getBlockState(hitPos.below());
        if (hitState.isAir()) return;
        Random random = new Random(piece.seed ^ piece.age * 131L);
        BlockParticleOption fragments = new BlockParticleOption(ParticleTypes.BLOCK, hitState);
        int count = Mth.clamp(8 + Math.round((piece.scale * 54.0F
                + (float) impactSpeed * 12.0F) * piece.massFactor()), 9, 38);
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
                Mth.clamp((0.18F + piece.scale * 0.85F) * piece.massFactor(), 0.14F, 0.65F),
                0.72F + (1.0F - piece.massFactor()) * 0.44F
                        + random.nextFloat() * 0.20F, false);
    }

    private static AABB boundsAt(Piece piece, Vec3 center) {
        Vec3 half = piece.halfExtents();
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
                Collision collision = satContact(center, piece.halfExtents(),
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
        private final DebrisPhysicsObject visual;
        private final Quaternionf orientation;
        private final Quaternionf previousOrientation;
        private final Vector3f angularVelocity;
        private final float scale;
        private final int variant;
        private final int lifetime;
        private final long seed;
        private Vec3 position;
        private Vec3 previousPosition;
        private Vec3 velocity = Vec3.ZERO;
        private int age;
        private int impacts;

        private Piece(Vec3 position, int variant, float scale, Random random) {
            this.position = position;
            this.previousPosition = position;
            this.variant = Mth.clamp(variant, 1, 6);
            this.visual = new DebrisPhysicsObject(this.variant);
            this.scale = scale;
            this.seed = random.nextLong();
            VariantProfile profile = profile(this.variant);
            this.lifetime = profile.minimumLifetime
                    + random.nextInt(profile.maximumLifetime - profile.minimumLifetime + 1);
            this.orientation = new Quaternionf().rotationXYZ(random.nextFloat() * Mth.TWO_PI,
                    random.nextFloat() * Mth.TWO_PI, random.nextFloat() * Mth.TWO_PI);
            this.previousOrientation = new Quaternionf(orientation);
            this.angularVelocity = new Vector3f((random.nextFloat() - 0.5F) * 0.42F,
                    (random.nextFloat() - 0.5F) * 0.42F,
                    (random.nextFloat() - 0.5F) * 0.42F).mul(profile.spinMultiplier);
        }

        private Vec3 halfExtents() {
            return profile(variant).halfExtents.scale(scale * 0.88);
        }

        private Vec3 modelCenter() {
            return profile(variant).modelCenter;
        }

        private double gravityFactor() {
            return profile(variant).gravityFactor;
        }

        private double airRetention() {
            return profile(variant).airRetention;
        }

        private float massFactor() {
            return profile(variant).massFactor;
        }

        private double mass() {
            Vec3 half = profile(variant).halfExtents;
            double modelVolume = half.x * half.y * half.z * 8.0D;
            return Math.max(1.0e-5D, massFactor() * modelVolume * scale * scale * scale);
        }

        private double restitution() {
            return profile(variant).restitution;
        }

        private double friction() {
            return profile(variant).friction;
        }

        private double breakSpeed() {
            VariantProfile profile = profile(variant);
            return profile.breakSpeed + scale * 0.52D * Math.sqrt(profile.massFactor);
        }

        private int maxImpacts() {
            return profile(variant).maximumImpacts;
        }

        private float spinMultiplier() {
            return profile(variant).spinMultiplier;
        }

        private boolean unbreakable() {
            return profile(variant).unbreakable;
        }

        private boolean rolling() {
            return profile(variant).rolling;
        }
    }

    private record VariantProfile(Vec3 halfExtents, Vec3 modelCenter, float massFactor,
                                  double gravityFactor, double airRetention,
                                  double restitution, double friction, double breakSpeed,
                                  int maximumImpacts, float spinMultiplier,
                                  boolean unbreakable, boolean rolling,
                                  int minimumLifetime, int maximumLifetime) { }
    private record Collision(Vec3 normal, double depth) { }
    private record DustSource(Vec3 position, Vec3 normal, Vec3 tangent) { }
}
