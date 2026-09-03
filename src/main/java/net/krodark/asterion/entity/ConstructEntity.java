package net.krodark.asterion.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.game.GasClouds;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** A rare Greek-fire ambusher with a readable armored/attack/recovery cycle. */
public final class ConstructEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Boolean> ATTACKING = SynchedEntityData.defineId(
            ConstructEntity.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlayAndHold("attack");
    private static final double NOTICE_RANGE = 34.0D;
    private static final double IGNITE_RANGE = 3.5D;
    /** Authored animation timing: 24 frames per second, rendered at 20 game ticks per second. */
    public static final int ATTACK_HIT_TICK = 42; // frame 50
    public static final int ATTACK_ANIMATION_TICKS = 155; // 7.75 seconds
    public static final int RECOVERY_TICKS = 100; // five seconds armored and lowered
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int attackTicks;
    private int recoveryTicks;

    public ConstructEntity(EntityType<? extends ConstructEntity> type, Level level) {
        super(type, level);
        xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, NOTICE_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.35D);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACKING, false);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    public boolean isAttacking() {
        return entityData.get(ATTACKING);
    }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || !isAlive()) return;

        if (isAttacking()) {
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0.0D, 1.0D, 0.0D));
            Player target = nearestTarget();
            if (target != null) {
                getLookControl().setLookAt(target, 40.0F, 40.0F);
            }
            if (attackTicks % 8 == 0)
                server.sendParticles(Asterion.GREEK_FIRE, getX(), getY() + getBbHeight() * .5D, getZ(),
                        1, .12D, .18D, .12D, .006D);
            attackTicks++;
            if (attackTicks == ATTACK_HIT_TICK && target != null) {
                sprayGreekFire(server, target);
                playSound(SoundEvents.FIRECHARGE_USE, 1.0F, 0.85F);
            }
            if (attackTicks >= ATTACK_ANIMATION_TICKS) {
                entityData.set(ATTACKING, false);
                attackTicks = 0;
                recoveryTicks = RECOVERY_TICKS;
            }
            return;
        }

        Player target = nearestTarget();
        if (recoveryTicks > 0) {
            recoveryTicks--;
            getNavigation().stop();
            if (target != null) getLookControl().setLookAt(target, 20.0F, 20.0F);
            return;
        }
        if (target == null) {
            getNavigation().stop();
            return;
        }
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (distanceToSqr(target) <= IGNITE_RANGE * IGNITE_RANGE) {
            entityData.set(ATTACKING, true);
            attackTicks = 0;
            getNavigation().stop();
        } else {
            if (getNavigation().isDone() || tickCount % 10 == 0)
                getNavigation().moveTo(target, 1.1D);
        }
    }

    private Player nearestTarget() {
        return level().getNearestPlayer(getX(), getY(), getZ(), NOTICE_RANGE,
                net.minecraft.world.entity.EntitySelector.NO_CREATIVE_OR_SPECTATOR);
    }

    private void sprayGreekFire(ServerLevel level, LivingEntity target) {
        Vec3 origin = getEyePosition().add(getLookAngle().scale(.45D));
        Vec3 direction = target.getBoundingBox().getCenter().subtract(origin);
        if (direction.lengthSqr() < 1.0E-5D || !hasLineOfSight(target)) return;
        direction = direction.normalize();
        GasClouds.emitCompactFlamethrower(level, origin, direction.scale(.43D), getUUID());
        GasClouds.ignite(level, origin, getUUID());
        level.sendParticles(Asterion.GREEK_FIRE_SOOT, origin.x, origin.y, origin.z,
                1, .05D, .05D, .05D, .006D);
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // The closed shell is absolute armor. Its long, clearly telegraphed attack
        // is the player's damage window, preventing ordinary spam-hit combat.
        return isAttacking() && super.hurtServer(level, source, amount);
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<ConstructEntity>("movement", 2, state -> {
            if (isAttacking()) return state.setAndContinue(ATTACK);
            if (getDeltaMovement().horizontalDistanceSqr() > 1.0E-4D)
                return state.setAndContinue(WALK);
            return PlayState.STOP;
        }));
    }

    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Attacking", isAttacking());
        output.putInt("AttackTicks", attackTicks);
        output.putInt("RecoveryTicks", recoveryTicks);
    }

    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(ATTACKING, input.getBooleanOr("Attacking", false));
        attackTicks = Math.clamp(input.getIntOr("AttackTicks", 0), 0, ATTACK_ANIMATION_TICKS);
        recoveryTicks = Math.clamp(input.getIntOr("RecoveryTicks", 0), 0, RECOVERY_TICKS);
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
