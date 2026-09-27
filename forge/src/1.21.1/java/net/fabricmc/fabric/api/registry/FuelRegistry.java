package net.fabricmc.fabric.api.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;

import java.util.IdentityHashMap;
import java.util.Map;

/** Forge-backed fuel registry used by Asterion's shared initializer. */
public final class FuelRegistry {
    public static final FuelRegistry INSTANCE = new FuelRegistry();
    private final Map<Item, Integer> burnTimes = new IdentityHashMap<>();

    private FuelRegistry() {
        MinecraftForge.EVENT_BUS.addListener(this::onFuelBurnTime);
    }

    public void add(ItemLike item, int burnTime) {
        burnTimes.put(item.asItem(), burnTime);
    }

    private void onFuelBurnTime(FurnaceFuelBurnTimeEvent event) {
        Integer burnTime = burnTimes.get(event.getItemStack().getItem());
        if (burnTime != null) event.setBurnTime(burnTime);
    }
}
