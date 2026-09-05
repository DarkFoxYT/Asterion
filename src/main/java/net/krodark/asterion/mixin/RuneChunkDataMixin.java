package net.krodark.asterion.mixin;

import net.krodark.asterion.block.RuneBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class RuneChunkDataMixin {
    @Inject(method = "promotePendingBlockEntity", at = @At("HEAD"), cancellable = true)
    private void asterion$discardOuterRuneData(BlockPos pos, CompoundTag tag, CallbackInfoReturnable<BlockEntity> result) {
        var state = ((LevelChunk)(Object)this).getBlockState(pos);
        // Old chunks may contain DUMMY entries or copied rune data for these non-owning sections.
        // The caller removes the pending entry; keep the actual rune anchor untouched.
        if (state.getBlock() instanceof RuneBlock && !RuneBlock.isRoot(state)) result.setReturnValue(null);
    }
}
