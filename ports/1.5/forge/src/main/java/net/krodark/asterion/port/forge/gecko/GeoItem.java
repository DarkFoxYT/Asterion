package net.krodark.asterion.port.forge.gecko;

import java.util.function.Consumer;

public interface GeoItem extends software.bernie.geckolib.animatable.GeoItem {
    void createGeoRenderer(Consumer<GeoRenderProvider> consumer);

    default void initializeClient(Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        createGeoRenderer(consumer::accept);
    }

    static void registerSyncedAnimatable(software.bernie.geckolib.core.animatable.GeoAnimatable value) {
        software.bernie.geckolib.animatable.GeoItem.registerSyncedAnimatable(value);
    }

    static long getOrAssignId(net.minecraft.world.item.ItemStack stack,
                              net.minecraft.server.level.ServerLevel level) {
        return software.bernie.geckolib.animatable.GeoItem.getOrAssignId(stack, level);
    }
}
