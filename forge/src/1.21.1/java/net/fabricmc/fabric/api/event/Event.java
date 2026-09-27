package net.fabricmc.fabric.api.event;

@FunctionalInterface
public interface Event<T> {
    void register(T listener);
}
