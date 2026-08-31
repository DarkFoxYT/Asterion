package net.krodark.asterion.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.game.GasClouds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A stationary room guardian; its short fire volleys stop at walls and never place fire blocks. */
public final class CursedBrazierEntity extends PathfinderMob implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int cooldown = 60, volley;
    private Vec3 aim = Vec3.ZERO;
    public CursedBrazierEntity(EntityType<? extends CursedBrazierEntity> type, Level level) { super(type, level); xpReward = 8; }
    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 24).add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.FOLLOW_RANGE, 18);
    }
    @Override protected void registerGoals() { }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || !isAlive() || isNoAi()) return;
        if (tickCount % 4 == 0) level.sendParticles(Asterion.BRAZIER_FIRE, getX(), getY() + .9, getZ(), 2, .4, .1, .4, .01);
        if (isInWater()) { volley = 0; cooldown = 80; return; }
        if (volley > 0) {
            var mouth = position().add(0, 1.1, 0);
            if (volley % 2 == 0) {
                GasClouds.emit(level, mouth, aim.scale(.7), getUUID());
                GasClouds.ignite(level, mouth, getUUID());
            }
            volley--; return;
        }
        if (--cooldown > 0) return;
        var target = level.getNearestPlayer(this, 18);
        if (target == null || target.isCreative() || target.isSpectator() || !hasLineOfSight(target)
                || net.krodark.asterion.WorldGenerator.isNearSafeRune(level, target.blockPosition())) { cooldown = 20; return; }
        aim = target.getBoundingBox().getCenter().subtract(position().add(0, 1.1, 0)).normalize();
        setYRot((float)Math.toDegrees(Math.atan2(-aim.x, aim.z))); yBodyRot = getYRot(); yHeadRot = getYRot();
        volley = 12; cooldown = 70 + random.nextInt(31);
        playSound(net.minecraft.sounds.SoundEvents.BLAZE_SHOOT, .8F, .7F);
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
