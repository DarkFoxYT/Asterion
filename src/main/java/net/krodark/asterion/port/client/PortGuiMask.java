package net.krodark.asterion.port.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.krodark.asterion.Asterion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.HashMap;
import java.util.Map;

/** Resource-pack aware alpha masks: multiplying the original black artwork cannot tint it. */
final class PortGuiMask {
    private static final Map<ResourceLocation, ResourceLocation> MASKS = new HashMap<>();
    static {
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
                .registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                    public ResourceLocation getFabricId() { return Asterion.id("gui_masks"); }
                    public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
                        var textures = Minecraft.getInstance().getTextureManager();
                        MASKS.forEach((source, mask) -> { if (!source.equals(mask)) textures.release(mask); });
                        MASKS.clear();
                    }
                });
    }
    static ResourceLocation get(ResourceLocation source) {
        return MASKS.computeIfAbsent(source, key -> {
            var client = Minecraft.getInstance();
            ResourceLocation id = Asterion.id("dynamic/mask/" + key.getPath());
            try (var input = client.getResourceManager().open(key)) {
                NativeImage pixels = NativeImage.read(input);
                boolean eyes = key.getPath().endsWith("minotaur_eyes.png");
                for (int y = 0; y < pixels.getHeight(); y++) for (int x = 0; x < pixels.getWidth(); x++) {
                    int pixel = pixels.getPixelRGBA(x, y);
                    int light = eyes ? Math.max(110, pixel & 255) : 255;
                    pixels.setPixelRGBA(x, y, (pixel & 0xFF000000) | light << 16 | light << 8 | light);
                }
                client.getTextureManager().register(id, new DynamicTexture(pixels));
                return id;
            } catch (java.io.IOException error) {
                Asterion.LOGGER.warn("Could not load GUI mask {}", key, error);
                return key;
            }
        });
    }
    static void blit(GuiGraphics g, ResourceLocation source, int x, int y, int u, int v,
                     int width, int height, int textureWidth, int textureHeight, int color) {
        g.setColor((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F,
                (color & 255) / 255F, (color >>> 24) / 255F);
        g.blit(get(source), x, y, u, v, width, height, textureWidth, textureHeight);
        g.setColor(1, 1, 1, 1);
    }
    static int mix(float amount, int a, int b) {
        float t = net.minecraft.util.Mth.clamp(amount, 0, 1);
        int r = Math.round((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t);
        int g = Math.round((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t);
        int blue = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return 0xFF000000 | r << 16 | g << 8 | blue;
    }
}
