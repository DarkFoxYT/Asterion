package net.krodark.labyrinth.entity;

import net.krodark.labyrinth.Labyrinth;
import net.krodark.labyrinth.LabyrinthConfig;
import net.krodark.labyrinth.WorldGenerator;
import net.krodark.labyrinth.event.DeadSunEventSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Eclipse stalking and chase controller. Boss behavior is deliberately reserved but unimplemented. */
public final class MinotaurEntity extends Monster {
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(
            MinotaurEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RAGE = SynchedEntityData.defineId(
            MinotaurEntity.class, EntityDataSerializers.INT);

    private UUID eclipseTarget;
    private int phaseTicks;
    private int gazeTicks;
    private int gazeTriggerTicks;
    private int sightings;
    private int sightingCooldown;
    private int warningTicks;
    private int chaseTicks;
    private int escapeDistanceTicks;
    private int stuckTicks;
    private int relocateTicks;
    private int approachTicks;
    private int damageThreshold;
    private float chaseDamage;
    private double previousTargetDistance = Double.MAX_VALUE;
    private Vec3 previousPosition = Vec3.ZERO;
    private boolean wasObserved;

    public enum BehaviorPhase { DORMANT, HUNTING, WARNING, CHASING, RETREATING, BOSS }
    public enum AnimationState { IDLE, WALK, WARNING, CHASE, ATTACK }

    public MinotaurEntity(EntityType<? extends MinotaurEntity> type, Level level) {
        super(type, level);
        xpReward = 35;
        moveControl = new HeavyMoveControl(this);
        setPathfindingMalus(PathType.WATER, 0.0F);
        setPathfindingMalus(PathType.WATER_BORDER, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.FOLLOW_RANGE, 96.0D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.8D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.82D)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.STEP_HEIGHT, 1.5D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, BehaviorPhase.DORMANT.ordinal());
        builder.define(DATA_RAGE, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
    }

    public static MinotaurEntity spawnHunter(ServerLevel level, ServerPlayer player) {
        MinotaurEntity minotaur = Labyrinth.MINOTAUR.create(level, EntitySpawnReason.EVENT);
        if (minotaur == null) return null;
        Vec3 spawn = minotaur.findStalkingPosition(player, true);
        if (spawn == null) return null;
        minotaur.setPos(spawn.x, spawn.y, spawn.z);
        minotaur.setYRot(player.getYRot() + 180.0F);
        minotaur.setPersistenceRequired();
        minotaur.beginHunting(player);
        return level.addFreshEntity(minotaur) ? minotaur : null;
    }

    public void beginHunting(ServerPlayer player) {
        eclipseTarget = player.getUUID();
        setBehaviorPhase(BehaviorPhase.HUNTING);
        setTarget(null);
        setAggressive(false);
        gazeTriggerTicks = random.nextIntBetweenInclusive(
                LabyrinthConfig.INSTANCE.minotaurGazeMinTicks, LabyrinthConfig.INSTANCE.minotaurGazeMaxTicks);
        damageThreshold = random.nextIntBetweenInclusive(
                LabyrinthConfig.INSTANCE.minotaurDamageMin, LabyrinthConfig.INSTANCE.minotaurDamageMax);
        previousPosition = position();
        previousTargetDistance = distanceTo(player);
    }

    public boolean isAssignedTo(Player player) {
        return eclipseTarget != null && eclipseTarget.equals(player.getUUID()) && !isRemoved();
    }

    public boolean isChasing() {
        return behaviorPhase() == BehaviorPhase.WARNING || behaviorPhase() == BehaviorPhase.CHASING;
    }

    public BehaviorPhase behaviorPhase() {
        int ordinal = Mth.clamp(getEntityData().get(DATA_PHASE), 0, BehaviorPhase.values().length - 1);
        return BehaviorPhase.values()[ordinal];
    }

    public int rage() {
        return getEntityData().get(DATA_RAGE);
    }

    private void setBehaviorPhase(BehaviorPhase phase) {
        getEntityData().set(DATA_PHASE, phase.ordinal());
        phaseTicks = 0;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        phaseTicks++;
        if (sightingCooldown > 0) sightingCooldown--;
        Player foundPlayer = eclipseTarget == null ? null : level.getPlayerByUUID(eclipseTarget);
        ServerPlayer player = foundPlayer instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        if (player == null || !player.isAlive() || player.isSpectator()
                || !player.level().dimension().equals(Labyrinth.LABYRINTH_LEVEL)) {
            beginRetreat(false);
            tickRetreat();
            return;
        }

        switch (behaviorPhase()) {
            case HUNTING -> tickHunting(level, player);
            case WARNING -> tickWarning(level, player);
            case CHASING -> tickChase(level, player);
            case RETREATING -> tickRetreat();
            case DORMANT -> { if (!DeadSunEventSystem.isEclipseActive(level)) discard(); }
            case BOSS -> { /* Reserved until the boss design is finalized. */ }
        }
    }

    private void tickHunting(ServerLevel level, ServerPlayer player) {
        if (!DeadSunEventSystem.isEclipseActive(level)) {
            beginRetreat(false);
            return;
        }
        setTarget(null);
        setAggressive(false);
        double distance = distanceTo(player);
        boolean observed = isPlayerLookingAtMe(player, distance);

        if (observed) {
            gazeTicks++;
            getLookControl().setLookAt(player, 8.0F, 8.0F);
            if (!wasObserved && sightingCooldown == 0) {
                sightings++;
                sightingCooldown = 50;
                playSound(SoundEvents.GOAT_AMBIENT, 1.8F, 0.45F + random.nextFloat() * 0.08F);
                if (sightings > 3 || random.nextInt(6) == 0) {
                    beginWarning();
                    return;
                }
            }
            if (gazeTicks >= gazeTriggerTicks) {
                beginWarning();
                return;
            }
        } else {
            if (wasObserved && gazeTicks > 15 && random.nextBoolean()) {
                playSound(SoundEvents.RAVAGER_STUNNED, 1.25F, 0.55F);
                relocateTicks = random.nextIntBetweenInclusive(25, 50);
            }
            gazeTicks = 0;
        }
        wasObserved = observed;

        boolean approaching = distance < previousTargetDistance - 0.055D;
        approachTicks = approaching ? approachTicks + 1 : Math.max(0, approachTicks - 2);
        if (distance <= LabyrinthConfig.INSTANCE.minotaurApproachDistance
                || (approachTicks >= 12 && distance < LabyrinthConfig.INSTANCE.minotaurApproachDistance + 7.0D)) {
            beginWarning();
            return;
        }
        previousTargetDistance = distance;

        if (relocateTicks > 0) {
            relocateTicks--;
            Vec3 away = position().subtract(player.position()).normalize().scale(12.0D).add(position());
            getNavigation().moveTo(away.x, away.y, away.z, 0.78D);
            if (relocateTicks == 0 && !player.hasLineOfSight(this)) relocateNear(player);
        } else if (distance < 30.0D || distance > 52.0D || (phaseTicks % 160 == 0 && !observed)) {
            relocateNear(player);
        } else if ((phaseTicks & 31) == 0 && getNavigation().isDone()) {
            Vec3 shadow = findStalkingPosition(player, false);
            if (shadow != null) getNavigation().moveTo(shadow.x, shadow.y, shadow.z, 0.58D);
        }
        playHeavySteps();
    }

    private void beginWarning() {
        if (behaviorPhase() == BehaviorPhase.WARNING || behaviorPhase() == BehaviorPhase.CHASING) return;
        setBehaviorPhase(BehaviorPhase.WARNING);
        warningTicks = random.nextIntBetweenInclusive(
                LabyrinthConfig.INSTANCE.minotaurWindupMinTicks, LabyrinthConfig.INSTANCE.minotaurWindupMaxTicks);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        playSound(SoundEvents.GOAT_PREPARE_RAM, 2.6F, 0.42F);
    }

    private void tickWarning(ServerLevel level, ServerPlayer player) {
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().multiply(0.15D, 1.0D, 0.15D));
        getLookControl().setLookAt(player, 5.0F, 5.0F);
        if ((phaseTicks % 12) == 0) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.15D, getZ(),
                    5, 0.55D, 0.05D, 0.55D, 0.015D);
            playSound(SoundEvents.RAVAGER_STEP, 1.6F, 0.52F);
        }
        if (--warningTicks <= 0) beginChase(player);
    }

    private void beginChase(ServerPlayer player) {
        setBehaviorPhase(BehaviorPhase.CHASING);
        chaseTicks = 0;
        escapeDistanceTicks = 0;
        stuckTicks = 0;
        setTarget(player);
        setAggressive(true);
        updateChaseSpeed();
        playSound(SoundEvents.RAVAGER_ROAR, 3.2F, 0.58F);
    }

    private void tickChase(ServerLevel level, ServerPlayer player) {
        setTarget(player);
        setAggressive(true);
        chaseTicks++;
        double distance = distanceTo(player);
        if ((phaseTicks % 5) == 0) getNavigation().moveTo(player, 1.0D);

        double moved = position().distanceToSqr(previousPosition);
        previousPosition = position();
        if (distance > 4.0D && moved < 0.003D) stuckTicks++; else stuckTicks = Math.max(0, stuckTicks - 2);

        if ((stuckTicks > 12 || player.getY() > getY() + 2.2D) && (phaseTicks % 8) == 0) {
            Vec3 toward = player.position().subtract(position()).normalize();
            AABB breaker = getBoundingBox().expandTowards(toward.scale(2.1D)).inflate(0.8D, 1.2D, 0.8D);
            int broken = WorldGenerator.breakPlayerBlocksAround(level, breaker);
            if (broken > 0) {
                playSound(SoundEvents.RAVAGER_ATTACK, 1.7F, 0.62F);
                stuckTicks = 0;
            }
        }
        if ((stuckTicks > 35 || player.getY() > getY() + 3.0D) && onGround() && distance < 18.0D) {
            Vec3 leap = player.position().subtract(position());
            Vec3 horizontal = new Vec3(leap.x, 0.0D, leap.z).normalize();
            setDeltaMovement(horizontal.x * 0.9D, 0.72D, horizontal.z * 0.9D);
            hurtMarked = true;
            playSound(SoundEvents.GOAT_LONG_JUMP, 1.8F, 0.55F);
            stuckTicks = 0;
        }
        Entity vehicle = player.getVehicle();
        if (vehicle != null && distance < 5.0D) {
            player.stopRiding();
            vehicle.hurtServer(level, damageSources().mobAttack(this), 40.0F);
        }

        if (chaseTicks >= LabyrinthConfig.INSTANCE.minotaurEscapeTicks
                && distance >= LabyrinthConfig.INSTANCE.minotaurEscapeDistance) {
            escapeDistanceTicks++;
            if (escapeDistanceTicks >= 200) beginRetreat(true);
        } else escapeDistanceTicks = Math.max(0, escapeDistanceTicks - 2);
        playHeavySteps();
    }

    private void beginRetreat(boolean finishEclipse) {
        if (behaviorPhase() == BehaviorPhase.RETREATING) return;
        setBehaviorPhase(BehaviorPhase.RETREATING);
        setTarget(null);
        setAggressive(false);
        getNavigation().stop();
        if (finishEclipse && level() instanceof ServerLevel level) DeadSunEventSystem.finishEclipse(level);
    }

    public void endEclipse() {
        beginRetreat(false);
    }

    private void tickRetreat() {
        setTarget(null);
        setAggressive(false);
        if (phaseTicks == 1) playSound(SoundEvents.RAVAGER_AMBIENT, 1.7F, 0.42F);
        if (phaseTicks > 50) discard();
    }

    private boolean isPlayerLookingAtMe(ServerPlayer player, double distance) {
        if (distance > 54.0D || !player.hasLineOfSight(this)) return false;
        Vec3 towardMe = getEyePosition().subtract(player.getEyePosition()).normalize();
        double requiredDot = distance < 26.0D ? 0.94D : 0.975D;
        return player.getViewVector(1.0F).normalize().dot(towardMe) >= requiredDot;
    }

    private void relocateNear(ServerPlayer player) {
        Vec3 destination = findStalkingPosition(player, false);
        if (destination == null) return;
        if (!player.hasLineOfSight(this) || distanceTo(player) > 54.0D) {
            teleportTo(destination.x, destination.y, destination.z);
            getNavigation().stop();
        } else getNavigation().moveTo(destination.x, destination.y, destination.z, 0.72D);
    }

    private Vec3 findStalkingPosition(ServerPlayer player, boolean acceptFirst) {
        double preferred = LabyrinthConfig.INSTANCE.minotaurStalkDistance;
        Vec3 view = player.getViewVector(1.0F);
        double baseAngle = Math.atan2(view.z, view.x);
        Vec3 originalPosition = position();
        Vec3 fallback = null;
        for (int attempt = 0; attempt < 12; attempt++) {
            double spread = attempt == 0 ? 0.0D : (random.nextDouble() - 0.5D) * 1.7D;
            double distance = preferred - 4.0D + random.nextDouble() * 8.0D;
            double angle = baseAngle + spread;
            Vec3 corridor = WorldGenerator.nearestMazeCorridor(
                    player.getX() + Math.cos(angle) * distance,
                    player.getZ() + Math.sin(angle) * distance);
            BlockPos feet = BlockPos.containing(corridor);
            level().getChunkAt(feet);
            setPos(corridor.x, corridor.y, corridor.z);
            boolean floor = level().getBlockState(feet.below()).isFaceSturdy(level(), feet.below(), Direction.UP);
            if (!floor || !level().noCollision(this)) continue;
            fallback = corridor;
            if (acceptFirst || player.hasLineOfSight(this)) {
                setPos(originalPosition.x, originalPosition.y, originalPosition.z);
                return corridor;
            }
        }
        setPos(originalPosition.x, originalPosition.y, originalPosition.z);
        return fallback;
    }

    private void updateChaseSpeed() {
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(Math.min(0.54D, 0.39D + rage() * 0.018D));
    }

    private void playHeavySteps() {
        if (getDeltaMovement().horizontalDistanceSqr() > 0.012D && (tickCount % 9) == 0)
            playSound(SoundEvents.RAVAGER_STEP, behaviorPhase() == BehaviorPhase.CHASING ? 1.8F : 1.05F,
                    behaviorPhase() == BehaviorPhase.CHASING ? 0.68F : 0.48F);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        float before = getHealth();
        boolean hurt = super.hurtServer(level, source, amount);
        float applied = Math.max(0.0F, before - getHealth());
        if (!hurt || applied <= 0.0F || behaviorPhase() == BehaviorPhase.RETREATING) return hurt;
        if (behaviorPhase() == BehaviorPhase.HUNTING) beginWarning();
        if (behaviorPhase() == BehaviorPhase.WARNING || behaviorPhase() == BehaviorPhase.CHASING) {
            chaseDamage += applied;
            getEntityData().set(DATA_RAGE, Math.min(8, rage() + 1));
            updateChaseSpeed();
            playSound(SoundEvents.RAVAGER_HURT, 1.8F, Math.max(0.42F, 0.72F - rage() * 0.025F));
            if (chaseDamage >= damageThreshold) beginRetreat(true);
        }
        return hurt;
    }

    @Override
    public boolean shouldBeSaved() {
        return behaviorPhase() == BehaviorPhase.BOSS && super.shouldBeSaved();
    }

    public AnimationState animationState() {
        if (swinging) return AnimationState.ATTACK;
        if (behaviorPhase() == BehaviorPhase.WARNING) return AnimationState.WARNING;
        if (behaviorPhase() == BehaviorPhase.CHASING) return AnimationState.CHASE;
        return getDeltaMovement().horizontalDistanceSqr() > 1.0E-4D ? AnimationState.WALK : AnimationState.IDLE;
    }

    private static final class HeavyMoveControl extends MoveControl {
        private final MinotaurEntity minotaur;
        private HeavyMoveControl(MinotaurEntity minotaur) { super(minotaur); this.minotaur = minotaur; }

        @Override
        public void tick() {
            float previousYaw = minotaur.getYRot();
            super.tick();
            if (minotaur.behaviorPhase() != BehaviorPhase.CHASING) return;
            float limited = Mth.approachDegrees(previousYaw, minotaur.getYRot(), 3.2F + minotaur.rage() * 0.25F);
            minotaur.setYRot(limited);
            minotaur.setYBodyRot(limited);
        }
    }
}
