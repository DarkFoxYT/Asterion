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
import net.krodark.asterion.GreekRune;
import net.krodark.asterion.WorldGenerator;
import net.krodark.asterion.event.DeadSunEventSystem;
import net.krodark.asterion.network.MazeZapPayload;
import net.krodark.asterion.network.MazeShiftPayload;
import net.krodark.asterion.network.BossTelegraphPayload;
import net.krodark.asterion.network.DazePayload;
import net.krodark.asterion.network.DeadSunStrikePayload;
import net.krodark.asterion.network.ragdoll.RagdollExplosionPayload;
import net.krodark.asterion.network.ragdoll.RagdollImpulsePayload;
import net.krodark.asterion.network.ragdoll.RagdollServerNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.tags.DamageTypeTags;
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
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
    private static final RawAnimation CHAIN_ANIMATION = RawAnimation.begin().thenLoop("chain_grapple");
    private static final RawAnimation PUNCH_ANIMATION = RawAnimation.begin().thenLoop("punch_combo");
    private static final RawAnimation HORN_ANIMATION = RawAnimation.begin().thenLoop("horn_ram");
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
    private static final EntityDataAccessor<Integer> DATA_REACH_ARM = SynchedEntityData.defineId(
            MinotaurEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CHARGE_WINDUP = SynchedEntityData.defineId(
            MinotaurEntity.class, EntityDataSerializers.INT);

    private UUID eclipseTarget;
    private int phaseTicks;
    private int gazeTicks;
    private int gazeTriggerTicks;
    private int sightings;
    private int sightingCooldown;
    private int warningTicks;
    private int chaseTicks;
    private int chaseLostSightTicks;
    private int pursuitDetectionTicks;
    private int escapeDistanceTicks;
    private int stuckTicks;
    private int relocateTicks;
    private int shadowRelocateCooldown;
    private int shadowArrivalTicks;
    private int mazeGrabTicks;
    private UUID mazeGrabbedPlayer;
    private int terrorCueTicks;
    private int approachTicks;
    private int stalkModeTicks;
    private int awarenessRepathTicks;
    private int attackCooldown;
    private int hitBackoffTicks;
    private Vec3 hitBackoffDirection = Vec3.ZERO;
    private UUID hitBackoffTarget;
    private int corridorChargeTicks;
    private int corridorChargeCooldown;
    private Vec3 corridorChargeDirection = Vec3.ZERO;
    private boolean heavyJumpArmed;
    private boolean heavyJumpWasAirborne;
    private int rageCalmTicks;
    private int lastReachArm = 1;
    private Vec3 grabbedReachDirection = Vec3.ZERO;
    private double grabbedReachLength;
    private int inaccessibleTicks;
    private int sanctuaryGateTicks;
    private int repelDamage;
    private int repelThreshold;
    private int bossAttackTicks;
    private int bossAttackCooldown;
    private int pillarOpportunityTicks;
    private final int[] bossAttackLockouts = new int[BossAttack.values().length];
    private BossAttack bossAttack = BossAttack.NONE;
    private BossAttack lastBossAttack = BossAttack.NONE;
    private BossAttack attackBeforeLast = BossAttack.NONE;
    private final ServerBossEvent healthBossBar = new ServerBossEvent(UUID.randomUUID(),
            Component.literal("THE MINOTAUR"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private final ServerBossEvent rageBossBar = new ServerBossEvent(UUID.randomUUID(),
            Component.literal("RAGE"), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_12);
    private Vec3 bossChargeDirection = Vec3.ZERO;
    private boolean bossChargeTargetsPillar;
    private Vec3 bossLeapTarget = Vec3.ZERO;
    private Vec3 greekFireAim = Vec3.ZERO;
    private Vec3 collapseAnchor = Vec3.ZERO;
    private BossStage bossStage = BossStage.PILLARS;
    private int collapseTicks;
    private int riposteTicks;
    private int bossStunTicks;
    private int bossPartySize = 1;
    private UUID grabbedPlayer;
    private UUID chainGrappleTarget;
    private UUID punchComboTarget;
    private boolean punchComboFromChain;
    private int punchStrikeMask;
    private UUID wallComboTarget;
    private int wallComboWindow;
    private UUID airborneCatchTarget;
    private int airborneCatchWindow;
    private boolean wallShoveHit;
    private int leapImpactTick = -1;
    private Vec3 leapImpactOrigin = Vec3.ZERO;
    private final Set<UUID> leapShockwaveHits = new HashSet<>();
    private UUID stompTarget;
    private Vec3 stompTargetPosition = Vec3.ZERO;
    private boolean stompWasAirborne;
    private int storedArrows;
    private UUID arrowReturnTarget;
    private Vec3 lightningStrikeTarget = Vec3.ZERO;
    private boolean lightningStrikeResolved;
    private GrabThrowStyle grabThrowStyle = GrabThrowStyle.ARENA;
    private int bossPressureHits;
    private int bossPressureWindowTicks;
    private int cornerPressureTicks;
    private int hitReactionCooldown;
    private boolean bossWasAirborne;
    private final Set<BlockPos> bossFireBlocks = new HashSet<>();
    private StalkMode stalkMode = StalkMode.SHADOWING;
    private Vec3 lastKnownPlayerPosition;
    private Vec3 stalkingDestination;
    private Vec3 stalkingAnchor;
    private Vec3 previousPlayerSample;
    private Vec3 trackedPlayerVelocity = Vec3.ZERO;
    private final ArrayDeque<Vec3> stalkingRoute = new ArrayDeque<>();
    private final ArrayDeque<Vec3> chaseRoute = new ArrayDeque<>();
    private Vec3 chaseRouteAnchor;
    private int chaseRouteTicks;
    private float hallwayMomentum;
    private float hallwayTarget;
    private int hallwayScanTicks;
    private Vec3 failedStalkingDestination;
    private int failedStalkingTicks;
    private int paranoiaCooldown;
    private double previousTargetDistance = Double.MAX_VALUE;
    private Vec3 previousPosition = Vec3.ZERO;
    private boolean wasObserved;

    public enum BehaviorPhase { DORMANT, ROAMING, HUNTING, WARNING, CHASING, RETREATING, BOSS }
    private enum StalkMode { PATROLLING, SHADOWING, OBSERVING, FLANKING, INTERCEPTING, VANISHING }
    private enum BossAttack { NONE, CLEAVE, CHARGE, SLAM, LEAP, SWORD_COMBO, SPIN_COMBO, GRAB,
        RED_LIGHTNING_CHARGE, PAWING, STAMPEDE, BACK_KICK, ARENA_SWEEP, RUBBLE_THROW, WALL_SHOVE,
        FIRE_RINGS, CHAIN_GRAPPLE,
        PUNCH_COMBO, HORN_RAM, RAGDOLL_STOMP, ARROW_RETURN, GREEK_FIRE_LASER }
    private enum BossStage { PILLARS, COLLAPSE, EXTREME, DEFEATED }
    private enum CombatRange { CLOSE, MEDIUM, FAR }
    private enum GrabThrowStyle { ARENA, SKY }
    public enum AnimationState { IDLE, WALK, WARNING, CHASE, ATTACK, VERTICAL_ATTACK, SWORD, SPIN,
        LEAP, CHAIN, PUNCH, HORN }

    public MinotaurEntity(EntityType<? extends MinotaurEntity> type, Level level) {
        super(type, level);
        xpReward = 35;
        moveControl = new HeavyMoveControl(this);
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
        builder.define(DATA_REACH_ARM, 0);
        builder.define(DATA_CHARGE_WINDUP, 50);
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
        Vec3 spawn = minotaur.findHiddenSpawnPosition(player);
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
        pursuitDetectionTicks = 0;
        shadowRelocateCooldown = random.nextIntBetweenInclusive(220, 420);
        hallwayMomentum = 0.0F;
        hallwayTarget = 0.0F;
        updateChaseSpeed();
        enterStalkMode(StalkMode.SHADOWING);
    }

    public boolean isRoaming() { return behaviorPhase() == BehaviorPhase.ROAMING; }

    public void beginHunting(ServerPlayer player) {
        eclipseTarget = player.getUUID();
        setBehaviorPhase(BehaviorPhase.HUNTING);
        setTarget(null);
        setAggressive(false);
        setRage(Math.max(rage(), random.nextIntBetweenInclusive(3, 5)));
        rageCalmTicks = 360;
        gazeTriggerTicks = random.nextIntBetweenInclusive(140, 200);
        previousPosition = position();
        previousTargetDistance = distanceTo(player);
        lastKnownPlayerPosition = player.position();
        pursuitDetectionTicks = 0;
        shadowRelocateCooldown = random.nextIntBetweenInclusive(100, 220);
        hallwayMomentum = 0.0F;
        hallwayTarget = 0.0F;
        updateChaseSpeed();
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
        breakEntanglingCobwebs(level);
        tickHeavyLanding(level);
        avoidWallHugging(level);
        if (sightingCooldown > 0) sightingCooldown--;
        if (paranoiaCooldown > 0) paranoiaCooldown--;
        if (attackCooldown > 0) attackCooldown--;
        if (hitReactionCooldown > 0) hitReactionCooldown--;
        if (corridorChargeCooldown > 0) corridorChargeCooldown--;
        if (rageCalmTicks > 0) rageCalmTicks--;
        else if (rage() > 0 && (tickCount % 160) == 0) {
            int floor = behaviorPhase() == BehaviorPhase.BOSS && bossStage == BossStage.EXTREME ? 4 : 0;
            if (behaviorPhase() != BehaviorPhase.CHASING && rage() > floor) setRage(rage() - 1);
        }
        if (failedStalkingTicks > 0) failedStalkingTicks--;
        if (shadowRelocateCooldown > 0) shadowRelocateCooldown--;
        for (int index = 0; index < bossAttackLockouts.length; index++)
            if (bossAttackLockouts[index] > 0) bossAttackLockouts[index]--;
        Player foundPlayer = eclipseTarget == null ? null : level.getPlayerByUUID(eclipseTarget);
        ServerPlayer player = foundPlayer instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        if (player == null || !player.isAlive() || player.isSpectator()
                || !player.level().dimension().equals(Asterion.ASTERION_LEVEL)) {
            if (behaviorPhase() == BehaviorPhase.BOSS) {
                player = level.players().stream()
                        .filter(candidate -> candidate.isAlive() && !candidate.isSpectator()
                                && !candidate.isCreative())
                        .min(java.util.Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
                if (player != null) eclipseTarget = player.getUUID();
                else {
                    setTarget(null);
                    setAggressive(false);
                    getNavigation().stop();
                    setDeltaMovement(Vec3.ZERO);
                    healthBossBar.removeAllPlayers();
                    rageBossBar.removeAllPlayers();
                    return;
                }
            } else {
                beginRetreat(false);
                tickRetreat();
                return;
            }
        }

        if (behaviorPhase() != BehaviorPhase.BOSS && behaviorPhase() != BehaviorPhase.RETREATING
                && WorldGenerator.isApproachingCenter(player.position())) {
            beginBossIntercept(player);
        }

        if (behaviorPhase() != BehaviorPhase.BOSS && (tickCount % 20) == 0) {
            int ringPressure = GreekRune.forRadius(player.getX(), player.getZ()).ordinal();
            int rageFloor = ringPressure / 3;
            if (rage() < rageFloor) setRage(rageFloor);
            AttributeInstance damage = getAttribute(Attributes.ATTACK_DAMAGE);
            if (damage != null) damage.setBaseValue(10.0D + ringPressure * 0.48D);
        }

        switch (behaviorPhase()) {
            case ROAMING -> tickRoaming(level, player);
            case HUNTING -> tickHunting(level, player);
            case WARNING -> tickWarning(level, player);
            case CHASING -> tickChase(level, player);
            case RETREATING -> tickRetreat();
            case DORMANT -> { if (!DeadSunEventSystem.isEclipseActive(level) && canDespawnUnseen()) discard(); }
            case BOSS -> tickBoss(level, player);
        }
    }

    private void tickRoaming(ServerLevel level, ServerPlayer player) {
        setTarget(null);
        setAggressive(false);
        double distance = distanceTo(player);
        if (phaseTicks > 0 && phaseTicks % 1200 == 0) increaseRage(1);
        float lookExposure = playerLookExposure(player, distance);
        boolean discovered = lookExposure > 0.12F;
        boolean directSight = canAcquirePlayerForChase(player, distance);
        pursuitDetectionTicks = directSight ? Math.min(24, pursuitDetectionTicks + 2)
                : Math.max(0, pursuitDetectionTicks - 1);
        if (pursuitDetectionTicks >= 6) {
            beginWarning(true);
            return;
        }
        tickStalking(level, player, discovered, distance);
        if (discovered) {
            gazeTicks++;
            getLookControl().setLookAt(player, 6.0F, 6.0F);
            if (gazeTicks >= 12) beginWarning();
        } else gazeTicks = Math.max(0, gazeTicks - 1);
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
        if (phaseTicks > 0 && phaseTicks % 720 == 0) increaseRage(1);
        float lookExposure = playerLookExposure(player, distance);
        boolean observed = lookExposure > 0.12F;
        lastKnownPlayerPosition = player.position();
        Vec3 sampledVelocity = previousPlayerSample == null ? player.getDeltaMovement()
                : player.position().subtract(previousPlayerSample);
        trackedPlayerVelocity = trackedPlayerVelocity.lerp(
                new Vec3(sampledVelocity.x, 0.0D, sampledVelocity.z), 0.22D);
        previousPlayerSample = player.position();
        if (stalkModeTicks > 0) stalkModeTicks--;

        boolean directSight = canAcquirePlayerForChase(player, distance);
        boolean caughtRunning = directSight
                && (player.isSprinting() || trackedPlayerVelocity.horizontalDistanceSqr() > 0.022D);
        pursuitDetectionTicks = directSight ? Math.min(30,
                pursuitDetectionTicks + (caughtRunning ? 3 : 2))
                : Math.max(0, pursuitDetectionTicks - 1);
        if (pursuitDetectionTicks >= 6) {
            beginWarning(true);
            return;
        }

        if (observed) {
            gazeTicks++;
            getLookControl().setLookAt(player, 8.0F, 8.0F);
            if (!wasObserved && sightingCooldown == 0) {
                sightings++;
                if ((sightings & 1) == 0) increaseRage(1);
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
            gazeTicks = Math.max(0, gazeTicks - 1);
        }
        wasObserved = observed;

        boolean approaching = distance < previousTargetDistance - 0.055D;
        approachTicks = approaching ? approachTicks + 1 : Math.max(0, approachTicks - 2);
        if (directSight && (distance <= AsterionConfig.INSTANCE.minotaurApproachDistance
                || (approachTicks >= 12
                && distance < AsterionConfig.INSTANCE.minotaurApproachDistance + 7.0D))) {
            beginWarning(true);
            return;
        }
        previousTargetDistance = distance;

        tickStalking(level, player, observed, distance);
        playHeavySteps();
    }

    private void tickStalking(ServerLevel level, ServerPlayer player, boolean observed, double distance) {
        if (shadowArrivalTicks > 0) {
            shadowArrivalTicks--;
            if (shadowArrivalTicks % 3 == 0)
                level.sendParticles(ParticleTypes.DUST_PLUME, getX(), getY() + 0.25D, getZ(),
                        3, 0.85D, 0.30D, 0.85D, 0.018D);
        }

        if (!observed && shadowRelocateCooldown <= 0 && (distance > 64.0D || distance < 26.0D)
                && phaseTicks % 20 == 0 && random.nextInt(3) == 0
                && tryShadowRelocation(level, player)) return;

        if (observed && stalkMode != StalkMode.OBSERVING && stalkMode != StalkMode.VANISHING)
            enterStalkMode(StalkMode.OBSERVING);
        if (stalkModeTicks <= 0) {
            enterStalkMode(nextStalkMode());
        }

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
            if (random.nextBoolean()) playRoar(0.75F, 0.55F + random.nextFloat() * 0.12F, 0.28F);
            else playSound(SoundEvents.RAVAGER_STEP, 0.75F, 0.55F + random.nextFloat() * 0.12F);
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
            if (trackedPlayerVelocity.horizontalDistanceSqr() > 0.0025D) {
                double leadDistance = Mth.clamp(trackedPlayerVelocity.horizontalDistance() * 75.0D,
                        AsterionConfig.INSTANCE.cellSize * 1.5D,
                        AsterionConfig.INSTANCE.cellSize * 5.0D);
                Vec3 lead = trackedPlayerVelocity.normalize().scale(leadDistance);
                Vec3 predicted = WorldGenerator.nearestMazeCorridor(
                        player.getX() + lead.x, player.getZ() + lead.z);
                if (failedStalkingTicks <= 0 || failedStalkingDestination == null
                        || predicted.distanceToSqr(failedStalkingDestination)
                        > AsterionConfig.INSTANCE.cellSize * AsterionConfig.INSTANCE.cellSize)
                    return predicted;
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
        if (route.isEmpty() && position().distanceToSqr(stalkingDestination) > 16.0D) {
            failedStalkingDestination = stalkingDestination;
            failedStalkingTicks = 100;
            stalkingDestination = null;
        }
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
            Vec3 waypoint = routeLookAhead(level, stalkingRoute, 3);
            double speed = switch (stalkMode) {
                case VANISHING -> 0.88D;
                case FLANKING, INTERCEPTING -> 0.74D;
                case PATROLLING -> 0.62D;
                default -> 0.56D;
            };
            if (!getNavigation().moveTo(waypoint.x, waypoint.y, waypoint.z, speed)) {
                failedStalkingDestination = stalkingDestination;
                failedStalkingTicks = 100;
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
        beginWarning(false);
    }

    private void beginWarning(boolean urgent) {
        if (behaviorPhase() == BehaviorPhase.WARNING || behaviorPhase() == BehaviorPhase.CHASING) return;
        setBehaviorPhase(BehaviorPhase.WARNING);
        warningTicks = urgent ? random.nextIntBetweenInclusive(28, 44)
                : random.nextIntBetweenInclusive(60, 100);
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
        chaseLostSightTicks = 0;
        escapeDistanceTicks = 0;
        sanctuaryGateTicks = 0;
        stuckTicks = 0;
        chaseRoute.clear();
        chaseRouteAnchor = null;
        chaseRouteTicks = 0;
        hallwayMomentum = 0.0F;
        hallwayTarget = 0.0F;
        hallwayScanTicks = 0;
        terrorCueTicks = random.nextIntBetweenInclusive(34, 72);
        corridorChargeTicks = 0;
        corridorChargeCooldown = random.nextIntBetweenInclusive(55, 110);
        setTarget(player);
        setAggressive(true);
        updateChaseSpeed();
        playRoar(3.2F, 1.0F, 0.82F);
    }

    private void tickChase(ServerLevel level, ServerPlayer player) {
        if (mazeGrabTicks > 0) {
            tickMazeGrab(level, player);
            return;
        }
        if (tickHitBackoff(level, player, false)) return;
        setTarget(player);
        setAggressive(true);
        chaseTicks++;
        double distance = distanceTo(player);
        chaseLostSightTicks = canSeeWithEyes(player) ? 0 : chaseLostSightTicks + 1;
        if (--terrorCueTicks <= 0) {
            terrorCueTicks = random.nextIntBetweenInclusive(Math.max(46, 105 - rage() * 4),
                    Math.max(82, 168 - rage() * 5));
            playSound(SoundEvents.WARDEN_HEARTBEAT, 1.7F, 0.54F + random.nextFloat() * 0.12F);
            double roofY = Math.min(level.getMaxY() - 2.0D,
                    player.getY() + Math.max(8.0D, AsterionConfig.INSTANCE.wallHeight - 2.0D));
            level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST,
                            Asterion.ANCIENT_STONE.defaultBlockState()),
                    player.getX(), roofY, player.getZ(), 12, 3.2D, 0.35D, 3.2D, 0.018D);
            if (!hasLineOfSight(player) && random.nextInt(3) == 0)
                playRoar(0.82F, 0.48F + random.nextFloat() * 0.08F, 0.24F);
        }
        boolean sealedSanctuary = WorldGenerator.isNearSafeRune(level, player.blockPosition())
                && hasClosedIronBarrierNear(player);
        if (sealedSanctuary) {
            sanctuaryGateTicks++;
            getNavigation().stop();
            if (sanctuaryGateTicks == 1) playRoar(4.2F, 0.72F, 1.05F);
            if (sanctuaryGateTicks >= 80) beginRetreat(true);
            return;
        } else sanctuaryGateTicks = 0;
        if (corridorChargeTicks > 0) {
            tickCorridorCharge(level, player);
            return;
        }
        tickHallwayMomentum(level, player);
        if (tryBeginCorridorCharge(level, player, distance)) return;
        int chaseRepathInterval = Math.max(6, AsterionConfig.INSTANCE.minotaurRepathTicks);
        boolean routeReady = true;
        if (getNavigation().isDone() || chaseRouteTicks-- <= 0 || phaseTicks % chaseRepathInterval == 0)
            routeReady = refreshChasePath(player);
        if (!routeReady && distance < 18.0D && !hasLineOfSight(player) && hasClosedIronBarrierNear(player)) {
            inaccessibleTicks++;
            if (inaccessibleTicks == 30) playRoar(3.4F, 0.88F, 0.74F);
            if (inaccessibleTicks >= 120) {
                beginRetreat(true);
                return;
            }
        } else inaccessibleTicks = Math.max(0, inaccessibleTicks - 2);
        double attackReach = getBbWidth() * 0.65D + player.getBbWidth() * 0.5D + 1.2D;
        boolean elevatedReach = player.getY() > getY() + 1.8D && canArmReach(player);
        if (attackCooldown <= 0 && (distance <= attackReach || elevatedReach) && canSeeWithEyes(player)) {
            boolean pinned = isPlayerPinned(level, player);
            if (elevatedReach || pinned || distance <= 2.9D || random.nextFloat() < 0.46F) {
                beginMazeGrab(player);
                return;
            }
            swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            doHurtTarget(level, player);
            Vec3 away = player.position().subtract(position());
            Vec3 horizontal = new Vec3(away.x, 0.0D, away.z);
            if (horizontal.lengthSqr() < 0.01D) horizontal = Vec3.directionFromRotation(0.0F, getYRot());
            ragdollPlayer(player, horizontal.normalize().scale(1.35D).add(0.0D, 0.48D, 0.0D),
                    1.05F, true);
            attackCooldown = Math.max(16, 26 - rage());
        }

        double moved = position().distanceToSqr(previousPosition);
        previousPosition = position();
        if (distance > 4.0D && moved < 0.003D) stuckTicks++; else stuckTicks = Math.max(0, stuckTicks - 2);

        if ((stuckTicks > 8 || player.getY() > getY() + 5.0D) && (phaseTicks % 5) == 0) {
            Vec3 toward = player.position().subtract(position()).normalize();
            AABB breaker = getBoundingBox().expandTowards(toward.scale(2.1D)).inflate(0.8D, 1.2D, 0.8D);
            int broken = WorldGenerator.breakPlayerBlocksAround(level, breaker);
            if (broken == 0 && !routeReady && stuckTicks > 18)
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
                || player.getY() > getY() + 4.5D) && onGround() && distance < 24.0D) {
            Vec3 leap = player.position().subtract(position());
            Vec3 horizontal = new Vec3(leap.x, 0.0D, leap.z).normalize();
            double rise = Mth.clamp(player.getY() - getY(), 0.0D, 10.0D);
            setDeltaMovement(horizontal.x * 0.82D, 0.86D + rise * 0.055D, horizontal.z * 0.82D);
            hurtMarked = true;
            armHeavyJump();
            playSound(SoundEvents.GOAT_LONG_JUMP, 2.5F, 0.42F);
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
            if (escapeDistanceTicks >= 60) beginRetreat(true);
        } else escapeDistanceTicks = Math.max(0, escapeDistanceTicks - 2);
        playHeavySteps();
    }

    private boolean tryBeginCorridorCharge(ServerLevel level, ServerPlayer player, double distance) {
        if (corridorChargeCooldown > 0 || hallwayMomentum < 0.58F || rage() < 3
                || distance < 10.0D || distance > 34.0D || !onGround()
                || Math.abs(player.getY() - getY()) > 2.8D || !canSeeWithEyes(player)) return false;
        Vec3 lead = player.position().add(player.getDeltaMovement().multiply(6.0D, 0.0D, 6.0D))
                .subtract(position());
        Vec3 horizontal = new Vec3(lead.x, 0.0D, lead.z);
        if (horizontal.lengthSqr() < 0.01D) return false;
        corridorChargeDirection = horizontal.normalize();
        corridorChargeTicks = 1;
        corridorChargeCooldown = Math.max(95, 190 - rage() * 6);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        getLookControl().setLookAt(player, 10.0F, 5.0F);
        playSound(SoundEvents.GOAT_PREPARE_RAM, 3.0F, 0.34F);
        return true;
    }

    private void tickCorridorCharge(ServerLevel level, ServerPlayer player) {
        corridorChargeTicks++;
        getNavigation().stop();
        if (corridorChargeTicks < 18) {
            setDeltaMovement(getDeltaMovement().multiply(0.08D, 1.0D, 0.08D));
            getLookControl().setLookAt(player, 8.0F, 5.0F);
            if ((corridorChargeTicks & 3) == 0)
                level.sendParticles(ParticleTypes.DUST_PLUME, getX(), getY() + 0.1D, getZ(),
                        7, 0.7D, 0.08D, 0.7D, 0.04D);
            return;
        }
        if (corridorChargeTicks == 18) playRoar(3.4F, 0.72F, 0.92F);
        double speed = 0.64D + rage() * 0.014D;
        setDeltaMovement(corridorChargeDirection.x * speed, getDeltaMovement().y,
                corridorChargeDirection.z * speed);
        AABB impact = getBoundingBox().expandTowards(corridorChargeDirection.scale(2.4D))
                .inflate(0.55D, 0.35D, 0.55D);
        int broken = WorldGenerator.breakPlayerBlocksAround(level, impact);
        if (horizontalCollision || broken > 0)
            broken += WorldGenerator.breakMazeWallAround(level, impact, this);
        if (broken > 0) {
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 3.2F, 0.40F);
            level.sendParticles(ParticleTypes.EXPLOSION, impact.getCenter().x,
                    getY() + getBbHeight() * 0.42D, impact.getCenter().z,
                    Math.min(12, 3 + broken / 7), 1.2D, 1.5D, 1.2D, 0.07D);
        }
        if (attackCooldown <= 0 && impact.intersects(player.getBoundingBox())) {
            player.hurtServer(level, damageSources().mobAttack(this), 16.0F + rage() * 0.35F);
            ragdollPlayer(player, corridorChargeDirection.scale(3.1D).add(0.0D, 0.62D, 0.0D),
                    1.6F, true);
            attackCooldown = 26;
            finishCorridorCharge();
            return;
        }
        if ((corridorChargeTicks & 1) == 0)
            level.sendParticles(ParticleTypes.DUST_PLUME, getX(), getY() + 0.08D, getZ(),
                    7, 0.9D, 0.08D, 0.9D, 0.05D);
        if (corridorChargeTicks >= 58 || (horizontalCollision && broken == 0)) {
            armHeavyJump();
            heavyJumpWasAirborne = true;
            tickHeavyLanding(level);
            finishCorridorCharge();
        }
    }

    private void finishCorridorCharge() {
        corridorChargeTicks = 0;
        corridorChargeDirection = Vec3.ZERO;
        hallwayMomentum = 0.0F;
        hallwayTarget = 0.0F;
        setDeltaMovement(getDeltaMovement().multiply(0.22D, 1.0D, 0.22D));
        updateChaseSpeed();
    }

    private void beginMazeGrab(ServerPlayer player) {
        mazeGrabTicks = 1;
        mazeGrabbedPlayer = player.getUUID();
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        setBossAttack(BossAttack.GRAB);
        getEntityData().set(DATA_BOSS_ATTACK_TICKS, 1);
        getEntityData().set(DATA_GRAB_TARGET_ID, player.getId());
        chooseReachArm(player);
        clearLockedReach();
        playRoar(2.4F, 0.76F, 0.52F);
    }

    private void tickMazeGrab(ServerLevel level, ServerPlayer fallback) {
        mazeGrabTicks++;
        getEntityData().set(DATA_BOSS_ATTACK_TICKS, mazeGrabTicks);
        Player found = mazeGrabbedPlayer == null ? null : level.getPlayerByUUID(mazeGrabbedPlayer);
        ServerPlayer grabbed = found instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        if (grabbed == null || !grabbed.isAlive()) {
            finishMazeGrab();
            return;
        }
        getLookControl().setLookAt(grabbed, 12.0F, 8.0F);
        if (mazeGrabTicks < 10) {
            setDeltaMovement(getDeltaMovement().multiply(0.08D, 1.0D, 0.08D));
            return;
        }
        if (mazeGrabTicks < 37) {
            if (grabbedReachDirection.lengthSqr() < 0.01D) lockReachTo(grabbed);
            float hold = Mth.clamp((mazeGrabTicks - 10) / 27.0F, 0.0F, 1.0F);
            double swing = Math.sin(hold * Math.PI) * 0.62D;
            Vec3 hand = reachHandPosition(grabbed, 0.12D + swing * 0.10D,
                    Math.sin(hold * Math.PI * 2.0D) * 0.22D,
                    swing * getBbHeight() * 0.09D);
            placePlayerInHand(grabbed, hand);
            grabbed.setDeltaMovement(Vec3.ZERO);
            grabbed.resetFallDistance();
            if ((mazeGrabTicks & 3) == 0)
                level.sendParticles(ParticleTypes.DUST_PLUME, hand.x, hand.y, hand.z,
                        4, 0.4D, 0.45D, 0.4D, 0.025D);
            return;
        }
        if (mazeGrabTicks == 37) {
            Vec3 direction = bestMazeThrowDirection(level, grabbed);
            Vec3 impulse = direction.scale(1.72D + random.nextDouble() * 0.42D)
                    .add(0.0D, 1.82D + random.nextDouble() * 0.42D, 0.0D);
            grabbed.hurtServer(level, damageSources().mobAttack(this), 14.0F);
            ragdollPlayer(grabbed, impulse, 1.72F, true);
            level.sendParticles(ParticleTypes.EXPLOSION, grabbed.getX(), grabbed.getY() + 0.7D,
                    grabbed.getZ(), 5, 0.7D, 0.8D, 0.7D, 0.035D);
            playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 2.3F, 0.58F);
        }
        if (mazeGrabTicks >= 47) finishMazeGrab();
    }

    private void finishMazeGrab() {
        mazeGrabTicks = 0;
        mazeGrabbedPlayer = null;
        setBossAttack(BossAttack.NONE);
        getEntityData().set(DATA_GRAB_TARGET_ID, -1);
        getEntityData().set(DATA_REACH_ARM, 0);
        clearLockedReach();
        attackCooldown = 34;
        chaseLostSightTicks = 0;
    }

    private boolean canArmReach(ServerPlayer player) {
        Vec3 shoulder = shoulderPosition(player);
        Vec3 target = player.position().add(0.0D, player.getBbHeight() * 0.52D, 0.0D);
        return target.distanceToSqr(shoulder) <= armReach() * armReach()
                && player.getY() >= getY() - 1.5D;
    }

    private void chooseReachArm(ServerPlayer player) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, yBodyRot).normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        int nearerArm = player.position().subtract(position()).dot(right) >= 0.0D ? 1 : -1;
        int chosen = random.nextFloat() < 0.72F ? nearerArm : -nearerArm;
        if (chosen == lastReachArm && random.nextFloat() < 0.38F) chosen = -chosen;
        lastReachArm = chosen;
        getEntityData().set(DATA_REACH_ARM, chosen);
    }

    private Vec3 shoulderPosition(Entity target) {
        int arm = getEntityData().get(DATA_REACH_ARM);
        if (arm == 0) arm = lastReachArm;
        Vec3 forward = Vec3.directionFromRotation(0.0F, yBodyRot).normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        return position().add(right.scale(arm * getBbWidth() * 0.30D))
                .add(forward.scale(0.08D)).add(0.0D, getBbHeight() * 0.69D, 0.0D);
    }

    private double armReach() {
        return getBbHeight() * 0.72D;
    }

    private Vec3 reachHandPosition(Entity target, double forwardOffset, double lateralOffset,
                                   double verticalOffset) {
        Vec3 shoulder = shoulderPosition(target);
        Vec3 targetCenter = target.position().add(0.0D, target.getBbHeight() * 0.52D, 0.0D);
        Vec3 delta = targetCenter.subtract(shoulder);
        if (delta.lengthSqr() < 0.001D)
            delta = Vec3.directionFromRotation(0.0F, yBodyRot).add(0.0D, -0.08D, 0.0D);
        Vec3 direction = grabbedReachDirection.lengthSqr() > 0.01D
                ? grabbedReachDirection : delta.normalize();
        double length = grabbedReachDirection.lengthSqr() > 0.01D
                ? grabbedReachLength : Mth.clamp(delta.length(), armReach() * 0.48D, armReach());
        Vec3 forward = Vec3.directionFromRotation(0.0F, yBodyRot).normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        return shoulder.add(direction.scale(length)).add(forward.scale(forwardOffset))
                .add(right.scale(lateralOffset)).add(0.0D, verticalOffset, 0.0D);
    }

    private void lockReachTo(Entity target) {
        Vec3 delta = target.position().add(0.0D, target.getBbHeight() * 0.52D, 0.0D)
                .subtract(shoulderPosition(target));
        if (delta.lengthSqr() < 0.001D)
            delta = Vec3.directionFromRotation(0.0F, yBodyRot).add(0.0D, -0.08D, 0.0D);
        grabbedReachDirection = delta.normalize();
        grabbedReachLength = Mth.clamp(delta.length(), armReach() * 0.48D, armReach());
    }

    private void clearLockedReach() {
        grabbedReachDirection = Vec3.ZERO;
        grabbedReachLength = 0.0D;
    }

    private static void placePlayerInHand(ServerPlayer player, Vec3 hand) {
        player.teleportTo(hand.x, hand.y - player.getBbHeight() * 0.52D, hand.z);
    }

    private boolean isPlayerPinned(ServerLevel level, ServerPlayer player) {
        int exits = 0;
        AABB body = player.getBoundingBox().deflate(0.04D);
        Vec3[] directions = {new Vec3(1, 0, 0), new Vec3(-1, 0, 0),
                new Vec3(0, 0, 1), new Vec3(0, 0, -1)};
        for (Vec3 direction : directions)
            if (level.noCollision(player, body.move(direction.scale(3.2D)))) exits++;
        return exits <= 1 || (exits <= 2 && player.getDeltaMovement().horizontalDistanceSqr() < 0.018D
                && distanceToSqr(player) < 4.8D * 4.8D);
    }

    private Vec3 bestMazeThrowDirection(ServerLevel level, ServerPlayer player) {
        Vec3[] directions = {new Vec3(1, 0, 0), new Vec3(-1, 0, 0),
                new Vec3(0, 0, 1), new Vec3(0, 0, -1)};
        int start = random.nextInt(directions.length);
        Vec3 best = directions[start];
        double bestClearance = -1.0D;
        AABB body = player.getBoundingBox().deflate(0.08D);
        for (int index = 0; index < directions.length; index++) {
            Vec3 direction = directions[(start + index) % directions.length];
            double clearance = 0.0D;
            for (double distance = 2.0D; distance <= 18.0D; distance += 2.0D) {
                if (!level.noCollision(player, body.move(direction.scale(distance)))) break;
                clearance = distance;
            }
            if (clearance > bestClearance) {
                bestClearance = clearance;
                best = direction;
            }
        }
        return best;
    }

    private boolean refreshChasePath(ServerPlayer player) {
        Vec3 observedVelocity = new Vec3(player.getDeltaMovement().x, 0.0D,
                player.getDeltaMovement().z);
        trackedPlayerVelocity = trackedPlayerVelocity.lerp(observedVelocity, 0.28D);
        if (distanceToSqr(player) <= 34.0D * 34.0D && hasLineOfSight(player)) {
            chaseRoute.clear();
            chaseRouteAnchor = player.position();
            Vec3 toPlayer = player.position().subtract(position());
            if (new Vec3(toPlayer.x, 0.0D, toPlayer.z).lengthSqr() < 3.4D * 3.4D) {
                getNavigation().stop();
                chaseRouteTicks = 3;
                return true;
            }
            double leadTicks = Mth.clamp(distanceTo(player) * 0.18D, 1.5D, 5.0D);
            Vec3 pursuit = player.position().add(trackedPlayerVelocity.scale(leadTicks));
            boolean direct = getNavigation().moveTo(pursuit.x, pursuit.y, pursuit.z, 1.0D);
            chaseRouteTicks = direct ? 8 : 2;
            return direct;
        }
        double leadTicks = Mth.clamp(distanceTo(player) * 0.36D, 4.0D, 14.0D);
        Vec3 velocityLead = trackedPlayerVelocity.scale(leadTicks);
        Vec3 predicted = WorldGenerator.nearestMazeCorridor(
                player.getX() + velocityLead.x, player.getZ() + velocityLead.z);
        double cell = AsterionConfig.INSTANCE.cellSize;
        boolean predictionChangedCell = chaseRouteAnchor == null
                || chaseRouteAnchor.distanceToSqr(predicted) > cell * cell * 0.72D;
        double reach = Math.max(2.8D, getBbWidth() * 0.58D);
        while (!chaseRoute.isEmpty() && position().distanceToSqr(chaseRoute.peekFirst()) <= reach * reach)
            chaseRoute.removeFirst();
        if (predictionChangedCell || chaseRoute.isEmpty()) {
            chaseRoute.clear();
            chaseRoute.addAll(WorldGenerator.mazeRoute((ServerLevel) level(), position(), predicted,
                    getBbWidth(), getBbHeight(), 3072));
            chaseRouteAnchor = predicted;
        }
        Vec3 waypoint = routeLookAhead((ServerLevel) level(), chaseRoute, 4);
        boolean routed = waypoint != null
                && getNavigation().moveTo(waypoint.x, waypoint.y, waypoint.z, 1.0D);
        if (!routed && hasLineOfSight(player)) routed = getNavigation().moveTo(player, 1.0D);
        chaseRouteTicks = routed ? 10 : 3;
        return routed;
    }

    private Vec3 routeLookAhead(ServerLevel level, ArrayDeque<Vec3> route, int nodeBudget) {
        Vec3 first = route.peekFirst();
        if (first == null) return null;
        Vec3 best = first;
        AABB body = getBoundingBox().deflate(0.10D).move(0.0D, 0.08D, 0.0D);
        Iterator<Vec3> nodes = route.iterator();
        int inspected = 0;
        while (nodes.hasNext() && inspected++ < nodeBudget) {
            Vec3 candidate = nodes.next();
            Vec3 delta = candidate.subtract(position());
            if (Math.abs(delta.y) > getBbHeight() * 0.45D) break;
            AABB swept = body.expandTowards(delta).inflate(0.08D, 0.0D, 0.08D);
            if (!level.noCollision(this, swept)) break;
            best = candidate;
        }
        return best;
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
        Arrays.fill(bossAttackLockouts, 0);
        setBossStage(BossStage.PILLARS);
        collapseTicks = 0;
        riposteTicks = 0;
        pillarOpportunityTicks = 0;
        grabbedPlayer = null;
        chainGrappleTarget = null;
        punchComboTarget = null;
        punchComboFromChain = false;
        punchStrikeMask = 0;
        wallComboTarget = null;
        wallComboWindow = 0;
        airborneCatchTarget = null;
        airborneCatchWindow = 0;
        wallShoveHit = false;
        leapImpactTick = -1;
        leapImpactOrigin = Vec3.ZERO;
        leapShockwaveHits.clear();
        stompTarget = null;
        stompTargetPosition = Vec3.ZERO;
        stompWasAirborne = false;
        storedArrows = 0;
        arrowReturnTarget = null;
        lightningStrikeTarget = Vec3.ZERO;
        lightningStrikeResolved = false;
        lastBossAttack = BossAttack.NONE;
        attackBeforeLast = BossAttack.NONE;
        getEntityData().set(DATA_REACH_ARM, 0);
        updateChaseSpeed();
        syncBossPartyScaling((ServerLevel)level(), true);
        playSound(SoundEvents.RAVAGER_AMBIENT, 2.4F, 0.38F);
    }

    private void tickBoss(ServerLevel level, ServerPlayer player) {
        if (phaseTicks % 40 == 0) syncBossPartyScaling(level, false);
        if (bossPartySize > 1 && phaseTicks % Math.max(36, 76 - bossPartySize * 8) == 0) {
            ServerPlayer tacticalTarget = level.players().stream()
                    .filter(candidate -> candidate.isAlive() && !candidate.isCreative()
                            && !candidate.isSpectator() && WorldGenerator.isInsideBossArena(candidate.position())
                            && !candidate.getUUID().equals(eclipseTarget))
                    .min(java.util.Comparator.comparingDouble(candidate ->
                            candidate.getHealth() * 0.35D + distanceTo(candidate) * 0.65D))
                    .orElse(null);
            if (tacticalTarget != null && bossAttack == BossAttack.NONE) {
                eclipseTarget = tacticalTarget.getUUID();
                player = tacticalTarget;
            }
        }
        setTarget(player);
        trackedPlayerVelocity = trackedPlayerVelocity.lerp(
                new Vec3(player.getDeltaMovement().x, 0.0D, player.getDeltaMovement().z), 0.24D);
        syncBossBars(level);
        if (bossStunTicks > 0) {
            bossStunTicks--;
            riposteTicks = Math.max(riposteTicks, bossStunTicks + 1);
            setAggressive(false);
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0.08D, 1.0D, 0.08D));
            if ((bossStunTicks & 5) == 0)
                level.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() * 0.72D,
                        getZ(), 5, 0.7D, 0.35D, 0.7D, 0.05D);
            return;
        }
        if (bossStage == BossStage.EXTREME && phaseTicks % 240 == 0) increaseRage(1);
        if (riposteTicks > 0) riposteTicks--;
        if (bossPressureWindowTicks > 0) bossPressureWindowTicks--;
        if (hitReactionCooldown <= 0) bossPressureHits = 0;
        boolean minotaurInside = WorldGenerator.isInsideBossArena(position());
        boolean playerInside = WorldGenerator.isInsideBossArena(player.position());

        if (!minotaurInside) {
            Vec3 approach = WorldGenerator.bossArenaApproach(position());
            if (position().distanceToSqr(approach) > 20.0D) {
                if (getNavigation().isDone() || phaseTicks % 12 == 0)
                    moveByMazeRoute(level, approach, 0.76D, 2048);
            } else {
                getNavigation().stop();
                Vec3 inward = WorldGenerator.bossArenaCenter().subtract(position());
                Vec3 horizontal = new Vec3(inward.x, 0.0D, inward.z).normalize();
                setDeltaMovement(horizontal.scale(0.34D).add(0.0D, getDeltaMovement().y, 0.0D));
            }
            playHeavySteps();
            return;
        }

        if (!playerInside) {
            if (bossStage == BossStage.EXTREME && phaseTicks > 100
                    && horizontalDistanceToArenaCenter(player.position()) < 62.0D
                    && phaseTicks % 16 == 0) containArenaEscape(level, player);
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
        if (bossStage == BossStage.EXTREME && (phaseTicks % 40) == 0) {
            float healing = 0.7F + rage() * 0.11F;
            setHealth(Math.min(getMaxHealth(), getHealth() + healing));
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + getBbHeight() * 0.55D,
                    getZ(), 12, 0.75D, 1.3D, 0.75D, 0.035D);
            // Lightning is now an occasional visible pulse, not the language of every heal.
            if ((phaseTicks % 160) == 0) {
                Vec3 source = new Vec3(AsterionConfig.INSTANCE.deadSunX,
                        AsterionConfig.INSTANCE.deadSunHeight, AsterionConfig.INSTANCE.deadSunZ);
                MazeZapPayload pulse = new MazeZapPayload(getId(), source, Vec3.ZERO, 8);
                for (ServerPlayer viewer : level.players())
                    if (ServerPlayNetworking.canSend(viewer, MazeZapPayload.TYPE))
                        ServerPlayNetworking.send(viewer, pulse);
            }
        }
        if (bossAttack != BossAttack.NONE) {
            tickBossAttack(level, player);
            return;
        }
        if (tickHitBackoff(level, player, true)) return;
        if (bossAttackCooldown > 0) bossAttackCooldown--;
        if (tickPendingCombos(level)) return;
        double distance = distanceTo(player);
        if (attackReady(BossAttack.RAGDOLL_STOMP) && distance <= 24.0D
                && RagdollServerNetworking.isRagdolled(player) && bossAttackCooldown <= 18) {
            beginBossAttack(player, BossAttack.RAGDOLL_STOMP);
            return;
        }
        if (storedArrows > 0 && attackReady(BossAttack.ARROW_RETURN)
                && distance > 5.0D && bossAttackCooldown <= 12) {
            beginBossAttack(player, BossAttack.ARROW_RETURN);
            return;
        }
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
            double bossRadius = Math.sqrt(getX() * getX() + getZ() * getZ());
            boolean pressuredAtWall = bossRadius > 27.0D && distance < 6.5D;
            cornerPressureTicks = pressuredAtWall ? Math.min(60, cornerPressureTicks + 1)
                    : Math.max(0, cornerPressureTicks - 2);
            if ((bossPressureHits >= 4 || cornerPressureTicks >= 24)
                    && (attackReady(BossAttack.ARENA_SWEEP)
                    || distance <= 5.0D && attackReady(BossAttack.HORN_RAM)
                    || distance < 4.4D && attackReady(BossAttack.BACK_KICK))) {
                bossPressureHits = 0;
                cornerPressureTicks = 0;
                BossAttack pressureResponse = distance <= 5.0D && attackReady(BossAttack.HORN_RAM)
                        ? BossAttack.HORN_RAM
                        : distance < 4.4D && attackReady(BossAttack.BACK_KICK)
                        ? BossAttack.BACK_KICK : BossAttack.ARENA_SWEEP;
                beginBossAttack(player, pressureResponse);
                return;
            }
            if (bossStage == BossStage.PILLARS) {
                if (phaseOnePillarTarget != null && pillarOpportunityTicks >= 18) {
                    beginBossAttack(player, BossAttack.CHARGE);
                    Vec3 towardPillar = phaseOnePillarTarget.subtract(position());
                    bossChargeDirection = new Vec3(towardPillar.x, 0.0D, towardPillar.z).normalize();
                    bossChargeTargetsPillar = true;
                    pillarOpportunityTicks = 0;
                    return;
                }
                next = choosePillarAttack(player, distance);
                if (next != BossAttack.NONE) {
                    beginBossAttack(player, next);
                    return;
                }
                if (distance > 4.8D) {
                    Vec3 waypoint = WorldGenerator.bossArenaTacticalWaypoint(position(), player.position());
                    getNavigation().moveTo(waypoint.x, waypoint.y, waypoint.z, 0.72D);
                }
                bossAttackCooldown = 8;
                return;
            } else if (bossStage == BossStage.EXTREME) {
                next = chooseExtremeAttack(player, distance);
            } else next = distance > 8.0D || random.nextInt(3) != 0
                    ? BossAttack.CHARGE : random.nextBoolean() ? BossAttack.CLEAVE : BossAttack.SLAM;
            if (next == BossAttack.NONE) {
                bossAttackCooldown = 6;
                return;
            }
            if (next == lastBossAttack) next = bossStage == BossStage.EXTREME
                    ? (attackReady(BossAttack.SLAM) ? BossAttack.SLAM : BossAttack.CLEAVE)
                    : (distance > 7.0D ? BossAttack.CLEAVE : BossAttack.SLAM);
            beginBossAttack(player, next);
        } else if (distance > 4.5D && (phaseTicks % 8 == 0 || getNavigation().isDone())) {
            Vec3 waypoint = WorldGenerator.bossArenaTacticalWaypoint(position(), player.position());
            getNavigation().moveTo(waypoint.x, waypoint.y, waypoint.z, 0.82D);
        }
        playHeavySteps();
    }

    private boolean tickPendingCombos(ServerLevel level) {
        if (airborneCatchWindow > 0) {
            airborneCatchWindow--;
            Player found = airborneCatchTarget == null ? null : level.getPlayerByUUID(airborneCatchTarget);
            ServerPlayer target = found instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            if (target == null || !target.isAlive() || target.isCreative() || target.isSpectator()) {
                airborneCatchTarget = null;
                airborneCatchWindow = 0;
            } else if (!target.onGround() && target.getY() > getY() + 1.35D) {
                double distance = distanceTo(target);
                if (distance <= 6.3D && attackReady(BossAttack.GRAB)) {
                    bossAttackLockouts[BossAttack.GRAB.ordinal()] = 0;
                    beginBossAttack(target, BossAttack.GRAB);
                    airborneCatchTarget = null;
                    airborneCatchWindow = 0;
                } else if (phaseTicks % 3 == 0 || getNavigation().isDone()) {
                    getNavigation().moveTo(target, 1.28D + rage() * 0.018D);
                }
                return true;
            } else {
                airborneCatchTarget = null;
                airborneCatchWindow = 0;
            }
        }

        if (wallComboWindow <= 0) return false;
        wallComboWindow--;
        Player found = wallComboTarget == null ? null : level.getPlayerByUUID(wallComboTarget);
        ServerPlayer target = found instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        if (target == null || !target.isAlive() || target.isCreative() || target.isSpectator()) {
            wallComboTarget = null;
            wallComboWindow = 0;
            return false;
        }
        boolean atWall = horizontalDistanceToArenaCenter(target.position()) >= 26.0D
                || isPlayerPinned(level, target);
        if (atWall && attackReady(BossAttack.WALL_SHOVE)) {
            bossAttackLockouts[BossAttack.WALL_SHOVE.ordinal()] = 0;
            beginBossAttack(target, BossAttack.WALL_SHOVE);
            wallShoveHit = false;
            return true;
        }
        return false;
    }

    private void scheduleWallCombo(ServerPlayer player, int ticks) {
        wallComboTarget = player.getUUID();
        wallComboWindow = Math.max(wallComboWindow, ticks);
        bossAttackLockouts[BossAttack.WALL_SHOVE.ordinal()] = 0;
    }

    private void scheduleAirCatch(ServerPlayer player, int ticks) {
        airborneCatchTarget = player.getUUID();
        airborneCatchWindow = Math.max(airborneCatchWindow, ticks);
        bossAttackLockouts[BossAttack.GRAB.ordinal()] = 0;
    }

    private void syncBossBars(ServerLevel level) {
        healthBossBar.setVisible(bossStage != BossStage.DEFEATED);
        rageBossBar.setVisible(bossStage != BossStage.DEFEATED);
        float healthProgress = bossStage == BossStage.PILLARS
                ? WorldGenerator.bossPillarsRemaining()
                        / (float)Math.max(1, AsterionConfig.INSTANCE.minotaurBossPillarCount)
                : bossStage == BossStage.COLLAPSE ? 0.0F : getHealth() / getMaxHealth();
        healthBossBar.setProgress(Mth.clamp(healthProgress, 0.0F, 1.0F));
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
        int previous = rage();
        setRage(previous + Math.max(0, amount));
        rageCalmTicks = 360;
        int current = rage();
        if (current > previous && (current == 4 || current == 8 || current == 12)) {
            playSound(current == 12 ? SoundEvents.RAVAGER_ROAR : SoundEvents.RAVAGER_HURT,
                    1.5F + current * 0.10F, 0.70F - current * 0.018F);
            if (level() instanceof ServerLevel level)
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(),
                        getY() + getBbHeight() * 0.78D, getZ(), current / 2,
                        0.65D, 0.45D, 0.65D, 0.02D);
        }
    }

    private void setRage(int value) {
        getEntityData().set(DATA_RAGE, Mth.clamp(value, 0, 12));
        updateChaseSpeed();
    }

    private BossAttack choosePillarAttack(ServerPlayer player, double distance) {
        List<BossAttack> choices = new ArrayList<>();
        switch (combatRange(distance)) {
            case CLOSE -> addReady(choices, BossAttack.GRAB, BossAttack.HORN_RAM,
                    BossAttack.PUNCH_COMBO, BossAttack.BACK_KICK);
            case MEDIUM -> addReady(choices, BossAttack.CHARGE, BossAttack.LEAP,
                    BossAttack.CHAIN_GRAPPLE, BossAttack.FIRE_RINGS, BossAttack.SLAM);
            case FAR -> addReady(choices, BossAttack.RUBBLE_THROW, BossAttack.STAMPEDE,
                    BossAttack.CHAIN_GRAPPLE);
        }
        return pickTacticalAttack(player, choices, distance);
    }

    private BossAttack chooseExtremeAttack(ServerPlayer player, double distance) {
        List<BossAttack> choices = new ArrayList<>();
        switch (combatRange(distance)) {
            case CLOSE -> addReady(choices, BossAttack.GRAB, BossAttack.HORN_RAM,
                    BossAttack.PUNCH_COMBO, BossAttack.SPIN_COMBO, BossAttack.BACK_KICK,
                    BossAttack.WALL_SHOVE);
            case MEDIUM -> {
                addReady(choices, BossAttack.CHARGE, BossAttack.LEAP, BossAttack.CHAIN_GRAPPLE,
                        BossAttack.FIRE_RINGS, BossAttack.ARENA_SWEEP, BossAttack.SLAM);
                if (level() instanceof ServerLevel level && WorldGenerator.activeBossBraziers(level) > 2)
                    addReady(choices, BossAttack.GREEK_FIRE_LASER);
                if (rage() >= 7) addReady(choices, BossAttack.RED_LIGHTNING_CHARGE);
            }
            case FAR -> addReady(choices, BossAttack.RUBBLE_THROW, BossAttack.STAMPEDE,
                    BossAttack.CHAIN_GRAPPLE, BossAttack.RED_LIGHTNING_CHARGE);
        }
        return pickTacticalAttack(player, choices, distance);
    }

    private static CombatRange combatRange(double distance) {
        if (distance <= 5.0D) return CombatRange.CLOSE;
        if (distance <= 20.0D) return CombatRange.MEDIUM;
        return CombatRange.FAR;
    }

    private void addReady(List<BossAttack> choices, BossAttack... attacks) {
        for (BossAttack attack : attacks)
            if (attackReady(attack) && !choices.contains(attack)) choices.add(attack);
    }

    private BossAttack pickTacticalAttack(ServerPlayer player, List<BossAttack> choices, double distance) {
        boolean clearChargeLane = hasClearChargeLane(player);
        choices.removeIf(attack -> attack == BossAttack.CHARGE && !clearChargeLane
                || attack == BossAttack.LEAP && clearChargeLane);
        choices.removeIf(attack -> attack == lastBossAttack || attack == attackBeforeLast);
        if (choices.isEmpty()) return BossAttack.NONE;
        double speed = trackedPlayerVelocity.horizontalDistance();
        double height = player.getY() - getY();
        double arenaRadius = horizontalDistanceToArenaCenter(player.position());
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYHeadRot());
        Vec3 delta = player.position().subtract(position());
        Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z);
        double facing = horizontal.lengthSqr() < 0.01D ? 1.0D
                : horizontal.normalize().dot(new Vec3(forward.x, 0.0D, forward.z).normalize());
        BossAttack best = BossAttack.NONE;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (BossAttack attack : choices) {
            double score = 1.0D + random.nextDouble() * 1.8D;
            score += switch (attack) {
                case PUNCH_COMBO -> distance < 5.8D ? 7.2D - distance : -5.0D;
                case GRAB -> height >= 1.8D && distance <= 6.4D
                        ? 18.0D : distance < 4.8D ? 6.0D - distance : -6.0D;
                case CLEAVE, SPIN_COMBO -> distance < 7.5D ? 5.5D - distance * 0.42D : -2.5D;
                case SLAM -> distance > 3.5D && distance < 10.5D ? 4.2D : -1.0D;
                case HORN_RAM -> distance <= 5.0D
                        ? (bossPressureHits >= 3 || nearbyBossPlayers(6.5D) >= 2 ? 12.0D : 2.2D)
                        : -3.5D;
                case CHAIN_GRAPPLE -> distance > 5.0D ? 3.8D + speed * 8.0D : -2.5D;
                case LEAP -> !clearChargeLane && distance > 5.0D && distance < 24.0D ? 7.0D : -4.0D;
                case RUBBLE_THROW -> distance > 16.0D ? 4.8D : -2.0D;
                case WALL_SHOVE -> arenaRadius > 23.0D ? 6.5D : -2.5D;
                case BACK_KICK -> facing < -0.18D ? 7.0D : -5.0D;
                case ARENA_SWEEP -> arenaRadius > 20.0D ? 4.5D : 1.0D;
                case FIRE_RINGS -> distance > 5.5D && distance < 14.0D ? 3.6D : 0.0D;
                case GREEK_FIRE_LASER -> distance > 7.0D && distance < 20.0D ? 6.2D : -4.0D;
                case CHARGE, STAMPEDE, PAWING, RED_LIGHTNING_CHARGE -> distance > 9.0D ? 3.5D : -2.0D;
                case RAGDOLL_STOMP -> RagdollServerNetworking.isRagdolled(player) ? 12.0D : -20.0D;
                case ARROW_RETURN -> storedArrows > 0 ? 10.0D : -20.0D;
                default -> 0.0D;
            };
            int rageTier = rage() / 4;
            if (attack == BossAttack.PUNCH_COMBO || attack == BossAttack.HORN_RAM
                    || attack == BossAttack.STAMPEDE || attack == BossAttack.CHAIN_GRAPPLE)
                score += rageTier * 0.9D;
            if (height > 1.5D && (attack == BossAttack.LEAP || attack == BossAttack.GRAB)) score += 3.0D;
            if (player.isBlocking()) {
                if (attack == BossAttack.GRAB || attack == BossAttack.WALL_SHOVE) score += 2.6D;
                if (attack == BossAttack.PUNCH_COMBO || attack == BossAttack.CHAIN_GRAPPLE) score -= 2.0D;
            }
            if (facing < 0.25D && (attack == BossAttack.PUNCH_COMBO || attack == BossAttack.CLEAVE)) score -= 1.8D;
            if (score > bestScore) {
                bestScore = score;
                best = attack;
            }
        }
        return best;
    }

    private int nearbyBossPlayers(double radius) {
        if (!(level() instanceof ServerLevel serverLevel)) return 0;
        return serverLevel.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(radius),
                player -> player.isAlive() && !player.isCreative() && !player.isSpectator()).size();
    }

    /** Charge needs an open corridor for the Minotaur's entire body, not merely eye contact. */
    private boolean hasClearChargeLane(Player player) {
        if (!hasLineOfSight(player) || Math.abs(player.getY() - getY()) > 1.75D) return false;
        Vec3 delta = player.position().subtract(position());
        Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z);
        double distance = horizontal.length();
        if (distance < 5.0D) return false;
        Vec3 direction = horizontal.scale(1.0D / distance);
        AABB body = getBoundingBox().deflate(0.16D, 0.10D, 0.16D).move(0.0D, 0.08D, 0.0D);
        double laneLength = Math.max(0.0D, distance - player.getBbWidth() * 0.55D);
        AABB sweptLane = body.expandTowards(direction.scale(laneLength)).inflate(0.10D, 0.0D, 0.10D);
        return level().noCollision(this, sweptLane);
    }

    private boolean attackReady(BossAttack attack) {
        return bossAttackLockouts[attack.ordinal()] <= 0;
    }

    private void beginBossAttack(ServerPlayer player, BossAttack attack) {
        attackBeforeLast = lastBossAttack;
        lastBossAttack = attack;
        setBossAttack(attack);
        bossAttackTicks = 0;
        getEntityData().set(DATA_BOSS_ATTACK_TICKS, 0);
        getNavigation().stop();
        bossAttackLockouts[attack.ordinal()] = switch (attack) {
            case GRAB -> 240;
            case RAGDOLL_STOMP -> 210;
            case ARROW_RETURN -> 180;
            case GREEK_FIRE_LASER -> 220;
            case ARENA_SWEEP, STAMPEDE, HORN_RAM, RED_LIGHTNING_CHARGE, FIRE_RINGS -> 190;
            case LEAP, RUBBLE_THROW, WALL_SHOVE, CHAIN_GRAPPLE -> 145;
            case SPIN_COMBO, SWORD_COMBO, PUNCH_COMBO -> 125;
            case CHARGE, SLAM -> 95;
            default -> 72;
        };
        if (attack == BossAttack.GRAB && bossPartySize > 1)
            bossAttackLockouts[attack.ordinal()] -= Math.min(70, (bossPartySize - 1) * 18);
        if (attack == BossAttack.WALL_SHOVE) {
            wallComboTarget = player.getUUID();
            wallShoveHit = false;
        }
        if (attack == BossAttack.CHARGE || attack == BossAttack.RED_LIGHTNING_CHARGE
                || attack == BossAttack.STAMPEDE || attack == BossAttack.PAWING
                || attack == BossAttack.HORN_RAM) {
            Vec3 delta = player.position().subtract(position());
            Vec3 lead = delta.add(player.getDeltaMovement().multiply(7.0D, 0.0D, 7.0D));
            bossChargeDirection = new Vec3(lead.x, 0.0D, lead.z).normalize();
            if (attack == BossAttack.CHARGE)
                getEntityData().set(DATA_CHARGE_WINDUP, 30 + random.nextInt(13));
            bossChargeTargetsPillar = false;
            if (level() instanceof ServerLevel level)
                sendBossTelegraph(level, position(), bossChargeDirection,
                        attack == BossAttack.STAMPEDE || attack == BossAttack.HORN_RAM ? 34.0F : 27.0F,
                        attack == BossAttack.CHARGE ? getEntityData().get(DATA_CHARGE_WINDUP)
                                : attack == BossAttack.RED_LIGHTNING_CHARGE ? 32
                                : attack == BossAttack.STAMPEDE || attack == BossAttack.HORN_RAM ? 30 : 20,
                        BossTelegraphPayload.CHARGE_LANE);
            if (attack == BossAttack.RED_LIGHTNING_CHARGE && level() instanceof ServerLevel level) {
                lightningStrikeTarget = WorldGenerator.clampBossArena(player.position()
                        .add(trackedPlayerVelocity.scale(11.0D)));
                lightningStrikeResolved = false;
                DeadSunStrikePayload strike = new DeadSunStrikePayload(
                        BlockPos.containing(lightningStrikeTarget), 30, 3.25F, random.nextLong());
                for (ServerPlayer viewer : level.players())
                    if (ServerPlayNetworking.canSend(viewer, DeadSunStrikePayload.TYPE))
                        ServerPlayNetworking.send(viewer, strike);
                playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 3.8F, 0.48F);
            } else playSound(SoundEvents.GOAT_PREPARE_RAM, attack == BossAttack.PAWING ? 3.2F : 2.8F,
                    attack == BossAttack.PAWING ? 0.31F : 0.38F);
        } else if (attack == BossAttack.LEAP) {
            bossLeapTarget = WorldGenerator.clampBossArena(player.position()
                    .add(player.getDeltaMovement().multiply(10.0D, 0.0D, 10.0D)));
            bossWasAirborne = false;
            leapImpactTick = -1;
            leapImpactOrigin = Vec3.ZERO;
            leapShockwaveHits.clear();
            if (level() instanceof ServerLevel level)
                sendBossTelegraph(level, bossLeapTarget, Vec3.ZERO, 7.5F, 26,
                        BossTelegraphPayload.TARGET_CIRCLE);
            playRoar(2.7F, 1.08F, 0.62F);
        } else if (attack == BossAttack.SLAM) {
            bossWasAirborne = false;
            if (level() instanceof ServerLevel level)
                sendBossTelegraph(level, position(), Vec3.ZERO, 8.0F, 24,
                        BossTelegraphPayload.TARGET_CIRCLE);
            playSound(SoundEvents.WARDEN_SONIC_CHARGE, 2.3F, 0.55F);
        } else if (attack == BossAttack.GRAB) {
            grabbedPlayer = null;
            clearLockedReach();
            grabThrowStyle = GrabThrowStyle.ARENA;
            getEntityData().set(DATA_GRAB_TARGET_ID, player.getId());
            chooseReachArm(player);
            if (level() instanceof ServerLevel level)
                sendBossTelegraph(level, position(), Vec3.ZERO, 5.2F, 13,
                        BossTelegraphPayload.TARGET_CIRCLE);
            playSound(SoundEvents.RAVAGER_AMBIENT, 2.0F, 0.55F);
        } else if (attack == BossAttack.CHAIN_GRAPPLE) {
            chainGrappleTarget = player.getUUID();
            getEntityData().set(DATA_GRAB_TARGET_ID, player.getId());
            clearLockedReach();
            chooseReachArm(player);
            if (level() instanceof ServerLevel level)
                sendBossTelegraph(level, position(), player.position().subtract(position()),
                        5.0F, 18, BossTelegraphPayload.FRONT_CONE);
            playSound(SoundEvents.CHAIN_PLACE, 2.6F, 0.58F);
        } else if (attack == BossAttack.GREEK_FIRE_LASER) {
            greekFireAim = player.getEyePosition().subtract(getEyePosition()).normalize();
            setGlowingTag(true);
            playSound(SoundEvents.BEACON_POWER_SELECT, 3.0F, 0.42F);
        } else if (attack == BossAttack.PUNCH_COMBO) {
            punchComboTarget = player.getUUID();
            punchComboFromChain = false;
            punchStrikeMask = 0;
            if (level() instanceof ServerLevel level)
                sendBossTelegraph(level, position(), player.position().subtract(position()),
                        5.8F, 12, BossTelegraphPayload.FRONT_CONE);
            playSound(SoundEvents.RAVAGER_ATTACK, 2.2F, 0.68F);
        } else if (attack == BossAttack.RAGDOLL_STOMP) {
            stompTarget = player.getUUID();
            stompTargetPosition = WorldGenerator.clampBossArena(player.position()
                    .add(player.getDeltaMovement().multiply(4.0D, 0.0D, 4.0D)));
            stompWasAirborne = false;
            if (level() instanceof ServerLevel level)
                sendBossTelegraph(level, stompTargetPosition, Vec3.ZERO, 4.2F, 34,
                        BossTelegraphPayload.TARGET_CIRCLE);
            playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.48F);
        } else if (attack == BossAttack.ARROW_RETURN) {
            arrowReturnTarget = player.getUUID();
            if (level() instanceof ServerLevel level)
                sendBossTelegraph(level, position(), player.position().subtract(position()),
                        28.0F, 22, BossTelegraphPayload.FRONT_CONE);
            playSound(SoundEvents.CROSSBOW_LOADING_END.value(), 2.4F, 0.62F);
        } else if (attack == BossAttack.ARENA_SWEEP && level() instanceof ServerLevel level) {
            Vec3 delta = player.position().subtract(position());
            bossChargeDirection = new Vec3(delta.x, 0.0D, delta.z).normalize();
            sendBossTelegraph(level, position(), bossChargeDirection, 31.0F, 38,
                    BossTelegraphPayload.HALF_ARENA_SWEEP);
            playSound(SoundEvents.WARDEN_SONIC_CHARGE, 3.0F, 0.38F);
        } else if (attack == BossAttack.RUBBLE_THROW) {
            bossLeapTarget = WorldGenerator.clampBossArena(player.position()
                    .add(player.getDeltaMovement().multiply(8.0D, 0.0D, 8.0D)));
            if (level() instanceof ServerLevel level)
                sendBossTelegraph(level, bossLeapTarget, Vec3.ZERO, 4.8F, 38,
                        BossTelegraphPayload.TARGET_CIRCLE);
            playSound(SoundEvents.STONE_BREAK, 3.0F, 0.55F);
        } else {
            if (level() instanceof ServerLevel level) {
                Vec3 facing = player.position().subtract(position());
                facing = new Vec3(facing.x, 0.0D, facing.z).normalize();
                int kind = attack == BossAttack.SPIN_COMBO ? BossTelegraphPayload.TARGET_CIRCLE
                        : BossTelegraphPayload.FRONT_CONE;
                sendBossTelegraph(level, position(), facing,
                        attack == BossAttack.SPIN_COMBO ? 6.5F : 6.0F, 15, kind);
            }
            playSound(SoundEvents.RAVAGER_ATTACK, 2.2F, 0.52F);
        }
    }

    private void sendBossTelegraph(ServerLevel level, Vec3 center, Vec3 direction,
                                   float radius, int duration, int kind) {
        BossTelegraphPayload telegraph = new BossTelegraphPayload(center, direction, radius, duration, kind);
        for (ServerPlayer viewer : level.players())
            if (ServerPlayNetworking.canSend(viewer, BossTelegraphPayload.TYPE))
                ServerPlayNetworking.send(viewer, telegraph);
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
                    armHeavyJump();
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
                int windupTicks = getEntityData().get(DATA_CHARGE_WINDUP);
                if (bossAttackTicks <= windupTicks) {
                    // Do not finish a long-range decision after the player has already closed into
                    // grab/punch range.  Re-evaluate before the charge is committed.
                    if (!bossChargeTargetsPillar && bossAttackTicks >= 6 && distanceTo(player) < 6.25D) {
                        BossAttack closeAttack = attackReady(BossAttack.PUNCH_COMBO)
                                ? BossAttack.PUNCH_COMBO
                                : attackReady(BossAttack.GRAB) ? BossAttack.GRAB
                                : attackReady(BossAttack.HORN_RAM) ? BossAttack.HORN_RAM : BossAttack.NONE;
                        if (closeAttack != BossAttack.NONE) {
                            beginBossAttack(player, closeAttack);
                            return;
                        }
                    }
                    setDeltaMovement(getDeltaMovement().multiply(0.08D, 1.0D, 0.08D));
                    getLookControl().setLookAt(player, 12.0F, 6.0F);
                    Vec3 aim = player.position().subtract(position());
                    Vec3 horizontal = new Vec3(aim.x, 0.0D, aim.z);
                    if (horizontal.lengthSqr() > 0.01D)
                        bossChargeDirection = bossChargeDirection.lerp(horizontal.normalize(), 0.11D).normalize();
                    if (bossAttackTicks >= 8 && (bossAttackTicks & 3) == 0) {
                        Vec3 right = new Vec3(-bossChargeDirection.z, 0.0D, bossChargeDirection.x);
                        double side = (bossAttackTicks & 4) == 0 ? -0.92D : 0.92D;
                        Vec3 hoof = position().add(bossChargeDirection.scale(1.38D)).add(right.scale(side));
                        level.sendParticles(ParticleTypes.DUST_PLUME, hoof.x, getY() + 0.10D, hoof.z,
                                10, 0.42D, 0.08D, 0.42D, 0.05D);
                        playSound(SoundEvents.RAVAGER_STEP, 2.35F,
                                0.32F + bossAttackTicks / (float)Math.max(1, windupTicks) * 0.10F);
                    }
                    if (bossAttackTicks == windupTicks && !bossChargeTargetsPillar
                            && !hasClearChargeLane(player)) {
                        finishBossAttack(18);
                        return;
                    }
                } else {
                    int runTicks = bossAttackTicks - windupTicks;
                    double acceleration = smootherStep(Mth.clamp(runTicks / 34.0D, 0.0D, 1.0D));
                    double minimumSpeed = 0.38D;
                    double maximumSpeed = 1.62D + rage() * 0.016D;
                    double speed = Mth.lerp(acceleration, minimumSpeed, maximumSpeed);
                    setDeltaMovement(bossChargeDirection.x * speed, getDeltaMovement().y,
                            bossChargeDirection.z * speed);
                    float yaw = (float)(Mth.atan2(bossChargeDirection.z, bossChargeDirection.x)
                            * Mth.RAD_TO_DEG) - 90.0F;
                    setYRot(yaw);
                    yBodyRot = yaw;
                    yHeadRot = yaw;
                    if ((bossAttackTicks & 1) == 0)
                        level.sendParticles(ParticleTypes.DUST_PLUME, getX(), getY() + 0.15D, getZ(),
                                3 + Mth.ceil(acceleration * 7.0D), 0.45D + acceleration * 0.55D,
                                0.12D, 0.45D + acceleration * 0.55D, 0.025D + acceleration * 0.035D);
                    AABB impact = getBoundingBox().expandTowards(bossChargeDirection.scale(1.8D))
                            .inflate(0.35D, 0.25D, 0.35D);
                    WorldGenerator.clearLowBossChargeObstacle(level, impact);
                    if (bossStage == BossStage.PILLARS && WorldGenerator.breakBossPillar(level, impact)) {
                        increaseRage(1);
                        applyBossCollisionDamage(level, true);
                        WorldGenerator.scarBossArena(level, position(), 4);
                        setDeltaMovement(bossChargeDirection.scale(-0.18D).add(0, 0.16D, 0));
                        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1.0D, getZ(),
                                8, 1.0D, 1.4D, 1.0D, 0.04D);
                        riposteTicks = 24;
                        bossStunTicks = 52;
                        finishBossAttack(46);
                        return;
                    }
                    int smashed = WorldGenerator.breakBossArenaWallAround(level, impact, this);
                    if (smashed > 0) {
                        applyBossCollisionDamage(level, false);
                        recoverFromArenaImpact();
                        setDeltaMovement(bossChargeDirection.scale(-0.22D).add(0.0D, 0.14D, 0.0D));
                        WorldGenerator.scarBossArena(level, position(), 4);
                        playSound(SoundEvents.RAVAGER_ATTACK, 2.5F, 0.42F);
                        riposteTicks = 28;
                        bossStunTicks = 58;
                        finishBossAttack(44);
                        return;
                    }
                    if (horizontalCollision) {
                        if (WorldGenerator.clearLowBossChargeObstacle(level, impact) > 0) return;
                        applyBossCollisionDamage(level, false);
                        recoverFromArenaImpact();
                        setDeltaMovement(bossChargeDirection.scale(-0.24D).add(0.0D, 0.16D, 0.0D));
                        riposteTicks = 30;
                        bossStunTicks = 58;
                        finishBossAttack(44);
                        return;
                    }
                    if (attackCooldown <= 0 && getBoundingBox().inflate(0.8D).intersects(player.getBoundingBox())) {
                        float damage = (float)Mth.lerp(acceleration, 6.0D, 15.0D);
                        // Calibrated to roughly seven blocks at the base, then scaled by momentum.
                        double knockback = Mth.lerp(acceleration, 1.05D, 2.65D);
                        if (player.hurtServer(level, damageSources().mobAttack(this), damage))
                            ragdollPlayer(player, bossChargeDirection.scale(knockback)
                                    .add(0.0D, 0.24D + acceleration * 0.34D, 0.0D),
                                    (float)(1.05D + acceleration * 0.60D));
                        scheduleWallCombo(player, 120);
                        attackCooldown = 18;
                        finishBossAttack(40);
                        return;
                    }
                    if (runTicks >= 68) finishBossAttack(36);
                }
            }
            case RED_LIGHTNING_CHARGE -> tickRedLightningCharge(level, player);
            case PAWING -> tickPawing(level, player);
            case STAMPEDE -> tickStampede(level, player);
            case HORN_RAM -> tickHornRam(level, player);
            case BACK_KICK -> tickBackKick(level);
            case ARENA_SWEEP -> tickArenaSweep(level);
            case RUBBLE_THROW -> tickRubbleThrow(level, player);
            case WALL_SHOVE -> tickWallShove(level, player);
            case FIRE_RINGS -> tickFireRings(level);
            case CHAIN_GRAPPLE -> tickChainGrapple(level);
            case PUNCH_COMBO -> tickPunchCombo(level, player);
            case RAGDOLL_STOMP -> tickRagdollStomp(level, player);
            case ARROW_RETURN -> tickArrowReturn(level, player);
            case GREEK_FIRE_LASER -> tickGreekFireLaser(level, player);
            case LEAP -> tickLeapAttack(level, player);
            case SWORD_COMBO -> {
                if (bossAttackTicks == 14 || bossAttackTicks == 27)
                    performSwordArc(level, bossAttackTicks == 27 ? 17.0F : 12.0F,
                            bossAttackTicks == 27 ? 1.85D : 1.25D);
                if (bossAttackTicks >= 42) finishBossAttack(36);
            }
            case SPIN_COMBO -> {
                if (bossAttackTicks == 19) performSpin(level);
                if (bossAttackTicks >= 36) finishBossAttack(46);
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
            playRoar(4.0F, 0.68F, 1.05F);
        }
    }

    private void tickStampede(ServerLevel level, ServerPlayer player) {
        if (bossAttackTicks <= 30) {
            setDeltaMovement(getDeltaMovement().multiply(0.08D, 1.0D, 0.08D));
            getLookControl().setLookAt(player, 10.0F, 6.0F);
            if ((bossAttackTicks & 3) == 0) {
                Vec3 right = new Vec3(-bossChargeDirection.z, 0.0D, bossChargeDirection.x);
                double side = (bossAttackTicks & 4) == 0 ? -1.0D : 1.0D;
                Vec3 hoof = position().add(bossChargeDirection.scale(1.4D)).add(right.scale(side));
                level.sendParticles(ParticleTypes.DUST_PLUME, hoof.x, getY() + 0.1D, hoof.z,
                        10, 0.45D, 0.08D, 0.45D, 0.05D);
                playSound(SoundEvents.RAVAGER_STEP, 2.4F,
                        0.36F + bossAttackTicks / 300.0F);
            }
            if (bossAttackTicks == 30) playRoar(4.0F, 0.68F, 1.05F);
            return;
        }
        int runTicks = bossAttackTicks - 30;
        if (runTicks <= 58) {
            if (runTicks < 12) {
                Vec3 desired = player.position().subtract(position());
                desired = new Vec3(desired.x, 0.0D, desired.z).normalize();
                bossChargeDirection = bossChargeDirection.lerp(desired, 0.035D).normalize();
            }
            double speed = 1.12D + rage() * 0.018D;
            setDeltaMovement(bossChargeDirection.x * speed, getDeltaMovement().y,
                    bossChargeDirection.z * speed);
            AABB impact = getBoundingBox().expandTowards(bossChargeDirection.scale(2.5D)).inflate(0.7D);
            WorldGenerator.clearLowBossChargeObstacle(level, impact);
            int smashed = WorldGenerator.breakBossArenaWallAround(level, impact, this);
            if (smashed > 0) {
                applyBossCollisionDamage(level, false);
                recoverFromArenaImpact();
                WorldGenerator.scarBossArena(level, position(), 4);
                level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.8D, getZ(),
                        5, 1.1D, 0.8D, 1.1D, 0.08D);
                riposteTicks = 34;
                bossStunTicks = 62;
                finishBossAttack(58);
                return;
            }
            if (attackCooldown <= 0 && impact.intersects(player.getBoundingBox())) {
                if (player.hurtServer(level, damageSources().mobAttack(this), 22.0F))
                    ragdollPlayer(player, bossChargeDirection.scale(3.7D).add(0.0D, 0.65D, 0.0D), 1.75F);
                attackCooldown = 30;
            }
            if ((bossAttackTicks & 1) == 0)
                level.sendParticles(ParticleTypes.DUST_PLUME, getX(), getY() + 0.1D, getZ(),
                        10, 1.0D, 0.12D, 1.0D, 0.06D);
        }
        if (bossAttackTicks >= 100) finishBossAttack(66);
    }

    private void tickHornRam(ServerLevel level, ServerPlayer player) {
        if (bossAttackTicks <= 28) {
            setDeltaMovement(getDeltaMovement().multiply(0.08D, 1.0D, 0.08D));
            getLookControl().setLookAt(player, 10.0F, 5.0F);
            Vec3 lead = player.position().add(trackedPlayerVelocity.scale(7.0D)).subtract(position());
            Vec3 desired = new Vec3(lead.x, 0.0D, lead.z);
            if (desired.lengthSqr() > 0.01D)
                bossChargeDirection = bossChargeDirection.lerp(desired.normalize(), 0.08D).normalize();
            if (bossAttackTicks >= 7 && (bossAttackTicks & 3) == 0) {
                Vec3 right = new Vec3(-bossChargeDirection.z, 0.0D, bossChargeDirection.x);
                double side = (bossAttackTicks & 4) == 0 ? -0.95D : 0.95D;
                Vec3 hoof = position().add(bossChargeDirection.scale(1.35D)).add(right.scale(side));
                level.sendParticles(ParticleTypes.DUST_PLUME, hoof.x, getY() + 0.08D, hoof.z,
                        12, 0.45D, 0.08D, 0.45D, 0.05D);
                playSound(SoundEvents.RAVAGER_STEP, 2.5F, 0.34F + bossAttackTicks * 0.004F);
            }
            if (bossAttackTicks == 24) playRoar(3.8F, 0.70F, 0.9F);
            return;
        }

        int runTicks = bossAttackTicks - 28;
        if (runTicks <= 62) {
            if (runTicks <= 9) {
                Vec3 desired = player.position().add(trackedPlayerVelocity.scale(3.5D)).subtract(position());
                desired = new Vec3(desired.x, 0.0D, desired.z);
                if (desired.lengthSqr() > 0.01D)
                    bossChargeDirection = bossChargeDirection.lerp(desired.normalize(), 0.026D).normalize();
            }
            double speed = 1.02D + rage() * 0.017D;
            setDeltaMovement(bossChargeDirection.x * speed, getDeltaMovement().y,
                    bossChargeDirection.z * speed);
            float yaw = (float)(Mth.atan2(bossChargeDirection.z, bossChargeDirection.x)
                    * Mth.RAD_TO_DEG) - 90.0F;
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
            AABB horns = getBoundingBox().expandTowards(bossChargeDirection.scale(2.6D))
                    .inflate(0.65D, 0.35D, 0.65D);
            WorldGenerator.clearLowBossChargeObstacle(level, horns);
            int smashed = WorldGenerator.breakBossArenaWallAround(level, horns, this);
            if (smashed > 0 || horizontalCollision) {
                applyBossCollisionDamage(level, false);
                recoverFromArenaImpact();
                setDeltaMovement(bossChargeDirection.scale(-0.28D).add(0.0D, 0.18D, 0.0D));
                WorldGenerator.scarBossArena(level, position(), 4);
                bossStunTicks = 66;
                riposteTicks = 66;
                playSound(SoundEvents.GENERIC_EXPLODE.value(), 3.2F, 0.40F);
                finishBossAttack(58);
                return;
            }
            for (ServerPlayer victim : level.getEntitiesOfClass(ServerPlayer.class, horns)) {
                if (!victim.isAlive() || victim.isCreative() || victim.isSpectator()) continue;
                if (victim.isBlocking()) {
                    victim.setDeltaMovement(bossChargeDirection.scale(1.05D).add(0.0D, 0.18D, 0.0D));
                    victim.hurtMarked = true;
                    playSound(SoundEvents.SHIELD_BLOCK.value(), 3.0F, 0.58F);
                    bossStunTicks = 32;
                    riposteTicks = 42;
                    finishBossAttack(54);
                    return;
                }
                if (victim.hurtServer(level, damageSources().mobAttack(this), 7.0F)) {
                    double knockback = 0.72D + random.nextDouble() * 0.30D;
                    ragdollPlayer(victim, bossChargeDirection.scale(knockback).add(0.0D, 0.34D, 0.0D),
                            1.35F, true);
                    level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + 1.0D,
                            victim.getZ(), 22, 0.7D, 0.8D, 0.7D, 0.15D);
                }
                riposteTicks = 34;
                finishBossAttack(62);
                return;
            }
            if ((bossAttackTicks & 1) == 0)
                level.sendParticles(ParticleTypes.DUST_PLUME, getX(), getY() + 0.08D, getZ(),
                        10, 0.9D, 0.1D, 0.9D, 0.055D);
        }
        if (bossAttackTicks >= 96) {
            riposteTicks = 48;
            finishBossAttack(58);
        }
    }

    private void tickRagdollStomp(ServerLevel level, ServerPlayer fallback) {
        Player found = stompTarget == null ? null : level.getPlayerByUUID(stompTarget);
        ServerPlayer target = found instanceof ServerPlayer serverPlayer ? serverPlayer : fallback;
        if (target != null && target.isAlive() && RagdollServerNetworking.isRagdolled(target)
                && bossAttackTicks <= 15)
            stompTargetPosition = WorldGenerator.clampBossArena(target.position()
                    .add(target.getDeltaMovement().multiply(2.0D, 0.0D, 2.0D)));

        if (bossAttackTicks <= 18) {
            Vec3 delta = stompTargetPosition.subtract(position());
            Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z);
            if (horizontal.lengthSqr() > 0.04D) {
                Vec3 direction = horizontal.normalize();
                double speed = 0.48D + rage() * 0.018D;
                setDeltaMovement(direction.x * speed, getDeltaMovement().y, direction.z * speed);
                getLookControl().setLookAt(stompTargetPosition.x, stompTargetPosition.y,
                        stompTargetPosition.z, 16.0F, 8.0F);
                WorldGenerator.clearLowBossChargeObstacle(level,
                        getBoundingBox().expandTowards(direction.scale(1.6D)).inflate(0.25D));
            }
            if ((bossAttackTicks & 3) == 0)
                level.sendParticles(ParticleTypes.DUST_PLUME, getX(), getY() + 0.08D, getZ(),
                        8, 0.7D, 0.08D, 0.7D, 0.045D);
            return;
        }
        if (bossAttackTicks == 19) {
            Vec3 delta = stompTargetPosition.subtract(position());
            Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z);
            if (horizontal.lengthSqr() < 0.01D) horizontal = Vec3.directionFromRotation(0.0F, getYRot());
            horizontal = horizontal.normalize();
            setDeltaMovement(horizontal.x * 0.52D, 0.78D, horizontal.z * 0.52D);
            hurtMarked = true;
            stompWasAirborne = true;
            armHeavyJump();
            playSound(SoundEvents.GOAT_LONG_JUMP, 2.8F, 0.46F);
            return;
        }
        if (stompWasAirborne && bossAttackTicks > 22 && onGround()) {
            stompWasAirborne = false;
            AABB impact = getBoundingBox().inflate(2.25D, 0.45D, 2.25D).move(0.0D, -0.18D, 0.0D);
            boolean hit = false;
            for (ServerPlayer victim : level.getEntitiesOfClass(ServerPlayer.class, impact)) {
                if (!victim.isAlive() || victim.isCreative() || victim.isSpectator()) continue;
                hit = true;
                if (victim.hurtServer(level, damageSources().mobAttack(this), 17.0F + rage() * 0.22F)) {
                    Vec3 away = victim.position().subtract(position());
                    Vec3 horizontal = new Vec3(away.x, 0.0D, away.z);
                    if (horizontal.lengthSqr() < 0.01D) horizontal = new Vec3(0.0D, 0.0D, 1.0D);
                    ragdollPlayer(victim, horizontal.normalize().scale(0.85D).add(0.0D, 0.62D, 0.0D),
                            1.55F, true);
                }
            }
            WorldGenerator.scarBossArena(level, position(), 5);
            level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.1D, getZ(),
                    9, 2.2D, 0.22D, 2.2D, 0.05D);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 3.2F, 0.42F);
            riposteTicks = hit ? 28 : 50;
            finishBossAttack(hit ? 54 : 44);
            return;
        }
        if (bossAttackTicks >= 48) {
            riposteTicks = 52;
            finishBossAttack(48);
        }
    }

    private void tickArrowReturn(ServerLevel level, ServerPlayer fallback) {
        Player found = arrowReturnTarget == null ? null : level.getPlayerByUUID(arrowReturnTarget);
        ServerPlayer target = found instanceof ServerPlayer serverPlayer ? serverPlayer : fallback;
        setDeltaMovement(getDeltaMovement().multiply(0.08D, 1.0D, 0.08D));
        if (target != null) getLookControl().setLookAt(target, 14.0F, 9.0F);
        if (bossAttackTicks >= 6 && bossAttackTicks < 20 && (bossAttackTicks & 2) == 0)
            level.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() * 0.68D,
                    getZ(), 3, 0.8D, 0.8D, 0.8D, 0.04D);
        if (bossAttackTicks == 20 && target != null && target.isAlive()) {
            int count = Mth.clamp(storedArrows, 1, 7);
            Vec3 targetCenter = target.position().add(0.0D, target.getBbHeight() * 0.52D, 0.0D)
                    .add(trackedPlayerVelocity.scale(6.0D));
            Vec3 right = new Vec3(-getLookAngle().z, 0.0D, getLookAngle().x).normalize();
            for (int index = 0; index < count; index++) {
                double offset = (index - (count - 1) * 0.5D) * 0.34D;
                Vec3 origin = getEyePosition().add(right.scale(offset)).add(0.0D, -0.35D, 0.0D);
                Arrow arrow = new Arrow(level, this, new ItemStack(Items.ARROW), ItemStack.EMPTY);
                arrow.setPos(origin);
                arrow.setBaseDamage(4.0D + rage() * 0.12D);
                arrow.setCritArrow(rage() >= 8);
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                Vec3 shot = targetCenter.subtract(origin);
                arrow.shoot(shot.x, shot.y, shot.z, 2.55F + rage() * 0.025F, 2.2F);
                level.addFreshEntity(arrow);
            }
            storedArrows = 0;
            playSound(SoundEvents.CROSSBOW_SHOOT, 3.0F, 0.52F);
        }
        if (bossAttackTicks >= 38) {
            riposteTicks = 32;
            finishBossAttack(52);
        }
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
                if (victim.hurtServer(level, damageSources().mobAttack(this), 16.0F))
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
                boolean damaged = victim.hurtServer(level, damageSources().mobAttack(this), 17.0F);
                Vec3 tangent = new Vec3(-bossChargeDirection.z, 0.0D, bossChargeDirection.x);
                if (damaged) ragdollPlayer(victim, tangent.scale(3.0D).add(0.0D, 0.7D, 0.0D), 1.55F);
            }
            WorldGenerator.scarBossArena(level, position().add(bossChargeDirection.scale(12.0D)), 12);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 4.0F, 0.52F);
        }
        if (bossAttackTicks >= 52) finishBossAttack(58);
    }

    private void tickChainGrapple(ServerLevel level) {
        Player found = chainGrappleTarget == null ? null : level.getPlayerByUUID(chainGrappleTarget);
        ServerPlayer target = found instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        if (target == null || !target.isAlive() || !hasLineOfSight(target)) {
            finishBossAttack(42);
            return;
        }
        setDeltaMovement(getDeltaMovement().multiply(0.06D, 1.0D, 0.06D));
        Vec3 hand = reachHandPosition(target, 0.20D, 0.0D, 0.05D);
        Vec3 chain = target.getEyePosition().subtract(hand);
        int links = Mth.clamp(Mth.ceil(chain.length() * 1.15D), 2, 72);
        if ((bossAttackTicks & 1) == 0) {
            for (int link = 0; link <= links; link++) {
                Vec3 point = hand.add(chain.scale(link / (double)links));
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_CHAIN.defaultBlockState()),
                        point.x, point.y, point.z, 1, 0.015D, 0.015D, 0.015D, 0.0D);
            }
        }
        if (bossAttackTicks >= 10 && bossAttackTicks <= 90 && (bossAttackTicks - 10) % 9 == 0) {
            Vec3 pull = hand.subtract(target.position());
            Vec3 horizontal = new Vec3(pull.x, 0.0D, pull.z);
            if (horizontal.lengthSqr() > 0.01D) {
                double strength = bossAttackTicks == 10
                        ? Mth.clamp(horizontal.length() * 0.13D, 1.65D, 3.60D)
                        : Mth.clamp(horizontal.length() * 0.085D, 0.95D, 2.15D);
                horizontal = horizontal.normalize().scale(strength);
                target.setDeltaMovement(horizontal.x, Mth.clamp(pull.y * 0.10D + 0.24D, 0.18D, 0.62D),
                        horizontal.z);
                target.hurtMarked = true;
                target.resetFallDistance();
                playSound(SoundEvents.CHAIN_HIT, 3.2F, bossAttackTicks == 10 ? 0.48F : 0.58F);
                level.sendParticles(ParticleTypes.DUST_PLUME, target.getX(), target.getY() + 0.3D,
                        target.getZ(), 12, 0.55D, 0.16D, 0.55D, 0.06D);
            }
        }
        if (bossAttackTicks >= 11 && distanceTo(target) <= 6.0D) {
            chainGrappleTarget = null;
            punchComboTarget = target.getUUID();
            punchComboFromChain = true;
            punchStrikeMask = 0;
            getEntityData().set(DATA_REACH_ARM, 0);
            getEntityData().set(DATA_GRAB_TARGET_ID, target.getId());
            setBossAttack(BossAttack.PUNCH_COMBO);
            bossAttackTicks = 0;
            getEntityData().set(DATA_BOSS_ATTACK_TICKS, 0);
            bossAttackLockouts[BossAttack.PUNCH_COMBO.ordinal()] = 125;
            sendBossTelegraph(level, position(), target.position().subtract(position()),
                    5.8F, 11, BossTelegraphPayload.FRONT_CONE);
            playSound(SoundEvents.CHAIN_HIT, 2.8F, 0.48F);
            return;
        }
        if (bossAttackTicks >= 180) finishBossAttack(48);
    }

    private void tickGreekFireLaser(ServerLevel level, ServerPlayer player) {
        setDeltaMovement(getDeltaMovement().multiply(0.04D, 1.0D, 0.04D));
        Vec3 origin = getEyePosition().add(0.0D, -0.25D, 0.0D);
        Vec3 desired = player.getEyePosition().subtract(origin).normalize();
        greekFireAim = greekFireAim.lerp(desired, bossAttackTicks < 24 ? 0.08D : 0.025D).normalize();
        if (bossAttackTicks >= 24 && bossAttackTicks <= 92) {
            for (int step = 1; step <= 48; step++) {
                Vec3 point = origin.add(greekFireAim.scale(step * 0.62D));
                level.sendParticles(step % 3 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                        point.x, point.y, point.z, 1, 0.025D, 0.025D, 0.025D, 0.0D);
            }
            Vec3 toPlayer = player.getEyePosition().subtract(origin);
            double along = Mth.clamp(toPlayer.dot(greekFireAim), 0.0D, 30.0D);
            double miss = player.getEyePosition().distanceTo(origin.add(greekFireAim.scale(along)));
            if (miss <= 1.15D && bossAttackTicks % 10 == 0) {
                player.hurtServer(level, damageSources().magic(), 5.0F);
                player.igniteForSeconds(2.0F);
            }
        }
        if (bossAttackTicks >= 108) finishBossAttack(66);
    }

    private void tickPunchCombo(ServerLevel level, ServerPlayer fallback) {
        Player found = punchComboTarget == null ? null : level.getPlayerByUUID(punchComboTarget);
        ServerPlayer target = found instanceof ServerPlayer serverPlayer ? serverPlayer : fallback;
        if (target == null || !target.isAlive() || target.isCreative() || target.isSpectator()) {
            finishBossAttack(42);
            return;
        }
        setDeltaMovement(getDeltaMovement().multiply(0.08D, 1.0D, 0.08D));
        if (bossAttackTicks <= 8 || bossAttackTicks >= 12 && bossAttackTicks <= 17
                || bossAttackTicks >= 20 && bossAttackTicks <= 30) {
            getLookControl().setLookAt(target, 15.0F, 9.0F);
            Vec3 approach = target.position().subtract(position());
            Vec3 horizontal = new Vec3(approach.x, 0.0D, approach.z);
            if (horizontal.length() > 2.6D && horizontal.lengthSqr() > 0.01D) {
                double step = bossAttackTicks < 9 ? 0.34D : 0.26D + rage() * 0.008D;
                setDeltaMovement(horizontal.normalize().scale(step).add(0.0D, getDeltaMovement().y, 0.0D));
            }
        }
        if (bossAttackTicks >= 7 && bossAttackTicks <= 9 && (punchStrikeMask & 1) == 0
                && performPunchStrike(level, target, 0)) return;
        if (bossAttackTicks == 11)
            sendBossTelegraph(level, position(), target.position().subtract(position()),
                    5.8F, 5, BossTelegraphPayload.FRONT_CONE);
        if (bossAttackTicks >= 15 && bossAttackTicks <= 17 && (punchStrikeMask & 2) == 0
                && performPunchStrike(level, target, 1)) return;
        if (bossAttackTicks == 22)
            sendBossTelegraph(level, position(), target.position().subtract(position()),
                    6.2F, 6, BossTelegraphPayload.FRONT_CONE);
        if (bossAttackTicks >= 28 && bossAttackTicks <= 31 && (punchStrikeMask & 4) == 0
                && performPunchStrike(level, target, 2)) return;
        if (bossAttackTicks >= 40) {
            riposteTicks = 36;
            finishBossAttack(punchComboFromChain ? 48 : 38);
        }
    }

    private boolean performPunchStrike(ServerLevel level, ServerPlayer target, int strike) {
        Vec3 delta = target.position().subtract(position());
        Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z);
        if (horizontal.length() > (strike == 2 ? 6.2D : 5.7D) || horizontal.lengthSqr() < 0.01D) {
            if (strike == 2) {
                riposteTicks = 46;
                finishBossAttack(48);
                return true;
            }
            return false;
        }
        Vec3 direction = horizontal.normalize();
        Vec3 facing = Vec3.directionFromRotation(0.0F, getYHeadRot());
        facing = new Vec3(facing.x, 0.0D, facing.z).normalize();
        if (direction.dot(facing) < 0.08D) return false;
        Vec3 right = new Vec3(-facing.z, 0.0D, facing.x);
        double lateral = strike == 0 ? 0.72D : strike == 1 ? -0.72D : 0.0D;
        double reach = strike == 2 ? 3.85D : 3.45D;
        Vec3 shoulder = position().add(facing.scale(0.72D)).add(right.scale(lateral * 0.55D))
                .add(0.0D, getBbHeight() * 0.59D, 0.0D);
        Vec3 fist = position().add(facing.scale(reach)).add(right.scale(lateral))
                .add(0.0D, getBbHeight() * (strike == 2 ? 0.58D : 0.54D), 0.0D);
        AABB physicalFist = new AABB(shoulder, fist).inflate(strike == 2 ? 1.22D : 0.92D,
                strike == 2 ? 1.10D : 0.88D, strike == 2 ? 1.22D : 0.92D);
        if (!physicalFist.intersects(target.getBoundingBox())) return false;
        if (target.isBlocking()) {
            punchStrikeMask |= 1 << strike;
            target.setDeltaMovement(direction.scale(0.62D + strike * 0.16D).add(0.0D, 0.12D, 0.0D));
            target.hurtMarked = true;
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0D,
                    target.getZ(), 12 + strike * 5, 0.48D, 0.65D, 0.48D, 0.10D);
            playSound(SoundEvents.SHIELD_BLOCK.value(), 2.5F, 0.78F - strike * 0.08F);
            if (strike == 2) {
                bossStunTicks = 24;
                riposteTicks = 36;
                finishBossAttack(42);
                return true;
            }
            return false;
        }
        float damage = strike < 2 ? 6.0F : 6.0F + rage() * 0.12F;
        boolean damaged = target.hurtServer(level, damageSources().mobAttack(this), damage);
        // Mark a physically connected strike even during vanilla hurt-invulnerability frames.  In
        // particular, the chain's rapid punches used to make the finisher deal no impulse at all.
        punchStrikeMask |= 1 << strike;
        if (strike < 2) {
            if (damaged) {
                target.setDeltaMovement(direction.scale(0.62D + strike * 0.06D).add(0.0D, 0.10D, 0.0D));
                target.hurtMarked = true;
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0D,
                        target.getZ(), 8 + strike * 4, 0.4D, 0.55D, 0.4D, 0.09D);
                playSound(SoundEvents.PLAYER_ATTACK_STRONG, 2.2F, 0.62F - strike * 0.08F);
            }
        } else {
            Vec3 launch = direction.scale(punchComboFromChain ? 1.72D : 1.38D)
                    .add(0.0D, punchComboFromChain ? 0.48D : 0.38D, 0.0D);
            ragdollPlayer(target, launch, punchComboFromChain ? 1.55F : 1.35F, true);
            level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 0.9D,
                    target.getZ(), 5, 0.55D, 0.75D, 0.55D, 0.05D);
            playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 3.0F, 0.46F);
            riposteTicks = 42;
            finishBossAttack(punchComboFromChain ? 66 : 54);
            return true;
        }
        return false;
    }

    private void tickFireRings(ServerLevel level) {
        setDeltaMovement(getDeltaMovement().multiply(0.05D, 1.0D, 0.05D));
        if (bossAttackTicks == 1) {
            clearBossFire(level);
            playRoar(3.2F, 0.62F, 0.75F);
        }
        if (bossAttackTicks >= 18 && bossAttackTicks <= 78 && bossAttackTicks % 4 == 2)
            igniteBossRing(level, 2 + (bossAttackTicks - 18) / 4);
        if (bossAttackTicks >= 108) {
            clearBossFire(level);
            riposteTicks = 34;
            finishBossAttack(62);
        }
    }

    private void igniteBossRing(ServerLevel level, int radius) {
        BlockPos center = blockPosition();
        int floorY = Mth.floor(getY()) - 1;
        boolean diagonalGates = ((radius + rage() / 4) & 1) == 0;
        var fireState = rage() >= 8 ? Blocks.SOUL_FIRE.defaultBlockState() : Blocks.FIRE.defaultBlockState();
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            double distance = Math.sqrt(x * x + z * z);
            if (Math.abs(distance - radius) > 0.55D) continue;
            // Four visible gaps alternate between cardinal and diagonal lanes, making every ring
            // solvable through movement instead of forcing a damage trade.
            boolean gate = diagonalGates ? Math.abs(Math.abs(x) - Math.abs(z)) <= 1
                    : Math.abs(x) <= 1 || Math.abs(z) <= 1;
            if (gate) continue;
            BlockPos fire = new BlockPos(center.getX() + x, floorY + 1, center.getZ() + z);
            if (level.getBlockState(fire).isAir() && level.getBlockState(fire.below()).isFaceSturdy(
                    level, fire.below(), Direction.UP)) {
                level.setBlock(fire, fireState, 2);
                bossFireBlocks.add(fire);
            }
        }
        for (ServerPlayer victim : level.players()) {
            BlockPos feet = victim.blockPosition();
            boolean standingInRing = level.getBlockState(feet).is(Blocks.FIRE)
                    || level.getBlockState(feet).is(Blocks.SOUL_FIRE);
            if (standingInRing && victim.getY() <= floorY + 1.35D) {
                if (victim.hurtServer(level, damageSources().mobAttack(this), 4.0F))
                    victim.igniteForSeconds(2.0F);
            }
        }
        level.sendParticles(rage() >= 8 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                getX(), Math.min(getY() - 0.18D, floorY + 0.82D), getZ(),
                Math.max(12, radius * 3), radius * 0.65D, 0.04D, radius * 0.65D, 0.02D);
    }

    private void clearBossFire(ServerLevel level) {
        for (BlockPos pos : bossFireBlocks)
            if (level.getBlockState(pos).is(Blocks.FIRE) || level.getBlockState(pos).is(Blocks.SOUL_FIRE))
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        bossFireBlocks.clear();
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
        Player found = wallComboTarget == null ? null : level.getPlayerByUUID(wallComboTarget);
        ServerPlayer target = found instanceof ServerPlayer serverPlayer ? serverPlayer : player;
        if (target == null || !target.isAlive() || target.isCreative() || target.isSpectator()) {
            finishBossAttack(34);
            return;
        }
        getLookControl().setLookAt(target, 18.0F, 10.0F);
        double distance = distanceTo(target);
        if (!wallShoveHit && distance > 4.6D) {
            if (bossAttackTicks % 3 == 0 || getNavigation().isDone())
                getNavigation().moveTo(target, 1.32D + rage() * 0.018D);
            Vec3 pursuit = target.position().subtract(position());
            Vec3 horizontal = new Vec3(pursuit.x, 0.0D, pursuit.z);
            if (getNavigation().isDone() && horizontal.lengthSqr() > 0.01D)
                setDeltaMovement(horizontal.normalize().scale(0.48D + rage() * 0.012D)
                        .add(0.0D, getDeltaMovement().y, 0.0D));
            if ((bossAttackTicks & 3) == 0)
                level.sendParticles(ParticleTypes.DUST_PLUME, getX(), getY() + 0.1D, getZ(),
                        7, 0.65D, 0.08D, 0.65D, 0.04D);
        }
        boolean atWall = horizontalDistanceToArenaCenter(target.position()) >= 25.5D
                || isPlayerPinned(level, target);
        if (!wallShoveHit && distance <= 5.4D && atWall) {
            wallShoveHit = true;
            getNavigation().stop();
            Vec3 wallward = new Vec3(target.getX(), 0.0D, target.getZ());
            if (wallward.lengthSqr() < 0.01D) wallward = target.position().subtract(position());
            wallward = wallward.normalize();
            if (target.hurtServer(level, damageSources().mobAttack(this), 10.0F))
                ragdollPlayer(target, wallward.scale(0.58D).add(0.0D, 0.16D, 0.0D), 1.25F, true);
            level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 1.0D,
                    target.getZ(), 5, 0.7D, 0.9D, 0.7D, 0.08D);
            WorldGenerator.scarBossArena(level, target.position(), 3);
            playSound(SoundEvents.RAVAGER_ATTACK, 3.4F, 0.40F);
        }
        if (wallShoveHit && bossAttackTicks >= 16 || bossAttackTicks >= 82) {
            wallComboTarget = null;
            wallComboWindow = 0;
            riposteTicks = wallShoveHit ? 32 : 46;
            finishBossAttack(wallShoveHit ? 44 : 36);
        }
    }

    private void tickRedLightningCharge(ServerLevel level, ServerPlayer player) {
        if (bossAttackTicks < 30) {
            setDeltaMovement(getDeltaMovement().multiply(0.12D, 1.0D, 0.12D));
            if ((bossAttackTicks & 3) == 0)
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(),
                        getY() + getBbHeight() * 0.52D, getZ(), 12,
                        0.9D, 1.5D, 0.9D, 0.08D);
            return;
        }
        if (!lightningStrikeResolved) {
            lightningStrikeResolved = true;
            for (ServerPlayer victim : level.getEntitiesOfClass(ServerPlayer.class,
                    new AABB(lightningStrikeTarget, lightningStrikeTarget).inflate(3.25D, 2.0D, 3.25D))) {
                if (victim.hurtServer(level, damageSources().lightningBolt(), 10.0F)) {
                    Vec3 away = victim.position().subtract(lightningStrikeTarget);
                    Vec3 horizontal = new Vec3(away.x, 0.0D, away.z);
                    if (horizontal.lengthSqr() < 0.01D) horizontal = new Vec3(0.0D, 0.0D, 1.0D);
                    ragdollPlayer(victim, horizontal.normalize().scale(1.35D).add(0.0D, 0.78D, 0.0D),
                            1.35F);
                }
            }
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, lightningStrikeTarget.x,
                    lightningStrikeTarget.y + 0.2D, lightningStrikeTarget.z,
                    34, 2.2D, 0.35D, 2.2D, 0.18D);
        }
        if (bossAttackTicks <= 38) {
            setDeltaMovement(getDeltaMovement().multiply(0.12D, 1.0D, 0.12D));
            return;
        }
        if (bossAttackTicks <= 68) {
            double speed = 1.04D + rage() * 0.012D;
            setDeltaMovement(bossChargeDirection.x * speed, getDeltaMovement().y,
                    bossChargeDirection.z * speed);
            AABB impact = getBoundingBox().expandTowards(bossChargeDirection.scale(2.2D))
                    .inflate(0.55D, 0.35D, 0.55D);
            WorldGenerator.clearLowBossChargeObstacle(level, impact);
            int smashed = WorldGenerator.breakBossArenaWallAround(level, impact, this);
            if (smashed > 0) {
                recoverFromArenaImpact();
                WorldGenerator.scarBossArena(level, position(), 5);
                level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1.0D, getZ(),
                        7, 1.2D, 1.1D, 1.2D, 0.08D);
                riposteTicks = 34;
                finishBossAttack(54);
                return;
            }
            if (horizontalCollision) {
                recoverFromArenaImpact();
                riposteTicks = 36;
                finishBossAttack(54);
                return;
            }
            if (attackCooldown <= 0 && impact.intersects(player.getBoundingBox())) {
                player.hurtServer(level, damageSources().mobAttack(this), 20.0F);
                ragdollPlayer(player, bossChargeDirection.scale(3.2D).add(0.0D, 0.72D, 0.0D), 1.6F);
                attackCooldown = 24;
            }
        }
        if (bossAttackTicks >= 78) {
            riposteTicks = 38;
            finishBossAttack(58);
        }
    }

    private void breakEntanglingCobwebs(ServerLevel level) {
        if ((tickCount & 1) != 0) return;
        AABB body = getBoundingBox().inflate(0.18D, 0.1D, 0.18D);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int broken = 0;
        for (int x = Mth.floor(body.minX); x <= Mth.floor(body.maxX) && broken < 10; x++)
            for (int y = Mth.floor(body.minY); y <= Mth.floor(body.maxY) && broken < 10; y++)
                for (int z = Mth.floor(body.minZ); z <= Mth.floor(body.maxZ) && broken < 10; z++) {
                    cursor.set(x, y, z);
                    if (!level.getBlockState(cursor).is(Blocks.COBWEB)) continue;
                    level.destroyBlock(cursor, false, this, 512);
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                                    Blocks.COBWEB.defaultBlockState()),
                            x + 0.5D, y + 0.5D, z + 0.5D, 7,
                            0.35D, 0.35D, 0.35D, 0.06D);
                    broken++;
                }
        if (broken > 0) {
            playSound(SoundEvents.SHEEP_SHEAR, 1.3F, 0.62F);
            Vec3 velocity = getDeltaMovement();
            if (velocity.horizontalDistanceSqr() < 0.04D && bossChargeDirection.horizontalDistanceSqr() > 0.01D)
                setDeltaMovement(bossChargeDirection.scale(0.42D).add(0.0D, velocity.y, 0.0D));
        }
    }

    private void recoverFromArenaImpact() {
        Vec3 retreat = bossChargeDirection.horizontalDistanceSqr() > 0.01D
                ? position().subtract(bossChargeDirection.normalize().scale(2.8D)) : position();
        Vec3 safe = WorldGenerator.clampBossArena(retreat);
        setPos(safe.x, Math.max(WorldGenerator.bossArenaCenter().y, safe.y), safe.z);
        resetFallDistance();
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
        double arenaRadius = Math.sqrt(getX() * getX() + getZ() * getZ());
        if (arenaRadius > 25.0D) {
            Vec3 outward = new Vec3(getX(), 0.0D, getZ()).normalize();
            AABB wallImpact = getBoundingBox().expandTowards(outward.scale(5.5D))
                    .inflate(3.2D, 1.8D, 3.2D);
            int broken = WorldGenerator.breakBossArenaWallAround(level, wallImpact, this);
            if (broken > 0)
                level.sendParticles(ParticleTypes.EXPLOSION, wallImpact.getCenter().x,
                        getY() + 2.0D, wallImpact.getCenter().z,
                        Math.min(12, 3 + broken / 12), 1.8D, 1.7D, 1.8D, 0.06D);
        }
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
        playRoar(4.0F, 0.82F, 1.25F);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + getBbHeight() * 0.5D, getZ(),
                60, 2.0D, 2.5D, 2.0D, 0.035D);
    }

    private void tickCollapse(ServerLevel level, ServerPlayer player) {
        collapseTicks++;
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().multiply(0.1D, 1.0D, 0.1D));
        if (collapseTicks >= 74 && collapseTicks < 138) {
            setPos(collapseAnchor.x, collapseAnchor.y, collapseAnchor.z);
            setDeltaMovement(Vec3.ZERO);
        }
        getLookControl().setLookAt(player, 2.0F, 2.0F);
        if (collapseTicks <= 30 && (collapseTicks % 5) == 0) {
            MazeShiftPayload rumble = new MazeShiftPayload(blockPosition(), 72.0F,
                    0.65F + collapseTicks / 18.0F, 14);
            for (ServerPlayer viewer : level.players())
                if (ServerPlayNetworking.canSend(viewer, MazeShiftPayload.TYPE))
                    ServerPlayNetworking.send(viewer, rumble);
            level.sendParticles(ParticleTypes.DUST_PLUME, getX(), getY() + 0.1D, getZ(),
                    18 + collapseTicks, 5.5D, 0.2D, 5.5D, 0.045D);
            playSound(SoundEvents.RAVAGER_STEP, 2.4F + collapseTicks * 0.04F,
                    0.46F - collapseTicks * 0.004F);
        }
        if (collapseTicks == 30) {
            MazeShiftPayload rupture = new MazeShiftPayload(blockPosition(), 160.0F, 4.8F, 28);
            for (ServerPlayer viewer : level.players())
                if (ServerPlayNetworking.canSend(viewer, MazeShiftPayload.TYPE))
                    ServerPlayNetworking.send(viewer, rupture);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 4.5F, 0.34F);
        }
        if (collapseTicks >= 34 && collapseTicks <= 68)
            WorldGenerator.collapseBossRoofRing(level, position(), collapseTicks - 34);
        if (collapseTicks == 74) {
            noPhysics = true;
            WorldGenerator.buryBossInRubble(level, position());
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 3.2F, 0.42F);
        }
        if (collapseTicks >= 84 && collapseTicks <= 132 && collapseTicks % 6 == 0
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
        if (collapseTicks == 138) {
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
        if (collapseTicks >= 168) {
            Vec3 safe = WorldGenerator.clampBossArena(collapseAnchor);
            setPos(safe.x, safe.y, safe.z);
            setDeltaMovement(Vec3.ZERO);
            resetFallDistance();
            setBossStage(BossStage.EXTREME);
            bossAttackCooldown = 35;
            setAggressive(true);
            getEntityData().set(DATA_RAGE, Math.max(rage(), 6));
            updateChaseSpeed();
            setHealth(getMaxHealth() * 0.70F);
            playRoar(4.5F, 1.12F, 1.45F);
            level.sendParticles(ParticleTypes.FLAME, getX(), getY() + getBbHeight() * 0.45D, getZ(),
                    80, 1.4D, 2.2D, 1.4D, 0.08D);
        }
    }

    private void tickLeapAttack(ServerLevel level, ServerPlayer player) {
        if (leapImpactTick >= 0) {
            tickLeapShockwave(level);
            return;
        }
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
            armHeavyJump();
            playSound(SoundEvents.GOAT_LONG_JUMP, 2.8F, 0.62F);
            return;
        }
        if (bossWasAirborne && bossAttackTicks > 20 && onGround()) {
            bossWasAirborne = false;
            leapImpactTick = bossAttackTicks;
            leapImpactOrigin = position();
            performLeapImpact(level);
            return;
        }
        if (getBoundingBox().inflate(0.65D).intersects(player.getBoundingBox()) && attackCooldown <= 0) {
            player.hurtServer(level, damageSources().mobAttack(this), 15.0F);
            Vec3 direction = player.position().subtract(position()).normalize();
            ragdollPlayer(player, direction.scale(3.0D).add(0, 1.15D, 0), 1.55F);
            scheduleWallCombo(player, 120);
            scheduleAirCatch(player, 34);
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
        for (ServerPlayer viewer : level.players())
            if (ServerPlayNetworking.canSend(viewer, RagdollExplosionPayload.TYPE))
                ServerPlayNetworking.send(viewer, new RagdollExplosionPayload(position(), 9.0F));
        sendBossTelegraph(level, position(), Vec3.ZERO, 17.5F, 26,
                BossTelegraphPayload.TARGET_CIRCLE);
        for (ServerPlayer victim : level.getEntitiesOfClass(ServerPlayer.class,
                getBoundingBox().inflate(3.6D))) {
            if (!victim.isAlive() || victim.isCreative() || victim.isSpectator()) continue;
            Vec3 away = victim.position().subtract(position());
            double distance = new Vec3(away.x, 0.0D, away.z).length();
            double power = Mth.clamp(1.0D - distance / 18.0D, 0.0D, 1.0D);
            Vec3 direction = new Vec3(away.x, 0.0D, away.z);
            if (direction.lengthSqr() < 0.01D) direction = Vec3.directionFromRotation(0.0F, getYRot());
            leapShockwaveHits.add(victim.getUUID());
            if (victim.hurtServer(level, damageSources().mobAttack(this),
                    (float)Mth.lerp(power, 3.0D, 15.0D))) {
                ragdollPlayer(victim, direction.normalize().scale(Mth.lerp(power, 1.0D, 3.0D))
                        .add(0.0D, Mth.lerp(power, 0.82D, 1.22D), 0.0D), 1.5F, true);
                scheduleWallCombo(victim, 120);
                scheduleAirCatch(victim, 34);
            }
        }
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 3.2F, 0.42F);
    }

    private void tickLeapShockwave(ServerLevel level) {
        setDeltaMovement(getDeltaMovement().multiply(0.04D, 1.0D, 0.04D));
        int elapsed = bossAttackTicks - leapImpactTick;
        double radius = 1.4D + elapsed * 0.72D;
        int points = Math.max(24, Mth.ceil(radius * 4.2D));
        for (int point = 0; point < points; point++) {
            double angle = Mth.TWO_PI * point / points;
            double x = leapImpactOrigin.x + Math.cos(angle) * radius;
            double z = leapImpactOrigin.z + Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.DUST_PLUME, x, leapImpactOrigin.y + 0.12D, z,
                    1, 0.08D, 0.03D, 0.08D, 0.025D);
            if ((point & 3) == 0)
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, leapImpactOrigin.y + 0.18D, z,
                        1, 0.05D, 0.02D, 0.05D, 0.02D);
        }
        for (ServerPlayer victim : level.players()) {
            if (!victim.isAlive() || victim.isCreative() || victim.isSpectator()
                    || leapShockwaveHits.contains(victim.getUUID())) continue;
            Vec3 away = victim.position().subtract(leapImpactOrigin);
            double horizontal = new Vec3(away.x, 0.0D, away.z).length();
            if (Math.abs(horizontal - radius) > 1.05D) continue;
            boolean jumpedClear = !victim.onGround()
                    && (victim.getY() > leapImpactOrigin.y + 0.62D
                    || victim.getDeltaMovement().y > 0.075D);
            if (jumpedClear) continue;
            leapShockwaveHits.add(victim.getUUID());
            Vec3 direction = new Vec3(away.x, 0.0D, away.z);
            if (direction.lengthSqr() < 0.01D) direction = Vec3.directionFromRotation(0.0F, getYRot());
            double power = Mth.clamp(1.0D - horizontal / 18.0D, 0.0D, 1.0D);
            float damage = (float)Mth.lerp(power, 3.0D, 15.0D);
            double knockback = Mth.lerp(power, 1.0D, 3.0D);
            double lift = Mth.lerp(power, 0.82D, 1.22D);
            if (victim.hurtServer(level, damageSources().mobAttack(this), damage)) {
                ragdollPlayer(victim, direction.normalize().scale(knockback).add(0.0D, lift, 0.0D),
                        1.35F + (float)power * 0.25F, true);
                scheduleWallCombo(victim, 120);
                scheduleAirCatch(victim, 34);
            }
        }
        if (elapsed >= 23) {
            riposteTicks = 56;
            finishBossAttack(58);
        }
    }

    private void performSwordArc(ServerLevel level, float damage, double force) {
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        Vec3 facing = Vec3.directionFromRotation(getXRot(), getYHeadRot());
        for (ServerPlayer victim : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(7.0D))) {
            Vec3 delta = victim.position().subtract(position());
            Vec3 horizontal = new Vec3(delta.x, 0, delta.z);
            if (horizontal.lengthSqr() < 0.01D || horizontal.normalize()
                    .dot(new Vec3(facing.x, 0, facing.z).normalize()) < 0.10D) continue;
            if (victim.hurtServer(level, damageSources().mobAttack(this), damage))
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
            if (victim.hurtServer(level, damageSources().mobAttack(this), 13.0F))
                ragdollPlayer(victim, new Vec3(away.x, 0, away.z).normalize().scale(2.1D).add(0, 0.45D, 0), 1.3F);
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + getBbHeight() * 0.48D, getZ(),
                10, 2.8D, 0.7D, 2.8D, 0.0D);
    }

    private void tickGrabAttack(ServerLevel level, ServerPlayer player) {
        if (bossAttackTicks == 11 && (distanceTo(player) <= 5.2D || canArmReach(player))
                && canSeeWithEyes(player)) {
            grabbedPlayer = player.getUUID();
            lockReachTo(player);
        }
        Player foundGrabbed = grabbedPlayer == null ? null : level.getPlayerByUUID(grabbedPlayer);
        ServerPlayer grabbed = foundGrabbed instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        if (grabbed == null && bossAttackTicks >= 15) {
            getEntityData().set(DATA_GRAB_TARGET_ID, -1);
            finishBossAttack(48);
            return;
        }
        if (grabbed != null && bossAttackTicks >= 11 && bossAttackTicks < 48) {
            float hold = Mth.clamp((bossAttackTicks - 11) / 37.0F, 0.0F, 1.0F);
            double windup = Math.sin(hold * Math.PI);
            double sweep = Math.sin(hold * Math.PI * 2.0D) * (0.45D + windup * 0.72D);
            double lift = windup * getBbHeight() * (grabThrowStyle == GrabThrowStyle.SKY
                    ? 0.22D : 0.11D);
            Vec3 hand = reachHandPosition(grabbed, 0.10D + windup * 0.18D,
                    sweep * 0.42D, lift);
            placePlayerInHand(grabbed, hand);
            grabbed.setYRot(getYRot() + 180.0F + (float)(sweep * 14.0D));
            grabbed.setXRot(Mth.lerp(0.28F, grabbed.getXRot(), -12.0F - hold * 18.0F));
            grabbed.setDeltaMovement(Vec3.ZERO);
            grabbed.resetFallDistance();
            if ((bossAttackTicks & 3) == 0)
                level.sendParticles(ParticleTypes.LARGE_SMOKE, hand.x, hand.y, hand.z,
                        3, 0.35D, 0.5D, 0.35D, 0.02D);
        }
        if (grabbed != null && bossAttackTicks == 48) {
            Vec3 center = WorldGenerator.bossArenaCenter();
            Vec3 away = grabbed.position().subtract(position());
            Vec3 horizontal = new Vec3(away.x, 0.0D, away.z);
            if (horizontal.lengthSqr() < 0.04D) horizontal = center.subtract(position());
            horizontal = new Vec3(horizontal.x, 0.0D, horizontal.z).normalize();
            double throwPower = 4.20D + random.nextDouble() * 1.20D;
            Vec3 impulse = horizontal.scale(throwPower).add(0.0D, 0.52D, 0.0D);
            grabbed.hurtServer(level, damageSources().mobAttack(this), 10.0F);
            ragdollPlayer(grabbed, impulse, 1.85F, true);
            scheduleWallCombo(grabbed, 150);
            level.sendParticles(ParticleTypes.EXPLOSION, grabbed.getX(), grabbed.getY() + 0.8D,
                    grabbed.getZ(), 4, 0.65D, 0.8D, 0.65D, 0.025D);
            playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 3.0F, 0.46F);
            grabbedPlayer = null;
            getEntityData().set(DATA_GRAB_TARGET_ID, -1);
        }
        if (bossAttackTicks >= 72) {
            grabbedPlayer = null;
            getEntityData().set(DATA_GRAB_TARGET_ID, -1);
            riposteTicks = 38;
            finishBossAttack(50);
        }
    }

    private static double bossImpactY(ServerPlayer player) {
        return Math.max(WorldGenerator.bossArenaCenter().y, player.getY());
    }

    private void ragdollPlayer(ServerPlayer player, Vec3 impulse, float force) {
        ragdollPlayer(player, impulse, force, false);
    }

    private void ragdollPlayer(ServerPlayer player, Vec3 impulse, float force, boolean guaranteed) {
        player.setDeltaMovement(impulse);
        player.hurtMarked = true;
        player.resetFallDistance();
        float chance = Mth.clamp(0.18F + force * 0.18F + rage() * 0.012F, 0.28F, 0.68F);
        if (!guaranteed && random.nextFloat() >= chance) return;
        RagdollServerNetworking.markRagdolled(player, 86);
        if (ServerPlayNetworking.canSend(player, RagdollImpulsePayload.TYPE))
            ServerPlayNetworking.send(player, new RagdollImpulsePayload(position(), impulse, force));
        if (ServerPlayNetworking.canSend(player, DazePayload.TYPE))
            ServerPlayNetworking.send(player, new DazePayload(82,
                    Mth.clamp(2 + Mth.ceil(force * 1.45F), 3, 6)));
    }

    private void finishBossAttack(int cooldown) {
        if (bossAttack == BossAttack.FIRE_RINGS && level() instanceof ServerLevel level)
            clearBossFire(level);
        if (bossAttack == BossAttack.CHAIN_GRAPPLE) {
            chainGrappleTarget = null;
            getEntityData().set(DATA_REACH_ARM, 0);
            getEntityData().set(DATA_GRAB_TARGET_ID, -1);
            clearLockedReach();
        }
        if (bossAttack == BossAttack.GREEK_FIRE_LASER) setGlowingTag(false);
        if (bossAttack == BossAttack.PUNCH_COMBO) {
            punchComboTarget = null;
            punchComboFromChain = false;
            punchStrikeMask = 0;
            getEntityData().set(DATA_GRAB_TARGET_ID, -1);
        }
        if (bossAttack == BossAttack.LEAP) {
            leapImpactTick = -1;
            leapImpactOrigin = Vec3.ZERO;
            leapShockwaveHits.clear();
        }
        if (bossAttack == BossAttack.RAGDOLL_STOMP) {
            stompTarget = null;
            stompTargetPosition = Vec3.ZERO;
            stompWasAirborne = false;
        }
        if (bossAttack == BossAttack.ARROW_RETURN) arrowReturnTarget = null;
        if (bossAttack == BossAttack.CHARGE) {
            getEntityData().set(DATA_CHARGE_WINDUP, 50);
            bossChargeTargetsPillar = false;
        }
        if (bossAttack == BossAttack.RED_LIGHTNING_CHARGE) {
            lightningStrikeTarget = Vec3.ZERO;
            lightningStrikeResolved = false;
        }
        if (bossAttack == BossAttack.GRAB) {
            grabbedPlayer = null;
            getEntityData().set(DATA_GRAB_TARGET_ID, -1);
            getEntityData().set(DATA_REACH_ARM, 0);
            clearLockedReach();
        }
        if (bossAttack == BossAttack.WALL_SHOVE) {
            wallComboTarget = null;
            wallComboWindow = 0;
            wallShoveHit = false;
        }
        setBossAttack(BossAttack.NONE);
        int minimumRecovery = Math.max(24, (bossStage == BossStage.EXTREME ? 40 : 36) - rage());
        bossAttackCooldown = Math.max(minimumRecovery,
                cooldown + 14 - rage() / 3 - Math.min(8, (bossPartySize - 1) * 3));
        bossAttackTicks = 0;
        getEntityData().set(DATA_BOSS_ATTACK_TICKS, 0);
    }

    private void syncBossPartyScaling(ServerLevel level, boolean initial) {
        int players = (int)level.players().stream()
                .filter(player -> player.isAlive() && !player.isCreative() && !player.isSpectator()
                        && (WorldGenerator.isInsideBossArena(player.position())
                        || player.distanceToSqr(WorldGenerator.bossArenaCenter()) < 72.0D * 72.0D))
                .count();
        players = Mth.clamp(players, 1, 6);
        if (!initial && players == bossPartySize) return;
        float healthRatio = initial ? 1.0F : getHealth() / getMaxHealth();
        bossPartySize = players;
        double scaledHealth = 720.0D * (1.0D + Math.min(1.8D, (players - 1) * 0.42D));
        var maxHealth = getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(scaledHealth);
        setHealth(Math.max(1.0F, getMaxHealth() * healthRatio));
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
        if (phaseTicks > 50 && canDespawnUnseen()) discard();
    }

    public boolean canDespawnUnseen() {
        for (Player candidate : level().players()) {
            if (candidate instanceof ServerPlayer player && player.isAlive() && !player.isSpectator()
                    && isPlayerLookingAtMe(player, distanceTo(player))) return false;
        }
        return true;
    }

    private void avoidWallHugging(ServerLevel level) {
        BehaviorPhase phase = behaviorPhase();
        if (phase != BehaviorPhase.CHASING && phase != BehaviorPhase.HUNTING
                && phase != BehaviorPhase.ROAMING) return;
        Vec3 motion = getDeltaMovement();
        Vec3 forward = new Vec3(motion.x, 0.0D, motion.z);
        if (forward.lengthSqr() < 0.002D) forward = Vec3.directionFromRotation(0.0F, yBodyRot);
        forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        AABB body = getBoundingBox().deflate(0.08D).move(0.0D, 0.08D, 0.0D);
        boolean clearRight = level.noCollision(this, body.move(right.scale(0.52D)));
        boolean clearLeft = level.noCollision(this, body.move(right.scale(-0.52D)));
        if (clearRight == clearLeft) return;
        Vec3 correction = right.scale(clearRight ? 0.035D : -0.035D);
        setDeltaMovement(motion.add(correction));
    }

    private float playerLookExposure(ServerPlayer player, double distance) {
        if (distance > 58.0D || !player.hasLineOfSight(this)) return 0.0F;
        Vec3 towardMe = getEyePosition().subtract(player.getEyePosition()).normalize();
        double dot = player.getViewVector(1.0F).normalize().dot(towardMe);
        double peripheral = distance < 26.0D ? 0.82D : 0.91D;
        double focused = distance < 26.0D ? 0.97D : 0.992D;
        return (float)Mth.clamp((dot - peripheral) / (focused - peripheral), 0.0D, 1.0D);
    }

    private boolean isPlayerLookingAtMe(ServerPlayer player, double distance) {
        return playerLookExposure(player, distance) >= 0.72F;
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

    private boolean canAcquirePlayerForChase(ServerPlayer player, double distance) {
        if (distance > 32.0D || !hasLineOfSight(player)) return false;
        Vec3 delta = player.getEyePosition().subtract(getEyePosition());
        double horizontalLength = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontalLength < 0.001D) return true;
        if (distance <= 9.0D) return true;
        Vec3 bodyForward = Vec3.directionFromRotation(0.0F, yBodyRot);
        double dot = new Vec3(bodyForward.x, 0.0D, bodyForward.z).normalize()
                .dot(new Vec3(delta.x, 0.0D, delta.z).normalize());
        double requiredDot = distance <= 18.0D ? Math.cos(Math.toRadians(72.0D))
                : Math.cos(Math.toRadians(57.0D));
        double pitch = Math.abs(Math.toDegrees(Math.atan2(delta.y, horizontalLength)));
        return dot >= requiredDot && pitch <= 58.0D;
    }

    private Vec3 findStalkingPosition(ServerPlayer player, boolean acceptFirst) {
        return findStalkingPosition(player, acceptFirst, 2);
    }

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
            if (!level().hasChunk(feet.getX() >> 4, feet.getZ() >> 4)) continue;
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
        return angleMode == 0 ? fallback : null;
    }

    private Vec3 findHiddenSpawnPosition(ServerPlayer player) {
        double preferred = AsterionConfig.INSTANCE.minotaurStalkDistance;
        Vec3 view = player.getViewVector(1.0F);
        double behind = Math.atan2(view.z, view.x) + Math.PI;
        Vec3 original = position();
        Vec3 best = null;
        double bestHallway = -1.0D;
        for (int attempt = 0; attempt < 28; attempt++) {
            double angle = behind + (random.nextDouble() - 0.5D) * 2.5D;
            double distance = preferred + random.nextDouble() * 14.0D;
            Vec3 candidate = WorldGenerator.nearestMazeCorridor(
                    player.getX() + Math.cos(angle) * distance,
                    player.getZ() + Math.sin(angle) * distance);
            BlockPos feet = BlockPos.containing(candidate);
            if (!level().hasChunk(feet.getX() >> 4, feet.getZ() >> 4)) continue;
            setPos(candidate.x, candidate.y, candidate.z);
            boolean valid = isConnectedHiddenSpawn(player, candidate, feet);
            double hallway = valid ? stalkingHallwaySpan() : -1.0D;
            setPos(original.x, original.y, original.z);
            if (valid && hallway > bestHallway) {
                best = candidate;
                bestHallway = hallway;
            }
            if (valid && hallway >= 48.0D) return candidate;
        }
        setPos(original.x, original.y, original.z);
        return best;
    }

    private boolean tryShadowRelocation(ServerLevel level, ServerPlayer player) {
        if (player.hasLineOfSight(this)) {
            shadowRelocateCooldown = 80;
            return false;
        }
        Vec3 destination = findShadowRelocation(player);
        if (destination == null) {
            shadowRelocateCooldown = random.nextIntBetweenInclusive(100, 180);
            return false;
        }

        level.sendParticles(ParticleTypes.DUST_PLUME, getX(), getY() + 0.35D, getZ(),
                24, 1.25D, 0.55D, 1.25D, 0.035D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.65D, getZ(),
                10, 0.80D, 0.45D, 0.80D, 0.018D);
        getNavigation().stop();
        setPos(destination.x, destination.y, destination.z);
        setDeltaMovement(Vec3.ZERO);
        level.sendParticles(ParticleTypes.DUST_PLUME, getX(), getY() + 0.35D, getZ(),
                30, 1.35D, 0.65D, 1.35D, 0.040D);
        playSound(SoundEvents.RAVAGER_STEP, 0.72F, 0.42F);

        stalkingRoute.clear();
        stalkingDestination = null;
        stalkingAnchor = player.position();
        lastKnownPlayerPosition = player.position();
        shadowArrivalTicks = 30;
        shadowRelocateCooldown = behaviorPhase() == BehaviorPhase.HUNTING
                ? random.nextIntBetweenInclusive(360, 620)
                : random.nextIntBetweenInclusive(560, 900);
        enterStalkMode(random.nextBoolean() ? StalkMode.SHADOWING : StalkMode.FLANKING);
        return true;
    }

    private Vec3 findShadowRelocation(ServerPlayer player) {
        Vec3 original = position();
        Vec3 view = player.getViewVector(1.0F);
        double facing = Math.atan2(view.z, view.x);
        Vec3 best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int attempt = 0; attempt < 24; attempt++) {
            boolean behind = attempt < 15;
            double offset = behind ? Math.PI : (random.nextBoolean() ? Math.PI * 0.5D : -Math.PI * 0.5D);
            double angle = facing + offset + (random.nextDouble() - 0.5D) * (behind ? 1.25D : 0.72D);
            double distance = 28.0D + random.nextDouble() * 20.0D;
            Vec3 candidate = WorldGenerator.nearestMazeCorridor(
                    player.getX() + Math.cos(angle) * distance,
                    player.getZ() + Math.sin(angle) * distance);
            BlockPos feet = BlockPos.containing(candidate);
            if (!level().hasChunk(feet.getX() >> 4, feet.getZ() >> 4)) continue;
            setPos(candidate.x, candidate.y, candidate.z);
            boolean valid = !WorldGenerator.isApproachingCenter(candidate)
                    && isConnectedHiddenSpawn(player, candidate, feet);
            double hallway = valid ? stalkingHallwaySpan() : 0.0D;
            setPos(original.x, original.y, original.z);
            if (valid) {
                double candidateDistance = candidate.distanceTo(player.position());
                double distanceScore = 18.0D - Math.abs(candidateDistance - 42.0D);
                double score = hallway + distanceScore;
                if (score > bestScore) {
                    best = candidate;
                    bestScore = score;
                }
                if (hallway >= 52.0D && candidateDistance >= 34.0D) return candidate;
            }
        }
        setPos(original.x, original.y, original.z);
        return best;
    }

    private double stalkingHallwaySpan() {
        if (!(level() instanceof ServerLevel serverLevel)) return 0.0D;
        double eastWest = clearHallwayDistance(serverLevel, new Vec3(1.0D, 0.0D, 0.0D))
                + clearHallwayDistance(serverLevel, new Vec3(-1.0D, 0.0D, 0.0D));
        double northSouth = clearHallwayDistance(serverLevel, new Vec3(0.0D, 0.0D, 1.0D))
                + clearHallwayDistance(serverLevel, new Vec3(0.0D, 0.0D, -1.0D));
        return Math.max(eastWest, northSouth);
    }

    private Vec3 findHiddenCenterApproachSpawn(ServerPlayer player) {
        Vec3 original = position();
        int cell = AsterionConfig.INSTANCE.cellSize;
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = attempt * (Math.PI * 0.5D) + random.nextDouble() * 0.35D;
            double distance = cell * (8.25D + random.nextDouble() * 2.5D);
            Vec3 candidate = WorldGenerator.nearestMazeCorridor(
                    Math.cos(angle) * distance, Math.sin(angle) * distance);
            BlockPos feet = BlockPos.containing(candidate);
            if (!level().hasChunk(feet.getX() >> 4, feet.getZ() >> 4)) continue;
            setPos(candidate.x, candidate.y, candidate.z);
            boolean valid = isConnectedHiddenSpawn(player, candidate, feet);
            setPos(original.x, original.y, original.z);
            if (valid) return candidate;
        }
        setPos(original.x, original.y, original.z);
        return null;
    }

    private boolean isConnectedHiddenSpawn(ServerPlayer player, Vec3 candidate, BlockPos feet) {
        if (!(level() instanceof ServerLevel serverLevel)
                || !level().getBlockState(feet.below()).isFaceSturdy(level(), feet.below(), Direction.UP)
                || !level().noCollision(this) || player.hasLineOfSight(this)) return false;
        Vec3 destination = WorldGenerator.nearestMazeCorridor(player.getX(), player.getZ());
        return !WorldGenerator.mazeRoute(serverLevel, candidate, destination,
                getBbWidth(), getBbHeight(), 4096).isEmpty();
    }

    private void moveByMazeRoute(ServerLevel level, Vec3 destination, double speed, int budget) {
        Vec3 waypoint = WorldGenerator.nextMazeWaypoint(level, position(), destination,
                getBbWidth(), getBbHeight(), budget);
        if (waypoint != null) getNavigation().moveTo(waypoint.x, waypoint.y, waypoint.z, speed);
    }

    private void updateChaseSpeed() {
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            double base = 0.35D + rage() * 0.012D;
            speed.setBaseValue(Math.min(0.58D, base + hallwayMomentum * 0.075D));
        }
    }

    private void tickHallwayMomentum(ServerLevel level, ServerPlayer player) {
        if (hallwayScanTicks-- <= 0) {
            hallwayScanTicks = 6;
            Vec3 aim = chaseRoute.peekFirst();
            if (aim == null || aim.distanceToSqr(position()) < 2.0D) aim = player.position();
            Vec3 delta = aim.subtract(position());
            Vec3 axis = Math.abs(delta.x) >= Math.abs(delta.z)
                    ? new Vec3(Math.signum(delta.x), 0.0D, 0.0D)
                    : new Vec3(0.0D, 0.0D, Math.signum(delta.z));
            double clear = axis.lengthSqr() < 0.5D ? 0.0D : clearHallwayDistance(level, axis);
            hallwayTarget = (float) smootherStep(Mth.clamp((clear - 13.0D) / 31.0D, 0.0D, 1.0D));
        }
        float response = hallwayTarget > hallwayMomentum ? 0.032F : 0.14F;
        hallwayMomentum = Mth.lerp(response, hallwayMomentum, hallwayTarget);
        updateChaseSpeed();
        if (hallwayMomentum > 0.42F && onGround() && tickCount % 5 == 0) {
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.12D, getZ(),
                    2, 0.35D, 0.05D, 0.35D, 0.012D);
        }
    }

    private double clearHallwayDistance(ServerLevel level, Vec3 axis) {
        AABB body = getBoundingBox().deflate(0.12D).move(0.0D, 0.08D, 0.0D);
        for (double distance = 3.0D; distance <= 46.0D; distance += 2.0D) {
            if (!level.noCollision(this, body.move(axis.x * distance, 0.0D, axis.z * distance)))
                return distance - 2.0D;
        }
        return 46.0D;
    }

    private static double smootherStep(double value) {
        return value * value * value * (value * (value * 6.0D - 15.0D) + 10.0D);
    }

    private void playHeavySteps() {
        if (getDeltaMovement().horizontalDistanceSqr() > 0.012D && (tickCount % 9) == 0) {
            playSound(SoundEvents.RAVAGER_STEP, behaviorPhase() == BehaviorPhase.CHASING ? 1.8F : 1.05F,
                    behaviorPhase() == BehaviorPhase.CHASING ? 0.68F : 0.48F);
            if (behaviorPhase() == BehaviorPhase.CHASING && level() instanceof ServerLevel level) {
                MazeShiftPayload footfall = new MazeShiftPayload(blockPosition(), 26.0F,
                        0.22F + rage() * 0.012F, 7);
                for (ServerPlayer viewer : level.players())
                    if (viewer.distanceToSqr(this) <= 30.0D * 30.0D
                            && ServerPlayNetworking.canSend(viewer, MazeShiftPayload.TYPE))
                        ServerPlayNetworking.send(viewer, footfall);
            }
        }
    }

    private void startHitBackoff(ServerPlayer attacker, int ticks, boolean counterGrab) {
        Vec3 away = position().subtract(attacker.position());
        hitBackoffDirection = new Vec3(away.x, 0.0D, away.z);
        if (hitBackoffDirection.lengthSqr() < 0.01D)
            hitBackoffDirection = Vec3.directionFromRotation(0.0F, getYRot()).scale(-1.0D);
        hitBackoffDirection = hitBackoffDirection.normalize();
        hitBackoffTicks = Math.max(hitBackoffTicks, ticks);
        hitBackoffTarget = counterGrab ? attacker.getUUID() : null;
        getNavigation().stop();
        setDeltaMovement(hitBackoffDirection.scale(0.42D).add(0.0D, getDeltaMovement().y, 0.0D));
        hurtMarked = true;
    }

    private boolean tickHitBackoff(ServerLevel level, ServerPlayer currentTarget, boolean boss) {
        if (hitBackoffTicks <= 0) return false;
        getNavigation().stop();
        getLookControl().setLookAt(currentTarget, 16.0F, 10.0F);
        double speed = 0.18D + hitBackoffTicks * 0.012D;
        setDeltaMovement(hitBackoffDirection.scale(speed).add(0.0D, getDeltaMovement().y, 0.0D));
        if ((hitBackoffTicks & 3) == 0)
            level.sendParticles(ParticleTypes.DUST_PLUME, getX(), getY() + 0.1D, getZ(),
                    5, 0.65D, 0.08D, 0.65D, 0.04D);
        hitBackoffTicks--;
        if (hitBackoffTicks > 0) return true;
        Player found = hitBackoffTarget == null ? null : level.getPlayerByUUID(hitBackoffTarget);
        hitBackoffTarget = null;
        if (found instanceof ServerPlayer attacker && attacker.isAlive()
                && distanceTo(attacker) <= 5.2D && canSeeWithEyes(attacker)) {
            if (boss && bossAttack == BossAttack.NONE && attackReady(BossAttack.GRAB)) {
                bossAttackCooldown = 0;
                beginBossAttack(attacker, BossAttack.GRAB);
            } else if (!boss && behaviorPhase() == BehaviorPhase.CHASING && attackCooldown <= 0) {
                beginMazeGrab(attacker);
            }
        }
        return true;
    }

    private void armHeavyJump() {
        heavyJumpArmed = true;
        heavyJumpWasAirborne = false;
    }

    private void tickHeavyLanding(ServerLevel level) {
        if (!heavyJumpArmed) return;
        if (!onGround()) {
            heavyJumpWasAirborne = true;
            return;
        }
        if (!heavyJumpWasAirborne) return;
        heavyJumpArmed = false;
        heavyJumpWasAirborne = false;
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.7F, 0.36F);
        playSound(SoundEvents.RAVAGER_STEP, 3.2F, 0.34F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                        level.getBlockState(blockPosition().below())),
                getX(), getY() + 0.12D, getZ(), 34, 1.45D, 0.18D, 1.45D, 0.16D);
        level.sendParticles(ParticleTypes.DUST_PLUME, getX(), getY() + 0.10D, getZ(),
                22, 1.8D, 0.12D, 1.8D, 0.07D);
        MazeShiftPayload impact = new MazeShiftPayload(blockPosition(),
                behaviorPhase() == BehaviorPhase.BOSS ? 58.0F : 34.0F,
                behaviorPhase() == BehaviorPhase.BOSS ? 1.2F : 0.72F, 14);
        for (ServerPlayer viewer : level.players())
            if (ServerPlayNetworking.canSend(viewer, MazeShiftPayload.TYPE))
                ServerPlayNetworking.send(viewer, impact);
        if (behaviorPhase() == BehaviorPhase.BOSS)
            WorldGenerator.scarBossArena(level, position(), 3);
        else
            WorldGenerator.breakPlayerBlocksAround(level, getBoundingBox().inflate(1.2D, 0.15D, 1.2D));
    }

    private void playRoar(float volume, float pitch, float quakeStrength) {
        playSound(Asterion.MINOTAUR_ROAR, volume, pitch);
        playSound(SoundEvents.WARDEN_ROAR, volume * 0.58F, Math.max(0.28F, pitch * 0.62F));
        playSound(SoundEvents.RAVAGER_ROAR, volume * 0.42F, Math.max(0.30F, pitch * 0.72F));
        if (!(level() instanceof ServerLevel level)) return;
        boolean boss = behaviorPhase() == BehaviorPhase.BOSS;
        float radius = boss ? 112.0F : 62.0F;
        float strength = Mth.clamp(quakeStrength * 1.32F + volume * 0.045F, 0.22F, 2.65F);
        MazeShiftPayload quake = new MazeShiftPayload(blockPosition(), radius, strength,
                boss ? 36 : 28);
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(this) <= (radius + 12.0F) * (radius + 12.0F)
                    && ServerPlayNetworking.canSend(viewer, MazeShiftPayload.TYPE))
                ServerPlayNetworking.send(viewer, quake);
        }

        double roofY = boss ? getY() + 18.0D
                : getY() + Math.max(8.0D, AsterionConfig.INSTANCE.wallHeight - 2.0D);
        roofY = Math.min(level.getMaxY() - 2.0D, roofY);
        BlockParticleOption rubble = new BlockParticleOption(ParticleTypes.FALLING_DUST,
                Asterion.ANCIENT_STONE.defaultBlockState());
        int rubbleCount = boss ? 30 : 18;
        level.sendParticles(rubble, getX(), roofY, getZ(), rubbleCount,
                boss ? 8.0D : 5.0D, 0.7D, boss ? 8.0D : 5.0D, 0.025D);
        level.sendParticles(ParticleTypes.DUST_PLUME, getX(), getY() + 0.12D, getZ(),
                boss ? 24 : 12, boss ? 5.0D : 3.0D, 0.08D, boss ? 5.0D : 3.0D, 0.025D);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getEntity() == this || source.getDirectEntity() == this
                || source.is(DamageTypeTags.IS_FIRE)) return false;
        if (behaviorPhase() == BehaviorPhase.BOSS
                && source.getDirectEntity() instanceof AbstractArrow arrow
                && source.getEntity() instanceof ServerPlayer archer) {
            // Resolve every boss-arrow collision here and remove the projectile immediately.  A
            // probabilistic fall-through left arrows attached to the custom GeckoLib entity while
            // its attack state could replace/discard it, which was the unstable impact path.
            arrow.discard();
            storedArrows = Math.min(7, storedArrows + 1);
            increaseRage(1);
            level.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() * 0.62D,
                    getZ(), 10, 0.8D, 0.9D, 0.8D, 0.08D);
            playSound(SoundEvents.SHIELD_BLOCK.value(), 2.4F, 0.46F);
            if (bossAttack == BossAttack.NONE && attackReady(BossAttack.ARROW_RETURN)
                    && distanceTo(archer) > 5.0D)
                beginBossAttack(archer, BossAttack.ARROW_RETURN);
            return false;
        }
        if (amount > 0.0F && source.getEntity() instanceof Player
                && behaviorPhase() != BehaviorPhase.RETREATING
                && bossStage != BossStage.DEFEATED)
            increaseRage(1 + Mth.floor(Math.min(16.0F, amount) / 8.0F));
        if (amount > 0.0F && source.getEntity() instanceof ServerPlayer attacker
                && behaviorPhase() != BehaviorPhase.BOSS
                && behaviorPhase() != BehaviorPhase.RETREATING)
            reactToMazeHit(attacker);
        if (behaviorPhase() == BehaviorPhase.BOSS) {
            if (bossStage == BossStage.DEFEATED || amount <= 0.0F
                    || !(source.getEntity() instanceof Player)) return false;
            boolean exposed = riposteTicks > 0;
            if (bossPressureWindowTicks <= 0) bossPressureHits = 0;
            bossPressureWindowTicks = 34;
            bossPressureHits = Math.min(8, bossPressureHits + 1);
            if (source.getEntity() instanceof ServerPlayer attacker)
                reactToBossHit(level, attacker);
            if (bossStage == BossStage.PILLARS) {
                playSound(SoundEvents.RAVAGER_HURT, 1.45F, 0.48F);
                return true;
            }
            float spamResistance = 1.0F / (1.0F + Math.max(0, bossPressureHits - 3) * 0.16F);
            float dealt = Math.max(0.9F, amount * (exposed ? 1.8F : 0.72F) * spamResistance);
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
        setHealth(getMaxHealth());
        if (behaviorPhase() == BehaviorPhase.HUNTING) beginWarning();
        if (behaviorPhase() == BehaviorPhase.WARNING || behaviorPhase() == BehaviorPhase.CHASING) {
            playSound(SoundEvents.RAVAGER_HURT, 1.8F, Math.max(0.42F, 0.72F - rage() * 0.025F));
            if (source.getEntity() instanceof Player) {
                repelDamage += Mth.ceil(amount);
                if (repelThreshold > 0 && repelDamage >= repelThreshold) {
                    playRoar(3.0F, 0.84F, 0.88F);
                    beginRetreat(true);
                }
            }
        }
        return true;
    }

    private void reactToBossHit(ServerLevel level, ServerPlayer attacker) {
        if (hitReactionCooldown > 0) return;
        hitReactionCooldown = 36;
        if (random.nextFloat() > 0.28F) return;
        Vec3 away = attacker.position().subtract(position());
        Vec3 horizontal = new Vec3(away.x, 0.0D, away.z);
        if (horizontal.lengthSqr() < 0.01D) horizontal = Vec3.directionFromRotation(0.0F, getYRot());
        horizontal = horizontal.normalize();
        boolean counterGrab = bossAttack == BossAttack.NONE && distanceTo(attacker) <= 5.2D
                && attackReady(BossAttack.GRAB) && random.nextFloat() < 0.38F;
        startHitBackoff(attacker, 9, counterGrab);
        attacker.setDeltaMovement(horizontal.scale(0.62D).add(0.0D, 0.12D, 0.0D));
        attacker.hurtMarked = true;
        level.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() * 0.62D, getZ(),
                12, 0.8D, 1.0D, 0.8D, 0.12D);
        bossPressureHits = Math.max(bossPressureHits, 4);
        bossAttackCooldown = Math.min(bossAttackCooldown, 5);
    }

    private void reactToMazeHit(ServerPlayer attacker) {
        if (hitReactionCooldown > 0 || mazeGrabTicks > 0) return;
        hitReactionCooldown = 14;
        if (behaviorPhase() == BehaviorPhase.ROAMING || behaviorPhase() == BehaviorPhase.HUNTING
                || behaviorPhase() == BehaviorPhase.WARNING)
            beginChase(attacker);
        boolean counterGrab = distanceTo(attacker) <= 5.2D && attackCooldown <= 0
                && random.nextFloat() < 0.72F;
        startHitBackoff(attacker, 11, counterGrab);
        playSound(SoundEvents.RAVAGER_HURT, 2.0F,
                Math.max(0.38F, 0.66F - rage() * 0.018F));
    }

    private void applyBossCollisionDamage(ServerLevel level, boolean pillar) {
        increaseRage(pillar ? 2 : 1);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, getX(), getY() + getBbHeight() * 0.58D,
                getZ(), pillar ? 20 : 10, 0.9D, 1.2D, 0.9D, 0.08D);
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    private static double horizontalDistanceToArenaCenter(Vec3 position) {
        Vec3 center = WorldGenerator.bossArenaCenter();
        double dx = position.x - center.x;
        double dz = position.z - center.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void containArenaEscape(ServerLevel level, ServerPlayer player) {
        Vec3 center = WorldGenerator.bossArenaCenter();
        Vec3 inward = center.subtract(player.position());
        Vec3 horizontal = new Vec3(inward.x, 0.0D, inward.z);
        if (horizontal.lengthSqr() < 0.01D) horizontal = new Vec3(1.0D, 0.0D, 0.0D);
        horizontal = horizontal.normalize();
        player.setDeltaMovement(horizontal.scale(1.45D).add(0.0D,
                player.getY() > center.y + 12.0D ? -0.72D : 0.18D, 0.0D));
        player.hurtMarked = true;
        player.resetFallDistance();
        MazeZapPayload zap = new MazeZapPayload(player.getId(),
                new Vec3(AsterionConfig.INSTANCE.deadSunX, AsterionConfig.INSTANCE.deadSunHeight,
                        AsterionConfig.INSTANCE.deadSunZ), Vec3.ZERO, 18);
        for (ServerPlayer viewer : level.players())
            if (ServerPlayNetworking.canSend(viewer, MazeZapPayload.TYPE))
                ServerPlayNetworking.send(viewer, zap);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0D,
                player.getZ(), 24, 0.55D, 0.9D, 0.55D, 0.14D);
        player.hurtServer(level, damageSources().magic(), 4.0F);
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
        playRoar(5.0F, 0.58F, 1.65F);
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
        return super.shouldBeSaved();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    public AnimationState animationState() {
        BossAttack renderedAttack = bossAttackState();
        int renderedAttackTicks = getEntityData().get(DATA_BOSS_ATTACK_TICKS);
        if (renderedAttack == BossAttack.GRAB && behaviorPhase() != BehaviorPhase.BOSS)
            return AnimationState.IDLE;
        if (behaviorPhase() == BehaviorPhase.BOSS) {
            if (renderedAttack == BossAttack.CHARGE || renderedAttack == BossAttack.RED_LIGHTNING_CHARGE
                    || renderedAttack == BossAttack.STAMPEDE || renderedAttack == BossAttack.PAWING)
                return renderedAttack == BossAttack.PAWING
                        || renderedAttackTicks < (renderedAttack == BossAttack.CHARGE
                                ? getEntityData().get(DATA_CHARGE_WINDUP) : 32)
                        ? AnimationState.WARNING : AnimationState.CHASE;
            if (renderedAttack == BossAttack.HORN_RAM) return AnimationState.HORN;
            if (renderedAttack == BossAttack.CLEAVE || renderedAttack == BossAttack.BACK_KICK
                    || renderedAttack == BossAttack.ARENA_SWEEP) return AnimationState.ATTACK;
            if (renderedAttack == BossAttack.SLAM) return AnimationState.VERTICAL_ATTACK;
            if (renderedAttack == BossAttack.LEAP || renderedAttack == BossAttack.RAGDOLL_STOMP)
                return AnimationState.LEAP;
            if (renderedAttack == BossAttack.GRAB) return AnimationState.IDLE;
            if (renderedAttack == BossAttack.SWORD_COMBO
                    || renderedAttack == BossAttack.WALL_SHOVE || renderedAttack == BossAttack.RUBBLE_THROW)
                return AnimationState.SWORD;
            if (renderedAttack == BossAttack.SPIN_COMBO) return AnimationState.SPIN;
            if (renderedAttack == BossAttack.CHAIN_GRAPPLE || renderedAttack == BossAttack.ARROW_RETURN)
                return AnimationState.CHAIN;
            if (renderedAttack == BossAttack.PUNCH_COMBO) return AnimationState.PUNCH;
        }
        if (swinging) return AnimationState.ATTACK;
        if (behaviorPhase() == BehaviorPhase.WARNING) return AnimationState.WARNING;
        if (behaviorPhase() == BehaviorPhase.CHASING)
            return corridorChargeTicks > 0 && corridorChargeTicks < 18
                    ? AnimationState.WARNING : AnimationState.CHASE;
        return getDeltaMovement().horizontalDistanceSqr() > 1.0E-4D ? AnimationState.WALK : AnimationState.IDLE;
    }

    public boolean isPerformingGrab() {
        return bossAttackState() == BossAttack.GRAB;
    }

    public boolean isPerformingReach() {
        BossAttack attack = bossAttackState();
        return attack == BossAttack.GRAB || attack == BossAttack.CHAIN_GRAPPLE;
    }

    public boolean isHornRamming() {
        return bossAttackState() == BossAttack.HORN_RAM;
    }

    public boolean isSpineCharging() {
        BossAttack attack = bossAttackState();
        return attack == BossAttack.HORN_RAM || attack == BossAttack.CHARGE
                || attack == BossAttack.STAMPEDE || attack == BossAttack.RED_LIGHTNING_CHARGE
                || attack == BossAttack.RAGDOLL_STOMP;
    }

    public boolean isPunchingCombo() {
        return bossAttackState() == BossAttack.PUNCH_COMBO;
    }

    public boolean isGreekFireLaserActive() {
        return bossAttackState() == BossAttack.GREEK_FIRE_LASER;
    }

    public int bossAttackAnimationTicks() {
        return getEntityData().get(DATA_BOSS_ATTACK_TICKS);
    }

    public int grabAttackTicks() {
        return isPerformingReach() ? getEntityData().get(DATA_BOSS_ATTACK_TICKS) : 0;
    }

    public int grabTargetEntityId() {
        return getEntityData().get(DATA_GRAB_TARGET_ID);
    }

    public int reachArmSide() {
        return getEntityData().get(DATA_REACH_ARM);
    }

    private void setBossAttack(BossAttack attack) {
        bossAttack = attack;
        getEntityData().set(DATA_BOSS_ATTACK, attack.ordinal());
        if (attack == BossAttack.NONE) {
            getEntityData().set(DATA_BOSS_ATTACK_TICKS, 0);
            getEntityData().set(DATA_GRAB_TARGET_ID, -1);
            getEntityData().set(DATA_REACH_ARM, 0);
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
        controllers.add(new AnimationController<MinotaurEntity>("movement", 8, test -> {
            RawAnimation animation = switch (animationState()) {
                case ATTACK -> ATTACK_ANIMATION;
                case VERTICAL_ATTACK -> VERTICAL_ATTACK_ANIMATION;
                case SWORD -> SWORD_ANIMATION;
                case SPIN -> SPIN_ANIMATION;
                case LEAP -> LEAP_ANIMATION;
                case CHAIN -> CHAIN_ANIMATION;
                case PUNCH -> PUNCH_ANIMATION;
                case HORN -> HORN_ANIMATION;
                case WARNING -> WARNING_ANIMATION;
                case CHASE -> RUN_ANIMATION;
                case WALK -> WALK_ANIMATION;
                case IDLE -> IDLE_ANIMATION;
            };
            if (animationState() == AnimationState.PUNCH)
                test.setControllerSpeed(1.5F + rage() * 0.012F);
            else if (animationState() == AnimationState.CHAIN)
                test.setControllerSpeed(1.25F + rage() * 0.010F);
            else if (animationState() == AnimationState.CHASE)
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
        private float angularVelocity;
        private float lastError;
        private HeavyMoveControl(MinotaurEntity minotaur) { super(minotaur); this.minotaur = minotaur; }

        @Override
        public void tick() {
            float previousYaw = minotaur.getYRot();
            super.tick();
            BehaviorPhase phase = minotaur.behaviorPhase();
            if (phase != BehaviorPhase.CHASING && phase != BehaviorPhase.BOSS
                    && phase != BehaviorPhase.ROAMING && phase != BehaviorPhase.HUNTING) {
                angularVelocity *= 0.55F;
                return;
            }
            float desiredYaw = minotaur.getYRot();
            float error = Mth.wrapDegrees(desiredYaw - previousYaw);
            float turnRate = switch (phase) {
                case CHASING -> 8.2F + minotaur.rage() * 0.18F;
                case BOSS -> 4.4F + minotaur.rage() * 0.13F;
                default -> 3.6F + minotaur.rage() * 0.08F;
            };
            float targetVelocity = Mth.clamp(error, -turnRate, turnRate);
            if (Math.signum(error) != Math.signum(lastError) && Math.abs(error) > 7.0F)
                angularVelocity *= 0.28F;
            angularVelocity = Mth.lerp(0.34F, angularVelocity, targetVelocity);
            if (Math.abs(error) < 0.45F) angularVelocity *= 0.42F;
            float smoothed = previousYaw + angularVelocity;
            minotaur.setYRot(smoothed);
            minotaur.setYBodyRot(smoothed);
            lastError = error;
        }
    }
}
