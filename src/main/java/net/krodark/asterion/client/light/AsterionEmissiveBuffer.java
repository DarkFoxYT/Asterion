package net.krodark.asterion.client.light;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.AmneticRenderTypeAccess;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
/** Full-bright surfaces rendered against Minecraft's real scene depth. */
public final class AsterionEmissiveBuffer {
    private static final Map<Identifier, RenderType> TEXTURED = new HashMap<>();
    private static final Map<CustomKey, RenderType> CUSTOM = new HashMap<>();

    private AsterionEmissiveBuffer() {
    }

    public static RenderType renderType(Identifier texture) {
        return TEXTURED.computeIfAbsent(texture, id -> AmneticRenderTypeAccess.create(
                "asterion_amnetic_emissive/" + id,
                RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE)
                        .withTexture("Sampler0", id)
                        .useLightmap()
                        .useOverlay()
                        .createRenderSetup()));
    }

    public static RenderType renderType(Identifier texture, boolean enhanced) {
        return renderType(texture);
    }

    public static RenderType surfaceRenderType(Identifier texture) {
        return renderType(texture);
    }

    public static RenderType entityRenderType(Identifier texture) {
        return renderType(texture);
    }

    public static RenderType blockRenderType(Identifier texture) {
        return renderType(texture);
    }

    public static RenderType itemRenderType(Identifier texture) {
        return renderType(texture);
    }

    public static RenderType geckoLibRenderType(Identifier texture) {
        return renderType(texture);
    }

    public static RenderType customRenderType(String name, RenderPipeline pipeline) {
        return customRenderType(name, pipeline, null);
    }

    public static RenderType customRenderType(String name, RenderPipeline pipeline, Identifier texture) {
        return CUSTOM.computeIfAbsent(new CustomKey(name, pipeline, texture), key -> {
            // Keep the visible surface in Minecraft's main target. Redirecting it into the
            // HDR attachment also redirected depth handling through a framebuffer alias;
            // some drivers then accepted color while rejecting scene depth, making every
            // emissive surface visible through floors. Amnetic's official emission pass
            // supplies the optional bloom copy with an explicit scene-depth blit.
            var setup = RenderSetup.builder(pipeline);
            if (texture != null) {
                setup.withTexture("Sampler0", texture).useLightmap().useOverlay();
            }
            return AmneticRenderTypeAccess.create(
                    "asterion_amnetic_emissive/" + name, setup.createRenderSetup());
        });
    }

    private record CustomKey(String name, RenderPipeline pipeline, Identifier texture) {
    }
}
