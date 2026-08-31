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
    private static final EntityDataAccessor<Integer> DATA_DOOR_ENTRY_TICKS = SynchedEntityData.defineId(
            MinotaurEntity.class, EntityDataSerializers.INT);
    private boolean doorEntryStarted;
    private BlockPos entryDoor;
    private net.minecraft.core.Direction entryFacing;
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("roar");
    private static final RawAnimation ROAR_START_ANIMATION = RawAnimation.begin().thenPlayAndHold("roar_start");
    private static final RawAnimation CHARGE_RUN_ANIMATION = RawAnimation.begin().thenLoop("run charge attack");
    private static final RawAnimation PUNCH_SINGLE_ANIMATION = RawAnimation.begin().thenPlayAndHold("punch_single");
    public static final int GRAPPLE_YANK_TICK = 25; // Authored frame 30 at 24 fps.
    private static final double GRAPPLE_CATCH_DISTANCE = 3.0;
    public static final int ROAR_START_TICKS = 150;
    public static final double WALK_BLOCKS_PER_SECOND = 4, RUN_BLOCKS_PER_SECOND = 7;
    private static final int[] COMBO_STRIKE_TICKS = MinotaurAnimationTiming.COMBO_HITS;
    private static final RawAnimation WALK_ANIMATION = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN_ANIMATION = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation WARNING_ANIMATION = RawAnimation.begin().thenPlayAndHold("charge_start");
    private static final RawAnimation ATTACK_ANIMATION = RawAnimation.begin().thenPlayAndHold("swing_axe_horizontal");
    private static final RawAnimation VERTICAL_ATTACK_ANIMATION = RawAnimation.begin().thenPlayAndHold("swing_axe_vertical");
    private static final RawAnimation AXE_CHOP_ANIMATION = RawAnimation.begin().thenPlayAndHold("swing_axe_vertical");
    private static final RawAnimation AXE_THROW_ANIMATION = RawAnimation.begin().thenPlayAndHold("axe_throw");
    private static final RawAnimation SWORD_ANIMATION = RawAnimation.begin().thenPlayAndHold("swing_swords_combo");
    private static final RawAnimation SPIN_ANIMATION = RawAnimation.begin().thenLoop("swing_swords_spinning_combo");
    private static final RawAnimation LEAP_ANIMATION = RawAnimation.begin().thenPlayAndHold("leep");
    private static final RawAnimation LAND_ANIMATION = RawAnimation.begin().thenPlayAndHold("asterion_leap_land");
    private static final RawAnimation CHAIN_ANIMATION = RawAnimation.begin().thenPlayAndHold("chain_grapple");
    private static final RawAnimation PUNCH_ANIMATION = RawAnimation.begin().thenPlayAndHold("punch combo");
    private static final RawAnimation DRAW_SWORD_ANIMATION = RawAnimation.begin().thenPlayAndHold("pull_sword_out");
    private static final RawAnimation DRAW_AXE_ANIMATION = RawAnimation.begin().thenPlayAndHold("pull_axe_from_back");
    private static final RawAnimation SHEATHE_SWORD_ANIMATION = RawAnimation.begin().thenPlayAndHold("asterion_sheathe_swords");
    private static final RawAnimation SHEATHE_AXE_ANIMATION = RawAnimation.begin().thenPlayAndHold("asterion_sheathe_axe");
    private static final RawAnimation RUBBLE_ANIMATION = RawAnimation.begin().thenPlayAndHold("rubble_throw");
    private static final RawAnimation DIES_ANIMATION = RawAnimation.begin().thenPlayAndHold("dies");
    private static final RawAnimation REVIVE_ANIMATION = RawAnimation.begin().thenPlayAndHold("asterion_revive");
    public static final int DRAW_SWORD_TICKS = 34, DRAW_AXE_TICKS = 24, AXE_CHOP_HIT_TICK = 26;
    private static final RawAnimation BELCH_ANIMATION = RawAnimation.begin().thenPlayAndHold("asterion_smoke_belch");
    private static final RawAnimation BACK_KICK_ANIMATION = RawAnimation.begin().thenPlayAndHold("asterion_back_kick");
    private int stompRecoveryCooldown = 48;
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
    private static final EntityDataAccessor<Integer> DATA_CORRIDOR_CHARGE_TICKS = SynchedEntityData.defineId(
            MinotaurEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_GRAB_TARGET_ID = SynchedEntityData.defineId(
            MinotaurEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HELD_PLAYER = SynchedEntityData.defineId(
            MinotaurEntity.class, EntityDataSerializers.INT);
    private UUID thrownPlayer;
    private boolean grapplePull;
    private int weaponUsesRemaining, weaponAdvanceTicks;
    private final int[] attacksSinceUse = new int[BossAttack.values().length];
    private Vec3 throwVelocity = Vec3.ZERO;
    private int throwFlightTicks;
    private Vec3 throwWallImpact;
    private boolean throwPursuitPending;
    private float closeBurstDamage;
    private int closeBurstTicks;
    private boolean hornKnockback;
    private double hornTravel;
    private double hornTravelLimit;
    private static final EntityDataAccessor<Integer> DATA_WEAPON = SynchedEntityData.defineId(MinotaurEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LEAP_LANDING = SynchedEntityData.defineId(MinotaurEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_WEAPON_FROM = SynchedEntityData.defineId(MinotaurEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_WEAPON_SWAP = SynchedEntityData.defineId(MinotaurEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_AXE_OUT = SynchedEntityData.defineId(MinotaurEntity.class, EntityDataSerializers.BOOLEAN);
    private UUID thrownAxe;
    private Vec3 axeLastPosition = Vec3.ZERO;
    private final java.util.Map<BlockPos, Integer> fireSootTrail = new java.util.LinkedHashMap<>();
    private int axeAge;
    private Vec3 axePickupGoal;
    private double axePickupBest = Double.MAX_VALUE;
    private int axePickupStall;
    public int weaponMode() { return getEntityData().get(DATA_WEAPON); } // 0: free hands, 1: axe, 2: swords
    public int weaponSwapTicks() { return getEntityData().get(DATA_WEAPON_SWAP); }
    public boolean axeInWorld() { return getEntityData().get(DATA_AXE_OUT); }
    public boolean isAxeAttackActive() { return weaponSwapTicks() == 0 && requiresAxe(bossAttackState()); }
    public double rageCooldownMultiplier() { return .84D - Math.min(12, rage()) * .037D; }
    private Vec3 wallPinPoint;
    private int wallPinTicks;
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
    private int lowSnagTicks;
    private int bossObstacleTicks;
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
    private boolean debugMode, debugAutomatic = true, debugPaused;
    private Vec3 debugOrigin = Vec3.ZERO;
    private String debugDecision = "Starting debug encounter";
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
    private AnimationState clientAnimationPose;
    private double clientPoseStartAge;
    private double clientPoseStartTick;
    private double clientPoseAge;
    private final MinotaurSmokeClouds smokeClouds = new MinotaurSmokeClouds();
    private long regenerationDeadline;
    private int clientMovingUntil;
    private MinotaurLeapPlan leapPlan;
    private int leapFlightTick, obstacleLeapRetryTick;
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

    /** Debug encounters never save, consume arena state, or trigger the real boss finale. */
    @Override public boolean shouldBeSaved() { return !debugMode && super.shouldBeSaved(); }
    public boolean isDebugMinotaur() { return debugMode; }
    public void beginDebug(ServerPlayer owner) {
        debugMode = true; debugOrigin = position(); eclipseTarget = owner.getUUID();
        beginBossIntercept(owner);
        setBossStage(BossStage.EXTREME);
        debugAutomatic = true; debugPaused = false;
        bossAttackCooldown = 40;
        setPersistenceRequired();
    }
    public static java.util.List<String> debugAttackNames() {
        return Arrays.stream(BossAttack.values()).filter(MinotaurEntity::enabledAttack)
                .map(a -> a.name().toLowerCase(java.util.Locale.ROOT)).toList();
    }
    public boolean forceDebugAttack(ServerPlayer owner, String name) {
        if (!debugMode || !owner.getUUID().equals(eclipseTarget) || bossAttack != BossAttack.NONE) return false;
        BossAttack attack;
        try { attack = BossAttack.valueOf(name.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException error) { return false; }
        if (!enabledAttack(attack)) return false;
        debugPaused = false; debugAutomatic = false;
        if (attack == BossAttack.ARROW_RETURN) storedArrows = 5;
        debugDecision = "Forced by debug command";
        beginBossAttack(owner, attack);
        return true;
    }
    public void setDebugRunning(boolean automatic) {
        debugAutomatic = automatic; debugPaused = !automatic;
        debugDecision = automatic ? "Automatic attack selection enabled" : "Paused by debug command";
    }
    public void stopDebug() {
        if (thrownAxe != null && level() instanceof ServerLevel server) {
            var axe = server.getEntity(thrownAxe); if (axe != null) axe.discard();
        }
        if (level() instanceof ServerLevel level) clearBossFire(level);
        healthBossBar.removeAllPlayers(); rageBossBar.removeAllPlayers(); discard();
    }
    public String debugStatus() {
        var target = debugMode && eclipseTarget != null ? level().getPlayerByUUID(eclipseTarget) : getTarget();
        String distance = target == null ? "none" : String.format(java.util.Locale.ROOT, "%.1fm", distanceTo(target));
        return "State=" + (debugPaused ? "PAUSED" : behaviorPhase()) + "/" + bossStage
                + " | weapon=" + (weaponMode() == 1 ? "AXE" : weaponMode() == 2 ? "SWORDS" : "FREE_HANDS")
                + " swap=" + weaponSwapTicks() + " axeOut=" + axeInWorld()
                + " | attack=" + bossAttack + " tick=" + (bossAttack == BossAttack.NONE ? 0 : bossAttackTicks) + " cooldown=" + bossAttackCooldown
                + " | HP=" + Math.round(getHealth()) + "/" + Math.round(getMaxHealth()) + " rage=" + rage()
                + " | target=" + (target == null ? "none" : target.getPlainTextName()) + " range=" + distance
                + " LOS=" + (target != null && hasLineOfSight(target)) + " | Decision: " + currentDecision();
    }
    public String debugStateKey() { return debugPaused + "/" + bossStage + "/" + bossAttack + "/" + currentDecision()
            + "/" + weaponMode() + "/" + weaponSwapTicks() / 10 + "/" + axeInWorld(); }
    private String currentDecision() {
        if (debugMode) return debugDecision;
        if (doorEntryTicks() > 0) return "Breaching the sealed boss entrance";
        if (bossStage == BossStage.COLLAPSE) return collapseTicks < 118 ? "Collapsed beneath rubble" : "Reviving through smoke";
        if (bossAttack == BossAttack.RETRIEVE_AXE) return axePickupGoal == null ? "Searching for a reachable pickup spot" : "Following a path to the axe";
        if (weaponSwapTicks() > 0) return "Changing weapons before attacking";
        if (bossAttack != BossAttack.NONE) return "Executing " + bossAttack;
        if (bossAttackCooldown > 0) return "Recovering and closing distance";
        return "Selecting by range, line of sight, rage and attack cooldowns";
    }

    private void tickDebugBoss(ServerLevel level) {
        var owner = level.getPlayerByUUID(eclipseTarget);
        if (!(owner instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()) {
            debugDecision = "Owner unavailable; ending test"; stopDebug(); return;
        }
        setTarget(player);
        if (!debugPaused) tickRegeneration(level);
        if (debugPaused) { getNavigation().stop(); setDeltaMovement(Vec3.ZERO); return; }
        for (int i = 0; i < bossAttackLockouts.length; i++) if (bossAttackLockouts[i] > 0) bossAttackLockouts[i]--;
        trackedPlayerVelocity = trackedPlayerVelocity.lerp(player.getDeltaMovement().multiply(1, 0, 1), .24);
        getLookControl().setLookAt(player, 12, 12);
        if (bossAttack != BossAttack.NONE) {
            tickBossAttack(level, player);
            if (bossAttackTicks > 240) { finishBossAttack(20); debugDecision = "Attack timeout; resetting"; }
            return;
        }
        if (tickPendingCombos(level)) { debugDecision = "Throw follow-up: " + bossAttack; return; }
        if (!debugAutomatic) { getNavigation().stop(); debugDecision = "Waiting for an attack command"; return; }
        if (bossAttackCooldown > 0) {
            bossAttackCooldown--;
            debugDecision = "Recovering; approaching target if out of reach";
            if (distanceTo(player) > 5) getNavigation().moveTo(player, .8);
            return;
        }
        BossAttack selected = chooseExtremeAttack(player, distanceTo(player));
        if (selected == BossAttack.NONE) {
            getNavigation().moveTo(player, .8); bossAttackCooldown = 8;
            debugDecision = "No eligible attack at current range/cooldowns; repositioning";
        } else {
            debugDecision = "Selected " + selected + " using range, cooldowns and recent attacks";
            beginBossAttack(player, selected);
        }
        playHeavySteps();
    }
    private int clearCombatObstacle(ServerLevel level, AABB bounds) {
        return 0; // Arena architecture remains intact during combat.
    }
    private Vec3 combatPoint(Vec3 point) { return debugMode ? point : WorldGenerator.clampBossArena(point); }
    private Vec3 combatCenter() { return debugMode ? debugOrigin : WorldGenerator.bossArenaCenter(); }
    private int breakCombatWall(ServerLevel level, AABB bounds, MinotaurEntity boss) {
        return 0; // Only scripted pillar and entrance debris may change the arena.
    }
    private void scarArena(ServerLevel level, Vec3 point, int radius) {
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            net.krodark.asterion.worldgen.ArenaDebris.queue(level, point.add(Math.cos(angle) * 2, .8, Math.sin(angle) * 2),
                    new Vec3(Math.cos(angle) * .35, .4, Math.sin(angle) * .35));
        }
    }

    public enum BehaviorPhase { DORMANT, ROAMING, HUNTING, WARNING, CHASING, RETREATING, BOSS }
    private enum StalkMode { PATROLLING, SHADOWING, OBSERVING, FLANKING, INTERCEPTING, VANISHING }
    private enum BossAttack { NONE, CLEAVE, CHARGE, SLAM, LEAP, SWORD_COMBO, SPIN_COMBO, GRAB,
        RED_LIGHTNING_CHARGE, PAWING, STAMPEDE, BACK_KICK, ARENA_SWEEP, RUBBLE_THROW, WALL_SHOVE,
        FIRE_RINGS, CHAIN_GRAPPLE,
        PUNCH_COMBO, HORN_RAM, RAGDOLL_STOMP, ARROW_RETURN, GREEK_FIRE_LASER, AXE_THROW, RETRIEVE_AXE, AXE_CHOP, PUNCH_SINGLE, SMOKE_BELCH }
    private enum BossStage { PILLARS, COLLAPSE, EXTREME, DEFEATED }
    private enum CombatRange { CLOSE, MEDIUM, FAR }
    private enum GrabThrowStyle { ARENA, SKY }
    public enum AnimationState { IDLE, WALK, WARNING, CHASE, ATTACK, VERTICAL_ATTACK, SWORD, SPIN,
        LEAP, CHAIN, PUNCH, HORN, AXE_CHOP, AXE_THROW, ROAR_START, CHARGE_RUN, PUNCH_SINGLE, LAND, DRAW_SWORD, DRAW_AXE, SHEATHE_SWORD, SHEATHE_AXE, RUBBLE, DIES, REVIVE, BELCH, BACK_KICK }

    public MinotaurEntity(EntityType<? extends MinotaurEntity> type, Level level) {
        super(type, level);
        xpReward = 35;
        moveControl = new HeavyMoveControl(this);
        lookControl = new net.minecraft.world.entity.ai.control.LookControl(this) {
            @Override public void tick() {
                if (bossAttackState() != BossAttack.NONE && bossAttackState() != BossAttack.RETRIEVE_AXE
                        || doorEntryTicks() > 0 || corridorChargeTicks > 0) {
                    yHeadRot = getYRot(); setXRot(0); return;
                }
                super.tick();
            }
        };
        getNavigation().setMaxVisitedNodesMultiplier(AsterionConfig.INSTANCE.minotaurPathfindingMultiplier);
        getNavigation().setRequiredPathLength(256.0F);
        setPathfindingMalus(PathType.WATER, 0.0F);
        setPathfindingMalus(PathType.WATER_BORDER, 0.0F);
    }

    @Override protected net.minecraft.world.entity.ai.control.BodyRotationControl createBodyControl() {
        return new net.minecraft.world.entity.ai.control.BodyRotationControl(this) {
            @Override public void clientTick() {
                if (bossAttackState() != BossAttack.NONE || doorEntryTicks() > 0
                        || getEntityData().get(DATA_CORRIDOR_CHARGE_TICKS) > 0) {
                    yBodyRot = getYRot(); return;
                }
                super.clientTick();
            }
        };
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
        builder.define(DATA_CORRIDOR_CHARGE_TICKS, 0);
        builder.define(DATA_DOOR_ENTRY_TICKS, 0);
        builder.define(DATA_GRAB_TARGET_ID, -1);
        builder.define(DATA_HELD_PLAYER, -1);
        builder.define(DATA_WEAPON, 0);
        builder.define(DATA_WEAPON_SWAP, 0);
        builder.define(DATA_WEAPON_FROM, 0);
        builder.define(DATA_LEAP_LANDING, -1);
        builder.define(DATA_AXE_OUT, false);
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
        if (!level.addFreshEntity(minotaur)) return null;
        minotaur.emitShadowArrival(level);
        return minotaur;
    }

    public static MinotaurEntity spawnRoamer(ServerLevel level, ServerPlayer player) {
        MinotaurEntity existing = existingMinotaur(level);
        if (existing != null) {
            if (existing.behaviorPhase() != BehaviorPhase.BOSS && existing.distanceTo(player) > 72.0D)
                existing.beginRoaming(player);
            return existing;
        }
        MinotaurEntity minotaur = Asterion.MINOTAUR.create(level, EntitySpawnReason.EVENT);
        if (minotaur == null) return null;
        Vec3 spawn = minotaur.findHiddenSpawnPosition(player);
        if (spawn == null) return null;
        minotaur.setPos(spawn.x, spawn.y, spawn.z);
        minotaur.setPersistenceRequired();
        minotaur.beginRoaming(player);
        if (!level.addFreshEntity(minotaur)) return null;
        minotaur.emitShadowArrival(level);
        return minotaur;
    }

    private static MinotaurEntity existingMinotaur(ServerLevel level) {
        for (Entity entity : level.getAllEntities())
            if (entity instanceof MinotaurEntity minotaur && minotaur.isAlive() && !minotaur.isRemoved())
                return minotaur;
        return null;
    }

    public static MinotaurEntity activateCenterBoss(ServerLevel level, ServerPlayer player,
                                                      MinotaurEntity existing, net.minecraft.core.Direction playerEntrance) {
        MinotaurEntity minotaur = existing == null
                ? Asterion.MINOTAUR.create(level, EntitySpawnReason.EVENT) : existing;
        if (minotaur == null) return null;
        // Additional party members must not teleport or restart an already active boss.
        if (minotaur.doorEntryStarted) return minotaur;
        Vec3 center = WorldGenerator.bossArenaCenter();
        minotaur.setPos(center.x, center.y, center.z);
        minotaur.setPersistenceRequired();
        minotaur.eclipseTarget = player.getUUID();
        minotaur.beginBossIntercept(player);
        minotaur.beginDoorEntry(playerEntrance);
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
        setRage(12);
        rageCalmTicks = 360;
        gazeTriggerTicks = random.nextIntBetweenInclusive(140, 200);
        previousPosition = position();
        previousTargetDistance = distanceTo(player);
        lastKnownPlayerPosition = player.position();
        pursuitDetectionTicks = 0;
        shadowRelocateCooldown = 0;
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
        if (closeBurstTicks > 0 && --closeBurstTicks == 0) closeBurstDamage = 0;
        tickWorldAxe(level);
        tickFireSootTrail(level);
        tickThrownPlayer(level);
        if (behaviorPhase() == BehaviorPhase.BOSS && bossStage != BossStage.COLLAPSE
                && bossStage != BossStage.DEFEATED && doorEntryTicks() == 0 && isAlive()) smokeClouds.tick(level, this);
        else smokeClouds.clear();
        if (debugMode) { tickDebugBoss(level); return; }
        if (tickDoorEntry(level)) return;
        breakEntanglingCobwebs(level);
        tickHeavyLanding(level);
        avoidWallHugging(level);
        tickLowObstacleRecovery(level);
        tickBossObstacleTraversal(level);
        if (sightingCooldown > 0) sightingCooldown--;
        if (paranoiaCooldown > 0) paranoiaCooldown--;
        if (attackCooldown > 0) attackCooldown--;
        if (hitReactionCooldown > 0) hitReactionCooldown--;
        if (corridorChargeCooldown > 0) corridorChargeCooldown--;
        if (rageCalmTicks > 0) rageCalmTicks--;
        else if (rage() > 0 && (tickCount % 160) == 0) {
            int floor = DeadSunEventSystem.isEclipseActive(level)
                    && (behaviorPhase() == BehaviorPhase.HUNTING || behaviorPhase() == BehaviorPhase.CHASING)
                    ? 12 : behaviorPhase() == BehaviorPhase.BOSS && bossStage == BossStage.EXTREME ? 4 : 0;
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
            // The arena encounter can only be activated by crossing a keyed entrance.
            beginRetreat(false);
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
        if (discovered && distance <= 52.0D && stalkingHallwaySpan() >= 34.0D) {
            beginWarning(false);
            return;
        }
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
                if (sightings > 3 || random.nextInt(rage() >= 12 ? 3 : 6) == 0) {
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

        if (!observed && shadowRelocateCooldown <= 0 && (distance > 58.0D || distance < 26.0D)
                && phaseTicks % 20 == 0 && (distance > 58.0D || random.nextInt(3) == 0)
                && tryShadowRelocation(level, player)) return;

        if (observed && stalkMode != StalkMode.OBSERVING && stalkMode != StalkMode.VANISHING)
            enterStalkMode(StalkMode.OBSERVING);
        if (stalkModeTicks <= 0) {
            enterStalkMode(nextStalkMode());
        }

        if (observed && stalkMode == StalkMode.OBSERVING && gazeTicks > 5 && random.nextInt(55) == 0) {
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
            else playSound(Asterion.MINOTAUR_STEP, 0.75F, 0.55F + random.nextFloat() * 0.12F);
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
        if (distance > 48.0D && phaseTicks % 40 == 0 && getNavigation().isDone()) {
            Vec3 toward = player.position().subtract(position());
            Vec3 direction = new Vec3(toward.x, 0.0D, toward.z);
            if (direction.lengthSqr() > 0.01D) {
                AABB breach = getBoundingBox().expandTowards(direction.normalize().scale(1.7D))
                        .inflate(0.35D, 0.55D, 0.35D);
                int broken = WorldGenerator.breakPlayerBlocksAround(level, breach);
                if (broken == 0 && failedStalkingTicks > 0)
                    broken = WorldGenerator.breakMazeWallAround(level, breach, this);
                if (broken > 0) {
                    playSound(SoundEvents.RAVAGER_ATTACK, 1.6F, 0.54F);
                    level.sendParticles(ParticleTypes.DUST_PLUME, breach.getCenter().x,
                            getY() + 1.0D, breach.getCenter().z, Math.min(18, broken * 2),
                            0.65D, 0.8D, 0.65D, 0.04D);
                    awarenessRepathTicks = 0;
                }
            }
        }
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
        warningTicks = ROAR_START_TICKS;
        getEntityData().set(DATA_BOSS_ATTACK_TICKS, 0);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        playSound(SoundEvents.GOAT_PREPARE_RAM, 2.6F, 0.42F);
    }

    private void tickWarning(ServerLevel level, ServerPlayer player) {
        getEntityData().set(DATA_BOSS_ATTACK_TICKS, ROAR_START_TICKS - warningTicks + 1);
        if (warningTicks == ROAR_START_TICKS - 20) playRoar(3F, .7F, .65F);
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().multiply(0.15D, 1.0D, 0.15D));
        getLookControl().setLookAt(player, 5.0F, 5.0F);
        if ((phaseTicks % 12) == 0) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.15D, getZ(),
                    5, 0.55D, 0.05D, 0.55D, 0.015D);
            playSound(Asterion.MINOTAUR_STEP, 1.6F, 0.52F);
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
        getEntityData().set(DATA_CORRIDOR_CHARGE_TICKS, 0);
        corridorChargeCooldown = random.nextIntBetweenInclusive(55, 110);
        setTarget(player);
        setAggressive(true);
        updateChaseSpeed();
        playRoar(3.2F, 1.0F, 0.82F);
    }

    private void tickChase(ServerLevel level, ServerPlayer player) {
        if (bossAttack == BossAttack.LEAP) { tickBossAttack(level, player); return; }
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
                || player.getY() > getY() + 4.5) && onGround() && distance < 32
                && tickCount >= obstacleLeapRetryTick) {
            obstacleLeapRetryTick = tickCount + 60;
            if (MinotaurLeapPlan.find(level, this, player.position()) != null) {
                corridorChargeTicks = 0;
                getEntityData().set(DATA_CORRIDOR_CHARGE_TICKS, 0);
                beginBossAttack(player, BossAttack.LEAP);
                stuckTicks = 0;
                return;
            }
            getNavigation().stop();
            chaseRouteTicks = 0;
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
        getEntityData().set(DATA_CORRIDOR_CHARGE_TICKS, 1);
        corridorChargeCooldown = Math.max(95, 190 - rage() * 6);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        getLookControl().setLookAt(player, 10.0F, 5.0F);
        playSound(SoundEvents.GOAT_PREPARE_RAM, 3.0F, 0.34F);
        return true;
    }

    private void tickCorridorCharge(ServerLevel level, ServerPlayer player) {
        faceDirection(corridorChargeDirection, 10);
        if (corridorChargeTicks >= 18) emitChargeSmoke(level, corridorChargeDirection);
        else if ((tickCount & 1) == 0)
            broadcastGroundTelegraph(level, new BossTelegraphPayload(position(), corridorChargeDirection,
                    30, 3, BossTelegraphPayload.CHARGE_LANE, getId(), 0, getBbWidth() * .5F + .8F, corridorChargeTicks / 18F));
        corridorChargeTicks++;
        getEntityData().set(DATA_CORRIDOR_CHARGE_TICKS, corridorChargeTicks);
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
        getEntityData().set(DATA_CORRIDOR_CHARGE_TICKS, 0);
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

    public int doorEntryTicks() { return getEntityData().get(DATA_DOOR_ENTRY_TICKS); }

    private void beginDoorEntry(net.minecraft.core.Direction playerEntrance) {
        doorEntryStarted = true;
        entryFacing = net.krodark.asterion.worldgen.MinotaurArenaEntrances.BOSS_ENTRANCE;
        entryDoor = net.krodark.asterion.worldgen.MinotaurArenaEntrances.door(entryFacing);
        if (!(level().getBlockEntity(entryDoor) instanceof net.krodark.asterion.block.MinotaurDoorBlockEntity)) return;
        // Leave room for the authored neck/head lunge as well as the collision body.
        Vec3 behind = Vec3.atBottomCenterOf(entryDoor)
                .add(entryFacing.getUnitVec3().scale(Math.max(5.5, getBbWidth() * .5 + 3.5)));
        setPos(behind.x, behind.y, behind.z);
        setDeltaMovement(Vec3.ZERO);
        getEntityData().set(DATA_DOOR_ENTRY_TICKS, 1);
    }

    private boolean tickDoorEntry(ServerLevel level) {
        int tick = doorEntryTicks();
        if (tick <= 0 || entryDoor == null || entryFacing == null) return false;
        getNavigation().stop();
        setTarget(null);
        setAggressive(false);
        Vec3 inward = entryFacing.getOpposite().getUnitVec3();
        float yaw = (float)(Math.atan2(-inward.x, inward.z) * Mth.RAD_TO_DEG);
        setYRot(yaw); setYHeadRot(yaw); yBodyRot = yaw;
        if (!WorldGenerator.isBossArenaReady()) { setDeltaMovement(Vec3.ZERO); return true; }
        int elapsed = tick - 1;
        if (tick == 1 && level.getBlockEntity(entryDoor) instanceof net.krodark.asterion.block.MinotaurDoorBlockEntity door)
            door.beginBreach();
        if (elapsed < 70) setDeltaMovement(Vec3.ZERO);
        else {
            if (elapsed == 70) {
                net.krodark.asterion.worldgen.MinotaurArenaEntrances.breakLintel(level, entryFacing, getBbHeight());
                if (level.getBlockEntity(entryDoor) instanceof net.krodark.asterion.block.MinotaurDoorBlockEntity door)
                    door.breakOff();
                playSound(Asterion.MINOTAUR_ROAR, 4F, .72F);
            }
            setNoGravity(false);
            Vec3 clearGate = Vec3.atBottomCenterOf(net.krodark.asterion.worldgen.MinotaurArenaEntrances.gate(entryFacing))
                    .add(inward.scale(getBbWidth() * .5 + 1.25));
            double remaining = clearGate.subtract(position()).dot(inward);
            setDeltaMovement(inward.scale(Math.clamp(remaining, 0, .42)).add(0, getDeltaMovement().y, 0));
        }
        if (elapsed >= ROAR_START_TICKS) {
            getEntityData().set(DATA_DOOR_ENTRY_TICKS, 0);
            bossAttackCooldown = 40;
            return false;
        }
        getEntityData().set(DATA_DOOR_ENTRY_TICKS, tick + 1);
        return true;
    }

    private void beginBossIntercept(ServerPlayer player) {
        getEntityData().set(DATA_CORRIDOR_CHARGE_TICKS, 0);
        setBehaviorPhase(BehaviorPhase.BOSS);
        setTarget(player);
        setAggressive(false);
        getNavigation().stop();
        setBossAttack(BossAttack.NONE);
        bossAttackCooldown = 55;
        interruptRegeneration();
        smokeClouds.clear();
        Arrays.fill(bossAttackLockouts, 0);
        Arrays.fill(attacksSinceUse, 0);
        weaponUsesRemaining = weaponAdvanceTicks = 0;
        setBossStage(BossStage.PILLARS);
        collapseTicks = 0;
        riposteTicks = 0;
        pillarOpportunityTicks = 0;
        grabbedPlayer = null;
        getEntityData().set(DATA_HELD_PLAYER, -1);
        thrownPlayer = null;
        throwWallImpact = null;
        grapplePull = false;
        wallPinTicks = 0;
        throwPursuitPending = false;
        wallPinPoint = null;
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
                Vec3 inward = combatCenter().subtract(position());
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
            Vec3 center = combatCenter();
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
        tickRegeneration(level);
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
        if (bossAttackCooldown <= 0 && weaponMode() != 0 && weaponUsesRemaining > 0
                && distance > 7 && distance < 24 && hasLineOfSight(player)
                && !(weaponMode() == 1 && distance > 12 && attackReady(BossAttack.AXE_THROW))
                && weaponAdvanceTicks++ < 45) {
            if (getNavigation().isDone() || phaseTicks % 12 == 0) getNavigation().moveTo(player, 1);
            return;
        }
        if (bossAttackCooldown <= 0) {
            BossAttack next;
            double bossRadius = Math.sqrt(getX() * getX() + getZ() * getZ());
            boolean pressuredAtWall = bossRadius > 27.0D && distance < 6.5D;
            cornerPressureTicks = pressuredAtWall ? Math.min(60, cornerPressureTicks + 1)
                    : Math.max(0, cornerPressureTicks - 2);
            if ((bossPressureHits >= 4 || cornerPressureTicks >= 24)
                    && (weaponMode() == 0 || shouldHornRam(player))
                    && (attackReady(BossAttack.PUNCH_COMBO)
                    || shouldHornRam(player)
                    || distance < 4.4D && attackReady(BossAttack.BACK_KICK))) {
                bossPressureHits = 0;
                cornerPressureTicks = 0;
                BossAttack pressureResponse = shouldHornRam(player)
                        ? BossAttack.HORN_RAM
                        : distance < 4.4D && attackReady(BossAttack.BACK_KICK)
                        ? BossAttack.BACK_KICK : BossAttack.PUNCH_COMBO;
                beginBossAttack(player, pressureResponse);
                return;
            }
            if (bossStage == BossStage.PILLARS) {
                if (phaseOnePillarTarget != null && pillarOpportunityTicks >= 18 && attackReady(BossAttack.CHARGE)
                        && lastBossAttack != BossAttack.CHARGE && weaponUsesRemaining == 0) {
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
                    Vec3 waypoint = hasLineOfSight(player) ? player.position() : WorldGenerator.bossArenaTacticalWaypoint(position(), player.position());
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
            beginBossAttack(player, next);
        } else if (distance > 4.5D && phaseTicks % 12 == 0) {
            Vec3 waypoint = hasLineOfSight(player) ? player.position() : WorldGenerator.bossArenaTacticalWaypoint(position(), player.position());
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
        if (target == null || !target.isAlive() || (!debugMode && target.isCreative()) || target.isSpectator()) {
            throwPursuitPending = false;
            wallComboTarget = null;
            wallComboWindow = 0;
            return false;
        }
        if (throwPursuitPending && distanceTo(target) > 6.25D) {
            BossAttack pursuit = attackReady(BossAttack.CHARGE) && hasClearChargeLane(target, true)
                    ? BossAttack.CHARGE
                    : attackReady(BossAttack.CHAIN_GRAPPLE) && hasLineOfSight(target)
                        ? BossAttack.CHAIN_GRAPPLE : BossAttack.NONE;
            if (pursuit != BossAttack.NONE) {
                throwPursuitPending = false;
                beginBossAttack(target, pursuit);
                return true;
            }
        }
        if (distanceTo(target) < 5.5 && attackReady(BossAttack.PUNCH_SINGLE)) {
            throwPursuitPending = false;
            wallComboWindow = 0;
            beginBossAttack(target, BossAttack.PUNCH_SINGLE);
            return true;
        }
        return false;
    }

    private void scheduleWallCombo(ServerPlayer player, int ticks) {
        wallComboTarget = player.getUUID();
        wallComboWindow = Math.max(wallComboWindow, ticks);
    }

    private boolean canCatchPlayer(ServerPlayer player) {
        double horizontal = player.position().subtract(position()).horizontalDistance();
        return !player.isSpectator() && player.isAlive() && horizontal <= 5.4D
                && player.getY() >= getY() - 1.5D && player.getY() <= getY() + getBbHeight()
                && (horizontal <= 3.2D || canArmReach(player));
    }

    private boolean shouldPrioritizeGrab(ServerPlayer player) {
        return attackReady(BossAttack.GRAB) && canCatchPlayer(player) && hasLineOfSight(player)
                && ((!player.onGround() && player.getY() - getY() >= 1.8D)
                || player.position().subtract(position()).horizontalDistance() <= 3.2D);
    }

    public int heldPlayerId() { return getEntityData().get(DATA_HELD_PLAYER); }

    private boolean nearThrowWall(ServerPlayer player) {
        return throwWallImpact != null && player.position().distanceToSqr(throwWallImpact) < 16;
    }

    public static boolean isHeld(Entity player) {
        return !player.level().getEntitiesOfClass(MinotaurEntity.class, player.getBoundingBox().inflate(16),
                boss -> boss.isAlive() && boss.heldPlayerId() == player.getId()).isEmpty();
    }

    public static boolean controlsPlayer(Entity player) {
        return !player.level().getEntitiesOfClass(MinotaurEntity.class, player.getBoundingBox().inflate(96),
                boss -> boss.isAlive() && (boss.heldPlayerId() == player.getId()
                        || player.getUUID().equals(boss.thrownPlayer)
                        || (boss.bossAttack == BossAttack.WALL_SHOVE && boss.wallPinTicks > 0
                            && player.getUUID().equals(boss.wallComboTarget)))).isEmpty();
    }

    private void tickThrownPlayer(ServerLevel level) {
        if (thrownPlayer == null) return;
        var found = level.getPlayerByUUID(thrownPlayer);
        if (!(found instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()
                || ++throwFlightTicks > (grapplePull ? 14 : 60)) { thrownPlayer = null; grapplePull = false; return; }
        if (grapplePull) {
            // One launch, then ordinary drag/collision. Clamp the last step so the yank cannot pass through the boss.
            double remaining = Math.max(0, player.position().subtract(position()).horizontalDistance() - GRAPPLE_CATCH_DISTANCE);
            double step = throwVelocity.horizontalDistance();
            if (step > remaining && step > .001)
                throwVelocity = new Vec3(throwVelocity.x * remaining / step, throwVelocity.y, throwVelocity.z * remaining / step);
        }
        Vec3 next = player.position().add(throwVelocity);
        BlockPos nextBlock = BlockPos.containing(next);
        if (!level.hasChunk(nextBlock.getX() >> 4, nextBlock.getZ() >> 4)) {
            thrownPlayer = null;
            grapplePull = false;
            return;
        }
        // Swept entity collision prevents tunnelling through thin walls at throw speed.
        if (hornKnockback) {
            double remaining = Math.max(0, hornTravelLimit - hornTravel);
            double horizontal = throwVelocity.horizontalDistance();
            if (horizontal > remaining) throwVelocity = new Vec3(throwVelocity.x * remaining / horizontal,
                    throwVelocity.y, throwVelocity.z * remaining / horizontal);
        }
        Vec3 beforeMove = player.position();
        player.setDeltaMovement(throwVelocity);
        player.move(net.minecraft.world.entity.MoverType.SELF, throwVelocity);
        if (hornKnockback) hornTravel += player.position().subtract(beforeMove).horizontalDistance();
        boolean wall = player.horizontalCollision;
        boolean landed = player.verticalCollision && throwVelocity.y < 0;
        player.teleportTo(player.getX(), player.getY(), player.getZ());
        player.resetFallDistance();
        throwVelocity = new Vec3(throwVelocity.x * .91, (throwVelocity.y - .08) * .98, throwVelocity.z * .91);
        boolean arrived = grapplePull && player.position().subtract(position()).horizontalDistance() <= GRAPPLE_CATCH_DISTANCE + .05;
        if (wall || landed || arrived || hornKnockback && hornTravel >= hornTravelLimit - .001) {
            thrownPlayer = null;
            throwVelocity = Vec3.ZERO;
            if (wall && !hornKnockback && !grapplePull) {
                throwWallImpact = player.position();
                // A nearby wall can be reached inside the release hit's immunity window.
                player.invulnerableTime = 0;
                player.hurtServer(level, damageSources().mobAttack(this), 10.0F);
                scheduleWallCombo(player, 150);
                debugDecision = "Throw hit a wall; pursuing with charge or punch";
                level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + .8, player.getZ(), 3, .4, .5, .4, .02);
                playSound(SoundEvents.PLAYER_BIG_FALL, 2F, .65F);
            }
            grapplePull = false;
        }
        player.setDeltaMovement(throwVelocity);
        player.hurtMarked = true;
        RagdollServerNetworking.forceAuthority(player, throwVelocity);
    }

    private void scheduleAirCatch(ServerPlayer player, int ticks) {
        airborneCatchTarget = player.getUUID();
        airborneCatchWindow = Math.max(airborneCatchWindow, ticks);
        bossAttackLockouts[BossAttack.GRAB.ordinal()] = 0;
    }

    private void syncBossBars(ServerLevel level) {
        for (ServerPlayer old : java.util.List.copyOf(healthBossBar.getPlayers()))
            if (!old.isAlive() || old.isRemoved() || !level.players().contains(old)) healthBossBar.removePlayer(old);
        for (ServerPlayer old : java.util.List.copyOf(rageBossBar.getPlayers()))
            if (!old.isAlive() || old.isRemoved() || !level.players().contains(old)) rageBossBar.removePlayer(old);
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
        return chooseCombatAttack(player, distance);
    }

    private BossAttack chooseExtremeAttack(ServerPlayer player, double distance) {
        return chooseCombatAttack(player, distance);
    }

    private BossAttack chooseCombatAttack(ServerPlayer player, double distance) {
        if (shouldHornRam(player)) return BossAttack.HORN_RAM;
        if (shouldPrioritizeGrab(player) && (player.getY() - getY() > 1.2 || weaponMode() == 0)) return BossAttack.GRAB;
        List<BossAttack> choices = new ArrayList<>();
        // Both arena phases have the full moveset. Context gates only moves that need a specific target state.
        if (distance < 6.2) addReady(choices, BossAttack.GRAB, BossAttack.PUNCH_SINGLE, BossAttack.PUNCH_COMBO, BossAttack.BACK_KICK);
        if (distance < 8) addReady(choices, BossAttack.CLEAVE, BossAttack.AXE_CHOP, BossAttack.SWORD_COMBO, BossAttack.SPIN_COMBO);
        if (distance < 11) addReady(choices, BossAttack.SLAM);
        if (distance > 6 && distance < 32) addReady(choices, BossAttack.CHARGE, BossAttack.LEAP, BossAttack.CHAIN_GRAPPLE);
        if (distance > 9) addReady(choices, BossAttack.PAWING, BossAttack.STAMPEDE, BossAttack.RUBBLE_THROW);
        if (distance > 5 && distance < 30) addReady(choices, BossAttack.FIRE_RINGS, BossAttack.GREEK_FIRE_LASER, BossAttack.SMOKE_BELCH);
        if (distance > 10 && distance < 35 && (weaponMode() != 2 || weaponUsesRemaining <= 1)
                && (weaponMode() != 1 || weaponUsesRemaining <= 3 || distance > 18)) addReady(choices, BossAttack.AXE_THROW);
        if (storedArrows > 0) addReady(choices, BossAttack.ARROW_RETURN);
        if (RagdollServerNetworking.isRagdolled(player) && distance < 24) addReady(choices, BossAttack.RAGDOLL_STOMP);
        if (axeInWorld() && axeAge > 160 && (distance > 8 || position().distanceToSqr(axeLastPosition) < 25))
            addReady(choices, BossAttack.RETRIEVE_AXE);
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
        choices.removeIf(attack -> !attackReady(attack) || attack == BossAttack.CHARGE && !clearChargeLane
                || attack == BossAttack.HORN_RAM && !shouldHornRam(player));
        double height = player.getY() - getY();
        Vec3 delta = player.position().subtract(position()).multiply(1, 0, 1);
        double facing = delta.lengthSqr() < .01 ? 1 : delta.normalize().dot(Vec3.directionFromRotation(0, getYHeadRot()));
        BossAttack selected = BossAttack.NONE;
        double totalWeight = 0;
        for (BossAttack attack : choices) {
            double score = switch (attack) {
                case PUNCH_SINGLE -> 5.6;
                case PUNCH_COMBO -> 5.2;
                case SWORD_COMBO -> 7.2;
                case GRAB -> height > 1.5 ? 11 : 3;
                case CLEAVE, SPIN_COMBO -> 6.5;
                case SLAM, AXE_CHOP -> 6.6;
                case HORN_RAM -> 10;
                case CHAIN_GRAPPLE -> 3.3;
                case LEAP -> clearChargeLane ? 4.3 : 6.3;
                case RUBBLE_THROW -> distance > 16 ? 5 : 3;
                case BACK_KICK -> facing < -.18 ? 8 : -8;
                case FIRE_RINGS -> 4;
                case GREEK_FIRE_LASER -> 4.0;
                case SMOKE_BELCH -> 4.8;
                case AXE_THROW -> weaponMode() == 1 ? 5.8 : 3.8;
                case RETRIEVE_AXE -> position().distanceToSqr(axeLastPosition) < 25 ? 5 : .5;
                case CHARGE -> 6.5;
                case STAMPEDE, PAWING -> 4.5;
                case RAGDOLL_STOMP, ARROW_RETURN -> 8;
                default -> -20;
            };
            if (score < -5) continue;
            boolean keepWeapon = weaponMode() != 0 && attackWeapon(attack) == weaponMode()
                    && attack != BossAttack.AXE_THROW;
            if (keepWeapon) score += weaponUsesRemaining > 0 ? 7 : 2.5;
            if (weaponUsesRemaining > 0 && !keepWeapon) score -= 2;
            if (attack == lastBossAttack) score -= 2;
            else if (attack == attackBeforeLast) score -= .65;
            if (attack != BossAttack.AXE_THROW && attack != BossAttack.RETRIEVE_AXE)
                score += Math.min(12, attacksSinceUse[attack.ordinal()]) * .18;
            if (height > 1.5 && attack == BossAttack.LEAP) score += 2;
            // Weighted selection keeps lower-priority moves possible instead of always choosing the same maximum.
            double weight = Math.exp(Math.clamp(score / 2, -6, 8));
            totalWeight += weight;
            if (random.nextDouble() * totalWeight < weight) selected = attack;
        }
        return selected;
    }

    private static boolean enabledAttack(BossAttack attack) {
        // Retain old ordinals for tracked-data compatibility, but never schedule or expose removed attacks.
        return attack != BossAttack.NONE && attack != BossAttack.RED_LIGHTNING_CHARGE
                && attack != BossAttack.WALL_SHOVE && attack != BossAttack.ARENA_SWEEP;
    }

    private static int attackWeapon(BossAttack attack) {
        return requiresAxe(attack) ? 1 : attack == BossAttack.SWORD_COMBO || attack == BossAttack.SPIN_COMBO ? 2 : 0;
    }

    public int pendingWeaponMode() { return attackWeapon(bossAttackState()); }

    private int nearbyBossPlayers(double radius) {
        if (!(level() instanceof ServerLevel serverLevel)) return 0;
        return serverLevel.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(radius),
                player -> player.isAlive() && !player.isCreative() && !player.isSpectator()).size();
    }

    /** Charge needs an open corridor for the Minotaur's entire body, not merely eye contact. */
    private boolean hasClearChargeLane(Player player) {
        return hasClearChargeLane(player, false);
    }

    private boolean hasClearChargeLane(Player player, boolean pursuingThrow) {
        if (!hasLineOfSight(player) || (!pursuingThrow && Math.abs(player.getY() - getY()) > 1.75D)) return false;
        Vec3 delta = player.position().subtract(position());
        Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z);
        double distance = horizontal.length();
        if (distance < 5.0D) return false;
        Vec3 direction = horizontal.scale(1.0D / distance);
        AABB body = getBoundingBox().deflate(0.16D, 0.10D, 0.16D).move(0.0D, 0.08D, 0.0D);
        double laneLength = Math.max(0.0D, distance - (getBbWidth() + player.getBbWidth()) * .5D - .25D);
        AABB sweptLane = body.expandTowards(direction.scale(laneLength)).inflate(0.10D, 0.0D, 0.10D);
        // The target is intentionally in the charge lane; only terrain should block selection.
        return !level().getBlockCollisions(this, sweptLane).iterator().hasNext()
                || level() instanceof ServerLevel serverLevel
                && WorldGenerator.isBreakableBossPath(serverLevel, sweptLane);
    }

    private boolean attackReady(BossAttack attack) {
        return enabledAttack(attack) && bossAttackLockouts[attack.ordinal()] <= 0 && !(requiresAxe(attack) && axeInWorld());
    }

    private void recordCloseDamage(DamageSource source, float damage) {
        if (damage <= 0 || !(source.getEntity() instanceof ServerPlayer player) || distanceTo(player) > 6) return;
        if (closeBurstTicks <= 0) { closeBurstDamage = 0; closeBurstTicks = 40; }
        closeBurstDamage += damage;
    }

    private boolean shouldHornRam(ServerPlayer target) {
        if (!attackReady(BossAttack.HORN_RAM) || distanceTo(target) > 6 || !hasLineOfSight(target)) return false;
        if (closeBurstTicks > 0 && closeBurstDamage >= 12) return true;
        var players = level().getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(6),
                player -> player.isAlive() && !player.isSpectator() && !player.isCreative()
                        && distanceTo(player) <= 6 && hasLineOfSight(player));
        for (int i = 0; i < players.size(); i++) for (int j = i + 1; j < players.size(); j++) {
            Vec3 a = players.get(i).position().subtract(position()).multiply(1, 0, 1).normalize();
            Vec3 b = players.get(j).position().subtract(position()).multiply(1, 0, 1).normalize();
            if (a.dot(b) <= 0) return true;
        }
        return false;
    }

    private static boolean requiresAxe(BossAttack attack) {
        return attack == BossAttack.CLEAVE || attack == BossAttack.SLAM || attack == BossAttack.AXE_THROW || attack == BossAttack.AXE_CHOP;
    }

    private static int sheathTicks(int mode) { return mode == 2 ? 24 : mode == 1 ? 20 : 0; }
    public int weaponSheathTicks() { return sheathTicks(getEntityData().get(DATA_WEAPON_FROM)); }
    public int weaponDrawTicks() { return pendingWeaponMode() == 2 ? DRAW_SWORD_TICKS : pendingWeaponMode() == 1 ? DRAW_AXE_TICKS : 0; }
    public int weaponTransitionMode() {
        return weaponSwapTicks() <= weaponSheathTicks() ? getEntityData().get(DATA_WEAPON_FROM) : pendingWeaponMode();
    }
    public boolean isSheathingWeapon() { return weaponSwapTicks() > 0 && weaponSwapTicks() <= weaponSheathTicks(); }
    /** Attachment changes at the authored hand contact, separately from permission to deal damage. */
    public int renderedWeaponMode() {
        if (weaponSwapTicks() == 0) return weaponMode();
        if (isSheathingWeapon()) {
            double progress = weaponSwapTicks() / (double)weaponSheathTicks();
            return progress < (weaponTransitionMode() == 2 ? .68 : .5) ? weaponTransitionMode() : 0;
        }
        double tick = weaponSwapTicks() - weaponSheathTicks();
        double seconds = (pendingWeaponMode() == 2 ? MinotaurAnimationTiming.DRAW_SWORD
                : MinotaurAnimationTiming.DRAW_AXE).seconds(tick);
        return seconds >= .5 ? pendingWeaponMode() : 0;
    }

    private boolean prepareWeapon(BossAttack attack) {
        if (attack == BossAttack.AXE_THROW && axeInWorld() && bossAttackTicks >= MinotaurAnimationTiming.AXE_RELEASE) return true;
        int wanted = attackWeapon(attack);
        if (weaponMode() == wanted && weaponSwapTicks() == 0) return true;
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().multiply(.2, 1, .2));
        if (weaponSwapTicks() == 0) getEntityData().set(DATA_WEAPON_FROM, weaponMode());
        int ticks = weaponSwapTicks() + 1;
        getEntityData().set(DATA_WEAPON_SWAP, ticks);
        if (ticks == weaponSheathTicks() && weaponSheathTicks() > 0) {
            getEntityData().set(DATA_WEAPON, 0);
            playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 1.4F, .65F);
        }
        if (ticks == weaponSheathTicks() + Math.max(1, weaponDrawTicks() / 2))
            playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 1.5F, .55F);
        if (ticks >= weaponSheathTicks() + weaponDrawTicks()) {
            getEntityData().set(DATA_WEAPON, wanted);
            weaponUsesRemaining = wanted == 0 ? 0 : 4;
            getEntityData().set(DATA_WEAPON_SWAP, 0);
        }
        return false;
    }

    private void throwAxe(ServerLevel level, ServerPlayer target) {
        if (axeInWorld()) return;
        Vec3 forward = Vec3.directionFromRotation(0, yBodyRot);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 origin = position().add(right.scale(getBbWidth() * .85)).add(forward.scale(1.1))
                .add(0, getBbHeight() * .95, 0);
        var axe = new MinotaurAxeEntity(Asterion.MINOTAUR_AXE, level);
        axe.setThrower(this);
        // Solve an arc for the real gravity, with capped lead so a dodge remains possible.
        double flightTicks = Math.clamp(origin.distanceTo(target.position()) / 1.65, 8, 24);
        Vec3 lead = target.getDeltaMovement().multiply(1, 0, 1).scale(Math.min(6, flightTicks * .35));
        if (lead.lengthSqr() > 4) lead = lead.normalize().scale(2);
        Vec3 aim = target.position().add(0, 3.15 * axe.modelScale(), 0).add(lead);
        axe.launchAimed(origin, aim, yBodyRot, flightTicks);
        if (!level.addFreshEntity(axe)) return;
        thrownAxe = axe.getUUID();
        axeLastPosition = origin;
        axeAge = 0;
        axePickupGoal = null; axePickupStall = 0; axePickupBest = Double.MAX_VALUE;
        getEntityData().set(DATA_AXE_OUT, true);
        getEntityData().set(DATA_WEAPON, 0);
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2F, .5F);
    }

    public static net.minecraft.world.item.ItemStack weaponAxeStack() {
        var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_AXE);
        stack.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, Asterion.id("minotaur_axe"));
        return stack;
    }

    @Override public void remove(Entity.RemovalReason reason) {
        // Discard/unload/reset does not call the defeat sequence. Always retire this entity's bars.
        healthBossBar.removeAllPlayers();
        rageBossBar.removeAllPlayers();
        if ((reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED)
                && thrownAxe != null && level() instanceof ServerLevel server) {
            var axe = server.getEntity(thrownAxe); if (axe != null) axe.discard();
        }
        super.remove(reason);
    }

    @Override public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        healthBossBar.removePlayer(player);
        rageBossBar.removePlayer(player);
    }

    private void tickWorldAxe(ServerLevel level) {
        if (thrownAxe == null) return;
        axeAge++;
        var axe = level.getEntity(thrownAxe);
        if (axe == null || axe.isRemoved()) return;
        // Upgrade an axe saved by the former dropped-item implementation in place.
        if (axe instanceof net.minecraft.world.entity.item.ItemEntity) {
            var physical = new MinotaurAxeEntity(Asterion.MINOTAUR_AXE, level);
            physical.setThrower(this);
            physical.launch(axe.position().add(0, 1.5, 0), axe.getDeltaMovement(), yBodyRot);
            if (level.addFreshEntity(physical)) {
                axe.discard(); axe = physical; thrownAxe = physical.getUUID();
                axeLastPosition = physical.position();
            }
        }
        axeLastPosition = axe.position();
    }

    private void retrieveAxe(ServerLevel level) {
        if (!axeInWorld()) { finishBossAttack(20); return; }
        var axe = thrownAxe == null ? null : level.getEntity(thrownAxe);
        Vec3 destination = axe == null ? axeLastPosition : axe.position();
        if (axe != null && axe.getBoundingBox().inflate(2).intersects(getBoundingBox()) && hasLineOfSight(axe)) {
            getNavigation().stop();
            axePickupGoal = null; axePickupStall = 0; axePickupBest = Double.MAX_VALUE;
            axe.discard();
            thrownAxe = null;
            getEntityData().set(DATA_AXE_OUT, false);
            getEntityData().set(DATA_WEAPON, 1);
            weaponUsesRemaining = 3;
            playSound(SoundEvents.ITEM_PICKUP, 1.5F, .6F);
            finishBossAttack(24);
            return;
        }
        getLookControl().setLookAt(destination.x, destination.y, destination.z, 20, 15);
        double remaining = position().distanceToSqr(destination);
        if (remaining < axePickupBest - .25) { axePickupBest = remaining; axePickupStall = 0; }
        else axePickupStall++;
        if (bossAttackTicks == 1 || bossAttackTicks % 24 == 1 || axePickupStall == 36) {
            net.minecraft.world.level.pathfinder.Path bestPath = null;
            double bestCost = Double.MAX_VALUE;
            // Path to a floor beside the resting body, never the elevated blade center.
            for (int ring = 0; ring < 2; ring++) for (int side = 0; side < 8; side++) {
                double angle = side * Math.PI / 4;
                Vec3 point = destination.add(Math.cos(angle) * (2.5 + ring * 2), 0, Math.sin(angle) * (2.5 + ring * 2));
                BlockPos ground = BlockPos.containing(point.x, Math.min(getY() + 3, destination.y), point.z);
                for (int down = 0; down < 9 && level.getBlockState(ground.below()).getCollisionShape(level, ground.below()).isEmpty(); down++) ground = ground.below();
                Vec3 candidate = Vec3.atBottomCenterOf(ground);
                if (!level.noCollision(this, getBoundingBox().move(candidate.subtract(position())))) continue;
                var path = getNavigation().createPath(ground, 0);
                if (path == null || !path.canReach()) continue;
                double cost = path.getNodeCount() + candidate.distanceToSqr(destination) * .25;
                if (cost < bestCost) { bestCost = cost; bestPath = path; axePickupGoal = candidate; }
            }
            if (bestPath != null) getNavigation().moveTo(bestPath, 1.2);
            else getNavigation().stop();
        }
        // An unreachable weapon does not trap the AI in endless circling. Fight with free hands/swords and retry later.
        if (axePickupStall > 65 || bossAttackTicks > 180) {
            getNavigation().stop(); axePickupGoal = null; axePickupStall = 0; axePickupBest = Double.MAX_VALUE;
            axeAge = -100; finishBossAttack(18);
        }
    }

    @Override protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (thrownAxe != null) output.putString("minotaur_axe_uuid", thrownAxe.toString());
        output.putDouble("minotaur_axe_x", axeLastPosition.x);
        output.putDouble("minotaur_axe_y", axeLastPosition.y);
        output.putDouble("minotaur_axe_z", axeLastPosition.z);
    }

    @Override protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        super.readAdditionalSaveData(input);
        String id = input.getStringOr("minotaur_axe_uuid", "");
        try { thrownAxe = id.isEmpty() ? null : UUID.fromString(id); } catch (IllegalArgumentException ignored) { thrownAxe = null; }
        getEntityData().set(DATA_AXE_OUT, thrownAxe != null);
        axeLastPosition = new Vec3(input.getDoubleOr("minotaur_axe_x", getX()), input.getDoubleOr("minotaur_axe_y", getY()), input.getDoubleOr("minotaur_axe_z", getZ()));
    }

    private void beginBossAttack(ServerPlayer player, BossAttack attack) {
        if (!enabledAttack(attack)) return;
        weaponAdvanceTicks = 0;
        getEntityData().set(DATA_WEAPON_SWAP, 0);
        for (int i = 0; i < attacksSinceUse.length; i++) attacksSinceUse[i] = Math.min(30, attacksSinceUse[i] + 1);
        attacksSinceUse[attack.ordinal()] = 0;
        if (attack == BossAttack.HORN_RAM) {
            closeBurstDamage = 0; closeBurstTicks = 0;
            wallComboTarget = null; wallComboWindow = 0; throwPursuitPending = false;
            airborneCatchTarget = null; airborneCatchWindow = 0;
        }
        if (requiresAxe(attack) && axeInWorld()) attack = BossAttack.RETRIEVE_AXE;
        attackBeforeLast = lastBossAttack;
        lastBossAttack = attack;
        setBossAttack(attack);
        bossAttackTicks = 0;
        if (attack == BossAttack.AXE_CHOP) {
            Vec3 aim = player.position().subtract(position()).multiply(1, 0, 1);
            bossChargeDirection = aim.lengthSqr() > .01 ? aim.normalize() : Vec3.directionFromRotation(0, getYRot());
        }
        getEntityData().set(DATA_BOSS_ATTACK_TICKS, 0);
        getNavigation().stop();
        bossAttackLockouts[attack.ordinal()] = switch (attack) {
            case GRAB -> 240;
            case RAGDOLL_STOMP -> 210;
            case ARROW_RETURN -> 180;
            case GREEK_FIRE_LASER -> 220;
            case SMOKE_BELCH -> 360;
            case ARENA_SWEEP, STAMPEDE, HORN_RAM, RED_LIGHTNING_CHARGE, FIRE_RINGS -> 190;
            case LEAP, RUBBLE_THROW, WALL_SHOVE, CHAIN_GRAPPLE -> 145;
            case SPIN_COMBO, SWORD_COMBO -> 80;
            case PUNCH_COMBO -> 105;
            case AXE_THROW -> 420;
            case RETRIEVE_AXE -> 200;
            case CHARGE, SLAM -> 95;
            default -> 72;
        };
        if (attack == BossAttack.GRAB && bossPartySize > 1)
            bossAttackLockouts[attack.ordinal()] -= Math.min(70, (bossPartySize - 1) * 18);
        bossAttackLockouts[attack.ordinal()] = Math.max(20, (int)Math.ceil(bossAttackLockouts[attack.ordinal()] * rageCooldownMultiplier()));
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
                lightningStrikeTarget = combatPoint(player.position()
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
            Vec3 landing = player.position().add(player.getDeltaMovement().multiply(4, 0, 4));
            bossLeapTarget = behaviorPhase() == BehaviorPhase.BOSS ? combatPoint(landing) : landing;
            leapPlan = null;
            getEntityData().set(DATA_LEAP_LANDING, -1);
            leapFlightTick = 0;
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
        } else if ((attack == BossAttack.PUNCH_COMBO || attack == BossAttack.PUNCH_SINGLE)) {
            punchComboTarget = player.getUUID();
            punchComboFromChain = false;
            punchStrikeMask = 0;
            if (level() instanceof ServerLevel level)
                sendBossTelegraph(level, position(), player.position().subtract(position()),
                        5.8F, 12, BossTelegraphPayload.FRONT_CONE);
            playSound(SoundEvents.RAVAGER_ATTACK, 2.2F, 0.68F);
        } else if (attack == BossAttack.RAGDOLL_STOMP) {
            getEntityData().set(DATA_LEAP_LANDING, -1);
            stompTarget = player.getUUID();
            stompTargetPosition = combatPoint(player.position()
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
            bossLeapTarget = combatPoint(player.position()
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
        switch (bossAttack) {
            case CLEAVE, AXE_CHOP, SWORD_COMBO, SPIN_COMBO, SLAM, CHARGE, HORN_RAM, STAMPEDE, PAWING, BACK_KICK, RUBBLE_THROW -> {
                updateGroundTelegraph(level);
                return;
            }
            case ARENA_SWEEP, LEAP -> { }
            // Projectiles, grabs and moving contact attacks do not damage their entire old cone.
            // Keep their animation cues rather than marking safe ground as a guaranteed hit area.
            default -> { return; }
        }
        float arc = kind == BossTelegraphPayload.TARGET_CIRCLE ? Mth.TWO_PI
                : kind == BossTelegraphPayload.FRONT_CONE ? 125 * Mth.DEG_TO_RAD : Mth.PI;
        broadcastGroundTelegraph(level, new BossTelegraphPayload(center, direction, radius, duration, kind,
                getId(), arc, 2.25F, 0));
    }

    private void broadcastGroundTelegraph(ServerLevel level, BossTelegraphPayload telegraph) {
        for (ServerPlayer viewer : level.players())
            if (viewer.distanceToSqr(this) < 128 * 128 && ServerPlayNetworking.canSend(viewer, BossTelegraphPayload.TYPE))
                ServerPlayNetworking.send(viewer, telegraph);
    }

    /** Melee hit checks use an expanded AABB; preserve its corners instead of promising a circular safe zone. */
    private void updateGroundTelegraph(ServerLevel level) {
        int strike;
        float expansion, arc = Mth.TWO_PI;
        int kind = BossTelegraphPayload.BOX;
        Vec3 facing = getLookAngle();
        Vec3 center = position();
        float bodyHalfWidth = getBbWidth() * .5F;
        switch (bossAttack) {
            case AXE_CHOP -> {
                if (bossAttackTicks >= AXE_CHOP_HIT_TICK) return;
                broadcastGroundTelegraph(level, new BossTelegraphPayload(position(), bossChargeDirection,
                        8, 3, BossTelegraphPayload.CHARGE_LANE, getId(), 0, 1.4F, bossAttackTicks / (float)AXE_CHOP_HIT_TICK));
                return;
            }
            case CLEAVE -> { strike = 18; expansion = 6; arc = (float)(2 * Math.acos(-.05)); kind = BossTelegraphPayload.BOX_CONE; }
            case SWORD_COMBO -> {
                strike = bossAttackTicks < COMBO_STRIKE_TICKS[0] ? COMBO_STRIKE_TICKS[0]
                        : bossAttackTicks < COMBO_STRIKE_TICKS[1] ? COMBO_STRIKE_TICKS[1] : COMBO_STRIKE_TICKS[2];
                expansion = 7;
                arc = (float)(2 * Math.acos(.10)); kind = BossTelegraphPayload.BOX_CONE;
                facing = Vec3.directionFromRotation(getXRot(), getYHeadRot());
            }
            case SPIN_COMBO -> { strike = 19; expansion = 6.5F; }
            case SLAM -> { strike = AXE_CHOP_HIT_TICK; expansion = 8; }
            case BACK_KICK -> {
                strike = 15; expansion = 5.2F; arc = (float)(2 * Math.acos(.30)); kind = BossTelegraphPayload.BOX_CONE;
                facing = Vec3.directionFromRotation(0, yBodyRot).scale(-1);
            }
            case RUBBLE_THROW -> { strike = 48; expansion = 4.5F; center = bossLeapTarget; bodyHalfWidth = 0; }
            case CHARGE, HORN_RAM, STAMPEDE, PAWING -> {
                int windup = chargeAnimationWindup();
                if (bossAttackTicks > windup) return;
                broadcastGroundTelegraph(level, new BossTelegraphPayload(position(), bossChargeDirection,
                        34, 3, BossTelegraphPayload.CHARGE_LANE, getId(), 0, getBbWidth() * .5F + .8F,
                        bossAttackTicks / (float)windup));
                return;
            }
            default -> { return; }
        }
        if (bossAttackTicks >= strike) return;
        int previousStrike = bossAttack != BossAttack.SWORD_COMBO || bossAttackTicks < COMBO_STRIKE_TICKS[0] ? 0
                : bossAttackTicks < COMBO_STRIKE_TICKS[1] ? COMBO_STRIKE_TICKS[0] : COMBO_STRIKE_TICKS[1];
        float progress = (bossAttackTicks - previousStrike) / (float)(strike - previousStrike);
        broadcastGroundTelegraph(level, new BossTelegraphPayload(center, facing,
                bodyHalfWidth + expansion, 3, kind, getId(), arc, 0, progress));
    }

    private void tickBossAttack(ServerLevel level, ServerPlayer player) {
        if (!enabledAttack(bossAttack)) { finishBossAttack(20); return; }
        faceAttackTarget(player);
        if ((tickCount & 1) == 0) updateGroundTelegraph(level);
        if (!prepareWeapon(bossAttack)) return;
        bossAttackTicks++;
        getEntityData().set(DATA_BOSS_ATTACK_TICKS, bossAttackTicks);
        switch (bossAttack) {
            case AXE_CHOP -> {
                getNavigation().stop();
                setDeltaMovement(getDeltaMovement().multiply(.08, 1, .08));
                if (bossAttackTicks <= 14) {
                    Vec3 aim = player.position().subtract(position()).multiply(1, 0, 1);
                    if (aim.lengthSqr() > .01) bossChargeDirection = aim.normalize();
                }
                float yaw = (float)(Math.atan2(bossChargeDirection.z, bossChargeDirection.x) * Mth.RAD_TO_DEG) - 90;
                setYRot(yaw); yBodyRot = yaw; yHeadRot = yaw;
                if (bossAttackTicks == AXE_CHOP_HIT_TICK) performAxeChop(level);
                if (bossAttackTicks >= 40) finishBossAttack(32);
            }
            case CLEAVE -> {
                if (bossAttackTicks == 18) performCleave(level);
                if (bossAttackTicks >= 48) finishBossAttack(24);
            }
            case SLAM -> {
                getNavigation().stop();
                setDeltaMovement(0, getDeltaMovement().y, 0);
                if (bossAttackTicks == AXE_CHOP_HIT_TICK) performGroundSlam(level);
                if (bossAttackTicks >= 44) { riposteTicks = 30; finishBossAttack(42); }
            }
            case CHARGE -> {
                int windupTicks = getEntityData().get(DATA_CHARGE_WINDUP);
                if (bossAttackTicks <= windupTicks) {
                    // Once telegraphed, finish the windup instead of snapping into a different attack.
                    setDeltaMovement(getDeltaMovement().multiply(0.08D, 1.0D, 0.08D));
                    getLookControl().setLookAt(player, 12.0F, 6.0F);
                    Vec3 aim = player.position().subtract(position());
                    Vec3 horizontal = new Vec3(aim.x, 0.0D, aim.z);
                    if (!bossChargeTargetsPillar && horizontal.lengthSqr() > 0.01D)
                        bossChargeDirection = bossChargeDirection.lerp(horizontal.normalize(), 0.11D).normalize();
                    if (bossAttackTicks >= 8 && (bossAttackTicks & 3) == 0) {
                        Vec3 right = new Vec3(-bossChargeDirection.z, 0.0D, bossChargeDirection.x);
                        double side = (bossAttackTicks & 4) == 0 ? -0.92D : 0.92D;
                        Vec3 hoof = position().add(bossChargeDirection.scale(1.38D)).add(right.scale(side));
                        level.sendParticles(ParticleTypes.DUST_PLUME, hoof.x, getY() + 0.10D, hoof.z,
                                10, 0.42D, 0.08D, 0.42D, 0.05D);
                        playSound(Asterion.MINOTAUR_STEP, 2.35F,
                                0.32F + bossAttackTicks / (float)Math.max(1, windupTicks) * 0.10F);
                    }
                    // A committed boss charge is allowed to smash a newly placed obstruction.
                    // The run phase clears the swept body volume before collision is resolved.
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
                    clearCombatObstacle(level, impact);
                    if (bossStage == BossStage.PILLARS && WorldGenerator.breakBossPillar(level, impact)) {
                        increaseRage(1);
                        applyBossCollisionDamage(level, true);
                        scarArena(level, position(), 4);
                        setDeltaMovement(bossChargeDirection.scale(-0.18D).add(0, 0.16D, 0));
                        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1.0D, getZ(),
                                8, 1.0D, 1.4D, 1.0D, 0.04D);
                        riposteTicks = 24;
                        bossStunTicks = 52;
                        finishBossAttack(46);
                        return;
                    }
                    int smashed = breakCombatWall(level, impact, this);
                    if (smashed > 0) {
                        applyBossCollisionDamage(level, false);
                        recoverFromArenaImpact();
                        setDeltaMovement(bossChargeDirection.scale(-0.22D).add(0.0D, 0.14D, 0.0D));
                        scarArena(level, position(), 4);
                        playSound(SoundEvents.RAVAGER_ATTACK, 2.5F, 0.42F);
                        riposteTicks = 28;
                        bossStunTicks = 58;
                        finishBossAttack(44);
                        return;
                    }
                    if (horizontalCollision) {
                        if (clearCombatObstacle(level, impact) > 0) return;
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
            case PUNCH_SINGLE -> tickPunchSingle(level, player);
            case RAGDOLL_STOMP -> tickRagdollStomp(level, player);
            case ARROW_RETURN -> tickArrowReturn(level, player);
            case GREEK_FIRE_LASER -> tickGreekFireLaser(level, player);
            case SMOKE_BELCH -> tickSmokeBelch(level);
            case LEAP -> tickLeapAttack(level, player);
            case SWORD_COMBO -> {
                getNavigation().stop();
                setDeltaMovement(0, getDeltaMovement().y, 0);
                for (int strike = 0; strike < COMBO_STRIKE_TICKS.length; strike++)
                    if (bossAttackTicks == COMBO_STRIKE_TICKS[strike])
                        performSwordArc(level, strike == 2 ? 17 : 12, strike == 2 ? 1.85 : 1.25);
                if (bossAttackTicks >= 73) finishBossAttack(36);
            }
            case SPIN_COMBO -> {
                if (bossAttackTicks == 19) performSpin(level);
                if (bossAttackTicks >= 36) finishBossAttack(46);
            }
            case GRAB -> tickGrabAttack(level, player);
            case AXE_THROW -> {
                if (bossAttackTicks == MinotaurAnimationTiming.AXE_RELEASE) throwAxe(level, player);
                if (bossAttackTicks >= 30) finishBossAttack(30);
            }
            case RETRIEVE_AXE -> retrieveAxe(level);
            case NONE -> { }
        }
        if (isLaneCharge(bossAttack)) {
            faceDirection(bossChargeDirection, 12);
            if (bossAttackTicks > chargeAnimationWindup()) emitChargeSmoke(level, bossChargeDirection);
        }
    }

    private static boolean isLaneCharge(BossAttack attack) {
        return attack == BossAttack.CHARGE || attack == BossAttack.HORN_RAM
                || attack == BossAttack.STAMPEDE || attack == BossAttack.PAWING;
    }

    private void faceDirection(Vec3 direction, float maxTurn) {
        if (direction.horizontalDistanceSqr() < .0001) return;
        float desired = (float)(Math.atan2(-direction.x, direction.z) * Mth.RAD_TO_DEG);
        float yaw = getYRot() + Mth.clamp(Mth.wrapDegrees(desired - getYRot()), -maxTurn, maxTurn);
        setYRot(yaw); yBodyRot = yaw; yHeadRot = yaw;
        setXRot(0);
    }

    private void faceAttackTarget(ServerPlayer player) {
        if (isLaneCharge(bossAttack) || bossAttack == BossAttack.AXE_CHOP) faceDirection(bossChargeDirection, 10);
        else if (bossAttack == BossAttack.LEAP) faceDirection(bossLeapTarget.subtract(position()), 10);
        else if (bossAttack == BossAttack.RUBBLE_THROW) faceDirection(bossLeapTarget.subtract(position()), 8);
        else if (bossAttack != BossAttack.BACK_KICK && bossAttack != BossAttack.RETRIEVE_AXE && grabbedPlayer == null)
            faceDirection(player.position().subtract(position()), bossAttackTicks <= 12 ? 10 : 4);
    }

    private void emitChargeSmoke(ServerLevel level, Vec3 direction) {
        if ((tickCount & 1) != 0) return;
        Vec3 trail = position().subtract(direction.scale(getBbWidth() * .45));
        // The exact door particle factory supplies its size, warm colour and gradual 3–4 second fade.
        level.sendParticles(Asterion.DOOR_SMOKE, trail.x, getY() + .3, trail.z,
                3, getBbWidth() * .35, .15, getBbWidth() * .35, .018);
    }

    private void tickSmokeBelch(ServerLevel level) {
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (bossAttackTicks == 12) playSound(SoundEvents.RAVAGER_ROAR, 2.6F, .5F);
        if (bossAttackTicks >= 20 && bossAttackTicks <= 44 && bossAttackTicks % 4 == 0) {
            Vec3 forward = Vec3.directionFromRotation(0, yBodyRot);
            Vec3 mouth = getEyePosition().add(0, -.35, 0).add(forward.scale(getBbWidth() * .5));
            smokeClouds.emit(this, mouth, forward);
            smokeClouds.emit(this, mouth, forward);
        }
        if (bossAttackTicks >= 65) finishBossAttack(32);
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
            if ((bossAttackTicks % 8) == 0) playSound(Asterion.MINOTAUR_STEP, 2.2F, 0.42F);
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
                playSound(Asterion.MINOTAUR_STEP, 2.4F,
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
            clearCombatObstacle(level, impact);
            int smashed = breakCombatWall(level, impact, this);
            if (smashed > 0) {
                applyBossCollisionDamage(level, false);
                recoverFromArenaImpact();
                scarArena(level, position(), 4);
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
                playSound(Asterion.MINOTAUR_STEP, 2.5F, 0.34F + bossAttackTicks * 0.004F);
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
            clearCombatObstacle(level, horns);
            int smashed = breakCombatWall(level, horns, this);
            if (smashed > 0 || horizontalCollision) {
                applyBossCollisionDamage(level, false);
                recoverFromArenaImpact();
                setDeltaMovement(bossChargeDirection.scale(-0.28D).add(0.0D, 0.18D, 0.0D));
                scarArena(level, position(), 4);
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
                    Vec3 impulse = bossChargeDirection.scale(1.4D).add(0, .55D, 0);
                    ragdollPlayer(victim, impulse,
                            1.35F, true);
                    thrownPlayer = victim.getUUID(); throwVelocity = impulse; throwFlightTicks = 0;
                    grapplePull = false; hornKnockback = true; hornTravel = 0; hornTravelLimit = 7 + random.nextDouble() * 3;
                    RagdollServerNetworking.suppressThrowFallDamage(victim, 100);
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
        int landed = getEntityData().get(DATA_LEAP_LANDING);
        if (landed >= 0) {
            setDeltaMovement(getDeltaMovement().multiply(.1, 1, .1));
            if (bossAttackTicks >= landed + 12) finishBossAttack(stompRecoveryCooldown);
            return;
        }
        Player found = stompTarget == null ? null : level.getPlayerByUUID(stompTarget);
        ServerPlayer target = found instanceof ServerPlayer serverPlayer ? serverPlayer : fallback;
        if (target != null && target.isAlive() && RagdollServerNetworking.isRagdolled(target)
                && bossAttackTicks <= 15)
            stompTargetPosition = combatPoint(target.position()
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
                clearCombatObstacle(level,
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
            scarArena(level, position(), 5);
            level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.1D, getZ(),
                    9, 2.2D, 0.22D, 2.2D, 0.05D);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 3.2F, 0.42F);
            riposteTicks = hit ? 28 : 50;
            stompRecoveryCooldown = hit ? 54 : 44;
            getEntityData().set(DATA_LEAP_LANDING, bossAttackTicks);
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
                // 26.1 requires server-owned arrows to retain the weapon that fired them;
                // an empty stack now throws "Invalid weapon firing an arrow" in AbstractArrow.
                Arrow arrow = new Arrow(level, this, new ItemStack(Items.ARROW),
                        new ItemStack(Items.CROSSBOW));
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
            scarArena(level, position().add(bossChargeDirection.scale(12.0D)), 12);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 4.0F, 0.52F);
        }
        if (bossAttackTicks >= 52) finishBossAttack(58);
    }

    public boolean isChainGrappleActive() { return bossAttackState() == BossAttack.CHAIN_GRAPPLE && weaponSwapTicks() == 0; }

    private void tickChainGrapple(ServerLevel level) {
        Player found = chainGrappleTarget == null ? null : level.getPlayerByUUID(chainGrappleTarget);
        ServerPlayer target = found instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (target == null || !target.isAlive() || target.isSpectator() || distanceTo(target) > 32 || !hasLineOfSight(target)) {
            finishBossAttack(42); return;
        }
        getLookControl().setLookAt(target, 12, 8);
        if (bossAttackTicks == GRAPPLE_YANK_TICK) {
            Vec3 pull = position().subtract(target.position()).multiply(1, 0, 1);
            double distance = pull.length();
            if (distance > .01) {
                double flightDrag = (1 - Math.pow(.91, 5)) / .09;
                Vec3 impulse = pull.scale(1 / distance).scale(Mth.clamp((distance - GRAPPLE_CATCH_DISTANCE) / flightDrag, 1.2, 7.25)).add(0, .48, 0);
                thrownPlayer = target.getUUID();
                throwVelocity = impulse;
                throwFlightTicks = 0;
                grapplePull = true;
                hornKnockback = false;
                target.setDeltaMovement(impulse);
                target.hurtMarked = true;
                target.resetFallDistance();
                RagdollServerNetworking.forceAuthority(target, impulse);
                playSound(SoundEvents.CHAIN_HIT, 3.2F, .48F);
                level.sendParticles(ParticleTypes.DUST_PLUME, target.getX(), target.getY() + .2, target.getZ(), 12, .5, .1, .5, .04);
            }
        }
        if (bossAttackTicks >= 36) {
            chainGrappleTarget = null;
            getEntityData().set(DATA_REACH_ARM, 0);
            getEntityData().set(DATA_GRAB_TARGET_ID, -1);
            if (canCatchPlayer(target) && !target.isCreative() && hasLineOfSight(target)) {
                // Transfer movement authority from the yank to the hand; no leftover throw tick may fight the hold.
                thrownPlayer = null; grapplePull = false; hornKnockback = false;
                throwVelocity = Vec3.ZERO;
                target.setDeltaMovement(Vec3.ZERO);
                beginBossAttack(target, BossAttack.GRAB);
                grabbedPlayer = target.getUUID();
                target.stopRiding();
                getEntityData().set(DATA_HELD_PLAYER, target.getId());
                lockReachTo(target);
                bossAttackTicks = 8;
                getEntityData().set(DATA_BOSS_ATTACK_TICKS, 8);
                debugDecision = "Chain connected: catching and throwing the player";
            } else finishBossAttack(30);
        }
    }

    private void tickPunchSingle(ServerLevel level, ServerPlayer fallback) {
        Player found = punchComboTarget == null ? null : level.getPlayerByUUID(punchComboTarget);
        ServerPlayer target = found instanceof ServerPlayer player ? player : fallback;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (target == null || !target.isAlive() || target.isSpectator()) { finishBossAttack(24); return; }
        getLookControl().setLookAt(target, 12, 8);
        if (bossAttackTicks == 20) performPunchStrike(level, target, 0);
        if (bossAttackTicks >= 40) finishBossAttack(26);
    }

    private void tickGreekFireLaser(ServerLevel level, ServerPlayer player) {
        int braziers = 4; // Innate Greek fire; temporary arena braziers are disabled.
        if (braziers == 0) { finishBossAttack(66); return; }
        setDeltaMovement(getDeltaMovement().multiply(0.04D, 1.0D, 0.04D));
        Vec3 origin = getEyePosition().add(0.0D, -0.25D, 0.0D);
        Vec3 desired = player.getEyePosition().subtract(origin).normalize();
        greekFireAim = greekFireAim.lerp(desired, bossAttackTicks < 24 ? 0.08D : 0.025D).normalize();
        if (bossAttackTicks >= 24 && bossAttackTicks <= 92) {
            // Animated sprites persist between emissions; stagger the line to avoid dense overdraw.
            if ((bossAttackTicks & 1) == 0) for (int step = 1; step <= 32; step++) {
                double distance = (step - ((bossAttackTicks & 2) == 0 ? 0.0D : 0.5D)) * 0.93D;
                Vec3 point = origin.add(greekFireAim.scale(distance));
                if (level.clip(new net.minecraft.world.level.ClipContext(origin, point,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this))
                        .getType() != net.minecraft.world.phys.HitResult.Type.MISS) break;
                level.sendParticles(Asterion.GREEK_FIRE,
                        point.x, point.y, point.z, 2, .12D, .16D, .12D, .012D);
                if (step % 4 == 0) leaveFireSoot(level, point);
            }
            Vec3 toPlayer = player.getEyePosition().subtract(origin);
            double along = Mth.clamp(toPlayer.dot(greekFireAim), 0.0D, 30.0D);
            double miss = player.getEyePosition().distanceTo(origin.add(greekFireAim.scale(along)));
            if (miss <= 1.15D && hasLineOfSight(player) && bossAttackTicks % 10 == 0) {
                player.hurtServer(level, damageSources().magic(), 5.0F * braziers / 4.0F);
                player.igniteForSeconds(2.0F);
            }
        }
        if (bossAttackTicks >= 108) finishBossAttack(66);
    }

    private void tickPunchCombo(ServerLevel level, ServerPlayer player) {
        Player found = punchComboTarget == null ? null : level.getPlayerByUUID(punchComboTarget);
        ServerPlayer target = found instanceof ServerPlayer serverPlayer ? serverPlayer : player;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (target == null || !target.isAlive() || target.isCreative() || target.isSpectator()) { finishBossAttack(30); return; }
        getLookControl().setLookAt(target, 15, 9);
        for (int strike = 0; strike < 3; strike++)
            if (bossAttackTicks == COMBO_STRIKE_TICKS[strike] && performPunchStrike(level, target, strike)) return;
        if (bossAttackTicks >= 73) { riposteTicks = 24; finishBossAttack(30); }
    }

    private boolean performPunchStrike(ServerLevel level, ServerPlayer target, int strike) {
        if (!hasLineOfSight(target)) return false;
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
        double lateral = strike == 2 ? -0.72D : 0.72D;
        double reach = strike == 2 ? 3.85D : 3.45D;
        Vec3 shoulder = position().add(facing.scale(0.72D)).add(right.scale(lateral * 0.55D))
                .add(0.0D, getBbHeight() * 0.59D, 0.0D);
        Vec3 fist = position().add(facing.scale(reach)).add(right.scale(lateral))
                .add(0.0D, Mth.clamp(target.getBoundingBox().getCenter().y - getY(), .8D, getBbHeight() * .65D), 0.0D);
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
                level.sendParticles(Asterion.GREEK_FIRE, fire.getX() + .5, fire.getY() + .4,
                        fire.getZ() + .5, 3, .16, .2, .16, .015);
                bossFireBlocks.add(fire);
                leaveFireSoot(level, Vec3.atBottomCenterOf(fire));
            }
        }
        for (ServerPlayer victim : level.players()) {
            BlockPos feet = victim.blockPosition();
            boolean standingInRing = bossFireBlocks.contains(feet);
            if (standingInRing && victim.getY() <= floorY + 1.35D) {
                if (victim.hurtServer(level, damageSources().mobAttack(this), 4.0F))
                    victim.igniteForSeconds(2.0F);
            }
        }
        level.sendParticles(Asterion.GREEK_FIRE,
                getX(), Math.min(getY() - 0.18D, floorY + 0.82D), getZ(),
                Math.max(12, radius * 3), radius * 0.65D, 0.04D, radius * 0.65D, 0.02D);
    }

    private void clearBossFire(ServerLevel level) {
        bossFireBlocks.clear();
    }

    private void leaveFireSoot(ServerLevel level, Vec3 point) {
        BlockPos floor = BlockPos.containing(point);
        for (int down = 0; down < 12 && level.getBlockState(floor.below()).getCollisionShape(level, floor.below()).isEmpty(); down++) floor = floor.below();
        if (level.getBlockState(floor.below()).getCollisionShape(level, floor.below()).isEmpty()) return;
        if (fireSootTrail.size() >= 64 && !fireSootTrail.containsKey(floor)) fireSootTrail.remove(fireSootTrail.keySet().iterator().next());
        fireSootTrail.put(floor.immutable(), tickCount + 75);
    }

    private void tickFireSootTrail(ServerLevel level) {
        fireSootTrail.values().removeIf(until -> until < tickCount);
        if (tickCount % 6 != 0) return;
        for (var entry : fireSootTrail.entrySet()) {
            BlockPos pos = entry.getKey();
            level.sendParticles(Asterion.GREEK_FIRE_SOOT, pos.getX() + .5, pos.getY() + .15, pos.getZ() + .5,
                    1, .3, .08, .3, .015);
            if (entry.getValue() - tickCount > 50)
                level.sendParticles(Asterion.GREEK_FIRE, pos.getX() + .5, pos.getY() + .4, pos.getZ() + .5,
                        1, .2, .15, .2, .008);
        }
    }

    private void tickRubbleThrow(ServerLevel level, ServerPlayer player) {
        setDeltaMovement(getDeltaMovement().multiply(0.1D, 1.0D, 0.1D));
        getNavigation().stop();
        if (bossAttackTicks == 26) {
            Vec3 target = bossLeapTarget;
            for (int piece = 0; piece < 5; piece++) {
                double angle = Mth.TWO_PI * piece / 5.0D;
                Vec3 origin = position().add(Vec3.directionFromRotation(0, yBodyRot).scale(2.2)).add(0, 3.4, 0);
                Vec3 landing = target.add(Math.cos(angle) * 1.1, .45, Math.sin(angle) * 1.1);
                double drag = .998, ticks = 22, sum = drag * (1 - Math.pow(drag, ticks)) / (1 - drag);
                Vec3 flight = landing.subtract(origin).add(0, .055 * drag / (1 - drag) * (ticks - sum), 0).scale(1 / sum);
                net.krodark.asterion.worldgen.ArenaDebris.queue(level, origin, flight);
            }
            playSound(SoundEvents.STONE_BREAK, 3.4F, 0.45F);
        }
        if (bossAttackTicks == 48) {
            scarArena(level, bossLeapTarget, 5);
            for (ServerPlayer victim : level.getEntitiesOfClass(ServerPlayer.class,
                    new AABB(bossLeapTarget, bossLeapTarget).inflate(4.5D))) {
                victim.hurtServer(level, damageSources().mobAttack(this), 13.0F);
                Vec3 away = victim.position().subtract(bossLeapTarget);
                ragdollPlayer(victim, new Vec3(away.x, 0.0D, away.z).normalize()
                        .scale(1.9D).add(0.0D, 0.8D, 0.0D), 1.25F);
            }
            level.sendParticles(Asterion.DOOR_SMOKE, bossLeapTarget.x, bossLeapTarget.y + .2,
                    bossLeapTarget.z, 28, 1.8D, .3D, 1.8D, .055D);
            playSound(SoundEvents.DEEPSLATE_BREAK, 2.5F, .5F);
        }
        if (bossAttackTicks >= 58) finishBossAttack(48);
    }

    private void tickWallShove(ServerLevel level, ServerPlayer player) {
        Player found = wallComboTarget == null ? null : level.getPlayerByUUID(wallComboTarget);
        ServerPlayer target = found instanceof ServerPlayer serverPlayer ? serverPlayer : player;
        if (target == null || !target.isAlive() || (!debugMode && target.isCreative()) || target.isSpectator()) {
            finishBossAttack(34);
            return;
        }
        getLookControl().setLookAt(target, 18.0F, 10.0F);
        if (wallPinTicks > 0 && wallPinPoint != null) {
            Vec3 shove = wallPinPoint.subtract(target.position());
            target.move(net.minecraft.world.entity.MoverType.SELF, shove.length() > .8 ? shove.normalize().scale(.8) : shove);
            target.teleportTo(target.getX(), target.getY(), target.getZ());
            target.setDeltaMovement(Vec3.ZERO);
            target.resetFallDistance();
            RagdollServerNetworking.forceAuthority(target, Vec3.ZERO);
            if (--wallPinTicks == 0) { throwWallImpact = null; finishBossAttack(44); }
            return;
        }
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
        Vec3 pinDestination = findWallPinPoint(level, target);
        if (!wallShoveHit && distance <= 5.4D && pinDestination != null && hasLineOfSight(target)) {
            wallShoveHit = true;
            wallPinPoint = pinDestination;
            wallPinTicks = 12;
            getNavigation().stop();
            Vec3 wallward = target.position().subtract(position()).multiply(1, 0, 1);
            if (wallward.lengthSqr() < 0.01D) wallward = target.position().subtract(position());
            wallward = wallward.normalize();
            if (target.hurtServer(level, damageSources().mobAttack(this), 10.0F))
                ragdollPlayer(target, wallward.scale(0.58D).add(0.0D, 0.16D, 0.0D), 1.25F, true);
            level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 1.0D,
                    target.getZ(), 5, 0.7D, 0.9D, 0.7D, 0.08D);
            // Keep the wall intact: it is the support for this pin.
            playSound(SoundEvents.RAVAGER_ATTACK, 3.4F, 0.40F);
        }
        if (wallPinTicks == 0 && wallShoveHit && bossAttackTicks >= 16 || bossAttackTicks >= 180) {
            throwWallImpact = null;
            wallComboTarget = null;
            wallComboWindow = 0;
            riposteTicks = wallShoveHit ? 32 : 46;
            finishBossAttack(wallShoveHit ? 44 : 36);
        }
    }

    private Vec3 findWallPinPoint(ServerLevel level, ServerPlayer target) {
        Vec3 away = target.position().subtract(position()).multiply(1, 0, 1).normalize();
        Vec3 center = target.position().add(0, target.getBbHeight() * .5, 0);
        Vec3 best = null;
        double nearest = 25;
        for (Vec3 direction : new Vec3[]{away, new Vec3(1,0,0), new Vec3(-1,0,0), new Vec3(0,0,1), new Vec3(0,0,-1)}) {
            var hit = level.clip(new net.minecraft.world.level.ClipContext(center, center.add(direction.scale(4)),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, target));
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || hit.getDirection().getAxis().isVertical()) continue;
            var normal = hit.getDirection();
            Vec3 feet = new Vec3(hit.getLocation().x + normal.getStepX() * (target.getBbWidth() * .5 + .04), target.getY(),
                    hit.getLocation().z + normal.getStepZ() * (target.getBbWidth() * .5 + .04));
            double distance = feet.distanceToSqr(target.position());
            if (distance < nearest && !level.getBlockCollisions(target, target.getBoundingBox().move(feet.subtract(target.position())).deflate(.01)).iterator().hasNext()) {
                nearest = distance; best = feet;
            }
        }
        return best;
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
            clearCombatObstacle(level, impact);
            int smashed = breakCombatWall(level, impact, this);
            if (smashed > 0) {
                recoverFromArenaImpact();
                scarArena(level, position(), 5);
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
        if (behaviorPhase() == BehaviorPhase.BOSS || (tickCount & 1) != 0) return;
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
        Vec3 safe = combatPoint(retreat);
        setPos(safe.x, Math.max(combatCenter().y, safe.y), safe.z);
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

    private void performAxeChop(ServerLevel level) {
        Vec3 forward = bossChargeDirection, right = new Vec3(-forward.z, 0, forward.x);
        for (ServerPlayer victim : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(9))) {
            Vec3 delta = victim.position().subtract(position());
            double along = delta.dot(forward), across = delta.dot(right);
            if (along < 0 || along > 8 || Math.abs(across) > 1.4 || Math.abs(delta.y) > 3.5 || !hasLineOfSight(victim)) continue;
            if (victim.hurtServer(level, damageSources().mobAttack(this), 18))
                ragdollPlayer(victim, forward.scale(1.2).add(0, .28, 0), 1.15F);
        }
        Vec3 impact = position().add(forward.scale(5.5));
        level.sendParticles(ParticleTypes.DUST_PLUME, impact.x, getY() + .12, impact.z, 20, .65, .12, .65, .07);
        playSound(SoundEvents.ANVIL_LAND, 2.2F, .55F);
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    private void performGroundSlam(ServerLevel level) {
        scarArena(level, position(), 7);
        double arenaRadius = Math.sqrt(getX() * getX() + getZ() * getZ());
        if (arenaRadius > 25.0D) {
            Vec3 outward = new Vec3(getX(), 0.0D, getZ()).normalize();
            AABB wallImpact = getBoundingBox().expandTowards(outward.scale(5.5D))
                    .inflate(3.2D, 1.8D, 3.2D);
            int broken = breakCombatWall(level, wallImpact, this);
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
        getEntityData().set(DATA_WEAPON_SWAP, 0);
        setBossAttack(BossAttack.NONE);
        setAggressive(false);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        collapseAnchor = combatPoint(position());
        noPhysics = false;
        playRoar(4.0F, 0.82F, 1.25F);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + getBbHeight() * 0.5D, getZ(),
                60, 2.0D, 2.5D, 2.0D, 0.035D);
    }

    private void tickCollapse(ServerLevel level, ServerPlayer player) {
        collapseTicks++;
        getEntityData().set(DATA_BOSS_ATTACK_TICKS, collapseTicks);
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().multiply(0.1D, 1.0D, 0.1D));
        if (collapseTicks >= 30 && collapseTicks < 168) {
            setPos(collapseAnchor.x, collapseAnchor.y, collapseAnchor.z);
            setDeltaMovement(Vec3.ZERO);
        }
        if (collapseTicks >= 55 && collapseTicks % 4 == 0)
            level.sendParticles(Asterion.DOOR_SMOKE, getX(), getY() + .6, getZ(),
                    collapseTicks >= 118 ? 14 : 5, 2.7, .35, 2.7, collapseTicks >= 118 ? .10 : .025);
        if (collapseTicks <= 30 && (collapseTicks % 5) == 0) {
            MazeShiftPayload rumble = new MazeShiftPayload(blockPosition(), 72.0F,
                    0.65F + collapseTicks / 18.0F, 14);
            for (ServerPlayer viewer : level.players())
                if (ServerPlayNetworking.canSend(viewer, MazeShiftPayload.TYPE))
                    ServerPlayNetworking.send(viewer, rumble);
            level.sendParticles(ParticleTypes.DUST_PLUME, getX(), getY() + 0.1D, getZ(),
                    18 + collapseTicks, 5.5D, 0.2D, 5.5D, 0.045D);
            playSound(Asterion.MINOTAUR_STEP, 2.4F + collapseTicks * 0.04F,
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
            noPhysics = false;
            WorldGenerator.buryBossInRubble(level, position());
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 3.2F, 0.42F);
        }
        if (collapseTicks >= 118 && collapseTicks <= 138 && collapseTicks % 4 == 2) {
            level.sendParticles(Asterion.GREEK_FIRE, getX(), getY() + 1.2, getZ(), 12, 1.4, .6, 1.4, .04);
            if (collapseTicks == 118) playSound(SoundEvents.WARDEN_HEARTBEAT, 3F, .65F);
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
            Vec3 safe = combatPoint(collapseAnchor);
            setPos(safe.x, safe.y, safe.z);
            setDeltaMovement(Vec3.ZERO);
            resetFallDistance();
            setBossStage(BossStage.EXTREME);
            interruptRegeneration();
            getEntityData().set(DATA_BOSS_ATTACK_TICKS, 0);
            bossAttackCooldown = 25;
            setAggressive(true);
            getEntityData().set(DATA_RAGE, Math.max(rage(), 6));
            updateChaseSpeed();
            setHealth(getMaxHealth() * 0.70F);
            playRoar(4.5F, 1.12F, 1.45F);
            level.sendParticles(Asterion.GREEK_FIRE, getX(), getY() + getBbHeight() * 0.45D, getZ(),
                    80, 1.4D, 2.2D, 1.4D, 0.08D);
        }
    }

    private void tickLeapAttack(ServerLevel level, ServerPlayer player) {
        getNavigation().stop();
        if (leapImpactTick >= 0) { tickLeapShockwave(level); return; }
        if (bossAttackTicks < 16) {
            setDeltaMovement(0, getDeltaMovement().y, 0);
            getLookControl().setLookAt(player, 12, 8);
            return;
        }
        if (bossAttackTicks == 16) {
            // Re-evaluate at takeoff: a long weapon sheath must not leave a stale landing target.
            Vec3 lead = player.getDeltaMovement().multiply(1, 0, 1).scale(4);
            if (lead.lengthSqr() > 4) lead = lead.normalize().scale(2);
            leapPlan = MinotaurLeapPlan.find(level, this, player.position().add(lead));
            if (leapPlan == null) {
                debugDecision = "Leap cancelled: no clear body arc or supported landing; routing around obstacle";
                obstacleLeapRetryTick = tickCount + 60;
                finishBossAttack(18);
                return;
            }
            bossLeapTarget = leapPlan.landing();
            leapFlightTick = 0;
            bossWasAirborne = true;
            setOnGround(false);
            armHeavyJump();
            sendBossTelegraph(level, bossLeapTarget, Vec3.ZERO, 7.5F, leapPlan.ticks(), BossTelegraphPayload.TARGET_CIRCLE);
            playSound(SoundEvents.GOAT_LONG_JUMP, 2.8F, .62F);
        }
        if (bossWasAirborne && leapFlightTick > 2 && onGround()) {
            bossWasAirborne = false;
            leapPlan = null;
            leapImpactTick = bossAttackTicks;
            getEntityData().set(DATA_LEAP_LANDING, bossAttackTicks);
            leapImpactOrigin = position();
            setDeltaMovement(0, getDeltaMovement().y, 0);
            performLeapImpact(level);
            return;
        }
        if (leapPlan != null) {
            if (horizontalCollision && leapFlightTick > 1) {
                // Geometry may change after planning. Fall safely; never tunnel or restart the jump.
                leapPlan = null;
                setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
            } else if (leapFlightTick < leapPlan.ticks()) {
                Vec3 next = leapPlan.point(++leapFlightTick);
                setDeltaMovement(next.subtract(position()));
                resetFallDistance();
                hurtMarked = true;
            } else setDeltaMovement(0, Math.min(-.08, getDeltaMovement().y), 0);
        }
        if (getBoundingBox().inflate(.65).intersects(player.getBoundingBox()) && attackCooldown <= 0) {
            player.hurtServer(level, damageSources().mobAttack(this), 15);
            Vec3 direction = player.position().subtract(position()).normalize();
            ragdollPlayer(player, direction.scale(3).add(0, 1.15, 0), 1.55F);
            scheduleWallCombo(player, 120);
            scheduleAirCatch(player, 34);
            attackCooldown = 24;
        }
        if (bossAttackTicks >= 90) { leapPlan = null; finishBossAttack(35); }
    }

    private void performLeapImpact(ServerLevel level) {
        scarArena(level, position(), 9);
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
        scarArena(level, position(), 5);
        for (ServerPlayer victim : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(6.5D))) {
            Vec3 away = victim.position().subtract(position());
            if (victim.hurtServer(level, damageSources().mobAttack(this), 13.0F))
                ragdollPlayer(victim, new Vec3(away.x, 0, away.z).normalize().scale(2.1D).add(0, 0.45D, 0), 1.3F);
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + getBbHeight() * 0.48D, getZ(),
                10, 2.8D, 0.7D, 2.8D, 0.0D);
    }

    private void tickGrabAttack(ServerLevel level, ServerPlayer player) {
        if (grabbedPlayer == null && bossAttackTicks >= 8 && bossAttackTicks <= 15
                && canCatchPlayer(player) && hasLineOfSight(player)) {
            grabbedPlayer = player.getUUID();
            player.stopRiding();
            getEntityData().set(DATA_HELD_PLAYER, player.getId());
            lockReachTo(player);
        }
        Player foundGrabbed = grabbedPlayer == null ? null : level.getPlayerByUUID(grabbedPlayer);
        ServerPlayer grabbed = foundGrabbed instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        if ((grabbed == null || !grabbed.isAlive() || grabbed.isSpectator()) && bossAttackTicks >= 15) {
            getEntityData().set(DATA_GRAB_TARGET_ID, -1);
            finishBossAttack(48);
            return;
        }
        if (grabbed != null && bossAttackTicks >= 8 && bossAttackTicks < 48) {
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
            RagdollServerNetworking.forceAuthority(grabbed, Vec3.ZERO);
            if ((bossAttackTicks & 3) == 0)
                level.sendParticles(ParticleTypes.LARGE_SMOKE, hand.x, hand.y, hand.z,
                        3, 0.35D, 0.5D, 0.35D, 0.02D);
        }
        if (grabbed != null && bossAttackTicks == 48) {
            Vec3 center = combatCenter();
            Vec3 away = grabbed.position().subtract(position());
            Vec3 horizontal = new Vec3(away.x, 0.0D, away.z);
            if (horizontal.lengthSqr() < 0.04D) horizontal = center.subtract(position());
            horizontal = new Vec3(horizontal.x, 0.0D, horizontal.z).normalize();
            if (horizontal.lengthSqr() < .01) horizontal = Vec3.directionFromRotation(0, getYRot());
            // Roughly 50–80 blocks on open, level ground; walls stop the flight early.
            double throwPower = 5.4D + random.nextDouble() * 1.5D;
            Vec3 impulse = horizontal.scale(throwPower).add(0.0D, 1.25D, 0.0D);
            getEntityData().set(DATA_HELD_PLAYER, -1);
            grabbed.hurtServer(level, damageSources().mobAttack(this), 10.0F);
            // The scripted wall hit owns impact damage, including delayed client tumble reports.
            RagdollServerNetworking.suppressThrowFallDamage(grabbed, 120);
            ragdollPlayer(grabbed, impulse, 1.85F, true);
            thrownPlayer = grabbed.getUUID();
            grapplePull = false;
            hornKnockback = false;
            throwWallImpact = null;
            throwVelocity = impulse;
            throwFlightTicks = 0;
            scheduleWallCombo(grabbed, 150);
            throwPursuitPending = true;
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

    private double bossImpactY(ServerPlayer player) {
        return Math.max(combatCenter().y, player.getY());
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
        leapPlan = null;
        getEntityData().set(DATA_WEAPON_SWAP, 0);
        if (attackWeapon(bossAttack) != 0 && bossAttack != BossAttack.AXE_THROW)
            weaponUsesRemaining = Math.max(0, weaponUsesRemaining - 1);
        getEntityData().set(DATA_HELD_PLAYER, -1);
        wallPinTicks = 0;
        wallPinPoint = null;
        if (bossAttack == BossAttack.FIRE_RINGS && level() instanceof ServerLevel level)
            clearBossFire(level);
        if (bossAttack == BossAttack.CHAIN_GRAPPLE) {
            chainGrappleTarget = null;
            getEntityData().set(DATA_REACH_ARM, 0);
            getEntityData().set(DATA_GRAB_TARGET_ID, -1);
            clearLockedReach();
        }
        if (bossAttack == BossAttack.GREEK_FIRE_LASER) setGlowingTag(false);
        if ((bossAttack == BossAttack.PUNCH_COMBO || bossAttack == BossAttack.PUNCH_SINGLE)) {
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
        bossAttackCooldown = Math.max(12, (int)Math.ceil(bossAttackCooldown * rageCooldownMultiplier()));
        bossAttackTicks = 0;
        getEntityData().set(DATA_BOSS_ATTACK_TICKS, 0);
    }

    private void syncBossPartyScaling(ServerLevel level, boolean initial) {
        int players = (int)level.players().stream()
                .filter(player -> player.isAlive() && !player.isCreative() && !player.isSpectator()
                        && (WorldGenerator.isInsideBossArena(player.position())
                        || player.distanceToSqr(combatCenter()) < 72.0D * 72.0D))
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

    private void tickLowObstacleRecovery(ServerLevel level) {
        BehaviorPhase phase = behaviorPhase();
        if (phase != BehaviorPhase.ROAMING && phase != BehaviorPhase.HUNTING
                && phase != BehaviorPhase.CHASING) {
            lowSnagTicks = 0;
            return;
        }
        lowSnagTicks = horizontalCollision ? lowSnagTicks + 1 : Math.max(0, lowSnagTicks - 2);
        if (lowSnagTicks < 3) return;
        Vec3 motion = getDeltaMovement();
        Vec3 forward = new Vec3(motion.x, 0.0D, motion.z);
        if (forward.lengthSqr() < 0.01D) forward = Vec3.directionFromRotation(0.0F, yBodyRot);
        AABB sweep = getBoundingBox().expandTowards(forward.normalize().scale(1.45D))
                .inflate(0.20D, 0.10D, 0.20D);
        int broken = behaviorPhase() == BehaviorPhase.BOSS ? 0 : WorldGenerator.breakPlayerBlocksAround(level, sweep);
        if (behaviorPhase() != BehaviorPhase.BOSS) broken += WorldGenerator.breakLowMazeSnags(level, sweep, this);
        if (broken > 0) {
            lowSnagTicks = 0;
            getNavigation().stop();
            awarenessRepathTicks = 0;
            level.sendParticles(ParticleTypes.DUST_PLUME, sweep.getCenter().x, getY() + 0.5D,
                    sweep.getCenter().z, Math.min(16, broken * 2), 0.55D, 0.45D, 0.55D, 0.04D);
            playSound(Asterion.MINOTAUR_STEP, 1.5F, 0.52F);
        }
    }

    private void tickBossObstacleTraversal(ServerLevel level) {
        AttributeInstance stepHeight = getAttribute(Attributes.STEP_HEIGHT);
        boolean activeBoss = behaviorPhase() == BehaviorPhase.BOSS && bossStage != BossStage.DEFEATED;
        if (stepHeight != null && Math.abs(stepHeight.getBaseValue() - (activeBoss ? 4 : 3)) > .01)
            stepHeight.setBaseValue(activeBoss ? 4 : 3);
        if (!activeBoss || bossStunTicks > 0 || bossStage == BossStage.COLLAPSE
                || bossAttack != BossAttack.NONE || weaponSwapTicks() > 0 || !onGround()) {
            bossObstacleTicks = 0;
            return;
        }
        bossObstacleTicks = horizontalCollision ? Math.min(20, bossObstacleTicks + 1) : Math.max(0, bossObstacleTicks - 2);
        if (bossObstacleTicks < 6 || tickCount < obstacleLeapRetryTick) return;
        obstacleLeapRetryTick = tickCount + 60;
        bossObstacleTicks = 0;
        if (getTarget() instanceof ServerPlayer target && attackReady(BossAttack.LEAP)
                && MinotaurLeapPlan.find(level, this, target.position()) != null) {
            debugDecision = "Obstacle ahead: committing to a checked leap toward the player";
            beginBossAttack(target, BossAttack.LEAP);
        } else {
            getNavigation().stop();
            debugDecision = "Obstacle cannot be vaulted safely; finding another route";
        }
    }

    private float playerLookExposure(ServerPlayer player, double distance) {
        if (distance > 58.0D || !player.hasLineOfSight(this)) return 0.0F;
        Vec3 towardMe = getEyePosition().subtract(player.getEyePosition()).normalize();
        double dot = player.getViewVector(1.0F).normalize().dot(towardMe);
        double peripheral = distance < 26.0D ? 0.82D : 0.91D;
        double focused = distance < 26.0D ? 0.97D : 0.992D;
        double angularExposure = Mth.clamp((dot - peripheral) / (focused - peripheral), 0.0D, 1.0D);
        Vec3 midpoint = player.getEyePosition().lerp(getEyePosition(), 0.62D);
        double dust = Math.max(WorldGenerator.volumetricDustDensity(midpoint, level().getGameTime()),
                WorldGenerator.volumetricDustDensity(position(), level().getGameTime()));
        return (float)(angularExposure * Mth.lerp(Mth.clamp(dust, 0.0D, 1.0D), 1.0D, 0.34D));
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
            if (!loadCandidateChunk(feet)) continue;
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
            double angle = behind + (random.nextDouble() - 0.5D) * 1.5D;
            double distance = 14.0D + random.nextDouble() * 8.0D;
            Vec3 candidate = WorldGenerator.nearestMazeCorridor(
                    player.getX() + Math.cos(angle) * distance,
                    player.getZ() + Math.sin(angle) * distance);
            BlockPos feet = BlockPos.containing(candidate);
            if (!loadCandidateChunk(feet)) continue;
            setPos(candidate.x, candidate.y, candidate.z);
            boolean valid = isConnectedHiddenSpawn(player, candidate, feet);
            double hallway = valid ? stalkingHallwaySpan() : -1.0D;
            setPos(original.x, original.y, original.z);
            double dust = valid ? WorldGenerator.volumetricDustDensity(candidate, level().getGameTime()) : 0.0D;
            double score = hallway + dust * 12.0D;
            if (valid && score > bestHallway) {
                best = candidate;
                bestHallway = score;
            }
            if (valid && hallway >= 48.0D && dust >= 0.40D) return candidate;
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
        playSound(Asterion.MINOTAUR_STEP, 0.72F, 0.42F);

        stalkingRoute.clear();
        stalkingDestination = null;
        stalkingAnchor = player.position();
        lastKnownPlayerPosition = player.position();
        shadowArrivalTicks = 30;
        shadowRelocateCooldown = behaviorPhase() == BehaviorPhase.HUNTING
                ? random.nextIntBetweenInclusive(180, 320)
                : random.nextIntBetweenInclusive(160, 280);
        enterStalkMode(random.nextBoolean() ? StalkMode.SHADOWING : StalkMode.FLANKING);
        return true;
    }

    private void emitShadowArrival(ServerLevel level) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + getBbHeight() * 0.42D,
                getZ(), 22, 1.05D, 1.35D, 1.05D, 0.018D);
        level.sendParticles(ParticleTypes.DUST_PLUME, getX(), getY() + 0.18D,
                getZ(), 28, 1.25D, 0.35D, 1.25D, 0.035D);
        playSound(Asterion.MINOTAUR_STEP, 0.82F, 0.40F);
    }

    private Vec3 findShadowRelocation(ServerPlayer player) {
        Vec3 original = position();
        Vec3 view = player.getViewVector(1.0F);
        double facing = Math.atan2(view.z, view.x);
        boolean emergencyLeash = distanceTo(player) > 58.0D;
        Vec3 best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int attempt = 0; attempt < 24; attempt++) {
            boolean behind = attempt < 15;
            double offset = behind ? Math.PI : (random.nextBoolean() ? Math.PI * 0.5D : -Math.PI * 0.5D);
            double angle = facing + offset + (random.nextDouble() - 0.5D) * (behind ? 1.25D : 0.72D);
            double distance = emergencyLeash
                    ? 18.0D + random.nextDouble() * 10.0D
                    : 28.0D + random.nextDouble() * 20.0D;
            Vec3 candidate = WorldGenerator.nearestMazeCorridor(
                    player.getX() + Math.cos(angle) * distance,
                    player.getZ() + Math.sin(angle) * distance);
            BlockPos feet = BlockPos.containing(candidate);
            if (!loadCandidateChunk(feet)) continue;
            setPos(candidate.x, candidate.y, candidate.z);
            boolean valid = !WorldGenerator.isApproachingCenter(candidate)
                    && isConnectedHiddenSpawn(player, candidate, feet);
            double hallway = valid ? stalkingHallwaySpan() : 0.0D;
            setPos(original.x, original.y, original.z);
            if (valid) {
                double candidateDistance = candidate.distanceTo(player.position());
                double distanceScore = 18.0D - Math.abs(candidateDistance - 42.0D);
                double dust = WorldGenerator.volumetricDustDensity(candidate, level().getGameTime());
                double score = hallway + distanceScore + dust * 15.0D;
                if (score > bestScore) {
                    best = candidate;
                    bestScore = score;
                }
                if (hallway >= 52.0D && candidateDistance >= 34.0D && dust >= 0.48D) return candidate;
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
            if (!loadCandidateChunk(feet)) continue;
            setPos(candidate.x, candidate.y, candidate.z);
            boolean valid = isConnectedHiddenSpawn(player, candidate, feet);
            setPos(original.x, original.y, original.z);
            if (valid) return candidate;
        }
        setPos(original.x, original.y, original.z);
        return null;
    }

    private boolean loadCandidateChunk(BlockPos position) {
        if (!(level() instanceof ServerLevel serverLevel)) return false;
        serverLevel.getChunkAt(position);
        return true;
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

    public boolean runningLocomotion() { return behaviorPhase() == BehaviorPhase.CHASING || behaviorPhase() == BehaviorPhase.BOSS; }
    // Mob.setSpeed also scales forward input, so ground acceleration is proportional to speed squared.
    public static double movementAttributeFor(double blocksPerSecond) { return Math.sqrt(blocksPerSecond * (1 - .6 * .91) / (20 * .98)); }
    private void updateChaseSpeed() {
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(movementAttributeFor(RUN_BLOCKS_PER_SECOND));
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
            playSound(Asterion.MINOTAUR_STEP, behaviorPhase() == BehaviorPhase.CHASING ? 1.8F : 1.05F,
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
        playSound(Asterion.MINOTAUR_STEP, 3.2F, 0.34F);
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
            scarArena(level, position(), 3);
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
        if (debugMode) {
            float before = getHealth();
            boolean damaged = super.hurtServer(level, source, Math.min(amount, Math.max(0, before - 1)));
            if (damaged) { interruptRegeneration(); recordCloseDamage(source, before - getHealth()); }
            return damaged;
        }
        if (doorEntryTicks() > 0) return false;
        if (source.getEntity() == this || source.getDirectEntity() == this
                || source.is(DamageTypeTags.IS_FIRE)) return false;
        if (behaviorPhase() == BehaviorPhase.BOSS
                && source.getDirectEntity() instanceof AbstractArrow arrow
                && source.getEntity() instanceof ServerPlayer archer) {
            // Resolve every boss-arrow collision here and remove the projectile immediately.  A
            // probabilistic fall-through left arrows attached to the custom GeckoLib entity while
            // its attack state could replace/discard it, which was the unstable impact path.
            arrow.discard();
            interruptRegeneration();
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
            interruptRegeneration();
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
            recordCloseDamage(source, dealt);
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

    private double horizontalDistanceToArenaCenter(Vec3 position) {
        Vec3 center = combatCenter();
        double dx = position.x - center.x;
        double dz = position.z - center.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void containArenaEscape(ServerLevel level, ServerPlayer player) {
        Vec3 center = combatCenter();
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

    private void interruptRegeneration() {
        regenerationDeadline = level().getGameTime() + 600 + random.nextInt(301);
    }

    private void tickRegeneration(ServerLevel level) {
        if (regenerationDeadline == 0) interruptRegeneration();
        if (bossStage != BossStage.EXTREME || doorEntryTicks() > 0 || bossStunTicks > 0
                || level.getGameTime() < regenerationDeadline || level.getGameTime() % 20 != 0
                || getHealth() >= getMaxHealth()) return;
        // At most one heart/second, also capped at 0.25% of total health. Any hit restarts the random delay.
        heal(Math.min(2F, getMaxHealth() * .0025F));
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + getBbHeight() * .55, getZ(),
                5, .8, 1, .8, .015);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    public int collapseAnimationTicks() {
        return getEntityData().get(DATA_BOSS_STAGE) == BossStage.COLLAPSE.ordinal()
                ? getEntityData().get(DATA_BOSS_ATTACK_TICKS) : 0;
    }

    public AnimationState animationState() {
        if (collapseAnimationTicks() > 0) return collapseAnimationTicks() <= 28 ? AnimationState.IDLE
                : collapseAnimationTicks() >= 138 ? AnimationState.REVIVE : AnimationState.DIES;
        if (weaponSwapTicks() > 0) return isSheathingWeapon()
                ? weaponTransitionMode() == 2 ? AnimationState.SHEATHE_SWORD : AnimationState.SHEATHE_AXE
                : pendingWeaponMode() == 2 ? AnimationState.DRAW_SWORD : AnimationState.DRAW_AXE;
        if (doorEntryTicks() > 0) return AnimationState.ROAR_START;
        BossAttack renderedAttack = bossAttackState();
        int renderedAttackTicks = getEntityData().get(DATA_BOSS_ATTACK_TICKS);
        if (renderedAttack == BossAttack.GRAB && behaviorPhase() != BehaviorPhase.BOSS)
            return AnimationState.IDLE;
        if (renderedAttack == BossAttack.LEAP) return getEntityData().get(DATA_LEAP_LANDING) >= 0 ? AnimationState.LAND : AnimationState.LEAP;
        if (behaviorPhase() == BehaviorPhase.BOSS) {
            if (renderedAttack == BossAttack.CHARGE || renderedAttack == BossAttack.RED_LIGHTNING_CHARGE
                    || renderedAttack == BossAttack.STAMPEDE || renderedAttack == BossAttack.PAWING)
                return renderedAttack == BossAttack.PAWING
                        || renderedAttackTicks <= chargeAnimationWindup()
                        ? AnimationState.WARNING : AnimationState.CHARGE_RUN;
            if (renderedAttack == BossAttack.HORN_RAM) return renderedAttackTicks <= 28
                    ? AnimationState.WARNING : AnimationState.CHARGE_RUN;
            if (renderedAttack == BossAttack.SMOKE_BELCH) return AnimationState.BELCH;
            if (renderedAttack == BossAttack.GREEK_FIRE_LASER || renderedAttack == BossAttack.FIRE_RINGS)
                return AnimationState.ROAR_START;
            if (renderedAttack == BossAttack.BACK_KICK) return AnimationState.BACK_KICK;
            if (renderedAttack == BossAttack.CLEAVE
                    || renderedAttack == BossAttack.ARENA_SWEEP) return AnimationState.ATTACK;
            if (renderedAttack == BossAttack.SLAM) return AnimationState.VERTICAL_ATTACK;
            if (renderedAttack == BossAttack.AXE_CHOP) return AnimationState.AXE_CHOP;
            if (renderedAttack == BossAttack.RUBBLE_THROW) return AnimationState.RUBBLE;
            if (renderedAttack == BossAttack.RAGDOLL_STOMP) return renderedAttackTicks <= 18 ? AnimationState.CHASE
                    : getEntityData().get(DATA_LEAP_LANDING) >= 0 ? AnimationState.LAND : AnimationState.LEAP;
            if (renderedAttack == BossAttack.GRAB) return AnimationState.IDLE;
            if (renderedAttack == BossAttack.WALL_SHOVE || renderedAttack == BossAttack.PUNCH_SINGLE) return AnimationState.PUNCH_SINGLE;
            if (renderedAttack == BossAttack.AXE_THROW) return AnimationState.AXE_THROW;
            if (renderedAttack == BossAttack.RETRIEVE_AXE) return getDeltaMovement().horizontalDistanceSqr() > .001 ? AnimationState.CHASE : AnimationState.IDLE;
            if (renderedAttack == BossAttack.SWORD_COMBO)
                return AnimationState.SWORD;
            if (renderedAttack == BossAttack.SPIN_COMBO) return AnimationState.SPIN;
            if (renderedAttack == BossAttack.CHAIN_GRAPPLE || renderedAttack == BossAttack.ARROW_RETURN)
                return AnimationState.CHAIN;
            if (renderedAttack == BossAttack.PUNCH_COMBO) return AnimationState.PUNCH;
        }
        if (swinging && behaviorPhase() != BehaviorPhase.BOSS) return AnimationState.ATTACK;
        if (behaviorPhase() == BehaviorPhase.WARNING) return AnimationState.ROAR_START;
        if (behaviorPhase() == BehaviorPhase.CHASING && getEntityData().get(DATA_CORRIDOR_CHARGE_TICKS) > 0)
            return getEntityData().get(DATA_CORRIDOR_CHARGE_TICKS) < 18 ? AnimationState.WARNING : AnimationState.CHARGE_RUN;
        if (getDeltaMovement().horizontalDistanceSqr() > .0025) clientMovingUntil = tickCount + 5;
        return tickCount < clientMovingUntil ? (runningLocomotion() ? AnimationState.CHASE : AnimationState.WALK) : AnimationState.IDLE;
    }

    public boolean isPerformingGrab() {
        return bossAttackState() == BossAttack.GRAB;
    }

    private int chargeAnimationWindup() {
        if (getEntityData().get(DATA_CORRIDOR_CHARGE_TICKS) > 0) return 18;
        return switch (bossAttackState()) {
            case CHARGE -> getEntityData().get(DATA_CHARGE_WINDUP);
            case STAMPEDE -> 30;
            case HORN_RAM -> 28;
            case RED_LIGHTNING_CHARGE, PAWING -> 38;
            default -> 32;
        };
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
        if (level() instanceof ServerLevel server)
            broadcastGroundTelegraph(server, new BossTelegraphPayload(position(), Vec3.ZERO, 0, 0,
                    BossTelegraphPayload.TARGET_CIRCLE, getId(), 0, 0, 0));
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
        controllers.add(new MinotaurAnimationController(test -> {
            AnimationState pose = animationState();
            RawAnimation animation = switch (pose) {
                case DRAW_SWORD -> DRAW_SWORD_ANIMATION;
                case DRAW_AXE -> DRAW_AXE_ANIMATION;
                case SHEATHE_SWORD -> SHEATHE_SWORD_ANIMATION;
                case SHEATHE_AXE -> SHEATHE_AXE_ANIMATION;
                case RUBBLE -> RUBBLE_ANIMATION;
                case DIES -> DIES_ANIMATION;
                case REVIVE -> REVIVE_ANIMATION;
                case ATTACK -> ATTACK_ANIMATION;
                case VERTICAL_ATTACK -> VERTICAL_ATTACK_ANIMATION;
                case AXE_CHOP -> AXE_CHOP_ANIMATION;
                case AXE_THROW -> AXE_THROW_ANIMATION;
                case SWORD -> SWORD_ANIMATION;
                case SPIN -> SPIN_ANIMATION;
                case LEAP -> LEAP_ANIMATION;
                case LAND -> LAND_ANIMATION;
                case CHAIN -> CHAIN_ANIMATION;
                case PUNCH -> PUNCH_ANIMATION;
                case PUNCH_SINGLE -> PUNCH_SINGLE_ANIMATION;
                case HORN -> HORN_ANIMATION;
                case WARNING -> WARNING_ANIMATION;
                case CHARGE_RUN -> CHARGE_RUN_ANIMATION;
                case ROAR_START -> ROAR_START_ANIMATION;
                case BELCH -> BELCH_ANIMATION;
                case BACK_KICK -> BACK_KICK_ANIMATION;
                case CHASE -> RUN_ANIMATION;
                case WALK -> WALK_ANIMATION;
                case IDLE -> IDLE_ANIMATION;
            };
            // Keep a continuous local tick clock; packet updates only anchor a newly entered pose.
            double age = test.renderState().getAnimatableAge();
            double observed = animationPhaseTick(pose) + test.renderState().getPartialTick();
            if (clientAnimationPose != pose) {
                clientAnimationPose = pose; clientPoseStartAge = age; clientPoseStartTick = observed; clientPoseAge = 0;
            }
            // Extra render passes can request partialTick=1 before the ordinary interpolated pass.
            double poseAge = clientPoseAge = Math.max(clientPoseAge, Math.max(0, age - clientPoseStartAge));
            double ticks = clientPoseStartTick + poseAge;
            double seconds = animationSeconds(pose, ticks);
            test.setControllerSpeed(pose == AnimationState.CHARGE_RUN
                    ? (float)Math.clamp(getDeltaMovement().horizontalDistance() / .35, 1, 2.4) : 1);
            var result = test.setAndContinue(animation);
            ((MinotaurAnimationController)test.controller()).samplePose(seconds, poseAge);
            return result;
        }));
    }

    private double animationPhaseTick(AnimationState pose) {
        if (weaponSwapTicks() > 0) return isSheathingWeapon() ? weaponSwapTicks() : weaponSwapTicks() - weaponSheathTicks();
        if (pose == AnimationState.DIES) return Math.max(0, collapseAnimationTicks() - 28);
        if (pose == AnimationState.REVIVE) return Math.max(0, collapseAnimationTicks() - 138);
        if (pose == AnimationState.ROAR_START && doorEntryTicks() > 0) return doorEntryTicks() - 1;
        if (pose == AnimationState.WARNING && getEntityData().get(DATA_CORRIDOR_CHARGE_TICKS) > 0)
            return getEntityData().get(DATA_CORRIDOR_CHARGE_TICKS);
        if (pose == AnimationState.LAND) return bossAttackAnimationTicks() - getEntityData().get(DATA_LEAP_LANDING);
        if (pose == AnimationState.LEAP && bossAttackState() == BossAttack.RAGDOLL_STOMP)
            return Math.max(0, bossAttackAnimationTicks() - 18);
        return bossAttackAnimationTicks();
    }

    private double animationSeconds(AnimationState pose, double tick) {
        return switch (pose) {
            case DRAW_SWORD -> MinotaurAnimationTiming.DRAW_SWORD.seconds(tick);
            case DRAW_AXE -> MinotaurAnimationTiming.DRAW_AXE.seconds(tick);
            case SHEATHE_SWORD -> MinotaurAnimationTiming.SHEATHE_SWORD.seconds(tick);
            case SHEATHE_AXE -> MinotaurAnimationTiming.SHEATHE_AXE.seconds(tick);
            case ATTACK -> MinotaurAnimationTiming.CLEAVE.seconds(tick);
            case AXE_CHOP -> MinotaurAnimationTiming.CHOP.seconds(tick);
            case VERTICAL_ATTACK -> MinotaurAnimationTiming.SLAM.seconds(tick);
            case AXE_THROW -> MinotaurAnimationTiming.THROW.seconds(tick);
            case SWORD, PUNCH -> MinotaurAnimationTiming.COMBO.seconds(tick);
            case SPIN -> MinotaurAnimationTiming.SPIN.seconds(tick);
            case PUNCH_SINGLE -> MinotaurAnimationTiming.PUNCH.seconds(tick);
            case BACK_KICK -> MinotaurAnimationTiming.BACK_KICK.seconds(tick);
            case CHAIN -> (bossAttackState() == BossAttack.ARROW_RETURN
                    ? MinotaurAnimationTiming.ARROWS : MinotaurAnimationTiming.CHAIN).seconds(tick);
            case RUBBLE -> MinotaurAnimationTiming.RUBBLE.seconds(tick);
            case WARNING -> MinotaurAnimationTiming.chargeSeconds(tick, chargeAnimationWindup());
            case ROAR_START -> (bossAttackState() == BossAttack.GREEK_FIRE_LASER || bossAttackState() == BossAttack.FIRE_RINGS
                    ? MinotaurAnimationTiming.FIRE_ROAR : MinotaurAnimationTiming.ROAR).seconds(tick);
            case BELCH -> MinotaurAnimationTiming.BELCH.seconds(tick);
            case LEAP -> MinotaurAnimationTiming.LEAP.seconds(tick);
            case LAND -> MinotaurAnimationTiming.LAND.seconds(tick);
            case DIES -> MinotaurAnimationTiming.DIES.seconds(tick);
            case REVIVE -> MinotaurAnimationTiming.REVIVE.seconds(tick);
            default -> -1; // Locomotion loops use the controller's clock, including their loop seam.
        };
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
            if (minotaur.bossAttack == BossAttack.LEAP || minotaur.behaviorPhase() == BehaviorPhase.BOSS
                    && (minotaur.bossStage == BossStage.COLLAPSE
                        || minotaur.bossAttack != BossAttack.NONE && minotaur.bossAttack != BossAttack.RETRIEVE_AXE)) {
                operation = Operation.WAIT;
                minotaur.zza = minotaur.xxa = 0;
                minotaur.setJumping(false);
                return;
            }
            float previousYaw = minotaur.getYRot();
            boolean moving = operation == Operation.MOVE_TO || operation == Operation.JUMPING;
            super.tick();
            if (minotaur.behaviorPhase() == BehaviorPhase.BOSS) {
                minotaur.setJumping(false);
                if (operation == Operation.JUMPING) operation = Operation.WAIT;
            }
            if (moving) minotaur.setSpeed((float)movementAttributeFor(minotaur.runningLocomotion()
                    ? RUN_BLOCKS_PER_SECOND : WALK_BLOCKS_PER_SECOND));
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
