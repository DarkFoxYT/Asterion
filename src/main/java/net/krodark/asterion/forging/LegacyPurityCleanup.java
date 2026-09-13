package net.krodark.asterion.forging;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;

 
public final class LegacyPurityCleanup {
    private LegacyPurityCleanup() {}
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 100 != 0) return;
        for (var player : server.getPlayerList().getPlayers())
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
                clean(player.getInventory().getItem(slot));
    }
    public static void clean(ItemStack stack) {
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        String name = id.getPath();
        if ((!id.getNamespace().equals("asterion") && !id.getNamespace().equals("minecraft"))
                || !(name.endsWith("_ore") || name.startsWith("raw_") || name.endsWith("_ingot")
                || name.startsWith("forged_"))) return;
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.copyTag().contains("purity")) {
            var tag = data.copyTag();
            tag.remove("purity");
            if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
            else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            // 1.21.1 CustomModelData stores one integer, so there is no separate
            // post-1.21 float channel to clean without destroying the item model.
        }
        var lore = stack.get(DataComponents.LORE);
        if (lore == null || lore.lines().stream().noneMatch(line -> line.getString().startsWith("Purity "))) return;
        var lines = new java.util.ArrayList<net.minecraft.network.chat.Component>();
        for (var line : lore.lines()) {
            String text = line.getString();
            if (!text.startsWith("Purity ")) lines.add(line);
            else if (text.contains("Weight ")) lines.add(net.minecraft.network.chat.Component.literal(
                    text.substring(text.indexOf("Weight "))).setStyle(line.getStyle()));
        }
        if (lines.isEmpty()) stack.remove(DataComponents.LORE);
        else stack.set(DataComponents.LORE, new ItemLore(lines));
    }
}
