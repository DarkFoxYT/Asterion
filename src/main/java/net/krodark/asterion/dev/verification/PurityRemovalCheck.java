package net.krodark.asterion.dev.verification;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.forging.LegacyPurityCleanup;
import net.krodark.asterion.recipe.ForgedSwordRecipe;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.CraftingInput;
import java.util.List;

final class PurityRemovalCheck {
    static void run() {
        var recipe = new ForgedSwordRecipe();
        ItemStack low = recipe.assemble(parts(1)), high = recipe.assemble(parts(100));
        check(ItemStack.isSameItemSameComponents(low, high), "Retired purity still changes sword stats");
        check(!low.get(DataComponents.CUSTOM_DATA).copyTag().contains("purity"), "New sword stores purity");
        ItemStack old = parts(30).items().getFirst();
        old.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Saved inscription"),
                Component.literal("Purity 30%  Weight 8"))));
        LegacyPurityCleanup.clean(old);
        check(!old.get(DataComponents.CUSTOM_DATA).copyTag().contains("purity"), "Old purity was not removed");
        check(old.get(DataComponents.CUSTOM_DATA).copyTag().getStringOr("metal_sequence", "").equals("4"), "Cleanup lost alloy data");
        check(old.get(DataComponents.LORE).lines().getFirst().getString().equals("Saved inscription"), "Cleanup lost unrelated lore");
        check(old.get(DataComponents.LORE).lines().getLast().getString().equals("Weight 8"), "Cleanup lost weight");
        Asterion.LOGGER.info("PASS: purity-independent sword stats and legacy item cleanup preserving alloy data");
    }
    private static CraftingInput parts(int purity) {
        var result = new java.util.ArrayList<ItemStack>();
        for (var item : List.of(Asterion.FORGED_SWORD_BLADE, Asterion.FORGED_SWORD_GUARD, Asterion.FORGED_SWORD_POMMEL)) {
            ItemStack stack = new ItemStack(item);
            var data = new CompoundTag(); data.putInt("purity", purity); data.putString("metal_sequence", "4");
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data)); result.add(stack);
        }
        return CraftingInput.of(3, 1, result);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
