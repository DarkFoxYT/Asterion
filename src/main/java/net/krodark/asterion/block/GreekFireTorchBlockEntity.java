package net.krodark.asterion.block;

import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class GreekFireTorchBlockEntity extends net.krodark.asterion.port.compat.VersionedBlockEntity implements GeoBlockEntity {
    private AnimatableInstanceCache cache;
    public GreekFireTorchBlockEntity(BlockPos pos,BlockState state) {
        super(Asterion.GREEK_FIRE_TORCH_BLOCK_ENTITY,pos,state);
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() {
        if (cache == null) cache = GeckoLibUtil.createInstanceCache(this);
        return cache;
    }
}
