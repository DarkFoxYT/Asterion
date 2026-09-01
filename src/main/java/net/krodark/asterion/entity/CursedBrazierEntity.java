package net.krodark.asterion.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.WorldGenerator;
import net.krodark.asterion.block.GreekBrazierBlock;
import net.krodark.asterion.effect.GreekFireBurn;
import net.krodark.asterion.game.GameplayContent;
import net.krodark.asterion.game.GasClouds;
import net.krodark.asterion.network.CursedBrazierAwakeningPayload;
import net.krodark.asterion.network.MazeShiftPayload;
import net.krodark.asterion.network.ragdoll.RagdollImpulsePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CursedBrazierEntity extends PathfinderMob implements GeoEntity {
    private static final RawAnimation IDLE_ANIMATION =
            RawAnimation.begin().thenLoop("animation.cursed_brazier.idle");
    private static final RawAnimation SHOOT_BEAM_ANIMATION =
            RawAnimation.begin().thenPlayAndHold("shoot_beam");
    private static final int TARGET_RANGE = 30;
    private static final int AWAKEN_RANGE = 17;
    public static final int AWAKENING_DURATION = 64;
    private static final int ENCOUNTER_DOOR_RANGE = 32;
    private static final int SHIELD_BRAZIER_COUNT = 7;
    private static final int BRAZIER_SEARCH_RANGE = 30;
    private static final List<Vec3> CARDINAL_DIRECTIONS = List.of(
            new Vec3(1, 0, 0), new Vec3(-1, 0, 0),
            new Vec3(0, 0, 1), new Vec3(0, 0, -1));
    private static final EntityDataAccessor<Boolean> SHIELDED = SynchedEntityData.defineId(
            CursedBrazierEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> ATTACK_ID = SynchedEntityData.defineId(
            CursedBrazierEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE_ID = SynchedEntityData.defineId(
            CursedBrazierEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE_STARTED_AT = SynchedEntityData.defineId(
            CursedBrazierEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ATTACK_STARTED_AT = SynchedEntityData.defineId(
            CursedBrazierEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossBar = new ServerBossEvent(
            UUID.randomUUID(), Component.translatable("entity.asterion.cursed_brazier"),
            BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_10);
    private final List<Vec3> jetPositions = new ArrayList<>();
    private final List<BlockPos> shieldBraziers = new ArrayList<>();
    private final Map<UUID, Integer> hitCooldowns = new java.util.HashMap<>();
    private final int[] attackUses = new int[Attack.values().length];

    private int cooldown = 50;
    private int attackTicks;
    private int attacksStarted;
    private int dashLeg;
    private int phaseTicks;
    private Attack attack = Attack.NONE;
    private Attack lastAttack = Attack.NONE;
    private Vec3 aim = Vec3.ZERO;
    private Vec3 lockedPosition = Vec3.ZERO;
    private boolean middleShieldUsed;
    private boolean finalShieldUsed;
    private Vec3 restingPosition;
    private float desiredYaw;
    private Vec3 gridMoveStart = Vec3.ZERO;
    private Vec3 gridMoveTarget = Vec3.ZERO;
    private int gridMoveTicks;
    private int gridMoveDuration;
    private boolean gridStepTaken;

    public enum Attack {
        NONE, FLOOR_JETS, FIRE_BEAM, SPIN_TORNADO, CARDINAL_DASH
    }

    public enum Phase {
        DORMANT, AWAKENING, ACTIVE
    }

    public CursedBrazierEntity(EntityType<? extends CursedBrazierEntity> type, Level level) {
        super(type, level);
        xpReward = 35;
        setNoGravity(true);
        desiredYaw = getYRot();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 140)
                .add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.ARMOR, 5)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1)
                .add(Attributes.FOLLOW_RANGE, 28);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SHIELDED, false);
        builder.define(ATTACK_ID, 0);
        builder.define(PHASE_ID, Phase.DORMANT.ordinal());
        builder.define(PHASE_STARTED_AT, 0);
        builder.define(ATTACK_STARTED_AT, 0);
    }

    @Override
    protected void registerGoals() {
    }

    public Attack attack() {
        if (!level().isClientSide()) return attack;
        int index = Math.clamp(entityData.get(ATTACK_ID), 0, Attack.values().length - 1);
        return Attack.values()[index];
    }

    public boolean shielded() {
        return entityData.get(SHIELDED);
    }

    public Phase phase() {
        int index = Math.clamp(entityData.get(PHASE_ID), 0, Phase.values().length - 1);
        return Phase.values()[index];
    }

    public float phaseAge(float partialTick) {
        if (!level().isClientSide()) return phaseTicks + partialTick;
        return Math.max(0, tickCount + partialTick - entityData.get(PHASE_STARTED_AT));
    }

    public float attackAge(float partialTick) {
        return attack() == Attack.NONE
                ? 0
                : Math.max(0, tickCount + partialTick - entityData.get(ATTACK_STARTED_AT));
    }

    public boolean flamesActive() {
        boolean lit = phase() == Phase.ACTIVE
                || phase() == Phase.AWAKENING && phaseAge(0) >= 42;
        return lit && isAlive() && !isInWater();
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (!(level() instanceof ServerLevel level)) return;
        hitCooldowns.replaceAll((uuid, ticks) -> ticks - 1);
        hitCooldowns.values().removeIf(ticks -> ticks <= 0);

        updateBossBar();
        if (!isAlive() || isNoAi()) return;
        if (restingPosition == null) restingPosition = position();
        if (phase() == Phase.DORMANT) {
            tickDormant(level);
            return;
        }
        if (phase() == Phase.AWAKENING) {
            tickAwakening(level);
            return;
        }
        if (isInWater()) {
            setShielded(false);
            finishAttack(80);
            GasClouds.clearOwner(level, getUUID());
            return;
        }

        if (tickCount % 4 == 0) {
            level.sendParticles(Asterion.BRAZIER_FIRE,
                    getX(), getY() + getBbHeight() * 0.78, getZ(),
                    3, 0.4, 0.1, 0.4, 0.01);
        }

        ServerPlayer target = nearestTarget(level);
        if (target == null) {
            leaveCombat(level);
            return;
        }

        level.players().stream().filter(this::canFight).forEach(bossBar::addPlayer);
        if (!shielded()) tryStartShieldPhase(level);
        updateShield(level);
        if (shielded()) return;

        if (attack == Attack.NONE) {
            if (!gridStepTaken && beginGridStep(level, target)) return;
            if (gridMoveTicks > 0) {
                tickGridStep();
                return;
            }
            if (--cooldown > 0) return;
            startAttack(chooseAttack(level, target), target);
            attacksStarted++;
        }

        switch (attack) {
            case FLOOR_JETS -> tickFloorJets(level, target);
            case FIRE_BEAM -> tickFireBeam(level, target);
            case SPIN_TORNADO -> tickSpinTornado(level, target);
            case CARDINAL_DASH -> tickCardinalDash(level, target);
            case NONE -> { }
        }
        if (attack != Attack.SPIN_TORNADO && gridMoveTicks == 0) updateFacing();
    }

    private void tickDormant(ServerLevel level) {
        setPos(restingPosition);
        setInvulnerable(true);
        setShielded(false);
        ServerPlayer player = level.players().stream()
                .filter(candidate -> candidate.isAlive() && !candidate.isCreative() && !candidate.isSpectator())
                .filter(candidate -> candidate.distanceToSqr(this) <= AWAKEN_RANGE * AWAKEN_RANGE)
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
        if (player != null) beginAwakening(level);
    }

    private void beginAwakening(ServerLevel level) {
        setPhase(Phase.AWAKENING);
        setInvulnerable(true);
        attack = Attack.NONE;
        entityData.set(ATTACK_ID, Attack.NONE.ordinal());
        closeEncounterDoors(level);
        level.playSound(null, blockPosition(), SoundEvents.TRIAL_SPAWNER_ABOUT_TO_SPAWN_ITEM,
                SoundSource.HOSTILE, 1.8F, 0.55F);

        MazeShiftPayload shake = new MazeShiftPayload(blockPosition(), 30F, 0.32F, 62);
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(this) > 42 * 42) continue;
            if (ServerPlayNetworking.canSend(viewer, CursedBrazierAwakeningPayload.TYPE)) {
                ServerPlayNetworking.send(viewer,
                        new CursedBrazierAwakeningPayload(getId(), AWAKENING_DURATION));
            }
            if (ServerPlayNetworking.canSend(viewer, MazeShiftPayload.TYPE)) {
                ServerPlayNetworking.send(viewer, shake);
            }
        }
    }

    private void tickAwakening(ServerLevel level) {
        setPos(restingPosition);
        int age = ++phaseTicks;
        if (age >= 30 && age <= 66 && age % 3 == 0) {
            float strength = Math.clamp((age - 30) / 24F, 0F, 1F);
            level.sendParticles(Asterion.GREEK_FIRE,
                    getX(), getY() + getBbHeight() * 0.62, getZ(),
                    2 + Math.round(strength * 6), 0.8, 0.35, 0.8,
                    0.02 + strength * 0.02);
        }
        if (age == 46) {
            level.playSound(null, blockPosition(), SoundEvents.FIRECHARGE_USE,
                    SoundSource.HOSTILE, 1.8F, 0.55F);
        }
        if (age < AWAKENING_DURATION) return;

        setInvulnerable(false);
        setPhase(Phase.ACTIVE);
        cooldown = 18;
        level.playSound(null, blockPosition(), SoundEvents.BLAZE_SHOOT,
                SoundSource.HOSTILE, 1.6F, 0.72F);
    }

    private void setPhase(Phase phase) {
        entityData.set(PHASE_ID, phase.ordinal());
        entityData.set(PHASE_STARTED_AT, tickCount);
        phaseTicks = 0;
    }

    private void updateBossBar() {
        bossBar.setProgress(Math.clamp(getHealth() / getMaxHealth(), 0, 1));
        for (ServerPlayer player : List.copyOf(bossBar.getPlayers())) {
            if (!isAlive() || phase() != Phase.ACTIVE || !canFight(player)) bossBar.removePlayer(player);
        }
    }

    private ServerPlayer nearestTarget(ServerLevel level) {
        return level.players().stream()
                .filter(this::canFight)
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    private boolean canFight(ServerPlayer player) {
        return player.isAlive()
                && !player.isCreative()
                && !player.isSpectator()
                && player.level() == level()
                && distanceToSqr(player) <= TARGET_RANGE * TARGET_RANGE
                && !WorldGenerator.isNearSafeRune((ServerLevel) level(), player.blockPosition());
    }

    private void leaveCombat(ServerLevel level) {
        if (attack != Attack.NONE) finishAttack(30);
        if (shielded()) {
            setShielded(false);
            shieldBraziers.clear();
        }
        GasClouds.clearOwner(level, getUUID());
    }

    private Attack chooseAttack(ServerLevel level, ServerPlayer target) {
        double distance = Math.sqrt(horizontalDistanceSqr(target.position(), position()));
        List<Attack> choices = new ArrayList<>();
        choices.add(Attack.FLOOR_JETS);
        if (distance < 8 && clearance(level, cardinalToward(target), 8) > 2.5) {
            choices.add(Attack.CARDINAL_DASH);
            choices.add(Attack.CARDINAL_DASH);
        }
        if (hasLineOfSight(target)) {
            choices.add(Attack.FIRE_BEAM);
            if (distance > 11) choices.add(Attack.FIRE_BEAM);
        }
        if (clearance(level, cardinalToward(target), 8) > 3.5) {
            choices.add(Attack.CARDINAL_DASH);
            if (distance > 13) choices.add(Attack.CARDINAL_DASH);
        }
        if (level.noCollision(this, getBoundingBox().inflate(2.2, 0, 2.2))) {
            choices.add(Attack.SPIN_TORNADO);
            if (distance < 10) choices.add(Attack.SPIN_TORNADO);
        }

        if (choices.size() > 1) choices.removeIf(candidate -> candidate == lastAttack);
        int leastUsed = choices.stream()
                .mapToInt(candidate -> attackUses[candidate.ordinal()])
                .min().orElse(0);
        choices.removeIf(candidate -> attackUses[candidate.ordinal()] > leastUsed);
        return choices.get(random.nextInt(choices.size()));
    }

    private void startAttack(Attack next, ServerPlayer target) {
        attack = next;
        lastAttack = next;
        attackUses[next.ordinal()]++;
        entityData.set(ATTACK_ID, next.ordinal());
        entityData.set(ATTACK_STARTED_AT, tickCount);
        attackTicks = 0;
        dashLeg = 0;
        jetPositions.clear();
        lockedPosition = target.position();
        aim = directionOrForward(target.getEyePosition().subtract(mouth()));
        face(aim);
        updateFacingImmediate();
        playSound(SoundEvents.FIRE_AMBIENT, 1.2F, 0.65F);
    }

    private void finishAttack(int delay) {
        attack = Attack.NONE;
        entityData.set(ATTACK_ID, 0);
        attackTicks = 0;
        dashLeg = 0;
        jetPositions.clear();
        gridStepTaken = false;
        gridMoveTicks = 0;
        cooldown = Math.min(delay, 6) + random.nextInt(5);
    }

    private void tickFloorJets(ServerLevel level, ServerPlayer target) {
        int tick = ++attackTicks;
        if (tick == 1) prepareFloorJets(level, target);
        if (tick <= 38 && tick % 3 == 0) {
            for (Vec3 position : jetPositions) {
                level.sendParticles(new DustParticleOptions(0xFF170D, 1.15F),
                        position.x, position.y + 0.035, position.z,
                        14, 0.48, 0.015, 0.48, 0.005);
                level.sendParticles(Asterion.GREEK_FIRE_SOOT, position.x, position.y, position.z,
                        6, 0.35, 0.03, 0.35, 0.01);
            }
        }
        if (tick >= 40 && tick <= 70 && tick % 6 == 0) {
            for (Vec3 position : jetPositions) {
                for (int burst = 0; burst < 4; burst++) {
                    spawnFlame(level, position, new Vec3(0, 0.22 + burst * 0.055, 0));
                }
                damagePlayers(level, new AABB(position.subtract(0.9, 0.2, 0.9),
                        position.add(0.9, 3.4, 0.9)), 7F, 45, false);
            }
        }
        if (tick > 82) finishAttack(4);
    }

    private void tickFireBeam(ServerLevel level, ServerPlayer target) {
        int tick = ++attackTicks;
        Vec3 desired = directionOrForward(target.getEyePosition().subtract(mouth()));
        aim = directionOrForward(aim.lerp(desired, tick < 35 ? 0.09 : 0.025));
        face(aim);
        if (tick < 35 && tick % 2 == 0) {
            Vec3 mouth = mouth();
            level.sendParticles(Asterion.GREEK_FIRE_SOOT, mouth.x, mouth.y, mouth.z,
                    5, 0.18, 0.12, 0.18, 0.015);
        }
        if (tick >= 35 && tick <= 82) traceFireBeam(level, tick);
        if (tick > 92) finishAttack(5);
    }

    private void traceFireBeam(ServerLevel level, int tick) {
        Vec3 origin = mouth();
        for (double distance = 1; distance <= 26; distance += 0.85) {
            Vec3 point = origin.add(aim.scale(distance));
            BlockPos block = BlockPos.containing(point);
            if (!level.getBlockState(block).getCollisionShape(level, block).isEmpty()) return;
            if ((tick + (int) (distance * 2)) % 3 == 0) spawnFlame(level, point, aim.scale(0.08));
            damagePlayers(level, new AABB(point.subtract(0.7, 0.7, 0.7),
                    point.add(0.7, 0.7, 0.7)), 8F, 18, false);
        }
    }

    private void tickSpinTornado(ServerLevel level, ServerPlayer target) {
        int tick = ++attackTicks;
        double targetHeight = target.getY() + target.getBbHeight() * 0.45;
        if (tick <= 45) {
            Vec3 next = position().add(0, Math.clamp(targetHeight - getY(), -0.12, 0.12), 0);
            if (canOccupy(level, next)) setPos(next);
        } else if (tick == 46) {
            lockedPosition = position();
        } else {
            setPos(lockedPosition);
        }

        if (tick < 70) {
            if (tick % 3 == 0) {
                level.sendParticles(Asterion.GREEK_FIRE_SOOT,
                        getX(), getY() + getBbHeight() * 0.55, getZ(),
                        8, 0.7, 0.25, 0.7, 0.02);
            }
            return;
        }

        double progress = Math.clamp((tick - 70) / 68.0, 0, 1);
        double eased = progress * progress * (3.0 - 2.0 * progress);
        double speed = 2.0 + 14.0 * eased;
        setYRot((float) (getYRot() + speed));
        yBodyRot = getYRot();
        int streams = progress < 0.35 ? 2 : progress < 0.7 ? 3 : 4;
        for (int stream = 0; stream < streams; stream++) {
            double angle = Math.toRadians(getYRot()) + stream * Math.PI * 2 / streams;
            Vec3 direction = new Vec3(Math.cos(angle), 0.015 + progress * 0.025, Math.sin(angle));
            spawnFlame(level, mouth().add(direction.scale(1.1)),
                    direction.scale(0.35 + progress * 0.52));
        }
        damagePlayers(level, getBoundingBox().inflate(1.35, 0.55, 1.35),
                9F, 12, true);
        if (tick > 160) finishAttack(5);
    }

    private void tickCardinalDash(ServerLevel level, ServerPlayer target) {
        int tick = ++attackTicks;
        int legTick = (tick - 1) % 14;
        if (legTick == 0) {
            aim = bestCardinalDirection(level, target);
            if (clearance(level, aim, 8) < 1.2) {
                finishAttack(35);
                return;
            }
            face(aim);
            updateFacingImmediate();
            dashLeg++;
        }
        if (legTick < 10) {
            Vec3 next = position().add(aim.scale(0.92));
            if (canOccupy(level, next)) setPos(next);
            else attackTicks += 10 - legTick;
            GasClouds.emit(level, position().add(0, 0.45, 0), aim.scale(-0.045), getUUID());
            level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.45, getZ(),
                    3, 0.3, 0.18, 0.3, 0.01);
            damagePlayers(level, getBoundingBox().inflate(0.65, 0.3, 0.65),
                    10F, 16, true);
        }
        if (dashLeg >= 7 && legTick >= 10) finishAttack(4);
    }

    private void tryStartShieldPhase(ServerLevel level) {
        float health = getHealth() / getMaxHealth();
        boolean middle = health <= 0.58F && !middleShieldUsed;
        boolean ending = health <= 0.24F && !finalShieldUsed;
        if (!middle && !ending) return;
        if (tickCount % 10 != 0) return;

        List<BlockPos> candidates = findLitBraziers(level);
        if (candidates.size() < SHIELD_BRAZIER_COUNT) return;
        if (attack != Attack.NONE) finishAttack(0);
        shieldBraziers.clear();
        shieldBraziers.addAll(candidates.subList(0, SHIELD_BRAZIER_COUNT));
        if (middle) middleShieldUsed = true;
        else finalShieldUsed = true;
        lockedPosition = snapToGrid(position());
        if (canOccupy(level, lockedPosition)) setPos(lockedPosition);
        else lockedPosition = position();
        setShielded(true);
        level.playSound(null, blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.HOSTILE, 1.8F, 0.52F);
    }

    private List<BlockPos> findLitBraziers(ServerLevel level) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos center = blockPosition();
        for (BlockPos cursor : BlockPos.betweenClosed(
                center.offset(-BRAZIER_SEARCH_RANGE, -10, -BRAZIER_SEARCH_RANGE),
                center.offset(BRAZIER_SEARCH_RANGE, 10, BRAZIER_SEARCH_RANGE))) {
            var state = level.getBlockState(cursor);
            if (!(state.getBlock() instanceof GreekBrazierBlock)
                    || !GreekBrazierBlock.isRoot(state)
                    || !state.getValue(BlockStateProperties.LIT)) continue;
            found.add(cursor.immutable());
        }
        found.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        return found;
    }

    private boolean beginGridStep(ServerLevel level, ServerPlayer target) {
        gridStepTaken = true;
        Vec3 origin = snapToGrid(position());
        List<Vec3> candidates = new ArrayList<>();
        for (Vec3 direction : CARDINAL_DIRECTIONS) {
            for (int blocks : new int[]{3, 5}) candidates.add(origin.add(direction.scale(blocks)));
        }
        candidates.removeIf(candidate -> !canOccupy(level, candidate));
        if (candidates.isEmpty()) return false;
        candidates.sort(Comparator.comparingDouble(candidate -> {
            double distance = Math.sqrt(horizontalDistanceSqr(candidate, target.position()));
            return Math.abs(distance - 9.0);
        }));
        gridMoveStart = position();
        gridMoveTarget = candidates.get(random.nextInt(Math.min(2, candidates.size())));
        gridMoveTicks = 1;
        gridMoveDuration = 7;
        Vec3 travel = gridMoveTarget.subtract(gridMoveStart);
        face(travel);
        updateFacingImmediate();
        return true;
    }

    private void tickGridStep() {
        float progress = Math.clamp(gridMoveTicks++ / (float) gridMoveDuration, 0F, 1F);
        float eased = progress * progress * (3F - 2F * progress);
        setPos(gridMoveStart.lerp(gridMoveTarget, eased));
        if (progress < 1F) return;
        setPos(gridMoveTarget);
        gridMoveTicks = 0;
    }

    private static Vec3 snapToGrid(Vec3 position) {
        return new Vec3(Math.floor(position.x) + 0.5,
                Math.rint(position.y), Math.floor(position.z) + 0.5);
    }

    private void prepareFloorJets(ServerLevel level, ServerPlayer target) {
        Vec3 velocity = new Vec3(target.getDeltaMovement().x, 0, target.getDeltaMovement().z);
        Vec3 center = target.position().add(velocity.scale(16));
        Vec3 forward = velocity.lengthSqr() > 0.01
                ? velocity.normalize()
                : directionOrForward(new Vec3(target.getX() - getX(), 0, target.getZ() - getZ()));
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        for (int index = -2; index <= 2; index++) {
            Vec3 position = center.add(right.scale(index * 1.8))
                    .add(forward.scale((index & 1) == 0 ? 1.25 : -1.25));
            jetPositions.add(findFloor(level, position));
        }
    }

    private Vec3 findFloor(ServerLevel level, Vec3 position) {
        BlockPos origin = BlockPos.containing(position);
        for (int offset = 4; offset >= -8; offset--) {
            BlockPos floor = origin.offset(0, offset, 0);
            BlockPos feet = floor.above();
            if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) continue;
            if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) continue;
            return new Vec3(position.x, feet.getY() + 0.08, position.z);
        }
        return new Vec3(position.x, position.y + 0.08, position.z);
    }

    private Vec3 cardinalToward(ServerPlayer target) {
        Vec3 offset = target.position().subtract(position());
        return Math.abs(offset.x) > Math.abs(offset.z)
                ? new Vec3(Math.copySign(1, offset.x), 0, 0)
                : new Vec3(0, 0, Math.copySign(1, offset.z));
    }

    private Vec3 bestCardinalDirection(ServerLevel level, ServerPlayer target) {
        Vec3 toward = directionOrForward(new Vec3(target.getX() - getX(), 0, target.getZ() - getZ()));
        Vec3 best = cardinalToward(target);
        double bestScore = -Double.MAX_VALUE;
        for (Vec3 candidate : CARDINAL_DIRECTIONS) {
            double score = clearance(level, candidate, 8) * 1.6 + candidate.dot(toward) * 3;
            if (candidate.equals(aim)) score -= 1.4;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private double clearance(ServerLevel level, Vec3 direction, double maximum) {
        double clear = 0;
        for (double distance = 0.7; distance <= maximum; distance += 0.7) {
            if (!level.noCollision(this, getBoundingBox().move(direction.scale(distance)))) break;
            clear = distance;
        }
        return clear;
    }

    private boolean canOccupy(ServerLevel level, Vec3 target) {
        return level.noCollision(this, getBoundingBox().move(target.subtract(position())));
    }

    private void updateShield(ServerLevel level) {
        if (!shielded()) return;
        setPos(lockedPosition);
        if (tickCount % 2 == 0) {
            double centerY = getY() + getBbHeight() * 0.52;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), centerY, getZ(),
                    9, 2.2, 1.7, 2.2, 0.035);
            level.sendParticles(Asterion.GREEK_FIRE, getX(), centerY, getZ(),
                    7, 2.0, 1.55, 2.0, 0.018);
        }
        int stillLit = 0;
        for (BlockPos brazier : shieldBraziers) {
            var state = level.getBlockState(brazier);
            if (!(state.getBlock() instanceof GreekBrazierBlock)
                    || !state.getValue(BlockStateProperties.LIT)) continue;
            stillLit++;
            if (tickCount % 2 == 0) {
                double x = brazier.getX() + 0.5;
                double y = brazier.getY() + 1.35;
                double z = brazier.getZ() + 0.5;
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z,
                        5, 1.35, 1.15, 1.35, 0.03);
                level.sendParticles(Asterion.GREEK_FIRE, x, y, z,
                        4, 1.15, 0.9, 1.15, 0.015);
            }
        }
        if (stillLit == 0) {
            setShielded(false);
            shieldBraziers.clear();
            cooldown = 3;
            gridStepTaken = true;
            level.sendParticles(ParticleTypes.CLOUD,
                    getX(), getY() + getBbHeight() * 0.52, getZ(),
                    35, 2.1, 1.5, 2.1, 0.08);
            playSound(SoundEvents.FIRE_EXTINGUISH, 1.6F, 0.7F);
        }
    }

    private void setShielded(boolean value) {
        entityData.set(SHIELDED, value);
        setGlowingTag(value);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FIRE)) return false;
        if (!shielded()) return super.hurtServer(level, source, amount);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                getX(), getY() + getBbHeight() * 0.52, getZ(),
                14, 2.1, 1.6, 2.1, 0.04);
        return false;
    }

    private void face(Vec3 direction) {
        float raw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        desiredYaw = Math.round(raw / 90F) * 90F;
    }

    private void updateFacing() {
        float difference = net.minecraft.util.Mth.wrapDegrees(desiredYaw - getYRot());
        setYRot(getYRot() + Math.clamp(difference, -7.5F, 7.5F));
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    private void updateFacingImmediate() {
        setYRot(desiredYaw);
        yRotO = desiredYaw;
        yBodyRot = desiredYaw;
        yHeadRot = desiredYaw;
    }

    private void damagePlayers(ServerLevel level, AABB area, float damage,
                               int immunityTicks, boolean ragdoll) {
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area, this::canFight)) {
            if (hitCooldowns.containsKey(player.getUUID())) continue;
            if (!player.hurtServer(level, level.damageSources().mobAttack(this), damage)) continue;
            GreekFireBurn.ignite(player, ragdoll ? 3F : 6F);
            hitCooldowns.put(player.getUUID(), immunityTicks);
            Vec3 impulse = directionOrForward(player.position().subtract(position()))
                    .scale(ragdoll ? 1.25 : 0.42).add(0, ragdoll ? 0.42 : 0.12, 0);
            player.push(impulse.x, impulse.y, impulse.z);
            player.hurtMarked = true;
            if (ragdoll && ServerPlayNetworking.canSend(player, RagdollImpulsePayload.TYPE)) {
                ServerPlayNetworking.send(player,
                        new RagdollImpulsePayload(position(), impulse, 1.15F));
            }
        }
    }

    private Vec3 mouth() {
        return position().add(0, getBbHeight() * 0.68, 0);
    }

    private void closeEncounterDoors(ServerLevel level) {
        visitEncounterDoors(level,
                net.krodark.asterion.block.CursedBrazierDoorBlockEntity::sealForFight);
    }

    private void openEncounterDoors(ServerLevel level) {
        visitEncounterDoors(level,
                net.krodark.asterion.block.CursedBrazierDoorBlockEntity::openAfterVictory);
    }

    private void visitEncounterDoors(ServerLevel level,
                                     java.util.function.Consumer<net.krodark.asterion.block.CursedBrazierDoorBlockEntity> action) {
        BlockPos center = blockPosition();
        for (BlockPos cursor : BlockPos.betweenClosed(
                center.offset(-ENCOUNTER_DOOR_RANGE, -12, -ENCOUNTER_DOOR_RANGE),
                center.offset(ENCOUNTER_DOOR_RANGE, 12, ENCOUNTER_DOOR_RANGE))) {
            if (level.getBlockEntity(cursor)
                    instanceof net.krodark.asterion.block.CursedBrazierDoorBlockEntity door) {
                action.accept(door);
            }
        }
    }

    private void spawnFlame(ServerLevel level, Vec3 position, Vec3 velocity) {
        GasClouds.emitFlamethrower(level, position, velocity, getUUID());
        GasClouds.ignite(level, position, getUUID());
    }

    private static double horizontalDistanceSqr(Vec3 first, Vec3 second) {
        double x = first.x - second.x;
        double z = first.z - second.z;
        return x * x + z * z;
    }

    private static Vec3 directionOrForward(Vec3 vector) {
        return vector.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : vector.normalize();
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel level) openEncounterDoors(level);
        clearCombatState();
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        clearCombatState();
        super.remove(reason);
    }

    private void clearCombatState() {
        bossBar.removeAllPlayers();
        setShielded(false);
        attack = Attack.NONE;
        if (level() instanceof ServerLevel level) GasClouds.clearOwner(level, getUUID());
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        spawnAtLocation(level, new ItemStack(GameplayContent.CURSED_BRAZIER_KEY));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("BrazierPhase", phase().ordinal());
        output.putInt("BrazierPhaseTicks", phaseTicks);
        Vec3 rest = restingPosition == null ? position() : restingPosition;
        output.putDouble("RestX", rest.x);
        output.putDouble("RestY", rest.y);
        output.putDouble("RestZ", rest.z);
        output.putBoolean("MiddleShieldUsed", middleShieldUsed);
        output.putBoolean("FinalShieldUsed", finalShieldUsed);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        int phaseIndex = Math.clamp(input.getIntOr("BrazierPhase", Phase.DORMANT.ordinal()),
                0, Phase.values().length - 1);
        Phase restored = Phase.values()[phaseIndex];
        entityData.set(PHASE_ID, restored.ordinal());
        entityData.set(PHASE_STARTED_AT, tickCount);
        phaseTicks = Math.max(0, input.getIntOr("BrazierPhaseTicks", 0));
        restingPosition = new Vec3(
                input.getDoubleOr("RestX", getX()),
                input.getDoubleOr("RestY", getY()),
                input.getDoubleOr("RestZ", getZ()));
        middleShieldUsed = input.getBooleanOr("MiddleShieldUsed", false);
        finalShieldUsed = input.getBooleanOr("FinalShieldUsed", false);
        setInvulnerable(restored != Phase.ACTIVE);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<CursedBrazierEntity>("attack", 2, state ->
                state.setAndContinue(attack() == Attack.FIRE_BEAM
                        ? SHOOT_BEAM_ANIMATION
                        : IDLE_ANIMATION)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
