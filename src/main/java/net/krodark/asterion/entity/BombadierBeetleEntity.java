package net.krodark.asterion.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.krodark.asterion.Asterion;

/** A timid maze beetle whose defensive smoke trail becomes its weapon. */
public final class BombadierBeetleEntity extends PathfinderMob implements GeoEntity {
    private static final int FLEE_TICKS = 100;
    private static final int IGNITION_SPREAD_TICKS = 24;
    private static final int GAS_BURN_TICKS = 18;
    private static final int DEFENCE_COOLDOWN_TICKS = 200;
    private static final double THREAT_DISTANCE = 4.5D;
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK_ANIMATION = RawAnimation.begin().thenLoop("walk");
    private static final EntityDataAccessor<Integer> DATA_DEFENCE_STATE = SynchedEntityData.defineId(
            BombadierBeetleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ATTACHED_SURFACE = SynchedEntityData.defineId(
            BombadierBeetleEntity.class, EntityDataSerializers.INT);
    private static final Direction[] WALL_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final List<Vec3> smokeTrail = new ArrayList<>();
    private final Set<UUID> ignitedVictims = new HashSet<>();
    private final List<BurningGas> burningGas = new ArrayList<>();
    private int defenceTicks;
    private int defenceCooldown;
    private int nextPanicTurn;
    private Vec3 threatPosition;
    private Vec3 lastPanicWaypoint;
    private int zigzagSide = 1;
    private int wallHeadingTicks;
    private int wallRunSide = 1;
    private double wallLateralMotion;
    private double wallVerticalMotion = 0.7D;
    private double targetWallLateralMotion;
    private double targetWallVerticalMotion = 0.7D;
    private Direction wallApproachDirection;
    private int wallApproachTicks;
    private int calmPatrolCooldown = 30;
    private Vec3 lastCalmPatrol;

    public BombadierBeetleEntity(EntityType<? extends BombadierBeetleEntity> type, Level level) {
        super(type, level);
        xpReward = 2;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D)
                .add(Attributes.FOLLOW_RANGE, 12.0D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason reason) {
        if (reason == EntitySpawnReason.NATURAL
                && (!(level instanceof ServerLevel serverLevel)
                || !serverLevel.dimension().equals(Asterion.ASTERION_LEVEL))) return false;
        return super.checkSpawnRules(level, reason);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DEFENCE_STATE, DefenceState.CALM.ordinal());
        builder.define(DATA_ATTACHED_SURFACE, Direction.DOWN.ordinal());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel) || !isAlive()) return;

        if (defenceCooldown > 0) defenceCooldown--;
        if (defenceState() == DefenceState.CALM) {
            boolean wallRunning = tickSurfaceLocomotion(false);
            if (!wallRunning) tickCalmPatrol();
            Player threat = defenceCooldown == 0 && tickCount % 10 == 0 ? nearbyThreat() : null;
            if (threat != null) beginDefence(threat.position());
            return;
        }

        defenceTicks++;
        if (defenceState() == DefenceState.FLEEING) tickFleeing(serverLevel);
        else tickIgnition(serverLevel);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive() && defenceState() == DefenceState.CALM && defenceCooldown == 0) {
            Entity attacker = source.getEntity();
            beginDefence(attacker == null ? null : attacker.position());
        }
        return hurt;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        resetFallDistance();
        return false;
    }

    private Player nearbyThreat() {
        return level().getNearestPlayer(getX(), getY(), getZ(), THREAT_DISTANCE,
                entity -> entity instanceof Player player && !player.isCreative()
                        && !player.isSpectator() && player.isAlive());
    }

    private void beginDefence(Vec3 threat) {
        smokeTrail.clear();
        burningGas.clear();
        ignitedVictims.clear();
        defenceTicks = 0;
        nextPanicTurn = 0;
        threatPosition = threat;
        lastPanicWaypoint = null;
        zigzagSide = random.nextBoolean() ? 1 : -1;
        setDefenceState(DefenceState.FLEEING);
    }

    private void tickFleeing(ServerLevel level) {
        boolean wallRunning = tickSurfaceLocomotion(true);
        if (!wallRunning && (nextPanicTurn-- <= 0 || navigation.isDone() || navigation.isStuck()))
            chooseMazeAwarePanicPath();

        if ((defenceTicks & 1) == 0) {
            Vec3 smoke = rearPosition().add(
                    (random.nextDouble() - 0.5D) * 0.18D, random.nextDouble() * 0.08D,
                    (random.nextDouble() - 0.5D) * 0.18D);
            smokeTrail.add(smoke);
            level.sendParticles(Asterion.BOMBARDIER_STENCH, smoke.x, smoke.y, smoke.z,
                    2, 0.10D, 0.04D, 0.10D, 0.006D);
        }

        if (defenceTicks >= FLEE_TICKS) {
            navigation.stop();
            holdToAttachedSurface();
            defenceTicks = 0;
            setDefenceState(DefenceState.IGNITING);
            Vec3 rear = rearPosition();
            level.sendParticles(ParticleTypes.FLAME, rear.x, rear.y, rear.z,
                    18, 0.22D, 0.15D, 0.22D, 0.035D);
            level.sendParticles(ParticleTypes.LAVA, rear.x, rear.y, rear.z,
                    5, 0.16D, 0.08D, 0.16D, 0.02D);
            playSound(SoundEvents.FIRECHARGE_USE, 0.9F, 1.45F + random.nextFloat() * 0.2F);
        }
    }

    private void tickIgnition(ServerLevel level) {
        navigation.stop();
        holdToAttachedSurface();
        Vec3 rear = rearPosition();
        level.sendParticles(ParticleTypes.FLAME, rear.x, rear.y, rear.z,
                3, 0.16D, 0.10D, 0.16D, 0.015D);
        if ((defenceTicks & 1) == 0)
            level.sendParticles(ParticleTypes.LAVA, rear.x, rear.y, rear.z,
                    2, 0.18D, 0.12D, 0.18D, 0.20D);

        if (defenceTicks <= IGNITION_SPREAD_TICKS) {
            int previous = smokeTrail.size() * (defenceTicks - 1) / IGNITION_SPREAD_TICKS;
            int current = smokeTrail.size() * defenceTicks / IGNITION_SPREAD_TICKS;
            for (int offset = previous; offset < current; offset++) {
                int index = smokeTrail.size() - 1 - offset;
                if (index >= 0) igniteSmokeAt(level, smokeTrail.get(index), (index & 1) == 0);
            }
            if (current > previous && defenceTicks % 4 == 0)
                playSound(SoundEvents.FIRECHARGE_USE, 0.34F, 1.65F + random.nextFloat() * 0.25F);
        }
        tickBurningGas(level);

        if (defenceTicks >= IGNITION_SPREAD_TICKS + GAS_BURN_TICKS) {
            smokeTrail.clear();
            burningGas.clear();
            ignitedVictims.clear();
            defenceTicks = 0;
            defenceCooldown = DEFENCE_COOLDOWN_TICKS;
            threatPosition = null;
            lastPanicWaypoint = null;
            setDefenceState(DefenceState.CALM);
        }
    }

    private void tickCalmPatrol() {
        if (calmPatrolCooldown-- > 0 || !navigation.isDone()) return;
        Path bestPath = null;
        Vec3 bestTarget = null;
        double bestScore = -Double.MAX_VALUE;
        for (int attempt = 0; attempt < 6; attempt++) {
            Vec3 target = DefaultRandomPos.getPos(this, 12, 4);
            if (target == null) continue;
            Path path = navigation.createPath(BlockPos.containing(target), 0);
            if (path == null || !path.canReach()) continue;
            double score = target.distanceToSqr(position()) - path.getNodeCount() * 0.3D;
            if (lastCalmPatrol != null && target.distanceToSqr(lastCalmPatrol) < 16.0D) score -= 30.0D;
            if (score > bestScore) {
                bestScore = score;
                bestPath = path;
                bestTarget = target;
            }
        }
        if (bestPath != null) {
            navigation.moveTo(bestPath, 0.82D);
            lastCalmPatrol = bestTarget;
        }
        calmPatrolCooldown = 45 + random.nextInt(75);
    }

    private void tickBurningGas(ServerLevel level) {
        for (int index = burningGas.size() - 1; index >= 0; index--) {
            BurningGas gas = burningGas.get(index);
            gas.age++;
            if ((gas.age & 1) == 0)
                level.sendParticles(ParticleTypes.SMALL_FLAME, gas.position.x, gas.position.y, gas.position.z,
                        2, 0.20D, 0.16D, 0.20D, 0.018D);
            if (gas.age == 1 || gas.age % 6 == 0)
                level.sendParticles(ParticleTypes.LAVA, gas.position.x, gas.position.y, gas.position.z,
                        1, 0.14D, 0.10D, 0.14D, 0.18D);
            if (gas.age >= GAS_BURN_TICKS) burningGas.remove(index);
        }
    }

    /**
     * Chooses alternating escape waypoints that are actually reachable through the maze. Candidate
     * paths are biased away from the threat and scored against immediately revisiting the last turn.
     */
    private void chooseMazeAwarePanicPath() {
        Vec3 origin = position();
        Vec3 away = threatPosition == null ? getLookAngle().multiply(1.0D, 0.0D, 1.0D)
                : origin.subtract(threatPosition).multiply(1.0D, 0.0D, 1.0D);
        if (away.lengthSqr() < 0.01D)
            away = new Vec3(random.nextDouble() - 0.5D, 0.0D, random.nextDouble() - 0.5D);
        away = away.normalize();
        Vec3 lateral = new Vec3(-away.z, 0.0D, away.x).scale(zigzagSide);

        Path bestPath = null;
        Vec3 bestPosition = null;
        double bestScore = -Double.MAX_VALUE;
        for (int attempt = 0; attempt < 10; attempt++) {
            double forward = 6.0D + random.nextDouble() * 7.0D;
            double sideways = 2.0D + random.nextDouble() * 4.5D;
            Vec3 desired = origin.add(away.scale(forward)).add(lateral.scale(sideways));
            Vec3 candidate = DefaultRandomPos.getPosTowards(this, 13, 5, desired, Math.PI * 0.72D);
            if (candidate == null) continue;
            Path path = navigation.createPath(BlockPos.containing(candidate), 0);
            if (path == null || path.getNodeCount() < 2) continue;

            double score = path.canReach() ? 80.0D : 0.0D;
            if (threatPosition != null) score += candidate.distanceToSqr(threatPosition) * 0.12D;
            score -= path.getNodeCount() * 0.35D;
            if (lastPanicWaypoint != null) {
                double repeatDistance = candidate.distanceToSqr(lastPanicWaypoint);
                if (repeatDistance < 20.0D) score -= 55.0D - repeatDistance * 2.0D;
            }
            score += random.nextDouble() * 8.0D;
            if (score > bestScore) {
                bestScore = score;
                bestPath = path;
                bestPosition = candidate;
            }
        }

        if (bestPath != null) {
            navigation.moveTo(bestPath, 2.15D);
            lastPanicWaypoint = bestPosition;
        } else {
            Vec3 fallback = threatPosition == null
                    ? DefaultRandomPos.getPos(this, 10, 4)
                    : DefaultRandomPos.getPosAway(this, 10, 4, threatPosition);
            if (fallback != null) navigation.moveTo(fallback.x, fallback.y, fallback.z, 2.15D);
        }
        zigzagSide = -zigzagSide;
        nextPanicTurn = 10 + random.nextInt(8);
    }

    /** Runs on the tangent plane of a contacted wall instead of applying spider-style upward motion. */
    private boolean tickSurfaceLocomotion(boolean frantic) {
        Direction surface = attachedSurface();
        if (surface == Direction.DOWN && horizontalCollision) {
            Direction wall = findContactWall();
            if (wall != null) attachToWall(wall);
            surface = attachedSurface();
        }
        if (surface == Direction.DOWN) {
            setNoGravity(false);
            if (frantic && tickWallApproach()) return true;
            return false;
        }

        if (horizontalCollision) {
            Direction corner = findContactWall();
            if (corner != null && corner != surface) {
                attachToWall(corner);
                surface = corner;
            } else targetWallLateralMotion = -targetWallLateralMotion;
        }

        if (!touchingSurface(surface)) {
            Direction replacement = findContactWall();
            if (replacement != null) {
                attachToWall(replacement);
                surface = replacement;
            } else {
                // Leave an exhausted edge gently; never launch away from the supporting wall.
                setDeltaMovement(getDeltaMovement().scale(0.42D));
                setAttachedSurface(Direction.DOWN);
                setNoGravity(false);
                resetFallDistance();
                return false;
            }
        }

        navigation.stop();
        setNoGravity(true);
        resetFallDistance();
        if (--wallHeadingTicks <= 0) {
            chooseWallHeading(frantic);
        }

        double steering = frantic ? 0.20D : 0.12D;
        wallLateralMotion = Mth.lerp(steering, wallLateralMotion, targetWallLateralMotion);
        wallVerticalMotion = Mth.lerp(steering, wallVerticalMotion, targetWallVerticalMotion);

        Vec3 normal = surface.getUnitVec3();
        Vec3 sideways = surface.getAxis() == Direction.Axis.X
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 tangent = new Vec3(0.0D, wallVerticalMotion, 0.0D)
                .add(sideways.scale(wallLateralMotion));
        if (tangent.lengthSqr() < 0.04D) tangent = sideways.scale(wallRunSide);
        tangent = tangent.normalize();
        double speed = frantic ? 0.32D : 0.15D;
        // The normal component is adhesion only; collision resolution keeps the body flush to the wall.
        setDeltaMovement(tangent.scale(speed).add(normal.scale(0.105D)));
        setYRot((float)(Mth.atan2(tangent.z, tangent.x) * Mth.RAD_TO_DEG) - 90.0F);
        setYBodyRot(getYRot());
        return true;
    }

    private void chooseWallHeading(boolean frantic) {
        double lateralMagnitude = 0.28D + random.nextDouble() * 0.82D;
        if (random.nextFloat() < 0.58F) wallRunSide = -wallRunSide;
        targetWallLateralMotion = wallRunSide * lateralMagnitude;
        float verticalChoice = random.nextFloat();
        targetWallVerticalMotion = verticalChoice < 0.16F
                ? -(0.20D + random.nextDouble() * 0.42D)
                : verticalChoice < 0.43F
                ? random.nextDouble() * 0.28D
                : 0.30D + random.nextDouble() * 0.78D;
        wallHeadingTicks = (frantic ? 9 : 18) + random.nextInt(frantic ? 14 : 22);
    }

    /** Occasionally cuts directly toward a nearby wall so wall-running is an intentional escape route. */
    private boolean tickWallApproach() {
        if (wallApproachTicks <= 0 && defenceTicks % 22 == 3 && random.nextFloat() < 0.7F) {
            wallApproachDirection = findNearbyWall();
            wallApproachTicks = wallApproachDirection == null ? 0 : 9;
        }
        if (wallApproachTicks <= 0 || wallApproachDirection == null) return false;

        navigation.stop();
        wallApproachTicks--;
        Vec3 toward = wallApproachDirection.getUnitVec3();
        Vec3 weave = wallApproachDirection.getAxis() == Direction.Axis.X
                ? new Vec3(0.0D, 0.0D, wallRunSide * 0.12D)
                : new Vec3(wallRunSide * 0.12D, 0.0D, 0.0D);
        Vec3 motion = toward.scale(0.29D).add(weave);
        setDeltaMovement(motion.x, getDeltaMovement().y, motion.z);
        setYRot((float)(Mth.atan2(motion.z, motion.x) * Mth.RAD_TO_DEG) - 90.0F);
        setYBodyRot(getYRot());
        return true;
    }

    private Direction findNearbyWall() {
        BlockPos origin = BlockPos.containing(getX(), getY() + 0.2D, getZ());
        Direction best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Direction direction : WALL_DIRECTIONS) {
            for (int distance = 1; distance <= 3; distance++) {
                BlockPos cursor = origin.relative(direction, distance);
                if (level().getBlockState(cursor).getCollisionShape(level(), cursor).isEmpty()) continue;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = direction;
                }
                break;
            }
        }
        return best;
    }

    private void attachToWall(Direction wall) {
        setAttachedSurface(wall);
        setNoGravity(true);
        wallRunSide = random.nextBoolean() ? 1 : -1;
        wallLateralMotion = wallRunSide * 0.45D;
        wallVerticalMotion = 0.72D;
        chooseWallHeading(defenceState() == DefenceState.FLEEING);
        wallApproachDirection = null;
        wallApproachTicks = 0;
        navigation.stop();
    }

    private Direction findContactWall() {
        Direction best = null;
        double bestAlignment = -Double.MAX_VALUE;
        Vec3 facing = Vec3.directionFromRotation(0.0F, getYRot());
        for (Direction direction : WALL_DIRECTIONS) {
            if (!touchingSurface(direction)) continue;
            Vec3 normal = direction.getUnitVec3();
            double alignment = facing.dot(normal);
            if (alignment > bestAlignment) {
                bestAlignment = alignment;
                best = direction;
            }
        }
        return best;
    }

    private boolean touchingSurface(Direction direction) {
        Vec3 normal = direction.getUnitVec3();
        return !level().noCollision(this, getBoundingBox().move(normal.scale(0.26D)));
    }

    private void holdToAttachedSurface() {
        Direction surface = attachedSurface();
        if (surface == Direction.DOWN || !touchingSurface(surface)) {
            setAttachedSurface(Direction.DOWN);
            setNoGravity(false);
            return;
        }
        setNoGravity(true);
        resetFallDistance();
        Vec3 normal = surface.getUnitVec3();
        setDeltaMovement(normal.scale(0.095D));
    }

    private void igniteSmokeAt(ServerLevel level, Vec3 point, boolean luminous) {
        burningGas.add(new BurningGas(point));
        level.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z,
                5, 0.22D, 0.18D, 0.22D, 0.025D);
        if (luminous)
            level.sendParticles(Asterion.BOMBARDIER_GAS_FIRE, point.x, point.y, point.z,
                    1, 0.08D, 0.05D, 0.08D, 0.012D);
        level.sendParticles(ParticleTypes.LAVA, point.x, point.y, point.z,
                2, 0.18D, 0.12D, 0.18D, 0.20D);
        level.sendParticles(ParticleTypes.SMALL_FLAME, point.x, point.y, point.z,
                2, 0.12D, 0.10D, 0.12D, 0.01D);
        AABB fireCloud = new AABB(point, point).inflate(1.05D);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, fireCloud,
                entity -> entity != this && entity.isAlive())) {
            if (!ignitedVictims.add(victim.getUUID())) continue;
            victim.igniteForSeconds(4.0F);
            victim.hurtServer(level, level.damageSources().inFire(), 4.0F);
        }
    }

    private Vec3 rearPosition() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        return position().add(Mth.sin(yaw) * 0.48D, 0.22D, -Mth.cos(yaw) * 0.48D);
    }

    public DefenceState defenceState() {
        int ordinal = Mth.clamp(getEntityData().get(DATA_DEFENCE_STATE), 0, DefenceState.values().length - 1);
        return DefenceState.values()[ordinal];
    }

    public Direction attachedSurface() {
        int ordinal = Mth.clamp(getEntityData().get(DATA_ATTACHED_SURFACE), 0, Direction.values().length - 1);
        return Direction.values()[ordinal];
    }

    private void setAttachedSurface(Direction direction) {
        getEntityData().set(DATA_ATTACHED_SURFACE, direction.ordinal());
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 3;
    }

    private void setDefenceState(DefenceState state) {
        getEntityData().set(DATA_DEFENCE_STATE, state.ordinal());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<BombadierBeetleEntity>("movement", 3, state -> {
            boolean moving = defenceState() == DefenceState.FLEEING
                    || getDeltaMovement().lengthSqr() > 0.0004D;
            state.setControllerSpeed(defenceState() == DefenceState.FLEEING ? 2.15F : 1.0F);
            return state.setAndContinue(moving ? WALK_ANIMATION : IDLE_ANIMATION);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    public enum DefenceState { CALM, FLEEING, IGNITING }

    private static final class BurningGas {
        private final Vec3 position;
        private int age;

        private BurningGas(Vec3 position) {
            this.position = position;
        }
    }
}
