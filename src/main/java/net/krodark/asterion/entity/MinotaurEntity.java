package net.krodark.asterion.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.WorldGenerator;
import net.krodark.asterion.event.DeadSunEventSystem;
import net.krodark.asterion.network.MazeZapPayload;
import net.krodark.asterion.network.BossTelegraphPayload;
import net.krodark.asterion.network.DazePayload;
import net.krodark.asterion.network.ragdoll.RagdollExplosionPayload;
import net.krodark.asterion.network.ragdoll.RagdollImpulsePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerBossEvent;
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
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;

/** Purpose-built maze predator controller: Eclipse hunting, committed chase, and center boss combat. */
public final class MinotaurEntity extends Monster implements GeoEntity {
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("roar");
    private static final RawAnimation WALK_ANIMATION = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN_ANIMATION = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation WARNING_ANIMATION = RawAnimation.begin().thenLoop("charge");
    private static final RawAnimation ATTACK_ANIMATION = RawAnimation.begin().thenLoop("swing_axe_horizontal");
    private static final RawAnimation VERTICAL_ATTACK_ANIMATION = RawAnimation.begin().thenLoop("swing_axe_vertical");
    private static final RawAnimation SWORD_ANIMATION = RawAnimation.begin().thenLoop("swing_swords_combo");
    private static final RawAnimation SPIN_ANIMATION = RawAnimation.begin().thenLoop("swing_swords_spinning_combo");
    private static final RawAnimation LEAP_ANIMATION = RawAnimation.begin().thenLoop("leap");
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(
            MinotaurEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RAGE = SynchedEntityData.defineId(
            MinotaurEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BOSS_ATTACK = SynchedEntityData.defineId(
            MinotaurEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BOSS_STAGE = SynchedEntityData.defineId(
            MinotaurEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BOSS_ATTACK_TICKS = SynchedEntityData.defineId(
            MinotaurEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_GRAB_TARGET_ID = SynchedEntityData.defineId(
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
    private int stalkModeTicks;
    private int awarenessRepathTicks;
    private int attackCooldown;
    private int inaccessibleTicks;
    private int repelDamage;
    private int repelThreshold;
    private int bossAttackTicks;
    private int bossAttackCooldown;
    private int pillarOpportunityTicks;
    private BossAttack bossAttack = BossAttack.NONE;
    private BossAttack lastBossAttack = BossAttack.NONE;
    private final ServerBossEvent healthBossBar = new ServerBossEvent(UUID.randomUUID(),
            Component.literal("THE MINOTAUR"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private final ServerBossEvent rageBossBar = new ServerBossEvent(UUID.randomUUID(),
            Component.literal("RAGE"), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_12);
    private Vec3 bossChargeDirection = Vec3.ZERO;
    private Vec3 bossLeapTarget = Vec3.ZERO;
    private Vec3 collapseAnchor = Vec3.ZERO;
    private BossStage bossStage = BossStage.PILLARS;
    private int collapseTicks;
    private int riposteTicks;
    private UUID grabbedPlayer;
    private boolean bossWasAirborne;
    private StalkMode stalkMode = StalkMode.SHADOWING;
    private Vec3 lastKnownPlayerPosition;
    private Vec3 stalkingDestination;
    private Vec3 stalkingAnchor;
    private Vec3 previousPlayerSample;
    private final ArrayDeque<Vec3> stalkingRoute = new ArrayDeque<>();
    private int paranoiaCooldown;
    private double previousTargetDistance = Double.MAX_VALUE;
    private Vec3 previousPosition = Vec3.ZERO;
    private boolean wasObserved;

    public enum BehaviorPhase { DORMANT, ROAMING, HUNTING, WARNING, CHASING, RETREATING, BOSS }
    /** Deliberate sub-phases keep the hunt readable: trail, watch, then flank for an ambush. */
    private enum StalkMode { PATROLLING, SHADOWING, OBSERVING, FLANKING, INTERCEPTING, VANISHING }
    private enum BossAttack { NONE, CLEAVE, CHARGE, SLAM, LEAP, SWORD_COMBO, SPIN_COMBO, GRAB,
        RED_LIGHTNING_CHARGE, PAWING, STAMPEDE, BACK_KICK, ARENA_SWEEP, RUBBLE_THROW, WALL_SHOVE }
    private enum BossStage { PILLARS, COLLAPSE, EXTREME, DEFEATED }
    public enum AnimationState { IDLE, WALK, WARNING, CHASE, ATTACK, VERTICAL_ATTACK, SWORD, SPIN, LEAP }

    public MinotaurEntity(EntityType<? extends MinotaurEntity> type, Level level) {
        super(type, level);
        xpReward = 35;
        moveControl = new HeavyMoveControl(this);
        // Large entities exhaust vanilla's conservative path budget quickly. The maze has long,
        // orthogonal routes, so allow a deep search and long paths instead of corner-cutting.
        getNavigation().setMaxVisitedNodesMultiplier(AsterionConfig.INSTANCE.minotaurPathfindingMultiplier);
        getNavigation().setRequiredPathLength(256.0F);
        setPathfindingMalus(PathType.WATER, 0.0F);
        setPathfindingMalus(PathType.WATER_BORDER, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 720.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.FOLLOW_RANGE, 96.0D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.8D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.82D)
                .add(Attributes.ARMOR, 14.0D)
                .add(Attributes.STEP_HEIGHT, 3.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, BehaviorPhase.DORMANT.ordinal());
        builder.define(DATA_RAGE, 0);
        builder.define(DATA_BOSS_ATTACK, BossAttack.NONE.ordinal());
        builder.define(DATA_BOSS_STAGE, BossStage.PILLARS.ordinal());
        builder.define(DATA_BOSS_ATTACK_TICKS, 0);
        builder.define(DATA_GRAB_TARGET_ID, -1);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    public static MinotaurEntity spawnHunter(ServerLevel level, ServerPlayer player) {
        MinotaurEntity existing = existingMinotaur(level);
        if (existing != null) {
            if (existing.behaviorPhase() != BehaviorPhase.BOSS) existing.beginHunting(player);
            return existing;
        }
        MinotaurEntity minotaur = Asterion.MINOTAUR.create(level, EntitySpawnReason.EVENT);
        if (minotaur == null) return null;
        // Never pop into view or into the center arena. The silhouette begins in a real corridor
        // on the far side of maze geometry and travels from there under its own navigation.
        Vec3 spawn = minotaur.findHiddenSpawnPosition(player);
        if (WorldGenerator.isApproachingCenter(player.position()))
            spawn = minotaur.findHiddenCenterApproachSpawn(player);
        if (spawn == null) return null;
        minotaur.setPos(spawn.x, spawn.y, spawn.z);
        minotaur.setYRot(player.getYRot() + 180.0F);
        minotaur.setPersistenceRequired();
        minotaur.beginHunting(player);
        return level.addFreshEntity(minotaur) ? minotaur : null;
    }

    public static MinotaurEntity spawnRoamer(ServerLevel level, ServerPlayer player) {
        MinotaurEntity existing = existingMinotaur(level);
        if (existing != null) return existing;
        MinotaurEntity minotaur = Asterion.MINOTAUR.create(level, EntitySpawnReason.EVENT);
        if (minotaur == null) return null;
        // Roamers enter through a real, visible corridor at stalking distance. This keeps them
        // present in the maze rather than technically spawned but hidden several walls away.
        Vec3 spawn = minotaur.findStalkingPosition(player, false, 0);
        if (spawn == null) spawn = minotaur.findHiddenSpawnPosition(player);
        if (spawn == null) return null;
        minotaur.setPos(spawn.x, spawn.y, spawn.z);
        minotaur.setPersistenceRequired();
        minotaur.beginRoaming(player);
        return level.addFreshEntity(minotaur) ? minotaur : null;
    }

    private static MinotaurEntity existingMinotaur(ServerLevel level) {
        for (Entity entity : level.getAllEntities())
            if (entity instanceof MinotaurEntity minotaur && minotaur.isAlive() && !minotaur.isRemoved())
                return minotaur;
        return null;
    }

    public static MinotaurEntity activateCenterBoss(ServerLevel level, ServerPlayer player,
                                                      MinotaurEntity existing) {
        MinotaurEntity minotaur = existing == null
                ? Asterion.MINOTAUR.create(level, EntitySpawnReason.EVENT) : existing;
        if (minotaur == null) return null;
        Vec3 center = WorldGenerator.bossArenaCenter();
        minotaur.setPos(center.x, center.y, center.z);
        minotaur.setPersistenceRequired();
        minotaur.eclipseTarget = player.getUUID();
        minotaur.beginBossIntercept(player);
        if (existing == null && !level.addFreshEntity(minotaur)) return null;
        return minotaur;
    }

    public void beginRoaming(ServerPlayer player) {
        eclipseTarget = player.getUUID();
        setBehaviorPhase(BehaviorPhase.ROAMING);
        setTarget(null);
        setAggressive(false);
        lastKnownPlayerPosition = player.position();
        previousTargetDistance = distanceTo(player);
        enterStalkMode(StalkMode.SHADOWING);
    }

    public boolean isRoaming() { return behaviorPhase() == BehaviorPhase.ROAMING; }

    public void beginHunting(ServerPlayer player) {
        eclipseTarget = player.getUUID();
        setBehaviorPhase(BehaviorPhase.HUNTING);
        setTarget(null);
        setAggressive(false);
        gazeTriggerTicks = random.nextIntBetweenInclusive(
                AsterionConfig.INSTANCE.minotaurGazeMinTicks, AsterionConfig.INSTANCE.minotaurGazeMaxTicks);
        previousPosition = position();
        previousTargetDistance = distanceTo(player);
        lastKnownPlayerPosition = player.position();
        stalkingDestination = null;
        stalkingAnchor = player.position();
        enterStalkMode(StalkMode.SHADOWING);
        repelDamage = 0;
        repelThreshold = random.nextIntBetweenInclusive(
                AsterionConfig.INSTANCE.minotaurDamageMin, AsterionConfig.INSTANCE.minotaurDamageMax);
    }

    public boolean isAssignedTo(Player player) {
        return eclipseTarget != null && eclipseTarget.equals(player.getUUID()) && !isRemoved();
    }

    public boolean isChasing() {
        return behaviorPhase() == BehaviorPhase.WARNING || behaviorPhase() == BehaviorPhase.CHASING
                || behaviorPhase() == BehaviorPhase.BOSS;
    }

    public BehaviorPhase behaviorPhase() {
        int ordinal = Mth.clamp(getEntityData().get(DATA_PHASE), 0, BehaviorPhase.values().length - 1);
        return BehaviorPhase.values()[ordinal];
    }

    public int rage() {
        return getEntityData().get(DATA_RAGE);
    }

    public boolean isExtremeBoss() {
        return behaviorPhase() == BehaviorPhase.BOSS && bossStage == BossStage.EXTREME;
    }

    public float bossDamageFraction() {
        if (behaviorPhase() != BehaviorPhase.BOSS) return 0.0F;
        if (bossStage == BossStage.DEFEATED) return 1.0F;
        return Mth.clamp(1.0F - getHealth() / getMaxHealth(), 0.0F, 1.0F);
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
        if (paranoiaCooldown > 0) paranoiaCooldown--;
        if (attackCooldown > 0) attackCooldown--;
        Player foundPlayer = eclipseTarget == null ? null : level.getPlayerByUUID(eclipseTarget);
        ServerPlayer player = foundPlayer instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        if (player == null || !player.isAlive() || player.isSpectator()
                || !player.level().dimension().equals(Asterion.ASTERION_LEVEL)) {
            beginRetreat(false);
            tickRetreat();
            return;
        }

        if (behaviorPhase() != BehaviorPhase.BOSS && behaviorPhase() != BehaviorPhase.RETREATING
                && WorldGenerator.isApproachingCenter(player.position())) {
            beginBossIntercept(player);
        }

        switch (behaviorPhase()) {
            case ROAMING -> tickRoaming(level, player);
            case HUNTING -> tickHunting(level, player);
            case WARNING -> tickWarning(level, player);
            case CHASING -> tickChase(level, player);
            case RETREATING -> tickRetreat();
            case DORMANT -> { if (!DeadSunEventSystem.isEclipseActive(level)) discard(); }
            case BOSS -> tickBoss(level, player);
        }
    }

    private void tickRoaming(ServerLevel level, ServerPlayer player) {
        setTarget(null);
        setAggressive(false);
        double distance = distanceTo(player);
        boolean discovered = isPlayerLookingAtMe(player, distance);
        tickStalking(level, player, discovered, distance);
        if (discovered) {
            gazeTicks++;
            getLookControl().setLookAt(player, 6.0F, 6.0F);
            if (gazeTicks >= 12) beginWarning();
        } else gazeTicks = 0;
        playHeavySteps();
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
        lastKnownPlayerPosition = player.position();
        if (stalkModeTicks > 0) stalkModeTicks--;

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
        if (distance <= AsterionConfig.INSTANCE.minotaurApproachDistance
                || (approachTicks >= 12 && distance < AsterionConfig.INSTANCE.minotaurApproachDistance + 7.0D)) {
            beginWarning();
            return;
        }
        previousTargetDistance = distance;

        tickStalking(level, player, observed, distance);
        playHeavySteps();
    }

    private void tickStalking(ServerLevel level, ServerPlayer player, boolean observed, double distance) {
        if (observed && stalkMode != StalkMode.OBSERVING && stalkMode != StalkMode.VANISHING)
            enterStalkMode(StalkMode.OBSERVING);
        if (stalkModeTicks <= 0) {
            enterStalkMode(nextStalkMode());
        }

        // A sighting does not always become a charge. Sometimes it hears the player notice it,
        // snorts, and deliberately disappears down a different branch of the maze.
        if (observed && stalkMode == StalkMode.OBSERVING && gazeTicks > 5 && random.nextInt(90) == 0) {
            playSound(SoundEvents.RAVAGER_STUNNED, 1.45F, 0.48F);
            enterStalkMode(StalkMode.VANISHING);
        }

        if (stalkMode == StalkMode.OBSERVING && distance >= 28.0D && distance <= 50.0D) {
            getNavigation().stop();
            getLookControl().setLookAt(player, 5.0F, 5.0F);
            return;
        }

        if (!observed && paranoiaCooldown <= 0 && distance > 24.0D && distance < 70.0D
                && random.nextInt(260) == 0) {
            // The sound is spatial and comes from the real roaming Minotaur, making the player
            // question which nearby corridor it is using without spawning fake entities.
            playSound(random.nextBoolean() ? Asterion.MINOTAUR_ROAR : SoundEvents.RAVAGER_STEP,
                    0.75F, 0.55F + random.nextFloat() * 0.12F);
            paranoiaCooldown = random.nextIntBetweenInclusive(180, 360);
        }

        awarenessRepathTicks--;
        double destinationReach = Math.max(3.0D, getBbWidth() * 0.75D);
        boolean playerMovedToNewArea = stalkingAnchor == null
                || stalkingAnchor.distanceToSqr(player.position())
                > AsterionConfig.INSTANCE.cellSize * AsterionConfig.INSTANCE.cellSize * 2.25D;
        if (stalkingDestination == null
                || position().distanceToSqr(stalkingDestination) < destinationReach * destinationReach
                || playerMovedToNewArea) {
            stalkingDestination = chooseStalkingDestination(player);
            stalkingAnchor = player.position();
            awarenessRepathTicks = 0;
            rebuildStalkingRoute(level);
        }
        if (stalkingDestination == null) stalkingDestination = lastKnownPlayerPosition;
        followStalkingRoute(level);
    }

    private StalkMode nextStalkMode() {
        return switch (stalkMode) {
            case OBSERVING -> random.nextBoolean() ? StalkMode.VANISHING : StalkMode.INTERCEPTING;
            case VANISHING -> StalkMode.PATROLLING;
            case PATROLLING -> random.nextBoolean() ? StalkMode.SHADOWING : StalkMode.INTERCEPTING;
            case SHADOWING -> random.nextBoolean() ? StalkMode.FLANKING : StalkMode.OBSERVING;
            case FLANKING, INTERCEPTING -> StalkMode.OBSERVING;
        };
    }

    private Vec3 chooseStalkingDestination(ServerPlayer player) {
        int angleMode = switch (stalkMode) {
            case OBSERVING -> 0;
            case FLANKING, INTERCEPTING -> 1;
            default -> 2;
        };
        if (stalkMode == StalkMode.INTERCEPTING) {
            Vec3 motion = player.position().subtract(previousPlayerSample == null
                    ? player.position() : previousPlayerSample);
            previousPlayerSample = player.position();
            if (motion.horizontalDistanceSqr() > 0.01D) {
                Vec3 lead = motion.normalize().scale(AsterionConfig.INSTANCE.cellSize * 3.0D);
                return WorldGenerator.nearestMazeCorridor(player.getX() + lead.x, player.getZ() + lead.z);
            }
        }
        return findStalkingPosition(player, stalkMode == StalkMode.PATROLLING, angleMode);
    }

    private void rebuildStalkingRoute(ServerLevel level) {
        stalkingRoute.clear();
        if (stalkingDestination == null) return;
        List<Vec3> route = WorldGenerator.mazeRoute(level, position(), stalkingDestination,
                getBbWidth(), getBbHeight(), 2048);
        stalkingRoute.addAll(route);
    }

    private void followStalkingRoute(ServerLevel level) {
        double reach = Math.max(2.7D, getBbWidth() * 0.58D);
        while (!stalkingRoute.isEmpty()
                && position().distanceToSqr(stalkingRoute.peekFirst()) <= reach * reach)
            stalkingRoute.removeFirst();
        if (stalkingRoute.isEmpty()) {
            if (position().distanceToSqr(stalkingDestination) > reach * reach && awarenessRepathTicks <= 0)
                rebuildStalkingRoute(level);
            if (stalkingRoute.isEmpty()) return;
        }
        if (awarenessRepathTicks <= 0 || getNavigation().isDone()) {
            Vec3 waypoint = stalkingRoute.peekFirst();
            double speed = switch (stalkMode) {
                case VANISHING -> 0.88D;
                case FLANKING, INTERCEPTING -> 0.74D;
                case PATROLLING -> 0.62D;
                default -> 0.56D;
            };
            if (!getNavigation().moveTo(waypoint.x, waypoint.y, waypoint.z, speed)) {
                stalkingRoute.clear();
                awarenessRepathTicks = 2;
            } else awarenessRepathTicks = 12;
        }
    }

    private void enterStalkMode(StalkMode mode) {
        stalkMode = mode;
        stalkModeTicks = switch (mode) {
            case PATROLLING -> random.nextIntBetweenInclusive(180, 320);
            case SHADOWING -> random.nextIntBetweenInclusive(100, 180);
            case OBSERVING -> random.nextIntBetweenInclusive(55, 110);
            case FLANKING -> random.nextIntBetweenInclusive(80, 150);
            case INTERCEPTING -> random.nextIntBetweenInclusive(90, 160);
            case VANISHING -> random.nextIntBetweenInclusive(70, 120);
        };
        awarenessRepathTicks = 0;
        stalkingDestination = null;
        stalkingAnchor = null;
        stalkingRoute.clear();
    }

    private void beginWarning() {
        if (behaviorPhase() == BehaviorPhase.WARNING || behaviorPhase() == BehaviorPhase.CHASING) return;
        setBehaviorPhase(BehaviorPhase.WARNING);
        warningTicks = random.nextIntBetweenInclusive(
                AsterionConfig.INSTANCE.minotaurWindupMinTicks, AsterionConfig.INSTANCE.minotaurWindupMaxTicks);
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
        playSound(Asterion.MINOTAUR_ROAR, 3.2F, 1.0F);
    }

    private void tickChase(ServerLevel level, ServerPlayer player) {
        setTarget(player);
        setAggressive(true);
        chaseTicks++;
        double distance = distanceTo(player);
        int chaseRepathInterval = Math.max(8, AsterionConfig.INSTANCE.minotaurRepathTicks);
        boolean routeReady = true;
        if (getNavigation().isDone() || phaseTicks % chaseRepathInterval == 0)
            routeReady = refreshChasePath(player);
        if (!routeReady && distance < 18.0D && !hasLineOfSight(player) && hasClosedIronBarrierNear(player)) {
            inaccessibleTicks++;
            if (inaccessibleTicks == 30) playSound(Asterion.MINOTAUR_ROAR, 3.4F, 0.88F);
            if (inaccessibleTicks >= 120) {
                beginRetreat(true);
                return;
            }
        } else inaccessibleTicks = Math.max(0, inaccessibleTicks - 2);
        double attackReach = getBbWidth() * 0.65D + player.getBbWidth() * 0.5D + 1.2D;
        if (attackCooldown <= 0 && distance <= attackReach && canSeeWithEyes(player)) {
            swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            doHurtTarget(level, player);
            attackCooldown = Math.max(9, 18 - rage());
        }

        double moved = position().distanceToSqr(previousPosition);
        previousPosition = position();
        if (distance > 4.0D && moved < 0.003D) stuckTicks++; else stuckTicks = Math.max(0, stuckTicks - 2);

        if ((stuckTicks > 8 || player.getY() > getY() + 5.0D) && (phaseTicks % 5) == 0) {
            Vec3 toward = player.position().subtract(position()).normalize();
            AABB breaker = getBoundingBox().expandTowards(toward.scale(2.1D)).inflate(0.8D, 1.2D, 0.8D);
            int broken = WorldGenerator.breakPlayerBlocksAround(level, breaker);
            if (broken == 0 && stuckTicks > 10)
                broken = WorldGenerator.breakMazeWallAround(level, breaker, this);
            if (broken > 0) {
                playSound(SoundEvents.RAVAGER_ATTACK, 1.7F, 0.62F);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, breaker.getCenter().x,
                        getY() + getBbHeight() * 0.45D, breaker.getCenter().z,
                        Math.min(28, broken * 2), 0.8D, 1.0D, 0.8D, 0.025D);
                stuckTicks = 0;
            }
        }
        if ((stuckTicks > AsterionConfig.INSTANCE.minotaurStuckRecoveryTicks
                || player.getY() > getY() + 7.0D) && onGround() && distance < 24.0D) {
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

        if (chaseTicks >= AsterionConfig.INSTANCE.minotaurEscapeTicks
                && distance >= AsterionConfig.INSTANCE.minotaurEscapeDistance) {
            escapeDistanceTicks++;
            if (escapeDistanceTicks >= 200) beginRetreat(true);
        } else escapeDistanceTicks = Math.max(0, escapeDistanceTicks - 2);
        playHeavySteps();
    }

    private boolean refreshChasePath(ServerPlayer player) {
        Vec3 velocityLead = player.getDeltaMovement().multiply(8.0D, 0.0D, 8.0D);
        Vec3 predicted = WorldGenerator.nearestMazeCorridor(
                player.getX() + velocityLead.x, player.getZ() + velocityLead.z);
        Vec3 waypoint = WorldGenerator.nextMazeWaypoint((ServerLevel) level(), position(), predicted,
                getBbWidth(), getBbHeight(), 1536);
        boolean routed = waypoint != null
                && getNavigation().moveTo(waypoint.x, waypoint.y, waypoint.z, 1.0D);
        if (!routed && hasLineOfSight(player)) routed = getNavigation().moveTo(player, 1.0D);
        // Do not teleport through walls when a route fails; the recovery controller breaks only
        // player construction, leaps where appropriate, or respects a sealed Sanctuary gate.
        return routed;
    }

    private boolean hasClosedIronBarrierNear(ServerPlayer player) {
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-3, -1, -3), center.offset(3, 4, 3))) {
            var block = level().getBlockState(pos).getBlock();
            if (block == Blocks.IRON_DOOR || block == Blocks.IRON_TRAPDOOR || block == Blocks.IRON_BARS)
                return true;
        }
        return false;
    }

    private void beginBossIntercept(ServerPlayer player) {
        setBehaviorPhase(BehaviorPhase.BOSS);
        setTarget(player);
        setAggressive(false);
        getNavigation().stop();
        setBossAttack(BossAttack.NONE);
        bossAttackCooldown = 55;
        setBossStage(BossStage.PILLARS);
        collapseTicks = 0;
        riposteTicks = 0;
        pillarOpportunityTicks = 0;
        grabbedPlayer = null;
        updateChaseSpeed();
        playSound(SoundEvents.RAVAGER_AMBIENT, 2.4F, 0.38F);
    }

    private void tickBoss(ServerLevel level, ServerPlayer player) {
        setTarget(player);
        syncBossBars(level);
        if (bossStage == BossStage.EXTREME && phaseTicks % 160 == 0) increaseRage(1);
        if (riposteTicks > 0) riposteTicks--;
        boolean minotaurInside = WorldGenerator.isInsideBossArena(position());
        boolean playerInside = WorldGenerator.isInsideBossArena(player.position());

        if (!minotaurInside) {
            Vec3 approach = WorldGenerator.bossArenaApproach(position());
            if (position().distanceToSqr(approach) > 20.0D) {
                if (getNavigation().isDone() || phaseTicks % 12 == 0)
                    moveByMazeRoute(level, approach, 0.76D, 2048);
            } else {
                // The pit is intentionally a one-way threshold. Walk over its lip visibly instead
                // of spawning or teleporting into the boss room.
                getNavigation().stop();
                Vec3 inward = WorldGenerator.bossArenaCenter().subtract(position());
                Vec3 horizontal = new Vec3(inward.x, 0.0D, inward.z).normalize();
                setDeltaMovement(horizontal.scale(0.34D).add(0.0D, getDeltaMovement().y, 0.0D));
            }
            playHeavySteps();
            return;
        }

        if (!playerInside) {
            // Arrive before the player and hold the room, facing the entrance in silence.
            setAggressive(false);
            setBossAttack(BossAttack.NONE);
            Vec3 center = WorldGenerator.bossArenaCenter();
            if (position().distanceToSqr(center) > 9.0D)
                getNavigation().moveTo(center.x, center.y, center.z, 0.54D);
            else {
                getNavigation().stop();
                getLookControl().setLookAt(player, 3.0F, 3.0F);
            }
            return;
        }

        if (!WorldGenerator.isBossArenaReady()) {
            setAggressive(false);
            getNavigation().stop();
            getLookControl().setLookAt(player, 3.0F, 3.0F);
            return;
        }
        if (bossStage == BossStage.PILLARS && WorldGenerator.bossPillarsRemaining() <= 0)
            beginCollapse(level);
        if (bossStage == BossStage.COLLAPSE) {
            tickCollapse(level, player);
            return;
        }
        if (bossStage == BossStage.DEFEATED) {
            tickDefeated(level);
            return;
        }

        setAggressive(true);
        if (bossStage == BossStage.EXTREME && (phaseTicks % 32) == 0) {
            Vec3 source = new Vec3(AsterionConfig.INSTANCE.deadSunX,
                    AsterionConfig.INSTANCE.deadSunHeight, AsterionConfig.INSTANCE.deadSunZ);
            MazeZapPayload lightning = new MazeZapPayload(getId(), source, Vec3.ZERO, 10);
            for (ServerPlayer viewer : level.players())
                if (ServerPlayNetworking.canSend(viewer, MazeZapPayload.TYPE))
                    ServerPlayNetworking.send(viewer, lightning);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + getBbHeight() * 0.5D,
                    getZ(), 14, 0.8D, 1.5D, 0.8D, 0.11D);
        }
        if (bossAttack != BossAttack.NONE) {
            tickBossAttack(level, player);
            return;
        }
        if (bossAttackCooldown > 0) bossAttackCooldown--;
        double distance = distanceTo(player);
        getLookControl().setLookAt(player, 8.0F, 8.0F);
        Vec3 phaseOnePillarTarget = null;
        if (bossStage == BossStage.PILLARS) {
            phaseOnePillarTarget = WorldGenerator.bossPillarChargeTarget(position(), player.position());
            pillarOpportunityTicks = phaseOnePillarTarget == null
                    ? Math.max(0, pillarOpportunityTicks - 2)
                    : Math.min(40, pillarOpportunityTicks + 1);
        }
        if (bossAttackCooldown <= 0) {
            BossAttack next;
            if (bossStage == BossStage.PILLARS) {
                // Pillars are environmental opportunities, never the objective. He must first
                // pressure the player into a genuine charge lane before committing to one.
                if (phaseOnePillarTarget != null && pillarOpportunityTicks >= 18) {
                    beginBossAttack(player, BossAttack.CHARGE);
                    Vec3 towardPillar = phaseOnePillarTarget.subtract(position());
                    bossChargeDirection = new Vec3(towardPillar.x, 0.0D, towardPillar.z).normalize();
                    pillarOpportunityTicks = 0;
                    return;
                }
                if (distance > 5.2D) {
                    Vec3 waypoint = WorldGenerator.bossArenaTacticalWaypoint(position(), player.position());
                    getNavigation().moveTo(waypoint.x, waypoint.y, waypoint.z, 0.72D);
                }
                else beginBossAttack(player,
                        lastBossAttack == BossAttack.SLAM ? BossAttack.CLEAVE : BossAttack.SLAM);
                bossAttackCooldown = 10;
                return;
            } else if (bossStage == BossStage.EXTREME) {
                next = chooseExtremeAttack(player, distance);
            } else next = distance > 8.0D || random.nextInt(3) != 0
                    ? BossAttack.CHARGE : random.nextBoolean() ? BossAttack.CLEAVE : BossAttack.SLAM;
            if (next == lastBossAttack) next = bossStage == BossStage.EXTREME
                    ? (distance > 8.0D ? BossAttack.LEAP : BossAttack.SWORD_COMBO)
                    : (distance > 7.0D ? BossAttack.CLEAVE : BossAttack.SLAM);
            beginBossAttack(player, next);
        } else if (distance > 4.5D && (phaseTicks % 8 == 0 || getNavigation().isDone())) {
            Vec3 waypoint = WorldGenerator.bossArenaTacticalWaypoint(position(), player.position());
            getNavigation().moveTo(waypoint.x, waypoint.y, waypoint.z, 0.82D);
        }
        playHeavySteps();
    }

    private void syncBossBars(ServerLevel level) {
        healthBossBar.setVisible(bossStage != BossStage.DEFEATED);
        rageBossBar.setVisible(bossStage == BossStage.EXTREME);
        healthBossBar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0.0F, 1.0F));
        rageBossBar.setProgress(Mth.clamp(rage() / 12.0F, 0.0F, 1.0F));
        for (ServerPlayer viewer : level.players()) {
            boolean show = WorldGenerator.isInsideBossArena(viewer.position())
                    && viewer.isAlive() && !viewer.isSpectator();
            if (show) {
                if (!healthBossBar.getPlayers().contains(viewer)) healthBossBar.addPlayer(viewer);
                if (!rageBossBar.getPlayers().contains(viewer)) rageBossBar.addPlayer(viewer);
            } else {
                healthBossBar.removePlayer(viewer);
                rageBossBar.removePlayer(viewer);
            }
        }
    }

    private void increaseRage(int amount) {
        getEntityData().set(DATA_RAGE, Math.min(12, rage() + Math.max(0, amount)));
        updateChaseSpeed();
    }

    /** Phase two is selected by combat context instead of a random attack table. */
    private BossAttack chooseExtremeAttack(ServerPlayer player, double distance) {
        double playerSpeed = player.getDeltaMovement().horizontalDistance();
        double arenaRadius = Math.sqrt(player.getX() * player.getX() + player.getZ() * player.getZ());
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYHeadRot());
        Vec3 toPlayer = player.position().subtract(position());
        Vec3 horizontalToPlayer = new Vec3(toPlayer.x, 0.0D, toPlayer.z);
        double facingDot = horizontalToPlayer.lengthSqr() < 0.01D ? 1.0D
                : horizontalToPlayer.normalize().dot(new Vec3(forward.x, 0.0D, forward.z).normalize());
        BossAttack choice;
        if (distance < 4.8D && facingDot < -0.35D) choice = BossAttack.BACK_KICK;
        else if (arenaRadius > 25.0D && distance < 5.5D) choice = BossAttack.WALL_SHOVE;
        else if (phaseTicks % 310 < 42 && distance > 7.0D) choice = BossAttack.PAWING;
        else if (phaseTicks % 240 < 45 && distance < 24.0D) choice = BossAttack.ARENA_SWEEP;
        else if (distance > 11.0D && phaseTicks % 3 == 0) choice = BossAttack.RUBBLE_THROW;
        else if (rage() >= 7 && distance >= 7.0D && distance <= 17.0D
                && Math.floorMod(phaseTicks / 70 + rage(), 3) == 0)
            choice = BossAttack.RED_LIGHTNING_CHARGE;
        else if (distance > 12.0D) choice = BossAttack.LEAP;
        else if (arenaRadius > 20.0D && distance > 6.5D) choice = BossAttack.CHARGE;
        else if (distance < 3.8D) choice = BossAttack.GRAB;
        else if (playerSpeed > 0.22D && distance < 7.5D) choice = BossAttack.SPIN_COMBO;
        else if (distance < 6.5D) choice = BossAttack.SWORD_COMBO;
        else choice = BossAttack.SLAM;
        if (choice != lastBossAttack) return choice;
        return switch (choice) {
            case LEAP, CHARGE, RED_LIGHTNING_CHARGE, STAMPEDE, PAWING -> BossAttack.SLAM;
            case GRAB, SWORD_COMBO, WALL_SHOVE -> BossAttack.ARENA_SWEEP;
            case SPIN_COMBO, SLAM, ARENA_SWEEP -> BossAttack.RUBBLE_THROW;
            case RUBBLE_THROW, BACK_KICK -> BossAttack.SWORD_COMBO;
            default -> BossAttack.SLAM;
        };
    }

    private void beginBossAttack(ServerPlayer player, BossAttack attack) {
        lastBossAttack = attack;
        setBossAttack(attack);
        bossAttackTicks = 0;
        getEntityData().set(DATA_BOSS_ATTACK_TICKS, 0);
        getNavigation().stop();
        if (attack == BossAttack.CHARGE || attack == BossAttack.RED_LIGHTNING_CHARGE
                || attack == BossAttack.STAMPEDE || attack == BossAttack.PAWING) {
            Vec3 delta = player.position().subtract(position());
            Vec3 lead = delta.add(player.getDeltaMovement().multiply(7.0D, 0.0D, 7.0D));
            bossChargeDirection = new Vec3(lead.x, 0.0D, lead.z).normalize();
            if (attack == BossAttack.RED_LIGHTNING_CHARGE && level() instanceof ServerLevel level) {
                Vec3 source = new Vec3(AsterionConfig.INSTANCE.deadSunX,
                        AsterionConfig.INSTANCE.deadSunHeight, AsterionConfig.INSTANCE.deadSunZ);
                MazeZapPayload zap = new MazeZapPayload(getId(), source, Vec3.ZERO, 76);
                for (ServerPlayer viewer : level.players())
                    if (ServerPlayNetworking.canSend(viewer, MazeZapPayload.TYPE))
                        ServerPlayNetworking.send(viewer, zap);
                playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 3.8F, 0.48F);
            } else playSound(SoundEvents.GOAT_PREPARE_RAM, attack == BossAttack.PAWING ? 3.2F : 2.8F,
                    attack == BossAttack.PAWING ? 0.31F : 0.38F);
        } else if (attack == BossAttack.LEAP) {
            bossLeapTarget = WorldGenerator.clampBossArena(player.position()
                    .add(player.getDeltaMovement().multiply(10.0D, 0.0D, 10.0D)));
            bossWasAirborne = false;
            playSound(Asterion.MINOTAUR_ROAR, 2.7F, 1.08F);
        } else if (attack == BossAttack.SLAM) {
            bossWasAirborne = false;
            playSound(SoundEvents.WARDEN_SONIC_CHARGE, 2.3F, 0.55F);
        } else if (attack == BossAttack.GRAB) {
            grabbedPlayer = null;
            getEntityData().set(DATA_GRAB_TARGET_ID, player.getId());
            playSound(SoundEvents.RAVAGER_AMBIENT, 2.0F, 0.55F);
        } else if (attack == BossAttack.ARENA_SWEEP && level() instanceof ServerLevel level) {
            Vec3 delta = player.position().subtract(position());
            bossChargeDirection = new Vec3(delta.x, 0.0D, delta.z).normalize();
            BossTelegraphPayload telegraph = new BossTelegraphPayload(position(), bossChargeDirection,
                    31.0F, 38, BossTelegraphPayload.HALF_ARENA_SWEEP);
            for (ServerPlayer viewer : level.players())
                if (ServerPlayNetworking.canSend(viewer, BossTelegraphPayload.TYPE))
                    ServerPlayNetworking.send(viewer, telegraph);
            playSound(SoundEvents.WARDEN_SONIC_CHARGE, 3.0F, 0.38F);
        } else if (attack == BossAttack.RUBBLE_THROW) {
            bossLeapTarget = WorldGenerator.clampBossArena(player.position()
                    .add(player.getDeltaMovement().multiply(8.0D, 0.0D, 8.0D)));
            playSound(SoundEvents.STONE_BREAK, 3.0F, 0.55F);
        } else playSound(SoundEvents.RAVAGER_ATTACK, 2.2F, 0.52F);
    }

    private void tickBossAttack(ServerLevel level, ServerPlayer player) {
        bossAttackTicks++;
        getEntityData().set(DATA_BOSS_ATTACK_TICKS, bossAttackTicks);
        getLookControl().setLookAt(player, 5.0F, 5.0F);
        switch (bossAttack) {
            case CLEAVE -> {
                if (bossAttackTicks == 14) performCleave(level);
                if (bossAttackTicks >= 30) finishBossAttack(28);
            }
            case SLAM -> {
                if (bossAttackTicks >= 4 && bossAttackTicks < 14 && (bossAttackTicks & 3) == 0) {
                    double radius = (bossAttackTicks - 6) * 0.24D;
                    for (int point = 0; point < 20; point++) {
                        double angle = Mth.TWO_PI * point / 20.0D;
                        level.sendParticles(ParticleTypes.DUST_PLUME,
                                getX() + Math.cos(angle) * radius, getY() + 0.12D,
                                getZ() + Math.sin(angle) * radius, 1, 0, 0.02D, 0, 0.015D);
                    }
                }
                if (bossAttackTicks == 12) {
                    setDeltaMovement(0.0D, 1.28D, 0.0D);
                    hurtMarked = true;
                    bossWasAirborne = true;
                    playSound(SoundEvents.GOAT_LONG_JUMP, 2.5F, 0.48F);
                }
                if (bossWasAirborne && bossAttackTicks > 17 && onGround()) {
                    bossWasAirborne = false;
                    performGroundSlam(level);
                    riposteTicks = 36;
                    finishBossAttack(42);
                }
                if (bossAttackTicks >= 62) {
                    performGroundSlam(level);
                    riposteTicks = 42;
                    finishBossAttack(46);
                }
            }
            case CHARGE -> {
                if (bossAttackTicks < 20) setDeltaMovement(getDeltaMovement().multiply(0.2D, 1.0D, 0.2D));
                else if (bossAttackTicks <= 48) {
                    setDeltaMovement(bossChargeDirection.x * 0.92D, getDeltaMovement().y,
                            bossChargeDirection.z * 0.92D);
                    if ((bossAttackTicks & 1) == 0)
                        level.sendParticles(ParticleTypes.DUST_PLUME, getX(), getY() + 0.15D, getZ(),
                                5, 0.65D, 0.12D, 0.65D, 0.025D);
                    AABB impact = getBoundingBox().expandTowards(bossChargeDirection.scale(1.8D))
                            .inflate(0.35D, 0.25D, 0.35D);
                    if (bossStage == BossStage.PILLARS && WorldGenerator.breakBossPillar(level, impact)) {
                        increaseRage(1);
                        WorldGenerator.scarBossArena(level, position(), 4);
                        setDeltaMovement(bossChargeDirection.scale(-0.18D).add(0, 0.16D, 0));
                        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1.0D, getZ(),
                                8, 1.0D, 1.4D, 1.0D, 0.04D);
                        riposteTicks = 24;
                        finishBossAttack(46);
                        return;
                    }
                    int smashed = WorldGenerator.breakMazeWallAround(level, impact, this);
                    if (smashed > 0) playSound(SoundEvents.RAVAGER_ATTACK, 2.1F, 0.48F);
                    if (attackCooldown <= 0 && getBoundingBox().inflate(0.8D).intersects(player.getBoundingBox())) {
                        player.hurtServer(level, damageSources().mobAttack(this), 18.0F);
                        ragdollPlayer(player, bossChargeDirection.scale(2.8D).add(0, 0.55D, 0), 1.35F);
                        attackCooldown = 18;
                    }
                }
                if (bossAttackTicks >= 58) finishBossAttack(38);
            }
            case RED_LIGHTNING_CHARGE -> tickRedLightningCharge(level, player);
            case PAWING -> tickPawing(level, player);
            case STAMPEDE -> tickStampede(level, player);
            case BACK_KICK -> tickBackKick(level);
            case ARENA_SWEEP -> tickArenaSweep(level);
            case RUBBLE_THROW -> tickRubbleThrow(level, player);
            case WALL_SHOVE -> tickWallShove(level, player);
            case LEAP -> tickLeapAttack(level, player);
            case SWORD_COMBO -> {
                if (bossAttackTicks == 12 || bossAttackTicks == 21 || bossAttackTicks == 30)
                    performSwordArc(level, bossAttackTicks == 30 ? 18.0F : 12.0F,
                            bossAttackTicks == 30 ? 2.0D : 1.35D);
                if (bossAttackTicks >= 40) finishBossAttack(24);
            }
            case SPIN_COMBO -> {
                if (bossAttackTicks == 16 || bossAttackTicks == 24) performSpin(level);
                if (bossAttackTicks >= 38) finishBossAttack(34);
            }
            case GRAB -> tickGrabAttack(level, player);
            case NONE -> { }
        }
    }

    private void tickPawing(ServerLevel level, ServerPlayer player) {
        setDeltaMovement(getDeltaMovement().multiply(0.08D, 1.0D, 0.08D));
        getLookControl().setLookAt(player, 12.0F, 6.0F);
        Vec3 forward = new Vec3(bossChargeDirection.x, 0.0D, bossChargeDirection.z).normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        if (bossAttackTicks >= 8 && bossAttackTicks <= 36 && (bossAttackTicks & 1) == 0) {
            double side = (bossAttackTicks & 2) == 0 ? -0.9D : 0.9D;
            Vec3 hoof = position().add(forward.scale(1.45D)).add(right.scale(side));
            level.sendParticles(ParticleTypes.DUST_PLUME, hoof.x, getY() + 0.12D, hoof.z,
                    9, 0.42D, 0.09D, 0.42D, 0.055D);
            if ((bossAttackTicks % 8) == 0) playSound(SoundEvents.RAVAGER_STEP, 2.2F, 0.42F);
        }
        if (bossAttackTicks == 38) {
            setBossAttack(BossAttack.STAMPEDE);
            bossAttackTicks = 0;
            getEntityData().set(DATA_BOSS_ATTACK_TICKS, 0);
            Vec3 lead = player.position().add(player.getDeltaMovement().multiply(12.0D, 0.0D, 12.0D))
                    .subtract(position());
            bossChargeDirection = new Vec3(lead.x, 0.0D, lead.z).normalize();
            playSound(Asterion.MINOTAUR_ROAR, 4.0F, 0.68F);
        }
    }

    private void tickStampede(ServerLevel level, ServerPlayer player) {
        if (bossAttackTicks <= 72) {
            // A stampede commits hard. It may correct slightly at the beginning, but cannot spin
            // around the player once its mass is moving.
            if (bossAttackTicks < 20) {
                Vec3 desired = player.position().subtract(position());
                desired = new Vec3(desired.x, 0.0D, desired.z).normalize();
                bossChargeDirection = bossChargeDirection.lerp(desired, 0.035D).normalize();
            }
            double speed = 1.12D + rage() * 0.018D;
            setDeltaMovement(bossChargeDirection.x * speed, getDeltaMovement().y,
                    bossChargeDirection.z * speed);
            AABB impact = getBoundingBox().expandTowards(bossChargeDirection.scale(2.5D)).inflate(0.7D);
            int smashed = WorldGenerator.breakMazeWallAround(level, impact, this);
            if (smashed > 0) {
                WorldGenerator.scarBossArena(level, position(), 4);
                level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.8D, getZ(),
                        5, 1.1D, 0.8D, 1.1D, 0.08D);
            }
            if (attackCooldown <= 0 && impact.intersects(player.getBoundingBox())) {
                player.hurtServer(level, damageSources().mobAttack(this), 22.0F);
                ragdollPlayer(player, bossChargeDirection.scale(3.7D).add(0.0D, 0.65D, 0.0D), 1.75F);
                attackCooldown = 30;
            }
            if ((bossAttackTicks & 1) == 0)
                level.sendParticles(ParticleTypes.DUST_PLUME, getX(), getY() + 0.1D, getZ(),
                        10, 1.0D, 0.12D, 1.0D, 0.06D);
        }
        if (bossAttackTicks >= 80) finishBossAttack(62);
    }

    private void tickBackKick(ServerLevel level) {
        setDeltaMovement(getDeltaMovement().multiply(0.1D, 1.0D, 0.1D));
        if (bossAttackTicks == 15) {
            Vec3 forward = Vec3.directionFromRotation(0.0F, yBodyRot).normalize();
            Vec3 rear = forward.scale(-1.0D);
            for (ServerPlayer victim : level.getEntitiesOfClass(ServerPlayer.class,
                    getBoundingBox().inflate(5.2D))) {
                Vec3 delta = victim.position().subtract(position());
                Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z);
                if (horizontal.lengthSqr() < 0.01D || horizontal.normalize().dot(rear) < 0.30D) continue;
                victim.hurtServer(level, damageSources().mobAttack(this), 16.0F);
                ragdollPlayer(victim, rear.scale(2.9D).add(0.0D, 1.0D, 0.0D), 1.45F);
            }
            playSound(SoundEvents.RAVAGER_ATTACK, 2.7F, 0.66F);
        }
        if (bossAttackTicks >= 30) finishBossAttack(38);
    }

    private void tickArenaSweep(ServerLevel level) {
        setDeltaMovement(getDeltaMovement().multiply(0.06D, 1.0D, 0.06D));
        if (bossAttackTicks >= 8 && bossAttackTicks < 38 && (bossAttackTicks & 3) == 0) {
            double progress = bossAttackTicks / 38.0D;
            double base = Math.atan2(bossChargeDirection.z, bossChargeDirection.x) - Math.PI * 0.5D;
            double angle = base + Math.PI * progress;
            for (int point = 3; point <= 30; point += 3)
                level.sendParticles(ParticleTypes.DUST_PLUME,
                        getX() + Math.cos(angle) * point, getY() + 0.14D,
                        getZ() + Math.sin(angle) * point, 2, 0.35D, 0.05D, 0.35D, 0.025D);
        }
        if (bossAttackTicks == 38) {
            for (ServerPlayer victim : level.players()) {
                Vec3 delta = victim.position().subtract(position());
                Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z);
                if (horizontal.length() > 31.0D || horizontal.lengthSqr() < 0.01D
                        || horizontal.normalize().dot(bossChargeDirection) < 0.0D) continue;
                victim.hurtServer(level, damageSources().mobAttack(this), 17.0F);
                Vec3 tangent = new Vec3(-bossChargeDirection.z, 0.0D, bossChargeDirection.x);
                ragdollPlayer(victim, tangent.scale(3.0D).add(0.0D, 0.7D, 0.0D), 1.55F);
            }
            WorldGenerator.scarBossArena(level, position().add(bossChargeDirection.scale(12.0D)), 12);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 4.0F, 0.52F);
        }
        if (bossAttackTicks >= 52) finishBossAttack(58);
    }

    private void tickRubbleThrow(ServerLevel level, ServerPlayer player) {
        setDeltaMovement(getDeltaMovement().multiply(0.1D, 1.0D, 0.1D));
        if (bossAttackTicks == 16) {
            Vec3 target = bossLeapTarget;
            for (int piece = 0; piece < 7; piece++) {
                double angle = Mth.TWO_PI * piece / 7.0D;
                Vec3 origin = position().add(Math.cos(angle) * 1.8D, 1.2D + (piece & 1),
                        Math.sin(angle) * 1.8D);
                FallingBlockEntity rubble = FallingBlockEntity.fall(level, BlockPos.containing(origin),
                        (piece & 1) == 0 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState()
                                : Blocks.TUFF.defaultBlockState());
                rubble.setPos(origin);
                Vec3 flight = target.subtract(origin).scale(0.055D).add(0.0D, 0.62D, 0.0D);
                rubble.setDeltaMovement(flight);
            }
            playSound(SoundEvents.STONE_BREAK, 3.4F, 0.45F);
        }
        if (bossAttackTicks == 38) {
            WorldGenerator.scarBossArena(level, bossLeapTarget, 5);
            for (ServerPlayer victim : level.getEntitiesOfClass(ServerPlayer.class,
                    new AABB(bossLeapTarget, bossLeapTarget).inflate(4.5D))) {
                victim.hurtServer(level, damageSources().mobAttack(this), 13.0F);
                Vec3 away = victim.position().subtract(bossLeapTarget);
                ragdollPlayer(victim, new Vec3(away.x, 0.0D, away.z).normalize()
                        .scale(1.9D).add(0.0D, 0.8D, 0.0D), 1.25F);
            }
            level.sendParticles(ParticleTypes.EXPLOSION, bossLeapTarget.x, bossLeapTarget.y,
                    bossLeapTarget.z, 8, 1.8D, 0.35D, 1.8D, 0.08D);
        }
        if (bossAttackTicks >= 52) finishBossAttack(48);
    }

    private void tickWallShove(ServerLevel level, ServerPlayer player) {
        if (bossAttackTicks < 12) getLookControl().setLookAt(player, 14.0F, 8.0F);
        if (bossAttackTicks == 12 && distanceTo(player) <= 5.8D) {
            Vec3 outward = new Vec3(player.getX(), 0.0D, player.getZ());
            if (outward.lengthSqr() < 0.01D) outward = player.position().subtract(position());
            outward = new Vec3(outward.x, 0.0D, outward.z).normalize();
            player.hurtServer(level, damageSources().mobAttack(this), 12.0F);
            ragdollPlayer(player, outward.scale(3.4D).add(0.0D, 0.35D, 0.0D), 1.65F);
            playSound(SoundEvents.RAVAGER_ATTACK, 3.0F, 0.44F);
        }
        if (bossAttackTicks == 24 && distanceTo(player) < 10.0D) {
            player.hurtServer(level, damageSources().mobAttack(this), 8.0F);
            level.sendParticles(ParticleTypes.DUST_PLUME, player.getX(), player.getY() + 1.0D,
                    player.getZ(), 22, 0.6D, 0.9D, 0.6D, 0.12D);
        }
        if (bossAttackTicks >= 38) finishBossAttack(50);
    }

    private void tickRedLightningCharge(ServerLevel level, ServerPlayer player) {
        if (bossAttackTicks < 32) {
            setDeltaMovement(getDeltaMovement().multiply(0.12D, 1.0D, 0.12D));
            if ((bossAttackTicks & 3) == 0)
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(),
                        getY() + getBbHeight() * 0.52D, getZ(), 18,
                        1.1D, 1.8D, 1.1D, 0.18D);
            return;
        }
        if (bossAttackTicks <= 58) {
            double speed = 1.04D + rage() * 0.012D;
            setDeltaMovement(bossChargeDirection.x * speed, getDeltaMovement().y,
                    bossChargeDirection.z * speed);
            AABB impact = getBoundingBox().expandTowards(bossChargeDirection.scale(2.2D))
                    .inflate(0.55D, 0.35D, 0.55D);
            int smashed = WorldGenerator.breakMazeWallAround(level, impact, this);
            if (smashed > 0) {
                WorldGenerator.scarBossArena(level, position(), 5);
                level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1.0D, getZ(),
                        7, 1.2D, 1.1D, 1.2D, 0.08D);
            }
            if (attackCooldown <= 0 && impact.intersects(player.getBoundingBox())) {
                player.hurtServer(level, damageSources().mobAttack(this), 20.0F);
                ragdollPlayer(player, bossChargeDirection.scale(3.2D).add(0.0D, 0.72D, 0.0D), 1.6F);
                attackCooldown = 24;
            }
        }
        if (bossAttackTicks >= 72) {
            riposteTicks = 38;
            finishBossAttack(58);
        }
    }

    private void performCleave(ServerLevel level) {
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        Vec3 facing = getLookAngle();
        for (ServerPlayer victim : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(6.0D))) {
            Vec3 delta = victim.position().subtract(position());
            Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z);
            if (horizontal.lengthSqr() > 0.01D && horizontal.normalize().dot(new Vec3(facing.x, 0, facing.z).normalize()) > -0.05D) {
                victim.hurtServer(level, damageSources().mobAttack(this), 14.0F);
                Vec3 forward = new Vec3(facing.x, 0, facing.z).normalize();
                Vec3 right = new Vec3(-forward.z, 0, forward.x);
                ragdollPlayer(victim, right.scale(2.55D).add(0, 0.52D, 0), 1.18F);
            }
        }
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.4F, 0.45F);
    }

    private void performGroundSlam(ServerLevel level) {
        WorldGenerator.scarBossArena(level, position(), 7);
        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.2D, getZ(),
                14, 3.5D, 0.25D, 3.5D, 0.02D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.15D, getZ(),
                45, 5.0D, 0.2D, 5.0D, 0.04D);
        for (ServerPlayer victim : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(8.0D))) {
            Vec3 away = victim.position().subtract(position());
            victim.hurtServer(level, damageSources().mobAttack(this), 12.0F);
            Vec3 horizontal = new Vec3(away.x, 0, away.z).normalize();
            ragdollPlayer(victim, horizontal.scale(1.35D).add(0, 1.0D, 0), 1.2F);
        }
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.8F, 0.48F);
    }

    private void beginCollapse(ServerLevel level) {
        setBossStage(BossStage.COLLAPSE);
        collapseTicks = 0;
        setBossAttack(BossAttack.NONE);
        setAggressive(false);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        collapseAnchor = WorldGenerator.clampBossArena(position());
        noPhysics = false;
        playSound(Asterion.MINOTAUR_ROAR, 4.0F, 0.82F);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + getBbHeight() * 0.5D, getZ(),
                60, 2.0D, 2.5D, 2.0D, 0.035D);
    }

    private void tickCollapse(ServerLevel level, ServerPlayer player) {
        collapseTicks++;
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().multiply(0.1D, 1.0D, 0.1D));
        // Rubble may hide him, but it never becomes a teleport: pin the controller to the same
        // arena-space anchor while collision is disabled, then emerge at that exact point.
        if (collapseTicks >= 62 && collapseTicks < 126) {
            setPos(collapseAnchor.x, collapseAnchor.y, collapseAnchor.z);
            setDeltaMovement(Vec3.ZERO);
        }
        getLookControl().setLookAt(player, 2.0F, 2.0F);
        if (collapseTicks >= 18 && collapseTicks <= 52)
            WorldGenerator.collapseBossRoofRing(level, position(), collapseTicks - 18);
        if (collapseTicks == 62) {
            noPhysics = true;
            WorldGenerator.buryBossInRubble(level, position());
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 3.2F, 0.42F);
        }
        // The Dead Sun only reacts once the Minotaur is genuinely surrounded by the rubble pile.
        if (collapseTicks >= 72 && collapseTicks <= 120 && collapseTicks % 6 == 0
                && WorldGenerator.isBossBuried(level, position())) {
            Vec3 source = new Vec3(AsterionConfig.INSTANCE.deadSunX,
                    AsterionConfig.INSTANCE.deadSunHeight, AsterionConfig.INSTANCE.deadSunZ);
            MazeZapPayload zap = new MazeZapPayload(getId(), source, Vec3.ZERO, 12);
            for (ServerPlayer viewer : level.players())
                if (ServerPlayNetworking.canSend(viewer, MazeZapPayload.TYPE))
                    ServerPlayNetworking.send(viewer, zap);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + getBbHeight() * 0.55D,
                    getZ(), 28, 1.4D, 2.2D, 1.4D, 0.16D);
            playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 3.5F, 0.7F + random.nextFloat() * 0.25F);
        }
        if (collapseTicks == 126) {
            WorldGenerator.explodeBossRubble(level, position());
            noPhysics = false;
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1.5D, getZ(),
                    7, 3.5D, 2.0D, 3.5D, 0.1D);
            for (ServerPlayer victim : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(15.0D))) {
                Vec3 away = victim.position().subtract(position());
                Vec3 impulse = new Vec3(away.x, 0, away.z).normalize().scale(2.2D).add(0, 0.85D, 0);
                ragdollPlayer(victim, impulse, 1.45F);
            }
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 4.0F, 0.38F);
        }
        if (collapseTicks >= 154) {
            Vec3 safe = WorldGenerator.clampBossArena(collapseAnchor);
            setPos(safe.x, safe.y, safe.z);
            setDeltaMovement(Vec3.ZERO);
            resetFallDistance();
            setBossStage(BossStage.EXTREME);
            bossAttackCooldown = 35;
            setAggressive(true);
            getEntityData().set(DATA_RAGE, Math.max(rage(), 6));
            updateChaseSpeed();
            // Phase one damage carries forward; only guarantee enough health for phase two to
            // demonstrate its new moves instead of silently replacing the player's progress.
            setHealth(Math.max(getHealth(), 160.0F));
            playSound(Asterion.MINOTAUR_ROAR, 4.5F, 1.12F);
            level.sendParticles(ParticleTypes.FLAME, getX(), getY() + getBbHeight() * 0.45D, getZ(),
                    80, 1.4D, 2.2D, 1.4D, 0.08D);
        }
    }

    private void tickLeapAttack(ServerLevel level, ServerPlayer player) {
        if (bossAttackTicks < 16) {
            setDeltaMovement(getDeltaMovement().multiply(0.18D, 1.0D, 0.18D));
            getLookControl().setLookAt(bossLeapTarget.x, bossLeapTarget.y, bossLeapTarget.z, 12.0F, 8.0F);
            return;
        }
        if (bossAttackTicks == 16) {
            Vec3 delta = bossLeapTarget.subtract(position());
            Vec3 horizontal = new Vec3(delta.x, 0, delta.z);
            double speed = Mth.clamp(horizontal.length() * 0.085D, 0.9D, 1.65D);
            horizontal = horizontal.lengthSqr() < 0.01D ? getLookAngle() : horizontal.normalize();
            setDeltaMovement(horizontal.x * speed, 1.05D, horizontal.z * speed);
            hurtMarked = true;
            bossWasAirborne = true;
            playSound(SoundEvents.GOAT_LONG_JUMP, 2.8F, 0.62F);
            return;
        }
        if (bossWasAirborne && bossAttackTicks > 20 && onGround()) {
            performLeapImpact(level);
            riposteTicks = 55;
            finishBossAttack(52);
            return;
        }
        if (getBoundingBox().inflate(0.65D).intersects(player.getBoundingBox()) && attackCooldown <= 0) {
            player.hurtServer(level, damageSources().mobAttack(this), 22.0F);
            Vec3 direction = player.position().subtract(position()).normalize();
            ragdollPlayer(player, direction.scale(3.0D).add(0, 0.7D, 0), 1.55F);
            attackCooldown = 24;
        }
        if (bossAttackTicks >= 64) {
            riposteTicks = 60;
            finishBossAttack(55);
        }
    }

    private void performLeapImpact(ServerLevel level) {
        WorldGenerator.scarBossArena(level, position(), 9);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.2D, getZ(),
                3, 2.0D, 0.4D, 2.0D, 0.05D);
        for (ServerPlayer victim : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(7.5D))) {
            Vec3 away = victim.position().subtract(position());
            victim.hurtServer(level, damageSources().mobAttack(this), 18.0F);
            ragdollPlayer(victim, new Vec3(away.x, 0, away.z).normalize().scale(1.9D).add(0, 1.15D, 0), 1.4F);
        }
        for (ServerPlayer viewer : level.players())
            if (ServerPlayNetworking.canSend(viewer, RagdollExplosionPayload.TYPE))
                ServerPlayNetworking.send(viewer, new RagdollExplosionPayload(position(), 9.0F));
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 3.2F, 0.42F);
    }

    private void performSwordArc(ServerLevel level, float damage, double force) {
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        Vec3 facing = Vec3.directionFromRotation(getXRot(), getYHeadRot());
        for (ServerPlayer victim : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(7.0D))) {
            Vec3 delta = victim.position().subtract(position());
            Vec3 horizontal = new Vec3(delta.x, 0, delta.z);
            if (horizontal.lengthSqr() < 0.01D || horizontal.normalize()
                    .dot(new Vec3(facing.x, 0, facing.z).normalize()) < 0.10D) continue;
            victim.hurtServer(level, damageSources().mobAttack(this), damage);
            ragdollPlayer(victim, horizontal.normalize().scale(force).add(0, 0.4D, 0), 1.2F);
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + facing.x * 2.0D,
                getY() + getBbHeight() * 0.5D, getZ() + facing.z * 2.0D,
                5, 1.4D, 1.0D, 1.4D, 0.0D);
    }

    private void performSpin(ServerLevel level) {
        WorldGenerator.scarBossArena(level, position(), 5);
        for (ServerPlayer victim : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(6.5D))) {
            Vec3 away = victim.position().subtract(position());
            victim.hurtServer(level, damageSources().mobAttack(this), 13.0F);
            ragdollPlayer(victim, new Vec3(away.x, 0, away.z).normalize().scale(2.1D).add(0, 0.45D, 0), 1.3F);
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + getBbHeight() * 0.48D, getZ(),
                10, 2.8D, 0.7D, 2.8D, 0.0D);
    }

    private void tickGrabAttack(ServerLevel level, ServerPlayer player) {
        if (bossAttackTicks == 11 && distanceTo(player) <= 5.2D && canSeeWithEyes(player))
            grabbedPlayer = player.getUUID();
        Player foundGrabbed = grabbedPlayer == null ? null : level.getPlayerByUUID(grabbedPlayer);
        ServerPlayer grabbed = foundGrabbed instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        if (grabbed != null && bossAttackTicks >= 11 && bossAttackTicks < 29) {
            Vec3 forward = Vec3.directionFromRotation(getXRot(), getYHeadRot()).normalize();
            double breathing = Math.sin(bossAttackTicks * 0.32D) * 0.035D;
            Vec3 hand = position().add(forward.scale(1.95D))
                    .add(0, getBbHeight() * 0.56D + breathing, 0);
            grabbed.teleportTo(hand.x, hand.y, hand.z);
            grabbed.setYRot(getYRot() + 180.0F);
            grabbed.setXRot(Mth.lerp(0.2F, grabbed.getXRot(), -8.0F));
            grabbed.setDeltaMovement(Vec3.ZERO);
            if ((bossAttackTicks & 3) == 0)
                level.sendParticles(ParticleTypes.LARGE_SMOKE, hand.x, hand.y, hand.z,
                        3, 0.35D, 0.5D, 0.35D, 0.02D);
        }
        if (grabbed != null && bossAttackTicks == 18) {
            setDeltaMovement(0.0D, 0.62D, 0.0D);
            hurtMarked = true;
        }
        if (grabbed != null && bossAttackTicks == 29) {
            ragdollPlayer(grabbed, new Vec3(0.0D, 1.65D, 0.0D), 1.45F);
        }
        if (grabbed != null && bossAttackTicks == 41) {
            grabbed.hurtServer(level, damageSources().mobAttack(this), 18.0F);
            ragdollPlayer(grabbed, new Vec3(0.0D, -3.2D, 0.0D), 1.85F);
            WorldGenerator.scarBossArena(level, grabbed.position(), 5);
            level.sendParticles(ParticleTypes.EXPLOSION, grabbed.getX(), bossImpactY(grabbed),
                    grabbed.getZ(), 8, 1.5D, 0.2D, 1.5D, 0.04D);
            grabbedPlayer = null;
        }
        if (bossAttackTicks >= 54) {
            grabbedPlayer = null;
            getEntityData().set(DATA_GRAB_TARGET_ID, -1);
            riposteTicks = 30;
            finishBossAttack(40);
        }
    }

    private static double bossImpactY(ServerPlayer player) {
        return Math.max(WorldGenerator.bossArenaCenter().y, player.getY());
    }

    private void ragdollPlayer(ServerPlayer player, Vec3 impulse, float force) {
        player.setDeltaMovement(impulse);
        player.hurtMarked = true;
        player.resetFallDistance();
        if (ServerPlayNetworking.canSend(player, RagdollImpulsePayload.TYPE))
            ServerPlayNetworking.send(player, new RagdollImpulsePayload(position(), impulse, force));
        if (ServerPlayNetworking.canSend(player, DazePayload.TYPE))
            ServerPlayNetworking.send(player, new DazePayload(105,
                    Mth.clamp(5 + Mth.ceil(force * 2.4F), 6, 12)));
    }

    private void finishBossAttack(int cooldown) {
        if (bossAttack == BossAttack.GRAB) {
            grabbedPlayer = null;
            getEntityData().set(DATA_GRAB_TARGET_ID, -1);
        }
        setBossAttack(BossAttack.NONE);
        // Rage changes movement and pressure, not animation readability. Every committed attack
        // gets a real recovery window so the fight cannot collapse into an input-locking chain.
        bossAttackCooldown = Math.max(34, cooldown + 14 - rage() / 3);
        bossAttackTicks = 0;
        getEntityData().set(DATA_BOSS_ATTACK_TICKS, 0);
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
        if (behaviorPhase() == BehaviorPhase.BOSS) return;
        Player found = eclipseTarget == null ? null : level().getPlayerByUUID(eclipseTarget);
        if (found instanceof ServerPlayer player && player.isAlive()) beginRoaming(player);
        else beginRetreat(false);
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

    private boolean canSeeWithEyes(ServerPlayer player) {
        Vec3 eye = getEyePosition();
        Vec3 delta = player.getEyePosition().subtract(eye);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 0.001D || delta.lengthSqr() > 96.0D * 96.0D || !hasLineOfSight(player)) return false;
        Vec3 forward = Vec3.directionFromRotation(getXRot(), getYHeadRot()).normalize();
        double horizontalDot = new Vec3(forward.x, 0.0D, forward.z).normalize()
                .dot(new Vec3(delta.x, 0.0D, delta.z).normalize());
        double yawLimit = Math.cos(Math.toRadians(AsterionConfig.INSTANCE.minotaurHorizontalFov * 0.5D));
        double pitch = Math.toDegrees(Math.atan2(delta.y, horizontal));
        return horizontalDot >= yawLimit
                && Math.abs(pitch - getXRot()) <= AsterionConfig.INSTANCE.minotaurVerticalFov * 0.5D;
    }

    private Vec3 findStalkingPosition(ServerPlayer player, boolean acceptFirst) {
        return findStalkingPosition(player, acceptFirst, 2);
    }

    /** angleMode: 0 visible observation point, 1 side flank, 2 behind the player's view. */
    private Vec3 findStalkingPosition(ServerPlayer player, boolean acceptFirst, int angleMode) {
        double preferred = AsterionConfig.INSTANCE.minotaurStalkDistance;
        Vec3 view = player.getViewVector(1.0F);
        double baseAngle = Math.atan2(view.z, view.x);
        Vec3 originalPosition = position();
        Vec3 fallback = null;
        for (int attempt = 0; attempt < 12; attempt++) {
            double spread = attempt == 0 ? 0.0D : (random.nextDouble() - 0.5D) * 1.15D;
            double distance = preferred - 4.0D + random.nextDouble() * 8.0D;
            double offset = angleMode == 1
                    ? (random.nextBoolean() ? Math.PI * 0.5D : -Math.PI * 0.5D)
                    : angleMode == 2 ? Math.PI : 0.0D;
            double angle = baseAngle + offset + spread;
            Vec3 corridor = WorldGenerator.nearestMazeCorridor(
                    player.getX() + Math.cos(angle) * distance,
                    player.getZ() + Math.sin(angle) * distance);
            BlockPos feet = BlockPos.containing(corridor);
            level().getChunkAt(feet);
            setPos(corridor.x, corridor.y, corridor.z);
            boolean floor = level().getBlockState(feet.below()).isFaceSturdy(level(), feet.below(), Direction.UP);
            if (!floor || !level().noCollision(this)) continue;
            fallback = corridor;
            boolean visible = player.hasLineOfSight(this);
            boolean desiredVisibility = angleMode == 0 ? visible : !visible;
            if (acceptFirst || desiredVisibility) {
                setPos(originalPosition.x, originalPosition.y, originalPosition.z);
                return corridor;
            }
        }
        setPos(originalPosition.x, originalPosition.y, originalPosition.z);
        return fallback;
    }

    private Vec3 findHiddenSpawnPosition(ServerPlayer player) {
        double preferred = AsterionConfig.INSTANCE.minotaurStalkDistance;
        Vec3 view = player.getViewVector(1.0F);
        double behind = Math.atan2(view.z, view.x) + Math.PI;
        Vec3 original = position();
        for (int attempt = 0; attempt < 28; attempt++) {
            double angle = behind + (random.nextDouble() - 0.5D) * 2.5D;
            double distance = preferred + random.nextDouble() * 14.0D;
            Vec3 candidate = WorldGenerator.nearestMazeCorridor(
                    player.getX() + Math.cos(angle) * distance,
                    player.getZ() + Math.sin(angle) * distance);
            BlockPos feet = BlockPos.containing(candidate);
            level().getChunkAt(feet);
            setPos(candidate.x, candidate.y, candidate.z);
            boolean floor = level().getBlockState(feet.below()).isFaceSturdy(level(), feet.below(), Direction.UP);
            boolean hidden = !player.hasLineOfSight(this);
            boolean valid = floor && level().noCollision(this) && hidden;
            setPos(original.x, original.y, original.z);
            if (valid) return candidate;
        }
        setPos(original.x, original.y, original.z);
        return null;
    }

    private Vec3 findHiddenCenterApproachSpawn(ServerPlayer player) {
        Vec3 original = position();
        int cell = AsterionConfig.INSTANCE.cellSize;
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = attempt * (Math.PI * 0.5D) + random.nextDouble() * 0.35D;
            double distance = cell * (6.0D + random.nextDouble() * 3.0D);
            Vec3 candidate = WorldGenerator.nearestMazeCorridor(
                    Math.cos(angle) * distance, Math.sin(angle) * distance);
            BlockPos feet = BlockPos.containing(candidate);
            level().getChunkAt(feet);
            setPos(candidate.x, candidate.y, candidate.z);
            boolean valid = level().getBlockState(feet.below()).isFaceSturdy(level(), feet.below(), Direction.UP)
                    && level().noCollision(this) && !player.hasLineOfSight(this);
            setPos(original.x, original.y, original.z);
            if (valid) return candidate;
        }
        setPos(original.x, original.y, original.z);
        return null;
    }

    private void moveByMazeRoute(ServerLevel level, Vec3 destination, double speed, int budget) {
        Vec3 waypoint = WorldGenerator.nextMazeWaypoint(level, position(), destination,
                getBbWidth(), getBbHeight(), budget);
        if (waypoint != null) getNavigation().moveTo(waypoint.x, waypoint.y, waypoint.z, speed);
    }

    private void updateChaseSpeed() {
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(Math.min(0.60D, 0.39D + rage() * 0.0175D));
    }

    private void playHeavySteps() {
        if (getDeltaMovement().horizontalDistanceSqr() > 0.012D && (tickCount % 9) == 0)
            playSound(SoundEvents.RAVAGER_STEP, behaviorPhase() == BehaviorPhase.CHASING ? 1.8F : 1.05F,
                    behaviorPhase() == BehaviorPhase.CHASING ? 0.68F : 0.48F);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (behaviorPhase() == BehaviorPhase.BOSS) {
            if (bossStage == BossStage.DEFEATED || amount <= 0.0F
                    || !(source.getEntity() instanceof Player)) return false;
            boolean exposed = riposteTicks > 0;
            // There is no invulnerability shield. Armor supplies steady resistance, while a
            // failed heavy move is now a damage bonus rather than the sole permission to attack.
            float dealt = Math.max(0.75F, amount * (exposed ? 1.65F : 0.62F));
            increaseRage(1);
            float remaining = getHealth() - dealt;
            if (bossStage != BossStage.EXTREME) remaining = Math.max(80.0F, remaining);
            setHealth(Math.max(1.0F, remaining));
            if (exposed) {
                riposteTicks = 0;
                setBossAttack(BossAttack.NONE);
                bossAttackCooldown = 54;
                getNavigation().stop();
                setDeltaMovement(Vec3.ZERO);
            }
            playSound(SoundEvents.RAVAGER_HURT, exposed ? 2.8F : 1.65F,
                    exposed ? 0.74F : 0.56F);
            level.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() * 0.62D, getZ(),
                    exposed ? 35 : 8, 1.0D, 1.2D, 1.0D, 0.18D);
            if (bossStage == BossStage.EXTREME && remaining <= 0.0F) beginDefeated(level);
            return true;
        }
        if (!AsterionConfig.INSTANCE.minotaurUnkillable)
            return super.hurtServer(level, source, amount);
        if (behaviorPhase() == BehaviorPhase.RETREATING || amount <= 0.0F) return false;
        // The Minotaur can be challenged and enraged, but its health never decreases and no
        // damage source can enter vanilla's death path.
        setHealth(getMaxHealth());
        if (behaviorPhase() == BehaviorPhase.HUNTING) beginWarning();
        if (behaviorPhase() == BehaviorPhase.WARNING || behaviorPhase() == BehaviorPhase.CHASING) {
            getEntityData().set(DATA_RAGE, Math.min(8, rage() + 1));
            updateChaseSpeed();
            playSound(SoundEvents.RAVAGER_HURT, 1.8F, Math.max(0.42F, 0.72F - rage() * 0.025F));
            if (source.getEntity() instanceof Player) {
                repelDamage += Mth.ceil(amount);
                if (repelThreshold > 0 && repelDamage >= repelThreshold) {
                    playSound(Asterion.MINOTAUR_ROAR, 3.0F, 0.84F);
                    beginRetreat(true);
                }
            }
        }
        return true;
    }

    private void beginDefeated(ServerLevel level) {
        setBossStage(BossStage.DEFEATED);
        setBossAttack(BossAttack.NONE);
        setAggressive(false);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        setHealth(1.0F);
        noPhysics = false;
        healthBossBar.removeAllPlayers();
        rageBossBar.removeAllPlayers();
        playSound(Asterion.MINOTAUR_ROAR, 5.0F, 0.58F);
        WorldGenerator.beginBossFinale(level, this);
    }

    private void tickDefeated(ServerLevel level) {
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        setTarget(null);
        setAggressive(false);
        if ((phaseTicks & 7) == 0)
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + getBbHeight() * 0.55D,
                    getZ(), 18, 1.0D, 1.6D, 1.0D, 0.12D);
    }

    @Override
    public boolean shouldBeSaved() {
        return behaviorPhase() == BehaviorPhase.BOSS && super.shouldBeSaved();
    }

    public AnimationState animationState() {
        BossAttack renderedAttack = bossAttackState();
        int renderedAttackTicks = getEntityData().get(DATA_BOSS_ATTACK_TICKS);
        if (behaviorPhase() == BehaviorPhase.BOSS) {
            if (renderedAttack == BossAttack.CHARGE || renderedAttack == BossAttack.RED_LIGHTNING_CHARGE
                    || renderedAttack == BossAttack.STAMPEDE || renderedAttack == BossAttack.PAWING)
                return renderedAttack == BossAttack.PAWING
                        || renderedAttackTicks < (renderedAttack == BossAttack.CHARGE ? 20 : 32)
                        ? AnimationState.WARNING : AnimationState.CHASE;
            if (renderedAttack == BossAttack.CLEAVE || renderedAttack == BossAttack.BACK_KICK
                    || renderedAttack == BossAttack.ARENA_SWEEP) return AnimationState.ATTACK;
            if (renderedAttack == BossAttack.SLAM) return AnimationState.VERTICAL_ATTACK;
            if (renderedAttack == BossAttack.LEAP) return AnimationState.LEAP;
            if (renderedAttack == BossAttack.SWORD_COMBO || renderedAttack == BossAttack.GRAB
                    || renderedAttack == BossAttack.WALL_SHOVE || renderedAttack == BossAttack.RUBBLE_THROW)
                return AnimationState.SWORD;
            if (renderedAttack == BossAttack.SPIN_COMBO) return AnimationState.SPIN;
        }
        if (swinging) return AnimationState.ATTACK;
        if (behaviorPhase() == BehaviorPhase.WARNING) return AnimationState.WARNING;
        if (behaviorPhase() == BehaviorPhase.CHASING) return AnimationState.CHASE;
        return getDeltaMovement().horizontalDistanceSqr() > 1.0E-4D ? AnimationState.WALK : AnimationState.IDLE;
    }

    public boolean isPerformingGrab() {
        return bossAttackState() == BossAttack.GRAB;
    }

    public int grabAttackTicks() {
        return isPerformingGrab() ? getEntityData().get(DATA_BOSS_ATTACK_TICKS) : 0;
    }

    public int grabTargetEntityId() {
        return getEntityData().get(DATA_GRAB_TARGET_ID);
    }

    private void setBossAttack(BossAttack attack) {
        bossAttack = attack;
        getEntityData().set(DATA_BOSS_ATTACK, attack.ordinal());
        if (attack == BossAttack.NONE) {
            getEntityData().set(DATA_BOSS_ATTACK_TICKS, 0);
            getEntityData().set(DATA_GRAB_TARGET_ID, -1);
        }
    }

    private BossAttack bossAttackState() {
        int ordinal = Mth.clamp(getEntityData().get(DATA_BOSS_ATTACK), 0, BossAttack.values().length - 1);
        return BossAttack.values()[ordinal];
    }

    private void setBossStage(BossStage stage) {
        bossStage = stage;
        getEntityData().set(DATA_BOSS_STAGE, stage.ordinal());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<MinotaurEntity>("movement", 5, test -> {
            RawAnimation animation = switch (animationState()) {
                case ATTACK -> ATTACK_ANIMATION;
                case VERTICAL_ATTACK -> VERTICAL_ATTACK_ANIMATION;
                case SWORD -> SWORD_ANIMATION;
                case SPIN -> SPIN_ANIMATION;
                case LEAP -> LEAP_ANIMATION;
                case WARNING -> WARNING_ANIMATION;
                case CHASE -> RUN_ANIMATION;
                case WALK -> WALK_ANIMATION;
                case IDLE -> IDLE_ANIMATION;
            };
            if (animationState() == AnimationState.CHASE)
                test.setControllerSpeed(1.0F + rage() * 0.045F);
            else test.setControllerSpeed(1.0F);
            return test.setAndContinue(animation);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    private static final class HeavyMoveControl extends MoveControl {
        private final MinotaurEntity minotaur;
        private HeavyMoveControl(MinotaurEntity minotaur) { super(minotaur); this.minotaur = minotaur; }

        @Override
        public void tick() {
            float previousYaw = minotaur.getYRot();
            super.tick();
            BehaviorPhase phase = minotaur.behaviorPhase();
            if (phase != BehaviorPhase.CHASING && phase != BehaviorPhase.BOSS) return;
            float turnRate = phase == BehaviorPhase.BOSS
                    ? 3.8F + minotaur.rage() * 0.12F
                    : 6.0F + minotaur.rage() * 0.35F;
            float limited = Mth.approachDegrees(previousYaw, minotaur.getYRot(), turnRate);
            minotaur.setYRot(limited);
            minotaur.setYBodyRot(limited);
        }
    }
}
