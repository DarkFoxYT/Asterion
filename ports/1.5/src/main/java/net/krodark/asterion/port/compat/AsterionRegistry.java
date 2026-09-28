package net.krodark.asterion.port.compat;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Keeps shared content construction separate from Forge's registration event. */
public final class AsterionRegistry {
    private static final List<Pending<?>> PENDING = new ArrayList<>();
    private static final List<Runnable> AFTER_REGISTRATION = new ArrayList<>();
    private static boolean deferred;

    private AsterionRegistry() {}

    public static void defer() {
        deferred = true;
    }

    public static boolean isDeferred() {
        return deferred;
    }

    public static void whenRegistered(Runnable action) {
        if (deferred) AFTER_REGISTRATION.add(action);
        else action.run();
    }

    public static void runAfterRegistration() {
        AFTER_REGISTRATION.forEach(Runnable::run);
        AFTER_REGISTRATION.clear();
    }

    public static <V, T extends V> T register(Registry<V> registry, ResourceLocation id, T value) {
        if (deferred) {
            PENDING.add(new Pending<>(registry.key(), id, value));
            return value;
        }
        return Registry.register(registry, id, value);
    }

    public static <V, T extends V> T register(Registry<V> registry, ResourceKey<V> key, T value) {
        return register(registry, key.location(), value);
    }

    public static <V, T extends V> T register(Registry<V> registry, String id, T value) {
        return register(registry, ResourceLocation.parse(id), value);
    }

    public static List<Pending<?>> pending() {
        return List.copyOf(PENDING);
    }

    public record Pending<T>(ResourceKey<? extends Registry<T>> registryKey,
                             ResourceLocation id, T value) {}
}
