package net.krodark.asterion.client.render.block;

import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.CustomBoneTextureGeoLayer;
import net.krodark.asterion.block.RuneBlockEntity;
import net.krodark.asterion.client.light.LedAmneticLight;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Renders the base model once, then re-renders only the glow bone with tint and Amnetic bloom. */
public final class RuneGeoRenderer extends GeoBlockRenderer<RuneBlockEntity, BlockEntityRenderState> {
    private static final DataTicket<Integer> GLOW_COLOR = DataTickets.create("asterion_rune_glow_color", Integer.class);
    private static final DataTicket<Float> GLOW_PERCENT = DataTickets.create("asterion_rune_glow_percent", Float.class);

    public RuneGeoRenderer(BlockEntityRendererProvider.Context context) {
        super(context, new RuneGeoModel());
        withRenderLayer(new CustomBoneTextureGeoLayer<>(this, "glow", Identifier.fromNamespaceAndPath("asterion", "textures/block/runes/1.png")) {
            @Override protected Identifier getTextureResource(BlockEntityRenderState state) {
                return RuneGeoRenderer.this.getTextureLocation(state);
            }

            @Override protected RenderType getRenderType(BlockEntityRenderState state, Identifier texture) {
                return state.getOrDefaultGeckolibData(GLOW_PERCENT, 0.0F) <= 0.05F
                        ? null : LedAmneticLight.bloomRenderLayer(texture);
            }

            @Override protected void renderBone(RenderPassInfo<BlockEntityRenderState> pass,
                                                  com.geckolib.cache.model.GeoBone bone,
                                                  SubmitNodeCollector renderTasks) {
                int original = pass.renderState().getOrDefaultGeckolibData(DataTickets.RENDER_COLOR, 0xFFFFFFFF);
                int rgb = pass.getOrDefaultGeckolibData(GLOW_COLOR, 0xFFFF9A3D) & 0x00FFFFFF;
                int alpha = Mth.clamp(Math.round(pass.getOrDefaultGeckolibData(GLOW_PERCENT, 0.0F) * 2.55F), 0, 255);
                pass.renderState().addGeckolibData(DataTickets.RENDER_COLOR, alpha << 24 | rgb);
                super.renderBone(pass, bone, renderTasks);
                pass.renderState().addGeckolibData(DataTickets.RENDER_COLOR, original);
            }
        });
    }

    @Override
    public void addRenderData(RuneBlockEntity rune, Void relatedObject, BlockEntityRenderState state,
                              float partialTick) {
        state.addGeckolibData(RuneGeoModel.RUNE_INDEX, rune.runeIndex());
        state.addGeckolibData(GLOW_COLOR, rune.glowColor());
        state.addGeckolibData(GLOW_PERCENT, rune.glowPercent());
        float glow = rune.glowPercent() / 100.0F;
        if (glow > 0.01F) {
            int color = rune.glowColor();
            LedAmneticLight.updateItemGlowLight(rune,
                    Vec3.atCenterOf(rune.getBlockPos()),
                    ((color >> 16) & 255) / 255.0F,
                    ((color >> 8) & 255) / 255.0F,
                    (color & 255) / 255.0F,
                    1.35F * glow, 5.5F * glow + 1.0F, false);
        } else LedAmneticLight.removeItemGlowLight(rune);
    }
}
