package net.krodark.asterion.mixin;

import net.krodark.asterion.block.RuneBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The eight outer rune sections have collision and signals, but no saved entity. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class RuneBlockEntityStateMixin {
    @Inject(method = "hasBlockEntity", at = @At("HEAD"), cancellable = true)
    private void asterion$runeOwner(CallbackInfoReturnable<Boolean> result) {
        BlockState state = (BlockState)(Object)this;
        if (state.getBlock() instanceof RuneBlock) result.setReturnValue(RuneBlock.isRoot(state));
    }
}
