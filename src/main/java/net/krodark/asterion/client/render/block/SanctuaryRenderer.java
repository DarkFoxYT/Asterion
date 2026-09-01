package net.krodark.asterion.client.render.block;

import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.SanctuaryBlock;
import net.krodark.asterion.block.SanctuaryBlockEntity;
import net.krodark.asterion.client.light.AsterionEmissiveBoneLayer;
import net.krodark.asterion.client.light.LedAmneticLight;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public final class SanctuaryRenderer extends GeoBlockRenderer<SanctuaryBlockEntity, BlockEntityRenderState> {
    private static final DataTicket<Boolean> ALTAR = DataTickets.create("asterion_sanctuary_altar", Boolean.class);
    private static final DataTicket<Integer> CHARGE = DataTickets.create("asterion_sanctuary_charge", Integer.class);
    private static final DataTicket<Float> TIME = DataTickets.create("asterion_sanctuary_time", Float.class);
    public SanctuaryRenderer(BlockEntityRendererProvider.Context context) {
        super(context, new Model());
        withRenderLayer(new AsterionEmissiveBoneLayer<>(this, "glow",
                Identifier.fromNamespaceAndPath("minecraft", "textures/block/gold_block.png")) {
            @Override protected Identifier getTextureResource(BlockEntityRenderState state) {
                return SanctuaryRenderer.this.getTextureLocation(state);
            }
            @Override public boolean shouldRenderBone(BlockEntityRenderState state) {
                return !state.getOrDefaultGeckolibData(ALTAR, false)
                        || state.getOrDefaultGeckolibData(CHARGE, 0) != 2;
            }
            @Override protected float surfaceBrightness(BlockEntityRenderState state) {
                return state.getOrDefaultGeckolibData(CHARGE, 0) == 1 ? .85F : 0F;
            }
            @Override protected int emissiveColor(BlockEntityRenderState state) {
                return state.getOrDefaultGeckolibData(CHARGE, 0) == 1 ? 0xFFFFE7B5 : 0xFF777777;
            }
        });
    }
    @Override public void addRenderData(SanctuaryBlockEntity entity, Void related,
                                       BlockEntityRenderState state, float partialTick) {
        boolean altar = ((SanctuaryBlock)entity.getBlockState().getBlock()).altar;
        int charge = entity.getBlockState().getValue(SanctuaryBlock.CHARGE);
        state.addGeckolibData(ALTAR, altar);
        state.addGeckolibData(CHARGE, charge);
        state.addGeckolibData(TIME, entity.getLevel() == null ? 0F : (entity.getLevel().getGameTime() % 24000) + partialTick);
        if (charge == 1) LedAmneticLight.updateItemGlowLight(entity,
                Vec3.atCenterOf(entity.getBlockPos()).add(0, .35, 0), 1F, .68F, .27F,
                altar ? .7F : 1.25F, altar ? 3F : 7F, false);
        else LedAmneticLight.removeItemGlowLight(entity);
    }
    @Override public void adjustModelBonesForRender(RenderPassInfo<BlockEntityRenderState> pass, BoneSnapshots bones) {
        boolean altar = pass.getOrDefaultGeckolibData(ALTAR, false);
        float time = pass.getOrDefaultGeckolibData(TIME, 0F);
        bones.ifPresent("glow", bone -> {
            bone.skipRender(altar && pass.getOrDefaultGeckolibData(CHARGE, 0) == 2);
            bone.skipChildrenRender(altar && pass.getOrDefaultGeckolibData(CHARGE, 0) == 2);
            if (altar) {
                bone.setTranslation(0, (float)Math.sin(time * .065) * 1.2F, 0);
                bone.setRotation(0, time * .025F, .15F);
            }
        });
    }
    private static final class Model extends GeoModel<SanctuaryBlockEntity> {
        @Override public Identifier getModelResource(GeoRenderState state) {
            return Asterion.id(state.getOrDefaultGeckolibData(ALTAR, false) ? "block/respawn_altar" : "block/respawn_obelisk");
        }
        @Override public Identifier getTextureResource(GeoRenderState state) {
            return Identifier.fromNamespaceAndPath("minecraft", "textures/block/"
                    + (state.getOrDefaultGeckolibData(CHARGE, 0) == 1 ? "gold_block.png" : "iron_block.png"));
        }
        @Override public Identifier getAnimationResource(SanctuaryBlockEntity entity) { return Asterion.id("block/sanctuary"); }
    }
}
