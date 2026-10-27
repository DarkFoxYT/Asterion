package net.fabricmc.fabric.api.client.item.v1;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

@FunctionalInterface
public interface ItemTooltipCallback {
    Event EVENT = new Event();

    void getTooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines);

    final class Event {
        public void register(ItemTooltipCallback callback) {
            ItemTooltipEvent.BUS.addListener(event -> callback.getTooltip(
                    event.getItemStack(), event.getContext(), event.getFlags(), event.getToolTip()));
        }
    }
}
