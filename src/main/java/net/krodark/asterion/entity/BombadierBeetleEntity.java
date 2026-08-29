package net.krodark.asterion.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.krodark.asterion.Asterion;

/** A timid maze beetle whose defensive smoke trail becomes its weapon. */
public final class BombadierBeetleEntity extends PathfinderMob implements GeoEntity {
    private static final int FLEE_TICKS = 100;
    private static final int IGNITION_TICKS = 24;
    private static final int DEFENCE_COOLDOWN_TICKS = 200;
    private static final double THREAT_DISTANCE = 4.5D;
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK_ANIMATION = RawAnimation.begin().thenLoop("walk");
    private static final EntityDataAccessor<Integer> DATA_DEFENCE_STATE = SynchedEntityData.defineId(
            BombadierBeetleEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final List<Vec3> smokeTrail = new ArrayList<>();
    private final Set<UUID> ignitedVictims = new HashSet<>();
    private int defenceTicks;
    private int defenceCooldown;
    private int nextPanicTurn;

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
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DEFENCE_STATE, DefenceState.CALM.ordinal());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel) || !isAlive()) return;

        if (defenceCooldown > 0) defenceCooldown--;
        if (defenceState() == DefenceState.CALM) {
            if (defenceCooldown == 0 && tickCount % 10 == 0 && nearbyThreat() != null)
                beginDefence();
            return;
        }

        defenceTicks++;
        if (defenceState() == DefenceState.FLEEING) tickFleeing(serverLevel);
        else tickIgnition(serverLevel);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive() && defenceState() == DefenceState.CALM && defenceCooldown == 0)
            beginDefence();
        return hurt;
    }

    private Player nearbyThreat() {
        return level().getNearestPlayer(getX(), getY(), getZ(), THREAT_DISTANCE,
                entity -> entity instanceof Player player && !player.isCreative()
                        && !player.isSpectator() && player.isAlive());
    }

    private void beginDefence() {
        smokeTrail.clear();
        ignitedVictims.clear();
        defenceTicks = 0;
        nextPanicTurn = 0;
        setDefenceState(DefenceState.FLEEING);
    }

    private void tickFleeing(ServerLevel level) {
        if (nextPanicTurn-- <= 0 || navigation.isDone()) {
            Vec3 destination = DefaultRandomPos.getPos(this, 11, 4);
            if (destination != null) navigation.moveTo(destination.x, destination.y, destination.z, 2.15D);
            nextPanicTurn = 5 + random.nextInt(8);
        }

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
        Vec3 rear = rearPosition();
        level.sendParticles(ParticleTypes.FLAME, rear.x, rear.y, rear.z,
                3, 0.16D, 0.10D, 0.16D, 0.015D);

        int start = smokeTrail.size() * (defenceTicks - 1) / IGNITION_TICKS;
        int end = smokeTrail.size() * defenceTicks / IGNITION_TICKS;
        for (int index = Math.max(0, start); index < Math.min(end, smokeTrail.size()); index++)
            igniteSmokeAt(level, smokeTrail.get(index));

        if (defenceTicks >= IGNITION_TICKS) {
            smokeTrail.clear();
            ignitedVictims.clear();
            defenceTicks = 0;
            defenceCooldown = DEFENCE_COOLDOWN_TICKS;
            setDefenceState(DefenceState.CALM);
        }
    }

    private void igniteSmokeAt(ServerLevel level, Vec3 point) {
        level.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z,
                5, 0.22D, 0.18D, 0.22D, 0.025D);
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

    private void setDefenceState(DefenceState state) {
        getEntityData().set(DATA_DEFENCE_STATE, state.ordinal());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<BombadierBeetleEntity>("movement", 3, state -> {
            boolean moving = defenceState() == DefenceState.FLEEING
                    || getDeltaMovement().horizontalDistanceSqr() > 0.0004D;
            state.setControllerSpeed(defenceState() == DefenceState.FLEEING ? 2.15F : 1.0F);
            return state.setAndContinue(moving ? WALK_ANIMATION : IDLE_ANIMATION);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    public enum DefenceState { CALM, FLEEING, IGNITING }
}
