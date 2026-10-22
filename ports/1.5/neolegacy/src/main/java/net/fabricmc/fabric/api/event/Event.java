package net.fabricmc.fabric.api.event;

import java.util.function.Consumer;

public class Event<T> {
    private final Consumer<T> registrar;

    public Event(Consumer<T> registrar) {
        this.registrar = registrar;
    }

    public void register(T listener) {
        registrar.accept(listener);
    }

    public T invoker() {
        throw new UnsupportedOperationException("This native event is dispatched by its loader");
    }
}
