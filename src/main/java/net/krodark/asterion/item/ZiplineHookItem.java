package net.krodark.asterion.item;

import net.krodark.asterion.zipline.ZiplineSystem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public final class ZiplineHookItem extends Item {
    public ZiplineHookItem(Properties properties) { super(properties); }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) ZiplineSystem.toggle(player);
        return InteractionResult.SUCCESS;
    }
}
