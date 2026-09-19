package net.krodark.asterion.mixin;
import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A later structure can seal an old container position with masonry. */
@Mixin(LevelChunk.class)
public abstract class LabyrinthBlockEntityCleanupMixin {
    @Inject(method="getBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk$EntityCreationType;)Lnet/minecraft/world/level/block/entity/BlockEntity;",at=@At("HEAD"))
    private void asterion$discardReplacedTemplateData(BlockPos pos,LevelChunk.EntityCreationType creation,CallbackInfoReturnable<BlockEntity> callback) {
        LevelChunk chunk=(LevelChunk)(Object)this;
        if(chunk.getLevel().dimension().equals(Asterion.ASTERION_LEVEL) && !chunk.getBlockState(pos).hasBlockEntity())
            ((ChunkAccessAccessor)chunk).asterion$pendingBlockEntities().remove(pos);
    }
}
