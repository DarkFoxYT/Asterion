package net.krodark.asterion.block;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class LabyrinthVineBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public LabyrinthVineBlockEntity(BlockPos pos, BlockState state) {
        super(Asterion.LABYRINTH_VINE_BLOCK_ENTITY, pos, state);
    }

    public boolean isEnd() { return getBlockState().getValue(LabyrinthVineBlock.END); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animationCache; }
}
