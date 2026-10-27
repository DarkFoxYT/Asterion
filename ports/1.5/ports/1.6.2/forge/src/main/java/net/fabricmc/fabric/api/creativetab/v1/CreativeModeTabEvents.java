package net.fabricmc.fabric.api.creativetab.v1;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;

import java.util.function.Consumer;

/** Forge creative tab content adapter for shared registrations. */
public final class CreativeModeTabEvents {
    private CreativeModeTabEvents() {}

    public static ModifyOutput modifyOutputEvent(ResourceKey<CreativeModeTab> tab) {
        return new ModifyOutput(tab);
    }

    public record ModifyOutput(ResourceKey<CreativeModeTab> tab) {
        public void register(Consumer<CreativeModeTab.Output> callback) {
            BuildCreativeModeTabContentsEvent.BUS.addListener(event -> {
                if (event.getTabKey().equals(tab)) callback.accept(event);
            });
        }
    }
}
