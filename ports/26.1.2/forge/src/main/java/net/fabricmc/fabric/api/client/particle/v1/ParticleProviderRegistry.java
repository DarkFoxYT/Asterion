package net.fabricmc.fabric.api.client.particle.v1;

import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;

import java.util.ArrayList;
import java.util.List;

/** Registers Asterion sprite particle factories during Forge's particle event. */
public final class ParticleProviderRegistry {
    private static final ParticleProviderRegistry INSTANCE = new ParticleProviderRegistry();
    private final List<Entry<?>> entries = new ArrayList<>();

    static {
        RegisterParticleProvidersEvent.BUS.addListener(INSTANCE::registerAll);
    }

    private ParticleProviderRegistry() {}
    public static ParticleProviderRegistry getInstance() { return INSTANCE; }

    @FunctionalInterface public interface Pending<T extends ParticleOptions> {
        ParticleProvider<T> create(SpriteSet sprites);
    }

    public <T extends ParticleOptions> void register(ParticleType<T> type, Pending<T> factory) {
        entries.add(new Entry<>(type, factory));
    }

    private record Entry<T extends ParticleOptions>(ParticleType<T> type, Pending<T> factory) {}

    private void registerAll(RegisterParticleProvidersEvent event) {
        entries.forEach(entry -> registerOne(event, entry));
    }

    private static <T extends ParticleOptions> void registerOne(RegisterParticleProvidersEvent event, Entry<T> entry) {
        event.registerSpriteSet(entry.type(), sprites -> entry.factory().create(sprites));
    }
}
