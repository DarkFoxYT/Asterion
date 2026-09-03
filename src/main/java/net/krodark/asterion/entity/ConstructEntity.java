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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.game.GasClouds;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

/** A rare Greek-fire ambusher with a readable armored/attack/recovery cycle. */
public final class ConstructEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Boolean> ATTACKING = SynchedEntityData.defineId(
            ConstructEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RUNNING = SynchedEntityData.defineId(
            ConstructEntity.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlayAndHold("attack");
    private static final double NOTICE_RANGE = 34.0D;
    private static final double IGNITE_RANGE = 3.5D;
    /** Authored animation timing: 24 frames per second, rendered at 20 game ticks per second. */
    public static final int ATTACK_HIT_TICK = 25; // authored frame 30
    public static final int ATTACK_ANIMATION_TICKS = 155; // 7.75 seconds
    public static final int RECOVERY_TICKS = 100; // five seconds armored and lowered
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int attackTicks;
    private int recoveryTicks;
    private int blockedHitCooldown;

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

    public static boolean isAllowedAsterionLocation(BlockPos pos) {
        return net.krodark.asterion.worldgen.CatacombLayout.contains(pos)
                && !net.krodark.asterion.worldgen.AuthoredCatacombs.insideCursedBrazierRoom(pos)
                && !(Math.abs((long)pos.getX()) <= net.krodark.asterion.worldgen.AuthoredCatacombs.ARENA_RADIUS
                && Math.abs((long)pos.getZ()) <= net.krodark.asterion.worldgen.AuthoredCatacombs.ARENA_RADIUS);
    }

    @Override
    public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason reason) {
        if (reason == EntitySpawnReason.NATURAL
                && (!(level instanceof ServerLevel server)
                || !server.dimension().equals(Asterion.ASTERION_LEVEL)
                || !isAllowedAsterionLocation(blockPosition()))) return false;
        return super.checkSpawnRules(level, reason);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACKING, false);
        builder.define(RUNNING, false);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    public boolean isAttacking() {
        return entityData.get(ATTACKING);
    }

    public boolean isRunning() {
        return entityData.get(RUNNING);
    }

    public boolean isVulnerable() {
        return isAttacking() && attackTicks >= ATTACK_HIT_TICK;
    }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || !isAlive()) return;
        if (blockedHitCooldown > 0) blockedHitCooldown--;

        if (isAttacking()) {
            entityData.set(RUNNING, false);
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0.0D, 1.0D, 0.0D));
            Player target = nearestTarget();
            updateConstrainedFacing(target);
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
        updateConstrainedFacing(target);
        if (recoveryTicks > 0) {
            entityData.set(RUNNING, false);
            recoveryTicks--;
            getNavigation().stop();
            return;
        }
        if (target == null) {
            entityData.set(RUNNING, false);
            getNavigation().stop();
            return;
        }
        if (distanceToSqr(target) <= IGNITE_RANGE * IGNITE_RANGE) {
            entityData.set(RUNNING, false);
            entityData.set(ATTACKING, true);
            attackTicks = 0;
            getNavigation().stop();
        } else {
            boolean running = distanceToSqr(target) > 10.0D * 10.0D;
            entityData.set(RUNNING, running);
            if (getNavigation().isDone() || tickCount % 10 == 0)
                getNavigation().moveTo(target, running ? 1.5D : 0.85D);
        }
    }

    private Player nearestTarget() {
        return level().getNearestPlayer(getX(), getY(), getZ(), NOTICE_RANGE,
                net.minecraft.world.entity.EntitySelector.NO_CREATIVE_OR_SPECTATOR);
    }

    /** Movement turns the chassis; looking only bends the constrained body/head chain. */
    private void updateConstrainedFacing(Player target) {
        Vec3 motion = getDeltaMovement();
        float bodyYaw = yBodyRot;
        if (motion.horizontalDistanceSqr() > 2.5E-4D) {
            float travelYaw = (float)(Mth.atan2(motion.z, motion.x) * Mth.RAD_TO_DEG) - 90.0F;
            bodyYaw = Mth.rotLerp(0.12F, bodyYaw, travelYaw);
        }
        setYBodyRot(bodyYaw);
        setYRot(bodyYaw);

        float desiredHeadYaw = bodyYaw;
        float desiredPitch = 0.0F;
        if (target != null) {
            Vec3 delta = target.getEyePosition().subtract(getEyePosition());
            float directYaw = (float)(Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
            desiredHeadYaw = bodyYaw + Mth.clamp(Mth.wrapDegrees(directYaw - bodyYaw), -58.0F, 58.0F);
            desiredPitch = Mth.clamp((float)-(Mth.atan2(delta.y,
                    Math.sqrt(delta.x * delta.x + delta.z * delta.z)) * Mth.RAD_TO_DEG), -28.0F, 34.0F);
        }
        setYHeadRot(Mth.rotLerp(0.16F, getYHeadRot(), desiredHeadYaw));
        setXRot(Mth.lerp(0.14F, getXRot(), desiredPitch));
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
        if (source.is(DamageTypeTags.IS_FIRE)) return false;
        // The shell only opens at authored frame 30. Before that point and throughout
        // recovery, blocked-hit feedback teaches the timing without a UI prompt.
        if (!isVulnerable()) {
            if (blockedHitCooldown == 0) {
                blockedHitCooldown = 8;
                playSound(SoundEvents.SHIELD_BLOCK.value(), 0.65F, 0.72F);
                level.sendParticles(ParticleTypes.WAX_OFF,
                        getX(), getY() + getBbHeight() * 0.55D, getZ(),
                        5, 0.28D, 0.38D, 0.28D, 0.045D);
            }
            return false;
        }
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            Vec3 hit = getBoundingBox().getCenter();
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                            Blocks.CUT_COPPER.defaultBlockState()),
                    hit.x, hit.y, hit.z, 5, 0.22D, 0.32D, 0.22D, 0.08D);
            level.sendParticles(ParticleTypes.WAX_OFF,
                    hit.x, hit.y, hit.z, 2, 0.16D, 0.22D, 0.16D, 0.035D);
        }
        return hurt;
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<ConstructEntity>("movement", 2, state -> {
            if (isAttacking()) return state.setAndContinue(ATTACK);
            if (getDeltaMovement().horizontalDistanceSqr() > 1.0E-4D) {
                state.setControllerSpeed(isRunning() ? 1.65F : 0.9F);
                return state.setAndContinue(WALK);
            }
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
