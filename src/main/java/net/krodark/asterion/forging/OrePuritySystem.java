package net.krodark.asterion.forging;

import net.krodark.asterion.Asterion;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.core.registries.BuiltInRegistries;

/** Gives ore, raw metal and ingot stacks a stable, server-authored purity value and tooltip. */
public final class OrePuritySystem {
    private OrePuritySystem() { }

    public static void tick(MinecraftServer server) {
        if ((server.getTickCount() & 15) != 0) return;
        for (var player : server.getPlayerList().getPlayers()) {
            for (ItemStack stack : player.getInventory()) {
                if (stack.isEmpty() || !isMetalResource(stack)) continue;
                CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
                CompoundTag tag = existing == null ? new CompoundTag() : existing.copyTag();
                int purity = tag.getIntOr("purity", 0);
                if (purity <= 0) {
                    purity = player.isCreative() ? 100 : rollPurity(stack, player.getRandom());
                    tag.putInt("purity", purity);
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                }
                ItemLore lore = stack.get(DataComponents.LORE);
                boolean alreadyShown = lore != null && lore.lines().stream()
                        .anyMatch(line -> line.getString().startsWith("Purity "));
                if (!alreadyShown) {
                    Component line = Component.literal("Purity " + purity + "%")
                            .withStyle(purity > 90 ? ChatFormatting.AQUA
                                    : purity < 50 ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY);
                    stack.set(DataComponents.LORE, lore == null ? new ItemLore(java.util.List.of(line))
                            : lore.withLineAdded(line));
                }
            }
        }
    }

    private static boolean isMetalResource(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return path.endsWith("_ore") || path.startsWith("raw_") || path.endsWith("_ingot");
    }

    private static int rollPurity(ItemStack stack, net.minecraft.util.RandomSource random) {
        if (stack.is(Asterion.TARNISHED_GOLD_INGOT)) return 20 + random.nextInt(30);
        if (stack.is(Asterion.CELESTIAL_GOLD_INGOT)) return 91 + random.nextInt(10);
        // Ordinary gold can naturally grade into tarnished, standard, or celestial material
        // when it reaches the crucible; its stable purity decides the result.
        if (stack.is(net.minecraft.world.item.Items.GOLD_INGOT)
                || stack.is(net.minecraft.world.item.Items.RAW_GOLD)
                || stack.is(net.minecraft.world.item.Items.GOLD_ORE)
                || stack.is(net.minecraft.world.item.Items.DEEPSLATE_GOLD_ORE))
            return 25 + random.nextInt(76);
        return 55 + random.nextInt(46);
    }
}
