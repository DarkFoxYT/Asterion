package net.krodark.asterion.block;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class GreekFireTorchBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    public GreekFireTorchBlockEntity(BlockPos pos,BlockState state) {
        super(Asterion.GREEK_FIRE_TORCH_BLOCK_ENTITY,pos,state);
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
