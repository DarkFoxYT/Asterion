package net.krodark.asterion;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

import java.util.function.BiConsumer;

/** Routes shared registrations through the active loader's attribute event. */
public final class EntityAttributeRegistration {
    private static BiConsumer<EntityType<? extends LivingEntity>, AttributeSupplier> registrar;

    private EntityAttributeRegistration() {}

    public static void setRegistrar(BiConsumer<EntityType<? extends LivingEntity>, AttributeSupplier> activeRegistrar) {
        registrar = activeRegistrar;
    }

    public static void register(EntityType<? extends LivingEntity> type, AttributeSupplier.Builder attributes) {
        if (registrar != null) registrar.accept(type, attributes.build());
        else net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(type, attributes);
    }
}
