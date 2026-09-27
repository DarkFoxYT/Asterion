package net.fabricmc.fabric.api.networking.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.concurrent.CopyOnWriteArrayList;

public final class EntityTrackingEvents {
    private static final CopyOnWriteArrayList<StartTracking> STARTS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<StopTracking> STOPS = new CopyOnWriteArrayList<>();
    public static final Event<StartTracking> START_TRACKING = STARTS::add;
    public static final Event<StopTracking> STOP_TRACKING = STOPS::add;

    private EntityTrackingEvents() {}

    public static void fireStart(Entity entity, ServerPlayer player) {
        STARTS.forEach(listener -> listener.onStartTracking(entity, player));
    }

    public static void fireStop(Entity entity, ServerPlayer player) {
        STOPS.forEach(listener -> listener.onStopTracking(entity, player));
    }

    @FunctionalInterface public interface StartTracking { void onStartTracking(Entity entity, ServerPlayer player); }
    @FunctionalInterface public interface StopTracking { void onStopTracking(Entity entity, ServerPlayer player); }
}
