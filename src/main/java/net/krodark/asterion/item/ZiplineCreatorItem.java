package net.krodark.asterion.item;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.ZiplineAnchorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.ChainBlock;

public final class ZiplineCreatorItem extends Item {
    public ZiplineCreatorItem(Properties properties) { super(properties); }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        ItemStack tool = context.getItemInHand();
        CompoundTag tag = tool.getOrDefault(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        BlockPos point = context.getClickedPos().relative(context.getClickedFace());
        if (!context.getLevel().getBlockState(point).canBeReplaced()) return InteractionResult.FAIL;
        if (!tag.contains("zipline_first")) {
            tag.putLong("zipline_first", point.asLong());
            tag.putString("zipline_dimension", context.getLevel().dimension().identifier().toString());
            tool.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
            player.sendOverlayMessage(Component.literal("Zipline start selected"));
            return InteractionResult.SUCCESS_SERVER;
        }
        BlockPos first = BlockPos.of(tag.getLongOr("zipline_first", point.asLong()));
        String firstDimension = tag.getStringOr("zipline_dimension", "");
        tag.remove("zipline_first"); tag.remove("zipline_dimension");
        tool.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        if (!firstDimension.equals(context.getLevel().dimension().identifier().toString())) {
            player.sendOverlayMessage(Component.literal("Zipline endpoints must be in the same dimension"));
            return InteractionResult.FAIL;
        }
        double distance = first.getCenter().distanceTo(point.getCenter());
        if (distance < 2 || distance > 64 || !context.getLevel().getBlockState(first).canBeReplaced()) return InteractionResult.FAIL;
        int cost = Math.max(1, (int)Math.ceil(distance));
        int chainSlot = -1;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (candidate.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ChainBlock
                    && candidate.getCount() >= cost) { chainSlot = slot; break; }
        }
        if (chainSlot < 0) { player.sendOverlayMessage(Component.literal("Need " + cost + " matching chains")); return InteractionResult.FAIL; }
        ItemStack chains = player.getInventory().getItem(chainSlot);
        Identifier chainId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(((BlockItem)chains.getItem()).getBlock());
        chains.shrink(cost);
        context.getLevel().setBlock(first, Asterion.ZIPLINE_ANCHOR.defaultBlockState(), 3);
        context.getLevel().setBlock(point, Asterion.ZIPLINE_ANCHOR.defaultBlockState(), 3);
        if (context.getLevel().getBlockEntity(first) instanceof ZiplineAnchorBlockEntity a) a.link(point, chainId.toString());
        if (context.getLevel().getBlockEntity(point) instanceof ZiplineAnchorBlockEntity b) b.link(first, chainId.toString());
        player.sendOverlayMessage(Component.literal("Zipline linked with " + cost + " chains"));
        return InteractionResult.SUCCESS_SERVER;
    }
}
