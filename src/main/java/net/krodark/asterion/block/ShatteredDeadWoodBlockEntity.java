package net.krodark.asterion.block;

import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ShatteredDeadWoodBlockEntity extends BlockEntity implements GeoBlockEntity {
    private AnimatableInstanceCache animationCache;

    public ShatteredDeadWoodBlockEntity(BlockPos pos, BlockState state) {
        super(Asterion.SHATTERED_DEAD_WOOD_BLOCK_ENTITY, pos, state);
    }

    public Direction facing() {
        return getBlockState().getValue(ShatteredDeadWoodBlock.FACING);
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() {
        if (animationCache == null) animationCache = GeckoLibUtil.createInstanceCache(this);
        return animationCache;
    }
}
