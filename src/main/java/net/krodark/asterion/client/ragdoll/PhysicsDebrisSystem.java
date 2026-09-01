package net.krodark.asterion.client.ragdoll;

import com.mojang.blaze3d.vertex.PoseStack;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.client.PerformanceGovernor;
import net.krodark.asterion.block.MinotaurDoorMotion;
import net.krodark.asterion.network.DoorBreakPayload;
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
                    Integer.MAX_VALUE, 2.15F, true, true, 220, 420),
            // Full authored door leaf: dense, low bounce, strong floor drag, long-lived and intact.
            new VariantProfile(new Vec3(1.75, 2.5, .25), new Vec3(.6 / 16.0, 2.5, 0),
                    8F, 1.35, .996, .14, .62, Double.POSITIVE_INFINITY,
                    Integer.MAX_VALUE, .3F, true, false, 900, 1100)
    };
    private static final List<DoorCloud> DOOR_CLOUDS = new ArrayList<>();
    private static final int MAX_PIECES = 192;
    private static ClientLevel trackedLevel;
    private static long lastAmbientTick = Long.MIN_VALUE;
    private static long lastImpactSoundTick = Long.MIN_VALUE;

    private PhysicsDebrisSystem() { }

    private static VariantProfile profile(int variant) {
        return PROFILES[Mth.clamp(variant, 1, 7)];
    }

    public static void clear() {
        PIECES.clear();
        DOOR_CLOUDS.clear();
        trackedLevel = null;
        lastAmbientTick = Long.MIN_VALUE;
        lastImpactSoundTick = Long.MIN_VALUE;
    }

    public static void spawnDoors(DoorBreakPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || !payload.facing().getAxis().isHorizontal()
                || client.player.distanceToSqr(Vec3.atCenterOf(payload.root())) > 96 * 96) return;
        if (trackedLevel != client.level) { clear(); trackedLevel = client.level; }
        while (PIECES.stream().filter(p -> p.variant == 7).count() > 10) {
            var oldest = PIECES.stream().filter(p -> p.variant == 7).findFirst().orElseThrow();
            PIECES.remove(oldest);
        }
        Random random = new Random(payload.seed());
        float angle = Float.isFinite(payload.angle()) ? Mth.clamp(payload.angle(), 0, MinotaurDoorMotion.OPEN_ANGLE) : 0;
        Vec3 inward = payload.facing().getOpposite().getUnitVec3();
        if (DOOR_CLOUDS.size() >= 4) DOOR_CLOUDS.removeFirst();
        DoorCloud cloud = new DoorCloud(Vec3.atBottomCenterOf(payload.root()), inward, random);
        DOOR_CLOUDS.add(cloud);
        cloud.emit(client.level, 160);
        for (int side : new int[] {-1, 1}) {
            Piece door = new Piece(MinotaurDoorMotion.leafCenter(payload.root(), payload.facing(), side, angle), 7, 1, random);
            float yaw = MinotaurDoorMotion.yaw(payload.facing()) + side * angle;
            door.orientation.rotationY(yaw);
            door.previousOrientation.set(door.orientation);
            // A kick supplies linear momentum and off-center torque; the leaves keep rotating freely.
            Vec3 across = payload.facing().getClockWise().getUnitVec3();
            door.velocity = inward.scale(1.55 + random.nextDouble() * .18)
                    .add(across.scale(side * .22)).add(0, .48 + random.nextDouble() * .10, 0);
            Vec3 spin = across.scale(.12 + random.nextDouble() * .035).add(inward.scale(side * .055));
            door.angularVelocity.set((float)spin.x, side * .10F, (float)spin.z);
            PIECES.add(door);
            doorDust(client.level, door, 52, .18);
        }
    }

    public static void spawnArenaDebris(net.krodark.asterion.network.ArenaDebrisPayload payload) {
        if (payload.fragments().isEmpty()) {
            PIECES.removeIf(piece -> piece.variant == 7 || piece.arenaRubble);
            DOOR_CLOUDS.clear();
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;
        if (trackedLevel != client.level) { clear(); trackedLevel = client.level; }
        Random random = new Random(payload.seed());
        int fragmentIndex = 0;
        for (var fragment : payload.fragments()) {
            Vec3 pos = fragment.position(), velocity = fragment.velocity();
            if (!Double.isFinite(pos.lengthSqr()) || !Double.isFinite(velocity.lengthSqr())
                    || pos.distanceToSqr(client.player.position()) > 96 * 96) continue;
            int variant = 2 + random.nextInt(3);
            Piece piece = new Piece(pos, variant, .55F + random.nextFloat() * .4F, random);
            piece.velocity = velocity.lengthSqr() > 9 ? velocity.normalize().scale(3) : velocity;
            piece.angularVelocity.mul(.55F);
            piece.arenaRubble = true;
            if ((fragmentIndex++ & 1) == 0) {
                piece.blockVisual = (random.nextBoolean() ? net.minecraft.world.level.block.Blocks.COBBLED_DEEPSLATE
                        : net.minecraft.world.level.block.Blocks.TUFF).defaultBlockState();
                piece.angularVelocity.mul(2.8F);
            }
            if (!isWorldClear(client.level, piece, pos)) continue;
            PIECES.add(piece);
            spawnDebrisSmoke(client.level, pos, random, 3);
        }
        trimDebris();
    }

    private static void trimDebris() {
        for (Iterator<Piece> it = PIECES.iterator(); PIECES.size() > MAX_PIECES && it.hasNext();) {
            Piece piece = it.next();
            if (piece.arenaRubble && piece.sleeping) it.remove();
        }
        for (Iterator<Piece> it = PIECES.iterator(); PIECES.size() > MAX_PIECES && it.hasNext();)
            if (it.next().variant != 7) it.remove();
    }

    private static final class DoorCloud {
        final Vec3 root, inward;
        final Random random;
        int age;
        DoorCloud(Vec3 root, Vec3 inward, Random random) { this.root = root; this.inward = inward; this.random = random; }
        void emit(ClientLevel level, int count) {
            Vec3 across = new Vec3(inward.z, 0, -inward.x);
            for (int i = 0; i < count; i++) {
                double side = (random.nextDouble() - .5) * 10;
                // Dense lower billows and frame plumes; a thinner center lets the eye glow read through.
                double height = Math.abs(side) > 2.2 ? random.nextDouble() * 4.8 : random.nextDouble() * 2.4;
                Vec3 pos = root.add(across.scale(side)).add(inward.scale(.4 + random.nextDouble() * 9.5)).add(0, height, 0);
                Vec3 drift = inward.scale(.12 + random.nextDouble() * .20).add(across.scale(side * .014));
                level.addParticle(Asterion.DOOR_SMOKE, true, false, pos.x, pos.y, pos.z, drift.x, .025 + random.nextDouble() * .07, drift.z);
            }
        }
    }

    public static void spawnRumble(Vec3 center, float radius, float intensity, long seed) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null
                || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)) return;
        double distance = client.player.position().distanceTo(center);
        if (distance > Math.max(24.0, radius + 28.0)) return;
        Random random = new Random(seed ^ client.level.getGameTime() * 0x9E3779B97F4A7C15L);
        int count = Mth.clamp(2 + Math.round(intensity * 4.0F), 2, 6);
        for (int i = 0; i < count; i++) spawnSurfaceRubble(client.level, client.player.position(), intensity, random);
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
        if (client.level == null) {
            clear();
            return;
        }
        if (trackedLevel != client.level) {
            clear();
            trackedLevel = client.level;
        }
        DOOR_CLOUDS.removeIf(cloud -> ++cloud.age > 42);
        for (DoorCloud cloud : DOOR_CLOUDS) cloud.emit(client.level, cloud.age < 16 ? 8 : 4);
        if(PIECES.isEmpty())return;
        int substeps = Mth.clamp(2 + Math.min(AsterionConfig.INSTANCE.ragdollPhysicsQuality,
                net.krodark.asterion.client.PerformanceGovernor.quality()), 2, 4);
        List<Piece> fracturedChildren = new ArrayList<>();
        Iterator<Piece> iterator = PIECES.iterator();
        while (iterator.hasNext()) {
            Piece piece = iterator.next();
            piece.previousPosition = piece.position;
            piece.previousOrientation.set(piece.orientation);
            piece.age++;
            boolean shattered = false;
            // Settled doors only recheck support twice a second, with no repeated substeps.
            if (!piece.sleeping || piece.age % 10 == 0)
                for (int step = 0; step < substeps && !shattered && (step == 0 || !piece.sleeping); step++)
                    shattered = simulateStep(client.level, piece, 1.0 / substeps, fracturedChildren);
            if (shattered || piece.age > piece.lifetime
                    || (client.player != null && piece.position.distanceToSqr(client.player.position()) > 128 * 128)) {
                if (!shattered && !piece.unbreakable() && piece.age <= piece.lifetime)
                    burst(client.level, piece, new Vec3(0, 1, 0), 0.25);
                iterator.remove();
            }
        }
        PIECES.addAll(fracturedChildren);
        trimDebris();
    }

    public static void submit(PoseStack poses, LevelRenderState state, SubmitNodeCollector collector) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || PIECES.isEmpty()) return;
        Vec3 camera = state.cameraRenderState.pos;
        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (Piece piece : PIECES) {
            Vec3 position = piece.previousPosition.lerp(piece.position, partialTick);
            if (position.distanceToSqr(camera) > 96 * 96) continue;
            double cullRadius = piece.halfExtents().length() + .5;
            if (!state.cameraRenderState.cullFrustum.isVisible(new AABB(position, position).inflate(cullRadius))) continue;
            Quaternionf rotation = new Quaternionf(piece.previousOrientation).slerp(piece.orientation, partialTick);
            poses.pushPose();
            poses.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z);
            poses.mulPose(rotation);
            poses.scale(piece.scale, piece.scale, piece.scale);
            if (piece.blockVisual != null) {
                poses.translate(-.5, -.5, -.5);
                var block = new net.minecraft.client.renderer.block.MovingBlockRenderState();
                block.blockPos = BlockPos.containing(position);
                block.randomSeedPos = block.blockPos;
                block.blockState = piece.blockVisual;
                block.biome = client.level.getBiome(block.blockPos);
                block.cardinalLighting = client.level.cardinalLighting();
                block.lightEngine = client.level.getLightEngine();
                collector.submitMovingBlock(poses, block);
                poses.popPose();
                continue;
            }
            Vec3 modelCenter = piece.modelCenter();
            poses.translate(-modelCenter.x, -modelCenter.y, -modelCenter.z);
            int light = LevelRenderer.getLightCoords(client.level, BlockPos.containing(position));
            RENDERER.performRenderPass(piece.visual, null, poses, collector, state.cameraRenderState,
                    light, partialTick);
            poses.popPose();
        }
    }

    private static void spawnSurfaceRubble(ClientLevel level, Vec3 observer, float intensity, Random random) {
        int cap = 32 + Math.min(AsterionConfig.INSTANCE.ragdollPhysicsQuality,
                PerformanceGovernor.quality()) * 16;
        if (PIECES.size() >= cap) return;
        var source = net.krodark.asterion.event.RumbleSources.find(level, observer, random);
        if (source == null) return;
        int variant = 2 + random.nextInt(5);
        Piece piece = new Piece(source.position(), variant, .14F + random.nextFloat() * .13F, random);
        piece.velocity = source.normal().scale(.045 + random.nextDouble() * .075)
                .add((random.nextDouble() - .5) * .04, -.025, (random.nextDouble() - .5) * .04);
        piece.angularVelocity.mul(.65F);
        if (!isWorldClear(level, piece, piece.position)) return;
        PIECES.add(piece);
        spawnDebrisSmoke(level, piece.position, random, 4 + Math.round(intensity * 3));
        Vec3 tangent = Math.abs(source.normal().y) > .5 ? new Vec3(1, 0, 0)
                : new Vec3(-source.normal().z, 0, source.normal().x);
        for (int i = 0; i < 8; i++) {
            Vec3 dust = piece.position.add(tangent.scale((random.nextDouble() - .5) * .6));
            level.addParticle(Asterion.ANCIENT_WALL_DUST, dust.x, dust.y, dust.z,
                    source.normal().x * .008, -.012, source.normal().z * .008);
        }
    }

    private static boolean simulateStep(ClientLevel level, Piece piece, double dt,
                                        List<Piece> fracturedChildren) {
        if (piece.sleeping) {
            if (!isWorldClear(level, piece, piece.position.add(0, -.04, 0))) return false;
            piece.sleeping = false;
        }
        if (piece.variant == 7 || piece.arenaRubble) {
            simulateDoorStep(level, piece, dt);
            return piece.shattered;
        }
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
            triggerImpactShake(level, piece, normal, impactSpeed);
            piece.impacts++;
            boolean shouldBreak = !piece.unbreakable() && (impactSpeed > piece.breakSpeed()
                    || piece.impacts >= piece.maxImpacts());
            boolean floorContact = normal.y > 0.55D;
            if (shouldBreak && piece.consumeSurfaceSurvival(floorContact)) {
                piece.impacts = Math.max(0, piece.impacts - 2);
                shouldBreak = false;
            }
            if (shouldBreak) {
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

    private static void simulateDoorStep(ClientLevel level, Piece door, double dt) {
        // Sweep both translation and rotation. Contact impulses use the plank's box inertia,
        // so a corner striking the floor changes its spin instead of forcing a canned flat pose.
        if (door.velocity.lengthSqr() > 3.2 * 3.2) door.velocity = door.velocity.normalize().scale(3.2);
        if (door.angularVelocity.lengthSquared() > 1) door.angularVelocity.normalize();
        int sweeps = Mth.clamp((int)Math.ceil((door.velocity.length()
                + door.angularVelocity.length() * door.halfExtents().length()) * dt / .16), 1, 16);
        double h = dt / sweeps;
        boolean supported = false;
        double strongestImpact = 0;
        for (int sweep = 0; sweep < sweeps; sweep++) {
            door.velocity = door.velocity.add(0, -.055 * door.gravityFactor() * h, 0)
                    .scale(Math.pow(door.airRetention(), h));
            door.angularVelocity.mul((float)Math.pow(.996, h));
            door.position = door.position.add(door.velocity.scale(h));
            door.orientation.premul(new Quaternionf().rotationXYZ(door.angularVelocity.x * (float)h,
                    door.angularVelocity.y * (float)h, door.angularVelocity.z * (float)h)).normalize();
            for (int iteration = 0; iteration < 6; iteration++) {
                Collision contact = collisionAt(level, door, door.position);
                if (contact == null) break;
                Vec3 normal = contact.normal.scale(-1);
                Vec3 point = contact.point;
                door.position = door.position.add(normal.scale(contact.depth + .0006));
                Vec3 lever = point.subtract(door.position);
                double speed = door.velocityAt(point).dot(normal);
                strongestImpact = Math.max(strongestImpact, -speed);
                triggerImpactShake(level, door, normal, -speed);
                if (door.blockVisual != null && door.age > 3 && speed < -.32) {
                    burst(level, door, normal, -speed);
                    door.shattered = true;
                    return;
                }
                supported |= normal.y > .55;
                if (speed >= 0) continue;
                double bounce = speed < -.18 ? door.restitution() : 0;
                double impulse = -(1 + bounce) * speed / door.effectiveInverseMass(lever, normal);
                door.applyImpulse(lever, normal.scale(impulse));
                Vec3 atContact = door.velocityAt(point);
                Vec3 tangent = atContact.subtract(normal.scale(atContact.dot(normal)));
                double sliding = tangent.length();
                if (sliding > 1.0e-6) {
                    tangent = tangent.scale(1 / sliding);
                    double friction = Math.min(door.friction() * impulse,
                            sliding / door.effectiveInverseMass(lever, tangent));
                    door.applyImpulse(lever, tangent.scale(-friction));
                }
            }
        }
        if (supported) door.angularVelocity.mul((float)Math.pow(.92, dt));
        boolean slow = door.velocity.horizontalDistanceSqr() < .0025 && Math.abs(door.velocity.y) < .09
                && door.angularVelocity.lengthSquared() < .004;
        // Tiny separation after contact resolution must not keep a resting plank awake forever.
        if (slow && !supported) supported = !isWorldClear(level, door, door.position.add(0, -.035, 0));
        boolean quiet = slow && supported;
        door.restingTime = quiet ? door.restingTime + dt : 0;
        if (door.restingTime > 12) {
            door.sleeping = true;
            door.velocity = Vec3.ZERO;
            door.angularVelocity.zero();
        }
        if ((strongestImpact > .18 || supported && door.velocity.horizontalDistanceSqr() > .015)
                && door.age - door.lastDustTick >= (strongestImpact > .18 ? 5 : 9)) {
            doorDust(level, door, strongestImpact > .18 ? (door.variant == 7 ? 48 : 8) : 4, Math.min(.24, strongestImpact * .16));
            door.lastDustTick = door.age;
            if (strongestImpact > .18 && (lastImpactSoundTick == Long.MIN_VALUE || level.getGameTime() - lastImpactSoundTick >= 2)) {
                lastImpactSoundTick = level.getGameTime();
                level.playLocalSound(door.position.x, door.position.y, door.position.z,
                        door.variant == 7 ? SoundEvents.ANVIL_LAND : SoundEvents.STONE_HIT,
                        SoundSource.BLOCKS, door.variant == 7 ? 1.6F : .8F, .5F, false);
            }
        }
    }

    private static void triggerImpactShake(ClientLevel level, Piece piece, Vec3 normal, double impactSpeed) {
        if (impactSpeed < .42D || normal.y < .45D) return;
        boolean fullSizeDebris = piece.variant == 1 || piece.variant == 7;
        boolean largeArenaRubble = piece.arenaRubble && piece.scale >= .70F;
        if (!fullSizeDebris && !largeArenaRubble) return;
        if (piece.lastShakeTick != Long.MIN_VALUE && level.getGameTime() - piece.lastShakeTick < 12L) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        float radius = piece.variant == 7 ? 30.0F : largeArenaRubble ? 18.0F : 22.0F;
        if (client.player.position().distanceToSqr(piece.position) >= radius * radius) return;

        double massWeight = Mth.clamp(Math.sqrt(piece.mass()) * .42D, .55D, 1.45D);
        float strength = (float) Mth.clamp((impactSpeed - .24D) * massWeight,
                .16D, piece.variant == 7 ? 1.35D : largeArenaRubble ? .62D : .95D);
        int duration = Mth.clamp(7 + (int)Math.round(impactSpeed * 7.0D), 9, 18);
        piece.lastShakeTick = level.getGameTime();
        net.krodark.asterion.client.event.DeadSunClientEvents.impact(
                piece.position, radius, strength, duration);
    }

    /** Existing boss/explosion packets can knock settled leaves loose again. */
    public static void throwDoors(Vec3 center, float radius) {
        if (!Float.isFinite(radius) || radius <= 0) return;
        double reach = Math.min(32, radius * 2.0 + 3);
        for (Piece door : PIECES) {
            if (door.variant != 7 && !door.arenaRubble) continue;
            Vec3 offset = door.position.subtract(center);
            double distance = offset.length();
            if (distance >= reach) continue;
            Vec3 away = distance > .01 ? offset.scale(1 / distance) : new Vec3(0, 1, 0);
            double strength = (1 - distance / reach) * Math.min(1.5, radius * .22);
            door.velocity = door.velocity.add(away.scale(strength)).add(0, strength * .55, 0);
            door.angularVelocity.add((float)(away.z * .14), .08F, (float)(-away.x * .14));
            door.sleeping = false;
            door.restingTime = 0;
        }
    }

    private static void doorDust(ClientLevel level, Piece door, int count, double speed) {
        AABB bounds = boundsAt(door, door.position);
        for (int i = 0; i < count; i++) {
            double x = bounds.minX + level.getRandom().nextDouble() * bounds.getXsize();
            double z = bounds.minZ + level.getRandom().nextDouble() * bounds.getZsize();
            level.addParticle(Asterion.DOOR_DUST, x, bounds.minY + .09, z,
                    (level.getRandom().nextDouble() - .5) * speed, .012 + level.getRandom().nextDouble() * speed * .3,
                    (level.getRandom().nextDouble() - .5) * speed);
        }
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

    /** The intact wall cluster breaks into a different surviving subset on every impact. */
    private static void splitPrimaryDebris(ClientLevel level, Piece parent, Vec3 normal,
                                           double impactSpeed, List<Piece> output) {
        burst(level, parent, normal, impactSpeed);
        Random random = new Random(parent.seed ^ parent.age * 0x9E3779B97F4A7C15L);
        RagdollMath.Basis basis = RagdollMath.directionBasis(normal);
        List<Piece> children = new ArrayList<>(5);
        double[] scales = {0.0D, 0.0D, 1.16D, 1.34D, 1.30D, 1.54D, 1.48D};
        int wanted = 2 + random.nextInt(4);
        int accepted = 0;
        for (int variant = 2; variant <= 6; variant++) {
            int remaining = 7 - variant;
            if (accepted >= wanted || (random.nextFloat() > 0.66F && accepted + remaining > wanted)) continue;
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
            accepted++;
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

    private static void spawnDebrisSmoke(ClientLevel level, Vec3 position, Random random, int count) {
        for (int i = 0; i < count; i++) level.addParticle(Asterion.RUMBLE_SMOKE,
                position.x + (random.nextDouble() - 0.5D) * 0.75D,
                position.y + (random.nextDouble() - 0.5D) * 0.45D,
                position.z + (random.nextDouble() - 0.5D) * 0.75D,
                (random.nextDouble() - 0.5D) * 0.025D,
                0.004D + random.nextDouble() * 0.018D,
                (random.nextDouble() - 0.5D) * 0.025D);
    }

    private static void burst(ClientLevel level, Piece piece, Vec3 normal, double impactSpeed) {
        BlockPos hitPos = BlockPos.containing(piece.position.subtract(normal.scale(0.12)));
        BlockState hitState = level.getBlockState(hitPos);
        if (hitState.isAir()) hitState = level.getBlockState(hitPos.below());
        if (piece.blockVisual != null) hitState = piece.blockVisual;
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
                if (collision != null && (deepest == null || collision.depth > deepest.depth)) {
                    if (piece.variant != 7 && !piece.arenaRubble) { deepest = collision; continue; }
                    Vec3 point = supportPoint(piece, center, collision.normal);
                    point = new Vec3(Mth.clamp(point.x, box.minX, box.maxX), Mth.clamp(point.y, box.minY, box.maxY),
                            Mth.clamp(point.z, box.minZ, box.maxZ));
                    deepest = new Collision(collision.normal, collision.depth, point);
                }
            }
        }
        return deepest;
    }

    private static final Vec3[] WORLD_AXES = {
            new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)
    };

    private static Vec3 supportPoint(Piece piece, Vec3 center, Vec3 direction) {
        Vec3 half = piece.halfExtents();
        Vec3[] axes = axes(piece);
        double[] extents = {half.x, half.y, half.z};
        Vec3 point = center;
        for (int i = 0; i < 3; i++) {
            double dot = axes[i].dot(direction);
            if (Math.abs(dot) > .001) point = point.add(axes[i].scale(Math.signum(dot) * extents[i]));
        }
        return point;
    }

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
        return bestAxis == null ? null : new Collision(bestAxis, bestDepth, null);
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
        private int floorSurvivals;
        private int wallSurvivals;
        private int lastDustTick = -20;
        private long lastShakeTick = Long.MIN_VALUE;
        private boolean sleeping;
        private boolean arenaRubble, shattered;
        private BlockState blockVisual;
        private double restingTime;

        private Piece(Vec3 position, int variant, float scale, Random random) {
            this.position = position;
            this.previousPosition = position;
            this.variant = Mth.clamp(variant, 1, 7);
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
            this.floorSurvivals = random.nextFloat() < (this.variant == 1 ? 0.72F : 0.42F)
                    ? 1 + random.nextInt(this.variant == 1 ? 3 : 2) : 0;
            this.wallSurvivals = random.nextFloat() < 0.58F ? 1 + random.nextInt(2) : 0;
        }

        private boolean consumeSurfaceSurvival(boolean floor) {
            if (floor && floorSurvivals > 0) { floorSurvivals--; return true; }
            if (!floor && wallSurvivals > 0) { wallSurvivals--; return true; }
            return false;
        }

        private Vec3 halfExtents() {
            return blockVisual != null ? new Vec3(.5, .5, .5).scale(scale)
                    : profile(variant).halfExtents.scale(scale * (variant == 7 ? 1.0 : .88));
        }

        private Vec3 modelCenter() {
            return profile(variant).modelCenter;
        }

        private double gravityFactor() {
            return arenaRubble ? 1.0 : profile(variant).gravityFactor;
        }

        private double airRetention() {
            return arenaRubble ? .998 : profile(variant).airRetention;
        }

        private float massFactor() {
            return profile(variant).massFactor;
        }

        private double mass() {
            Vec3 half = profile(variant).halfExtents;
            double modelVolume = half.x * half.y * half.z * 8.0D;
            return Math.max(1.0e-5D, massFactor() * modelVolume * scale * scale * scale);
        }

        private Vec3 inverseInertia(Vec3 torque) {
            Vector3f local = new Vector3f((float)torque.x, (float)torque.y, (float)torque.z);
            new Quaternionf(orientation).conjugate().transform(local);
            Vec3 half = halfExtents();
            double m = mass() / 3;
            local.set((float)(local.x / (m * (half.y * half.y + half.z * half.z))),
                    (float)(local.y / (m * (half.x * half.x + half.z * half.z))),
                    (float)(local.z / (m * (half.x * half.x + half.y * half.y))));
            orientation.transform(local);
            return new Vec3(local.x, local.y, local.z);
        }

        private Vec3 velocityAt(Vec3 point) {
            Vec3 spin = new Vec3(angularVelocity.x, angularVelocity.y, angularVelocity.z);
            return velocity.add(spin.cross(point.subtract(position)));
        }

        private double effectiveInverseMass(Vec3 lever, Vec3 direction) {
            return 1 / mass() + direction.dot(inverseInertia(lever.cross(direction)).cross(lever));
        }

        private void applyImpulse(Vec3 lever, Vec3 impulse) {
            velocity = velocity.add(impulse.scale(1 / mass()));
            Vec3 spin = inverseInertia(lever.cross(impulse));
            angularVelocity.add((float)spin.x, (float)spin.y, (float)spin.z);
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
    private record Collision(Vec3 normal, double depth, Vec3 point) { }
}
