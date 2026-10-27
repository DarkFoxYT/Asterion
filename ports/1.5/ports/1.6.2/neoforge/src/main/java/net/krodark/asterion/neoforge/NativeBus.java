package net.krodark.asterion.neoforge;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Routes shared callbacks to their native mod or game event bus. */
public final class NativeBus {
    private static IEventBus modBus;
    private static final java.util.List<Consumer<IEventBus>> PENDING = new java.util.ArrayList<>();

    private NativeBus() {}

    public static void initialize(IEventBus bus) {
        modBus = bus;
        PENDING.forEach(task -> task.accept(bus));
        PENDING.clear();
    }

    private static void install(Class<?> type, Consumer<IEventBus> task) {
        if (net.neoforged.fml.event.IModBusEvent.class.isAssignableFrom(type)) {
            if (modBus == null) PENDING.add(task);
            else task.accept(modBus);
        } else task.accept(net.neoforged.neoforge.common.NeoForge.EVENT_BUS);
    }

    public static <E extends Event> Registration<E> event(Class<E> type) {
        return new Registration<>(type);
    }

    public record Registration<E extends Event>(Class<E> type) {
        public void addListener(Consumer<E> callback) {
            install(type, bus -> bus.addListener(EventPriority.NORMAL, false, type, callback));
        }

        public void addListener(BiConsumer<E, Boolean> callback) {
            install(
                    type,
                    bus ->
                            bus.addListener(
                                    EventPriority.LOWEST,
                                    true,
                                    type,
                                    event ->
                                            callback.accept(
                                                    event,
                                                    event
                                                                    instanceof
                                                                    net.neoforged.bus.api
                                                                                    .ICancellableEvent
                                                                            cancellable
                                                            && cancellable.isCanceled())));
        }
    }
}
