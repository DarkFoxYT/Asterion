package net.krodark.asterion.port.forge.nativelegacy;

import net.minecraftforge.eventbus.api.IEventBus;

public final class NativeLegacyBindings {
    private NativeLegacyBindings() {}

    public static void register(IEventBus modBus) {
        NativeLegacyNetwork.initialize();
        NativeLifecycle.register();
        modBus.addListener(
                (net.minecraftforge.event.BuildCreativeModeTabContentsEvent event) ->
                        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.fire(event));
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist
                == net.minecraftforge.api.distmarker.Dist.CLIENT)
            NativeLegacyClientBindings.register(modBus);
    }
}
