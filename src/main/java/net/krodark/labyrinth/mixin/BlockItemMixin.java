package net.krodark.labyrinth.mixin;

import net.krodark.labyrinth.WorldGenerator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "placeBlock", at = @At("RETURN"))
    private void labyrinth$trackTemporaryBlock(BlockPlaceContext context, BlockState state,
                                                CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValue() && context.getLevel() instanceof ServerLevel level)
            WorldGenerator.trackPlayerPlacement(level, context.getClickedPos(), state);
    }
}
