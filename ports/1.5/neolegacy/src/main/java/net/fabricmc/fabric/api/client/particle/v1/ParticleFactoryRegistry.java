package net.fabricmc.fabric.api.client.particle.v1;

import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;

public final class ParticleFactoryRegistry {
    private static final ParticleFactoryRegistry INSTANCE = new ParticleFactoryRegistry();
    private final java.util.List<Entry<?>> entries = new java.util.ArrayList<>();

    private ParticleFactoryRegistry() {}

    public static ParticleFactoryRegistry getInstance() {
        return INSTANCE;
    }

    @FunctionalInterface
    public interface PendingParticleFactory<T extends ParticleOptions> {
        ParticleProvider<T> create(SpriteSet sprites);
    }

    public <T extends ParticleOptions> void register(
            ParticleType<T> type, PendingParticleFactory<T> factory) {
        entries.add(new Entry<>(type, factory));
    }

    private record Entry<T extends ParticleOptions>(
            ParticleType<T> type, PendingParticleFactory<T> factory) {}

    public static void registerNative(
            net.minecraftforge.client.event.RegisterParticleProvidersEvent event) {
        INSTANCE.entries.forEach(entry -> registerOne(event, entry));
    }

    private static <T extends ParticleOptions> void registerOne(
            net.minecraftforge.client.event.RegisterParticleProvidersEvent event, Entry<T> entry) {
        event.registerSpriteSet(entry.type(), entry.factory()::create);
    }
}
