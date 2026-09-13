package net.krodark.asterion.port.neoforge;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
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
    private static LightRenderHandle<PointLightData> heldLight;

    private AsterionNeoForgeClient() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            clearLight();
            return;
        }
        int color = lightColor(client.player.getMainHandItem());
        if (color < 0) color = lightColor(client.player.getOffhandItem());
        if (color < 0) {
            clearLight();
            return;
        }
        if (heldLight == null || !heldLight.isValid()) {
            heldLight = VeilRenderSystem.renderer().getLightRenderer().addLight(new PointLightData()
                    .setRadius(7.5F).setBrightness(1.1F).setOcclusionEnabled(true).setColor(color));
        }
        heldLight.getLightData().setColor(color).setPosition(
                client.player.getX(), client.player.getEyeY() - 0.2D, client.player.getZ());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clearLight();
    }

    private static int lightColor(ItemStack stack) {
        if (stack.is(Asterion.GREEK_FIRE_LANTERN.asItem())
                || stack.is(Asterion.GREEK_FIRE_FLOOR_TORCH.asItem())
                || stack.is(Asterion.GREEK_FIRE_WALL_TORCH.asItem())) return 0x38F0A0;
        if (stack.is(Asterion.RED_FIRE_LANTERN.asItem())
                || stack.is(Asterion.RED_FIRE_FLOOR_TORCH.asItem())
                || stack.is(Asterion.RED_FIRE_WALL_TORCH.asItem())) return 0xFF3B24;
        if (stack.is(Asterion.ORANGE_FIRE_FLOOR_TORCH.asItem())
                || stack.is(Asterion.ORANGE_FIRE_WALL_TORCH.asItem())) return 0xFF9A32;
        return -1;
    }

    private static void clearLight() {
        if (heldLight != null) {
            heldLight.free();
            heldLight = null;
        }
    }
}
