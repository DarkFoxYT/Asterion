package net.krodark.asterion.client.light;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.krodark.asterion.mixin.RenderTypeFactory;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
 
public final class AsterionEmissiveBuffer {
    private static final Map<Identifier, RenderType> TEXTURED = new HashMap<>();
    private static final Map<CustomKey, RenderType> CUSTOM = new HashMap<>();
    private static final RenderPipeline SURFACE_PIPELINE = surfacePipeline();
    private static RenderPipeline surfacePipeline() {
        var source=RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE;
        var builder=RenderPipeline.builder().withLocation(net.krodark.asterion.Asterion.id("pipeline/emissive_surface"))
                .withVertexShader(source.getVertexShader()).withFragmentShader(net.krodark.asterion.Asterion.id("core/enhanced_emissive"))
                //? if >=26.2 {
                /*.withVertexBinding(0, source.getVertexFormatBinding(0)).withPrimitiveTopology(source.getPrimitiveTopology()).withCull(source.isCull())
                *///?} else {
                .withVertexFormat(source.getVertexFormat(),source.getVertexFormatMode()).withCull(source.isCull())
                //?}
                .withColorTargetState(source.getColorTargetState()).withDepthStencilState(source.getDepthStencilState());
        //? if >=26.2 {
        /*for (var layout : source.getBindGroupLayouts()) builder.withBindGroupLayout(layout);
        *///?} else {
        for(var sampler:source.getSamplers())builder.withSampler(sampler);
        for(var uniform:source.getUniforms())builder.withUniform(uniform.name(),uniform.type());
        //?}
        for(var flag:source.getShaderDefines().flags())builder.withShaderDefine(flag);
        return builder.build();
    }

    public static void clearCaches() { TEXTURED.clear(); CUSTOM.clear(); }

    private AsterionEmissiveBuffer() {
    }

    public static RenderType renderType(Identifier texture) {
        return TEXTURED.computeIfAbsent(texture, id -> RenderTypeFactory.create(
                "asterion_amnetic_emissive/" + id,
                RenderSetup.builder(SURFACE_PIPELINE)
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
             
             
             
             
             
            var setup = RenderSetup.builder(pipeline);
            if (texture != null) {
                setup.withTexture("Sampler0", texture).useLightmap().useOverlay();
            }
            return RenderTypeFactory.create(
                    "asterion_amnetic_emissive/" + name, setup.createRenderSetup());
        });
    }

    private record CustomKey(String name, RenderPipeline pipeline, Identifier texture) {
    }
}
