package net.fabricmc.fabric.api.itemgroup.v1;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public final class FabricItemGroupEntries implements CreativeModeTab.Output {
    private final CreativeModeTab.Output output;

    FabricItemGroupEntries(CreativeModeTab.Output output) {
        this.output = output;
    }

    public void accept(ItemLike item) {
        output.accept(item);
    }

    public void accept(ItemStack stack) {
        output.accept(stack);
    }

    @Override public void accept(ItemStack stack, CreativeModeTab.TabVisibility visibility) {
        output.accept(stack, visibility);
    }
}
