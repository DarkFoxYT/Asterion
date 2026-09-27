package net.krodark.asterion.forge;

import net.krodark.asterion.Asterion;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Mod(Asterion.MOD_ID)
public final class AsterionForge {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    public AsterionForge() {
        EntityAttributeCreationEvent.BUS.addListener(AsterionForge::initializeSharedContent);
    }

    @SuppressWarnings({"rawtypes", "unchecked", "deprecation"})
    private static void initializeSharedContent(EntityAttributeCreationEvent event) {
        if (!INITIALIZED.compareAndSet(false, true)) return;
        List<MappedRegistry> registries = BuiltInRegistries.REGISTRY.stream()
                .filter(MappedRegistry.class::isInstance)
                .map(MappedRegistry.class::cast)
                .toList();
        registries.forEach(AsterionForge::unlockRegistry);
        new Asterion().onInitialize();
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.apply(event);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.finishRegistration();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            new net.krodark.asterion.client.AsterionClient().onInitializeClient();
        }
    }

    private static void unlockRegistry(MappedRegistry<?> registry) {
        registry.unfreeze();
        Class<?> type = registry.getClass();
        while (type != null) {
            try {
                var locked = type.getDeclaredField("locked");
                locked.setAccessible(true);
                locked.setBoolean(registry, false);
                try {
                    var delegateField = registry.getClass().getDeclaredField("delegate");
                    delegateField.setAccessible(true);
                    Object delegate = delegateField.get(registry);
                    var unfreeze = delegate.getClass().getMethod("unfreeze");
                    unfreeze.setAccessible(true);
                    unfreeze.invoke(delegate);
                } catch (NoSuchFieldException ignored) {
                    // Vanilla registry: MappedRegistry.unfreeze() above is sufficient.
                }
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Could not unlock Forge registry " + registry.key(), error);
            }
        }
    }
}
