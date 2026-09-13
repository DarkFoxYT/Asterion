package net.krodark.asterion.port.client;

import foundry.veil.api.client.render.rendertype.VeilRenderType;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.21.1 replacement for Amnetic's emission buffer.
 *
 * <p>The Veil render type writes the surface normally and replays the same
 * geometry into Veil's depth-aware bloom framebuffer. This keeps emission in
 * world space and behind solid geometry instead of compositing it as a screen
 * overlay.</p>
 */
public final class PortEmissiveBuffer {
    private static final ResourceLocation SURFACE = Asterion.id("emissive/surface");
    private static final ResourceLocation BLOOM = Asterion.id("emissive/textured");

    private PortEmissiveBuffer() {}

    public static RenderType renderType(ResourceLocation texture) {
        ResourceLocation id = bloomEnabled() ? BLOOM : SURFACE;
        RenderType type = VeilRenderType.get(id, texture.toString());
        return type != null ? type : RenderType.entityTranslucent(texture);
    }

    public static RenderType surfaceRenderType(ResourceLocation texture) { return renderType(texture); }
    public static RenderType entityRenderType(ResourceLocation texture) { return renderType(texture); }
    public static RenderType blockRenderType(ResourceLocation texture) { return renderType(texture); }
    public static RenderType itemRenderType(ResourceLocation texture) { return renderType(texture); }
    public static RenderType geckoLibRenderType(ResourceLocation texture) { return renderType(texture); }

    public static boolean bloomEnabled() {
        int configured = AsterionConfig.INSTANCE.bloomQuality;
        int quality = configured < 0 ? AsterionConfig.INSTANCE.cinematicQuality + 1 : configured;
        return quality > 0;
    }
}
