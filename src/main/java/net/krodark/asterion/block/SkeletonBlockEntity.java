package net.krodark.asterion.block;

import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class SkeletonBlockEntity extends BlockEntity implements GeoBlockEntity {
    private AnimatableInstanceCache animationCache;

    public SkeletonBlockEntity(BlockPos pos, BlockState state) {
        super(Asterion.SKELETON_BLOCK_ENTITY, pos, state);
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() {
        if (animationCache == null) animationCache = GeckoLibUtil.createInstanceCache(this);
        return animationCache;
    }
}
