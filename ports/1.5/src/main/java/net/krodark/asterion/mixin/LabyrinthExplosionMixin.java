package net.krodark.asterion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.krodark.asterion.worldgen.WorldGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.function.BiConsumer;

@Mixin(Explosion.class)
public abstract class LabyrinthExplosionMixin {
    //? if >=1.20.5 {
    @WrapOperation(method = "finalizeExplosion", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/state/BlockState;onExplosionHit(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Explosion;Ljava/util/function/BiConsumer;)V"))
    private void asterion$renewTerrain(BlockState state, Level level, BlockPos pos, Explosion explosion,
                                      BiConsumer<ItemStack, BlockPos> drops, Operation<Void> original) {
        boolean restore = level instanceof ServerLevel server && WorldGenerator.restoresExplosionBlock(server, pos, state);
        original.call(state, level, pos, explosion, restore ? (BiConsumer<ItemStack, BlockPos>) (stack, at) -> {} : drops);
        if (restore && level.getBlockState(pos) != state) WorldGenerator.trackExplosionBreak((ServerLevel) level, pos, state);
    }
    //?} else {
    /*@WrapOperation(method = "finalizeExplosion", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/state/BlockState;spawnAfterBreak(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;Z)V"))
    private void asterion$noRenewableExperience(BlockState state, ServerLevel level, BlockPos pos, ItemStack tool,
            boolean experience, Operation<Void> original) {
        original.call(state, level, pos, tool, experience && !WorldGenerator.restoresExplosionBlock(level, pos, state));
    }

    @WrapOperation(method = "finalizeExplosion", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/state/BlockState;getDrops(Lnet/minecraft/world/level/storage/loot/LootParams$Builder;)Ljava/util/List;"))
    private java.util.List<ItemStack> asterion$noRenewableDrops(BlockState state,
            net.minecraft.world.level.storage.loot.LootParams.Builder loot, Operation<java.util.List<ItemStack>> original) {
        var origin = loot.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN);
        if (origin != null && WorldGenerator.restoresExplosionBlock(loot.getLevel(), BlockPos.containing(origin), state))
            return java.util.List.of();
        return original.call(state, loot);
    }

    @WrapOperation(method = "finalizeExplosion", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"), require = 0)
    private boolean asterion$renewTerrain(Level level, BlockPos pos, BlockState replacement, int flags, Operation<Boolean> original) {
        BlockState previous = level.getBlockState(pos);
        boolean changed = original.call(level, pos, replacement, flags);
        if (changed && level instanceof ServerLevel server) WorldGenerator.trackExplosionBreak(server, pos, previous);
        return changed;
    }

    // Forge replaces the vanilla setBlock call with its own block callback.
    @WrapOperation(method = "finalizeExplosion", at = @At(value = "INVOKE", remap = false, target =
            "Lnet/minecraft/world/level/block/state/BlockState;onBlockExploded(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Explosion;)V"), require = 0)
    private void asterion$renewForgeTerrain(BlockState state, Level level, BlockPos pos, Explosion explosion, Operation<Void> original) {
        original.call(state, level, pos, explosion);
        if (level instanceof ServerLevel server && level.getBlockState(pos) != state)
            WorldGenerator.trackExplosionBreak(server, pos, state);
    }*/
    //?}
}
