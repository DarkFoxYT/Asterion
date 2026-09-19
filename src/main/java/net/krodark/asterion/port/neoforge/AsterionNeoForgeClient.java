package net.krodark.asterion.port.neoforge;

import net.krodark.asterion.Asterion;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Asterion.MOD_ID, value = Dist.CLIENT)
public final class AsterionNeoForgeClient {
    private AsterionNeoForgeClient() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        net.krodark.asterion.port.client.PortClientFeatures.tick(Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        net.krodark.asterion.port.client.PortLight.clear();
    }
}
