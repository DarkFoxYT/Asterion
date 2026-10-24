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

@Mixin(net.minecraft.world.level.ServerExplosion.class)
public abstract class LabyrinthExplosionMixin {
    @WrapOperation(method = "interactWithBlocks", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/state/BlockState;onExplosionHit(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Explosion;Ljava/util/function/BiConsumer;)V"))
    private void asterion$renewTerrain(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion,
                                      BiConsumer<ItemStack, BlockPos> drops, Operation<Void> original) {
        boolean restore = WorldGenerator.restoresExplosionBlock(level, pos, state);
        original.call(state, level, pos, explosion, restore ? (BiConsumer<ItemStack, BlockPos>) (stack, at) -> {} : drops);
        if (restore && level.getBlockState(pos) != state) WorldGenerator.trackExplosionBreak(level, pos, state);
    }
}
