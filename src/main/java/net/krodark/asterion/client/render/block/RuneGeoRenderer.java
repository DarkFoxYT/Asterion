package net.krodark.asterion.client.render.block;

import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.GeoBlockRenderer;
import net.krodark.asterion.client.light.AsterionEmissiveBoneLayer;
import net.krodark.asterion.block.RuneBlockEntity;
import net.krodark.asterion.client.light.LedAmneticLight;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class RuneGeoRenderer extends GeoBlockRenderer<RuneBlockEntity, BlockEntityRenderState> {
    private static final DataTicket<Integer> GLOW_COLOR = DataTickets.create("asterion_rune_glow_color", Integer.class);
    private static final DataTicket<Float> GLOW_PERCENT = DataTickets.create("asterion_rune_glow_percent", Float.class);

    public RuneGeoRenderer(BlockEntityRendererProvider.Context context) {
        super(context, new RuneGeoModel());
        withRenderLayer(new AsterionEmissiveBoneLayer<>(this, "glow", Identifier.fromNamespaceAndPath("asterion", "textures/block/runes/1.png")) {
            @Override protected Identifier getTextureResource(BlockEntityRenderState state) {
                return RuneGeoRenderer.this.getTextureLocation(state);
            }

            @Override protected int emissiveColor(BlockEntityRenderState state) {
                int rgb = state.getOrDefaultGeckolibData(GLOW_COLOR, 0xFFFF9A3D) & 0x00FFFFFF;
                int alpha = Mth.clamp(Math.round(state.getOrDefaultGeckolibData(GLOW_PERCENT, 0.0F) * 2.55F), 0, 255);
                return alpha << 24 | rgb;
            }
        });
    }

    @Override public boolean shouldRenderOffScreen() { return true; }

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
