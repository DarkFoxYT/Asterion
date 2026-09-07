package net.krodark.asterion.mixin;

import net.krodark.asterion.block.RuneBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

 
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class RuneBlockEntityStateMixin {
    @ModifyReturnValue(method = "hasBlockEntity", at = @At("RETURN"))
    private boolean asterion$runeOwner(boolean original) {
        if (!original) return false;
        BlockState state = (BlockState)(Object)this;
        if (state.getBlock() instanceof RuneBlock) return RuneBlock.isRoot(state);
        if (state.getBlock() instanceof net.krodark.asterion.block.CrucibleBlock)
            return net.krodark.asterion.block.CrucibleBlock.isRoot(state);
        return true;
    }
}
