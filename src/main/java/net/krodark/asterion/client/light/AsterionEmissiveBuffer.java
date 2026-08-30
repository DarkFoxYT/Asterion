package net.krodark.asterion.client.light;

import com.meekdev.amnetic.client.gbuffer.internal.GBufferTargets;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.AmneticRenderTypeAccess;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Shared bridge from Minecraft/GeckoLib submitted geometry into Amnetic's emissive G-buffer.
 * The returned types are valid for entities, block entities, dropped or held items, GeckoLib
 * bone layers, and custom geometry; the caller only decides which geometry is emissive.
 */
public final class AsterionEmissiveBuffer {
    private static final EmissiveTarget TARGET = new EmissiveTarget();
    private static final OutputTarget OUTPUT = new OutputTarget(
            "asterion_amnetic_emissive", TARGET::refresh);
    private static final Map<Identifier, RenderType> TEXTURED_TYPES = new HashMap<>();

    private AsterionEmissiveBuffer() { }

    /** Standard textured emissive type, suitable for every model category and GeckoLib layer. */
    public static RenderType renderType(Identifier texture) {
        return TEXTURED_TYPES.computeIfAbsent(texture, id -> createTextured(
                "asterion_emissive/" + id, RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE, id));
    }

    public static RenderType entityRenderType(Identifier texture) { return renderType(texture); }
    public static RenderType blockRenderType(Identifier texture) { return renderType(texture); }
    public static RenderType itemRenderType(Identifier texture) { return renderType(texture); }
    public static RenderType geckoLibRenderType(Identifier texture) { return renderType(texture); }

    /** Emissive target for untextured/custom pipelines such as beams, trails, and primitives. */
    public static RenderType customRenderType(String name, RenderPipeline pipeline) {
        try {
            return AmneticRenderTypeAccess.create("asterion_emissive/" + name,
                    RenderSetup.builder(pipeline).setOutputTarget(OUTPUT).createRenderSetup());
        } catch (Throwable unavailable) {
            return RenderType.create("asterion_emissive_fallback/" + name,
                    RenderSetup.builder(pipeline).createRenderSetup());
        }
    }

    /** Textured custom pipeline variant for GeckoLib item/entity effects and special meshes. */
    public static RenderType customRenderType(String name, RenderPipeline pipeline,
                                               Identifier texture) {
        return createTextured("asterion_emissive/" + name, pipeline, texture);
    }

    /** Explicitly wakes Amnetic's lazy attachment; useful before deferred custom submissions. */
    public static boolean prepare() {
        return TARGET.refresh() == TARGET && TARGET.usingAmnetic;
    }

    private static RenderType createTextured(String name, RenderPipeline pipeline,
                                             Identifier texture) {
        try {
            return AmneticRenderTypeAccess.create(name,
                    RenderSetup.builder(pipeline)
                            .withTexture("Sampler0", texture)
                            .useLightmap()
                            .useOverlay()
                            .setOutputTarget(OUTPUT)
                            .createRenderSetup());
        } catch (Throwable unavailable) {
            return RenderTypes.entityTranslucentEmissive(texture);
        }
    }

    private static void markPopulated() {
        try {
            int previous = GBufferTargets.INSTANCE.bind();
            if (previous < 0) return;
            try {
                GBufferTargets.INSTANCE.setPopulated(true);
            } finally {
                GBufferTargets.INSTANCE.restore(previous);
            }
        } catch (Throwable ignored) {
            // Unsupported graphics backends retain ordinary full-bright rendering.
        }
    }

    private static final class EmissiveTarget extends RenderTarget {
        private int wrappedId;
        private int wrappedWidth;
        private int wrappedHeight;
        private boolean usingAmnetic;

        private EmissiveTarget() {
            super("Asterion Amnetic Emissive", true);
        }

        private RenderTarget refresh() {
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            width = main.width;
            height = main.height;
            depthTexture = main.getDepthTexture();
            depthTextureView = main.getDepthTextureView();
            int id = emissiveAttachment();
            usingAmnetic = id != 0;
            if (!usingAmnetic) {
                colorTexture = main.getColorTexture();
                colorTextureView = main.getColorTextureView();
                wrappedId = 0;
                wrappedWidth = width;
                wrappedHeight = height;
                return this;
            }
            if (id != wrappedId || width != wrappedWidth || height != wrappedHeight) {
                colorTexture = new RenderAttachmentAlias(id, width, height);
                colorTextureView = RenderSystem.getDevice().createTextureView(colorTexture);
                wrappedId = id;
                wrappedWidth = width;
                wrappedHeight = height;
            }
            markPopulated();
            return this;
        }

        private static int emissiveAttachment() {
            try {
                int previous = GBufferTargets.INSTANCE.bind();
                if (previous < 0) return 0;
                try {
                    return GBufferTargets.INSTANCE.emissiveGlId();
                } finally {
                    GBufferTargets.INSTANCE.restore(previous);
                }
            } catch (Throwable unavailable) {
                return 0;
            }
        }

        @Override public void createBuffers(int width, int height) { refresh(); }
        @Override public void resize(int width, int height) { refresh(); }
        @Override public void destroyBuffers() { /* Non-owning aliases only. */ }
    }

    private static final class RenderAttachmentAlias extends GlTexture {
        private RenderAttachmentAlias(int id, int width, int height) {
            super(GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                    "asterion_amnetic_emissive_alias", TextureFormat.RGBA8,
                    width, height, 1, 1, id);
        }

        @Override public void close() { /* Owned by Amnetic. */ }
    }
}
