package net.fabricmc.fabric.api.registry;

import net.minecraft.world.item.Item;
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Forge fuel event adapter for declarations shared with Fabric. */
public final class FuelValueEvents {
    private static final Map<Item, Integer> FUELS = new ConcurrentHashMap<>();
    public static final Event BUILD = new Event();

    static {
        FurnaceFuelBurnTimeEvent.BUS.addListener(event -> {
            Integer duration = FUELS.get(event.getItemStack().getItem());
            if (duration != null) event.setBurnTime(duration);
        });
    }

    private FuelValueEvents() {}

    @FunctionalInterface
    public interface Callback {
        void build(Builder builder, Context context);
    }

    public record Context(int baseSmeltTime) {}

    public static final class Builder {
        public void add(Item item, int burnTime) {
            FUELS.put(item, burnTime);
        }
    }

    public static final class Event {
        public void register(Callback callback) {
            callback.build(new Builder(), new Context(200));
        }
    }
}
