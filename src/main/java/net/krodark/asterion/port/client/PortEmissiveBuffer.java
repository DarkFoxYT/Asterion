package net.krodark.asterion.port.client;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Full-bright surfaces replayed into the Amnetic emission pass. */
public final class PortEmissiveBuffer extends RenderType {
    private static net.minecraft.client.renderer.ShaderInstance surfaceShader;
    static void initialize() {
        net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback.EVENT.register(context ->
                context.register(Asterion.id("world_emission"), com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,
                        shader -> surfaceShader = shader));
    }

    private static final java.util.Map<ResourceLocation, RenderType> TYPES = new java.util.HashMap<>();
    private PortEmissiveBuffer() {
        super("asterion_emission", com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 1536, false, true, () -> {}, () -> {});
    }

    public static RenderType renderType(ResourceLocation texture) {
        return TYPES.computeIfAbsent(texture, key -> create("asterion_world_emission",
                com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 1536, false, true,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> surfaceShader))
                        .setTextureState(new TextureStateShard(key, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false)));
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
