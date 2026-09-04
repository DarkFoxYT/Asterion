package net.krodark.asterion.item;

import net.krodark.asterion.zipline.ZiplineSystem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.UseOnContext;

public final class ZiplineHookItem extends Item {
    public ZiplineHookItem(Properties properties) { super(properties); }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        if (!level.isClientSide()) ZiplineSystem.begin(player);
        // Consume without either a client or server swing: holding the hook should not
        // repeatedly punch/swing the player's hand while they are attached.
        return InteractionResult.CONSUME;
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (!ZiplineSystem.isChain(context.getLevel().getBlockState(context.getClickedPos())))
            return InteractionResult.PASS;
        if (context.getPlayer() != null) {
            context.getPlayer().startUsingItem(context.getHand());
            if (!context.getLevel().isClientSide())
                ZiplineSystem.begin(context.getPlayer(), context.getClickedPos());
        }
        return InteractionResult.CONSUME;
    }
    @Override public int getUseDuration(ItemStack stack, LivingEntity user) { return 72_000; }
    @Override public ItemUseAnimation getUseAnimation(ItemStack stack) { return ItemUseAnimation.NONE; }
    @Override public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (!level.isClientSide() && entity instanceof Player player) ZiplineSystem.stop(player);
        return false;
    }
}
