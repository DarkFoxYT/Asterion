package net.krodark.asterion.client.render.block;

import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.*;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.*;
import net.krodark.asterion.client.light.AsterionEmissiveBoneLayer;
import net.krodark.asterion.client.light.LedAmneticLight;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.resources.Identifier;

public final class GreekFireTorchRenderer extends GeoBlockRenderer<GreekFireTorchBlockEntity,BlockEntityRenderState> {
    private static final DataTicket<Boolean> WALL=DataTickets.create("asterion_torch_wall",Boolean.class);
    private static final DataTicket<Boolean> TOP=DataTickets.create("asterion_torch_top",Boolean.class);
    private static final DataTicket<Boolean> LIT=DataTickets.create("asterion_torch_lit",Boolean.class);
    private static final DataTicket<GreekFireTorchBlock.FireColor> COLOR=DataTickets.create("asterion_torch_color",GreekFireTorchBlock.FireColor.class);
    public GreekFireTorchRenderer(BlockEntityRendererProvider.Context context) {
        super(context,new Model());
        withRenderLayer(new AsterionEmissiveBoneLayer<>(this,"flame",Asterion.id("textures/block/torch_greek_fire.png")) {
            @Override public boolean shouldRenderBone(BlockEntityRenderState state) {
                return state.getOrDefaultGeckolibData(LIT,true)
                        &&(state.getOrDefaultGeckolibData(WALL,false)||state.getOrDefaultGeckolibData(TOP,true));
            }
            @Override protected float surfaceBrightness(BlockEntityRenderState state) { return 1.5F; }
            @Override public Identifier getTextureResource(BlockEntityRenderState state) {
                return texture(state);
            }
            @Override protected boolean enhancedSurface(BlockEntityRenderState state) { return true; }
            @Override protected Identifier amneticEmissionMesh(BlockEntityRenderState state) {
                return getGeoModel().getModelResource(state);
            }
            @Override protected void renderBone(RenderPassInfo<BlockEntityRenderState> pass,
                    com.geckolib.cache.model.GeoBone bone,net.minecraft.client.renderer.SubmitNodeCollector tasks) {
                if(bone.name().equals("flame")) super.renderBone(pass,bone,tasks);
            }
        });
    }
    @Override public void addRenderData(GreekFireTorchBlockEntity torch,Void related,BlockEntityRenderState state,float partial) {
        GreekFireTorchBlock block=(GreekFireTorchBlock)torch.getBlockState().getBlock();
        state.addGeckolibData(WALL,block.wall);
        state.addGeckolibData(TOP,torch.getBlockState().getValue(GreekFireTorchBlock.TOP));
        state.addGeckolibData(LIT,torch.getBlockState().getValue(GreekFireTorchBlock.LIT));
        state.addGeckolibData(COLOR,block.fireColor);
        boolean flame=torch.getBlockState().getValue(GreekFireTorchBlock.LIT)
                &&(block.wall||torch.getBlockState().getValue(GreekFireTorchBlock.TOP));
        if(flame) {
            var color=block.fireColor;
            var pos=torch.getBlockPos();
            LedAmneticLight.updateItemGlowLight(torch,
                    new net.minecraft.world.phys.Vec3(pos.getX()+.5,pos.getY()+(block.wall?1.05:1.15),pos.getZ()+.5),
                    color.red,color.green,color.blue,1.35F,6.25F,false);
        } else LedAmneticLight.removeItemGlowLight(torch);
    }
    @Override public void adjustModelBonesForRender(RenderPassInfo<BlockEntityRenderState> pass,BoneSnapshots bones) {
        boolean wall=pass.getOrDefaultGeckolibData(WALL,false), top=pass.getOrDefaultGeckolibData(TOP,true);
        bones.ifPresent("shaft",bone->bone.skipRender(!wall&&top));
        bones.ifPresent("top",bone->{bone.skipRender(!wall&&!top);bone.skipChildrenRender(!wall&&!top);});
        bones.ifPresent("flame",bone->bone.skipRender(true));
        // GeoBlockRenderer already reads HORIZONTAL_FACING and rotates the whole
        // render pose once. Rotating the root bone here as well doubled every yaw.
    }
    private static final class Model extends GeoModel<GreekFireTorchBlockEntity> {
        @Override public Identifier getModelResource(GeoRenderState state) {
            return Asterion.id(state.getOrDefaultGeckolibData(WALL,false)?"block/wall_torch":"block/floor_torch");
        }
        @Override public Identifier getTextureResource(GeoRenderState state) { return texture(state); }
        @Override public Identifier getAnimationResource(GreekFireTorchBlockEntity entity) { return Asterion.id("block/greek_fire_torch"); }
    }
    private static Identifier texture(GeoRenderState state) {
        if(!state.getOrDefaultGeckolibData(LIT,true)) return Asterion.id("textures/block/torch_no_fire.png");
        return Asterion.id("textures/block/"+state.getOrDefaultGeckolibData(COLOR,GreekFireTorchBlock.FireColor.GREEK).texture+".png");
    }
}
