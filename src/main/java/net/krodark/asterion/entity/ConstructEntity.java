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
import net.krodark.asterion.WorldGenerator;
import net.krodark.asterion.effect.GreekFireBurn;
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

/** A relentless, Greek-fire construct whose authored attack detonates on frame 186. */
public final class ConstructEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Boolean> ATTACKING = SynchedEntityData.defineId(
            ConstructEntity.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlayAndHold("attack");
    private static final double NOTICE_RANGE = 34.0D;
    private static final double IGNITE_RANGE = 3.5D;
    /** 186 authored frames at the animation's 24 FPS = 7.75 seconds = 155 game ticks. */
    public static final int EXPLOSION_TICK = 155;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int attackTicks;
    private int rageTicks;

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
                if (attackTicks % 20 == 8) sprayGreekFire(server, target);
            }
            if ((attackTicks & 3) == 0)
                server.sendParticles(Asterion.GREEK_FIRE, getX(), getY() + getBbHeight() * .5D, getZ(),
                        3, .35D, .45D, .35D, .015D);
            if (++attackTicks >= EXPLOSION_TICK) {
                WorldGenerator.queueConstructExplosionRepair(server, blockPosition(), 7);
                server.explode(this, getX(), getY() + getBbHeight() * 0.45D, getZ(), 3.0F,
                        Level.ExplosionInteraction.MOB);
                for (LivingEntity victim : server.getEntitiesOfClass(LivingEntity.class,
                        getBoundingBox().inflate(5.0D), entity -> entity != this && entity.isAlive()))
                    GreekFireBurn.ignite(victim, 6.0F);
                discard();
            }
            return;
        }

        if (rageTicks > 0) rageTicks--;
        Player target = nearestTarget();
        if (target == null) {
            getNavigation().stop();
            return;
        }
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (distanceToSqr(target) <= IGNITE_RANGE * IGNITE_RANGE) {
            entityData.set(ATTACKING, true);
            attackTicks = 0;
            getNavigation().stop();
            playSound(SoundEvents.CREEPER_PRIMED, 1.0F, 0.8F);
        } else {
            getNavigation().moveTo(target, rageTicks > 0 ? 1.35D : 1.15D);
            if (tickCount % (rageTicks > 0 ? 10 : 16) == 0 && distanceToSqr(target) < 18.0D * 18.0D)
                sprayGreekFire(server, target);
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
        GasClouds.emitFlamethrower(level, origin, direction.scale(.43D), getUUID());
        GasClouds.ignite(level, origin, getUUID());
        level.sendParticles(Asterion.GREEK_FIRE_SOOT, origin.x, origin.y, origin.z,
                4, .15D, .15D, .15D, .02D);
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            rageTicks = Math.max(rageTicks, 120);
            if (source.getEntity() instanceof Player attacker && !isAttacking())
                getNavigation().moveTo(attacker, 1.45D);
        }
        return hurt;
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
        output.putInt("RageTicks", rageTicks);
    }

    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(ATTACKING, input.getBooleanOr("Attacking", false));
        attackTicks = Math.clamp(input.getIntOr("AttackTicks", 0), 0, EXPLOSION_TICK);
        rageTicks = Math.clamp(input.getIntOr("RageTicks", 0), 0, 120);
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
