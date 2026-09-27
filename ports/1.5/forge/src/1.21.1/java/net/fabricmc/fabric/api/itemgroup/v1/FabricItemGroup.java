package net.fabricmc.fabric.api.itemgroup.v1;

import net.minecraft.world.item.CreativeModeTab;

/** Forge-backed entry point matching the shared Fabric item group builder call. */
public final class FabricItemGroup {
    private FabricItemGroup() {}

    public static CreativeModeTab.Builder builder() {
        return CreativeModeTab.builder();
    }
}
