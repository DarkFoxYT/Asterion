package net.krodark.asterion.client.light;

import com.meekdev.amnetic.client.gbuffer.internal.GBufferTargets;
import com.mojang.blaze3d.opengl.GlTexture;
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

/** Routes selected glowing geometry into Amnetic's dedicated emissive attachment. */
public final class AsterionEmissiveBuffer {
    private static final EmissiveTarget TARGET = new EmissiveTarget();
    private static final OutputTarget OUTPUT = new OutputTarget(
            "asterion_amnetic_emissive", TARGET::refresh);
    private static final Map<Identifier, RenderType> TYPES = new HashMap<>();

    private AsterionEmissiveBuffer() {}

    public static RenderType renderType(Identifier texture) {
        return TYPES.computeIfAbsent(texture, id -> {
            try {
                return AmneticRenderTypeAccess.create("asterion_emissive/" + id,
                        RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE)
                                .withTexture("Sampler0", id)
                                .useLightmap()
                                .useOverlay()
                                .setOutputTarget(OUTPUT)
                                .createRenderSetup());
            } catch (Throwable unsupportedRenderer) {
                // Keep emissive geometry visible even when Amnetic's renderer accessor is
                // unavailable (unsupported GPU/backend, compatibility renderer, etc.).
                return RenderTypes.entityTranslucentEmissive(id);
            }
        });
    }

    private static final class EmissiveTarget extends RenderTarget {
        private int wrappedId;
        private int wrappedWidth;
        private int wrappedHeight;

        private EmissiveTarget() {
            super("Asterion Amnetic Emissive", true);
        }

        private RenderTarget refresh() {
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            width = main.width;
            height = main.height;
            depthTexture = main.getDepthTexture();
            depthTextureView = main.getDepthTextureView();
            int id = 0;
            try {
                int previous = GBufferTargets.INSTANCE.bind();
                if (previous >= 0) {
                    GBufferTargets.INSTANCE.restore(previous);
                    id = GBufferTargets.INSTANCE.emissiveGlId();
                }
            } catch (Throwable unsupportedTarget) {
                // A full-bright main-target pass is the portable fallback.
            }
            if (id != 0 && (id != wrappedId || width != wrappedWidth || height != wrappedHeight)) {
                colorTexture = new RenderAttachmentAlias(id, width, height);
                colorTextureView = RenderSystem.getDevice().createTextureView(colorTexture);
                wrappedId = id;
                wrappedWidth = width;
                wrappedHeight = height;
            }
            if (id != 0) {
                try {
                    // The target is now known-good and will receive this bone's draw call.
                    // Mark it without rebinding the framebuffer a second time.
                    GBufferTargets.INSTANCE.setPopulated(true);
                } catch (Throwable unsupportedTarget) {
                    useMainTarget(main);
                }
            } else useMainTarget(main);
            return this;
        }

        private void useMainTarget(RenderTarget main) {
            colorTexture = main.getColorTexture();
            colorTextureView = main.getColorTextureView();
            wrappedId = 0;
            wrappedWidth = width;
            wrappedHeight = height;
        }

        @Override
        public void createBuffers(int width, int height) {
            refresh();
        }

        @Override
        public void resize(int width, int height) {
            refresh();
        }

        @Override
        public void destroyBuffers() {
            // The color and depth attachments are non-owning aliases.
        }
    }

    private static final class RenderAttachmentAlias extends GlTexture {
        private RenderAttachmentAlias(int id, int width, int height) {
            super(GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                    "asterion_amnetic_emissive_alias", TextureFormat.RGBA8,
                    width, height, 1, 1, id);
        }

        @Override
        public void close() {
            // Amnetic owns and disposes the underlying OpenGL texture.
        }
    }
}
