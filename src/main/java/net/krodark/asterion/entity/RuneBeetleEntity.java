package net.krodark.asterion.entity;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.level.Level;


public final class RuneBeetleEntity extends PathfinderMob implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int runeIndex;
    public void setRuneIndex(int index) { runeIndex = net.krodark.asterion.port.compat.MathCompat.clamp(index, 0, 23); }
    public int runeIndex() { return runeIndex; }

    public RuneBeetleEntity(EntityType<? extends RuneBeetleEntity> type, Level level) { super(type, level); }


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
        controllers.add(new software.bernie.geckolib.animation.AnimationController<RuneBeetleEntity>(this, "movement", 4,
                state -> state.setAndContinue(software.bernie.geckolib.animation.RawAnimation.begin()
                        .thenLoop(getDeltaMovement().horizontalDistanceSqr() > .0001 ? "walk" : "idle"))));
    }
    @Override public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag out) {
        super.addAdditionalSaveData(out); out.putInt("RuneIndex", runeIndex);
    }
    @Override public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag in) {
        super.readAdditionalSaveData(in); setRuneIndex(net.krodark.asterion.port.compat.NbtCompat.getInt(in, "RuneIndex", 0));
    }
    @Override
//? if >=1.20.5 {
protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.damagesource.DamageSource source, boolean killedByPlayer) {
//?} else {
/*protected void dropCustomDeathLoot(net.minecraft.world.damagesource.DamageSource source, int looting, boolean killedByPlayer) {
 var level = (net.minecraft.server.level.ServerLevel)level();*/
//?}


//? if >=1.20.5 {
super.dropCustomDeathLoot(level, source, killedByPlayer);
//?} else {
/*super.dropCustomDeathLoot(source, looting, killedByPlayer);*/
//?}

        spawnAtLocation(new net.minecraft.world.item.ItemStack(net.krodark.asterion.Asterion.RUNE_TABLETS[runeIndex]));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
//? if <1.20.5 {
/*@Override protected float getStandingEyeHeight(net.minecraft.world.entity.Pose pose,net.minecraft.world.entity.EntityDimensions dimensions){return .15F;}*/
//?}
}
