package net.krodark.asterion.block;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.krodark.asterion.game.AncientContent;

public final class MinotaurTrophyBlockEntity extends BlockEntity implements GeoBlockEntity {
    private AnimatableInstanceCache cache;
    public MinotaurTrophyBlockEntity(BlockPos pos, BlockState state) { super(AncientContent.TROPHY_BLOCK_ENTITY, pos, state); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() {
        if (cache == null) cache = GeckoLibUtil.createInstanceCache(this);
        return cache;
    }
}
