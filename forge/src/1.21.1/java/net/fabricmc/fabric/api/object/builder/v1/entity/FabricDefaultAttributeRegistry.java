package net.fabricmc.fabric.api.object.builder.v1.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;

/** Routes shared Fabric attribute registrations into Forge's registration event. */
public final class FabricDefaultAttributeRegistry {
    private static final ThreadLocal<EntityAttributeCreationEvent> ACTIVE_EVENT = new ThreadLocal<>();

    private FabricDefaultAttributeRegistry() {}

    public static void begin(EntityAttributeCreationEvent event) {
        ACTIVE_EVENT.set(event);
    }

    public static void end() {
        ACTIVE_EVENT.remove();
    }

    public static <T extends LivingEntity> void register(EntityType<T> type, AttributeSupplier.Builder attributes) {
        EntityAttributeCreationEvent event = ACTIVE_EVENT.get();
        if (event == null) throw new IllegalStateException("Entity attributes registered outside Forge's attribute event");
        event.put(type, attributes.build());
    }
}
