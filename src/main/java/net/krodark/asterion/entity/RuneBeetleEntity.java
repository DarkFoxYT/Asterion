package net.krodark.asterion.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.level.Level;

/** Passive rune wildlife, using the small beetle model until its own GeckoLib model is supplied. */
public final class RuneBeetleEntity extends PathfinderMob implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int runeIndex;
    public void setRuneIndex(int index) { runeIndex = Math.clamp(index, 0, 23); }
    public int runeIndex() { return runeIndex; }

    public RuneBeetleEntity(EntityType<? extends RuneBeetleEntity> type, Level level) { super(type, level); }

    @Override public boolean canBreatheUnderwater() { return true; }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 6)
                .add(Attributes.MOVEMENT_SPEED, .16).add(Attributes.FOLLOW_RANGE, 8);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 1));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    @Override public boolean removeWhenFarAway(double distanceSquared) { return true; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new com.geckolib.animation.AnimationController<RuneBeetleEntity>("movement", 4,
                state -> state.setAndContinue(com.geckolib.animation.RawAnimation.begin()
                        .thenLoop(getDeltaMovement().horizontalDistanceSqr() > .0001 ? "walk" : "idle"))));
    }
    @Override protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out) {
        super.addAdditionalSaveData(out); out.putInt("RuneIndex", runeIndex);
    }
    @Override protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
        super.readAdditionalSaveData(in); setRuneIndex(in.getIntOr("RuneIndex", 0));
    }
    @Override protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.damagesource.DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        spawnAtLocation(level, new net.minecraft.world.item.ItemStack(net.krodark.asterion.Asterion.RUNE_TABLETS[runeIndex]));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
