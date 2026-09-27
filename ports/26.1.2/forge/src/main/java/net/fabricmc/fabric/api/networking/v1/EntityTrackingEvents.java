package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Forge tracking adapter for shared ragdoll synchronization. */
public final class EntityTrackingEvents {
    public static final Start START_TRACKING = new Start();
    public static final Stop STOP_TRACKING = new Stop();
    private EntityTrackingEvents() {}

    @FunctionalInterface public interface Callback { void onTracking(Entity entity, ServerPlayer viewer); }

    public static final class Start {
        public void register(Callback callback) {
            PlayerEvent.StartTracking.BUS.addListener(event -> {
                if (event.getEntity() instanceof ServerPlayer viewer)
                    callback.onTracking(event.getTarget(), viewer);
            });
        }
    }
    public static final class Stop {
        public void register(Callback callback) {
            PlayerEvent.StopTracking.BUS.addListener(event -> {
                if (event.getEntity() instanceof ServerPlayer viewer)
                    callback.onTracking(event.getTarget(), viewer);
            });
        }
    }
}
