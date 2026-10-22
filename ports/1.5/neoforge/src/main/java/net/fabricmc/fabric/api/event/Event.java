package net.fabricmc.fabric.api.event;

/** Binary-compatible registration surface used by the bundled renderer. */
public final class Event<T> {
    private final java.util.function.Consumer<T> registrar;

    public Event(java.util.function.Consumer<T> registrar) {
        this.registrar = registrar;
    }

    public void register(T listener) {
        registrar.accept(java.util.Objects.requireNonNull(listener));
    }
}
