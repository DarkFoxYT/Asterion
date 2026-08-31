package net.krodark.asterion.client.light;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/** Local full-bright material cache. Uses Minecraft's batched vertex buffers, never Amnetic's targets. */
public final class AsterionEmissiveBuffer {
    private static final Map<Identifier, RenderType> TEXTURED = new HashMap<>();
    private static final Map<CustomKey, RenderType> CUSTOM = new HashMap<>();
    private static final RenderPipeline SURFACE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asterion", "pipeline/emissive_surface"))
            .withVertexShader("core/entity").withFragmentShader("core/entity")
            .withShaderDefine("EMISSIVE").withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING").withShaderDefine("ALPHA_CUTOUT", 0.1f)
            .withSampler("Sampler0")
            .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .withCull(false).build();

    private static final Map<Identifier, RenderType> ENHANCED = new HashMap<>();
    private static final RenderPipeline ENHANCED_SURFACE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("asterion", "pipeline/enhanced_emissive_surface"))
            .withVertexShader("core/entity").withFragmentShader(Identifier.fromNamespaceAndPath("asterion", "core/enhanced_emissive"))
            .withShaderDefine("EMISSIVE").withShaderDefine("NO_OVERLAY").withShaderDefine("NO_CARDINAL_LIGHTING")
            .withSampler("Sampler0").withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false)).withCull(false).build();

    public static RenderType renderType(Identifier texture, boolean enhanced) {
        if (!enhanced) return renderType(texture);
        return ENHANCED.computeIfAbsent(texture, key -> RenderType.create("asterion_emissive/enhanced/" + key,
                RenderSetup.builder(ENHANCED_SURFACE).withTexture("Sampler0", key).createRenderSetup()));
    }

    private AsterionEmissiveBuffer() {}

    /** A sharp, depth-tested surface: no HDR amplification, glow attachment or fullscreen blur. */
    public static RenderType renderType(Identifier texture) {
        return TEXTURED.computeIfAbsent(texture, key -> RenderType.create("asterion_emissive/" + key,
                RenderSetup.builder(SURFACE).withTexture("Sampler0", key).createRenderSetup()));
    }

    public static RenderType surfaceRenderType(Identifier texture) { return renderType(texture); }
    public static RenderType entityRenderType(Identifier texture) { return renderType(texture); }
    public static RenderType blockRenderType(Identifier texture) { return renderType(texture); }
    public static RenderType itemRenderType(Identifier texture) { return renderType(texture); }
    public static RenderType geckoLibRenderType(Identifier texture) { return renderType(texture); }

    public static RenderType customRenderType(String name, RenderPipeline pipeline) {
        return customRenderType(name, pipeline, null);
    }

    public static RenderType customRenderType(String name, RenderPipeline pipeline, Identifier texture) {
        return CUSTOM.computeIfAbsent(new CustomKey(name, pipeline, texture), key -> {
            var setup = RenderSetup.builder(pipeline);
            if (texture != null) setup.withTexture("Sampler0", texture).useLightmap().useOverlay();
            return RenderType.create("asterion_emissive/" + name, setup.createRenderSetup());
        });
    }

    private record CustomKey(String name, RenderPipeline pipeline, Identifier texture) {}
}
