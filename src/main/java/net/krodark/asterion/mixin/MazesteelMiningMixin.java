package net.krodark.asterion.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.class)
public abstract class MazesteelMiningMixin {
    @org.spongepowered.asm.mixin.Unique
    private static final TagKey<Block> ASTERION_MAZESTEEL = TagKey.create(Registries.BLOCK,
            net.minecraft.resources.Identifier.fromNamespaceAndPath("asterion", "mazesteel"));
    @Inject(method = "getDestroyProgress", at = @At("HEAD"), cancellable = true)
    private void asterion$fixedMiningTime(BlockState state, Player player, BlockGetter level, BlockPos pos,
                                         CallbackInfoReturnable<Float> cir) {
        if (state.is(ASTERION_MAZESTEEL)) cir.setReturnValue(1F / 1600F);
    }
}
