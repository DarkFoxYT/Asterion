package net.krodark.asterion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.krodark.asterion.block.ShaleSpikeBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PointedDripstoneBlock.class)
abstract class ShaleSpikeDripstoneMixin {
    // Vanilla's private helpers otherwise recognize only its singleton block.
    @WrapOperation(method = {"isTip", "isPointedDripstoneWithDirection", "isStalactiteStartPos",
            "lambda$findTip$0", "lambda$findRootBlock$0", "lambda$findRootBlock$1"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z"))
    private static boolean asterion$recognizeSpike(BlockState state, Object expected, Operation<Boolean> original) {
        return expected == Blocks.POINTED_DRIPSTONE && state.getBlock() instanceof ShaleSpikeBlock
                || original.call(state, expected);
    }
}
