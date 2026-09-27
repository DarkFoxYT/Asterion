package net.fabricmc.fabric.api.itemgroup.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ItemGroupEvents {
    private static final Map<ResourceKey<CreativeModeTab>, List<ModifyEntries>> LISTENERS = new HashMap<>();

    private ItemGroupEvents() {}

    public static Event<ModifyEntries> modifyEntriesEvent(ResourceKey<CreativeModeTab> key) {
        return listener -> LISTENERS.computeIfAbsent(key, ignored -> new ArrayList<>()).add(listener);
    }

    public static void fire(BuildCreativeModeTabContentsEvent event) {
        List<ModifyEntries> listeners = LISTENERS.get(event.getTabKey());
        if (listeners == null) return;
        FabricItemGroupEntries entries = new FabricItemGroupEntries(event);
        listeners.forEach(listener -> listener.modifyEntries(entries));
    }

    @FunctionalInterface
    public interface ModifyEntries {
        void modifyEntries(FabricItemGroupEntries entries);
    }
}
