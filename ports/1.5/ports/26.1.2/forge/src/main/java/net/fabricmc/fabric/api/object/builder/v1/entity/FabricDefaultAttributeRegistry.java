package net.fabricmc.fabric.api.object.builder.v1.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;

import java.util.IdentityHashMap;
import java.util.Map;

/** Collects shared entity attributes for Forge's attribute creation event. */
public final class FabricDefaultAttributeRegistry {
    private static final Map<EntityType<? extends LivingEntity>, AttributeSupplier> PENDING = new IdentityHashMap<>();

    private FabricDefaultAttributeRegistry() {}

    public static <T extends LivingEntity> void register(EntityType<T> type, AttributeSupplier.Builder attributes) {
        PENDING.put(type, attributes.build());
    }

    public static void apply(EntityAttributeCreationEvent event) {
        PENDING.forEach(event::put);
        PENDING.clear();
    }
}
