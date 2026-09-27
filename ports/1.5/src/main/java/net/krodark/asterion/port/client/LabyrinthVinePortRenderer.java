package net.krodark.asterion.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.LabyrinthVineBlock;
import net.krodark.asterion.block.LabyrinthVineBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import software.bernie.geckolib.cache.object.BakedGeoModel;

/** Correct up/down geometry and terminal bulb visibility for ancient vines. */
public final class LabyrinthVinePortRenderer extends SimpleGeoBlockRenderer<LabyrinthVineBlockEntity> {
    private static net.minecraft.client.renderer.culling.Frustum frustum;
    static void initializeCulling() {
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.START.register(context -> frustum = null);
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_SETUP.register(context -> frustum = context.frustum());
    }

    @Override
    public boolean shouldRender(LabyrinthVineBlockEntity vine, net.minecraft.world.phys.Vec3 camera) {
        // Keep the global block-entity path for section-edge geometry, but cull
        // the complete model before GeckoLib transforms or queues emissive bones.
        return super.shouldRender(vine, camera) && (frustum == null
                || frustum.isVisible(new net.minecraft.world.phys.AABB(vine.getBlockPos()).inflate(3)));
    }

    public LabyrinthVinePortRenderer() {
        super(entity -> Asterion.id(entity.getBlockState().getValue(LabyrinthVineBlock.FACING)
                        == net.minecraft.core.Direction.UP ? "block/labyrinth_vine_up" : "block/labyrinth_vine"),
                ignored -> Asterion.id("textures/block/labyrinth_vine.png"),
                ignored -> Asterion.id("block/labyrinth_vine"));
        withEmissiveBones(ignored -> (Math.round(PortEmissiveConfig.vineGlowStrength()*255)<<24)|0xFFFFFF, LabyrinthVineBlockEntity::isEnd, "glow");
    }

    @Override

//? if >=1.20.5 {
public void preRender(PoseStack poses, LabyrinthVineBlockEntity vine, BakedGeoModel model,
                          MultiBufferSource buffers, VertexConsumer buffer, boolean reRender,
                          float partialTick, int light, int overlay, int color) {
//?} else {
/*public void preRender(PoseStack poses, LabyrinthVineBlockEntity vine, BakedGeoModel model,
                          MultiBufferSource buffers, VertexConsumer buffer, boolean reRender,
                          float partialTick, int light, int overlay, float red, float green, float blue, float alpha) {
 int color = ((int)(alpha*255)<<24)|((int)(red*255)<<16)|((int)(green*255)<<8)|(int)(blue*255);*/
//?}

        model.getBone("bulb").ifPresent(bone -> bone.setHidden(!vine.isEnd()));

//? if >=1.20.5 {
super.preRender(poses, vine, model, buffers, buffer, reRender, partialTick,
                light, overlay, color);
//?} else {
/*super.preRender(poses, vine, model, buffers, buffer, reRender, partialTick,
                light, overlay, ((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F, ((color >>> 24) & 255) / 255F);*/
//?}

    }
}
