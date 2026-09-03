package net.krodark.asterion.client.render.block;

import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import net.krodark.asterion.block.ShatteredDeadWoodBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

public final class ShatteredDeadWoodGeoRenderer
        extends GeoBlockRenderer<ShatteredDeadWoodBlockEntity, BlockEntityRenderState> {
    private static final DataTicket<Integer> FACING = DataTickets.create(
            "asterion_shattered_dead_wood_facing", Integer.class);

    public ShatteredDeadWoodGeoRenderer(BlockEntityRendererProvider.Context context) {
        super(context, new ShatteredDeadWoodGeoModel());
    }

    @Override
    public void addRenderData(ShatteredDeadWoodBlockEntity wood, Void related,
                              BlockEntityRenderState state, float partialTick) {
        state.addGeckolibData(FACING, wood.facing().ordinal());
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<BlockEntityRenderState> pass, BoneSnapshots bones) {
        super.adjustModelBonesForRender(pass, bones);
        Direction facing = Direction.values()[Math.clamp(
                pass.getOrDefaultGeckolibData(FACING, Direction.UP.ordinal()),
                0, Direction.values().length - 1)];
        float x = 0.0F;
        float z = 0.0F;
        switch (facing) {
            case DOWN -> x = Mth.PI;
            case NORTH -> x = -Mth.HALF_PI;
            case SOUTH -> x = Mth.HALF_PI;
            case WEST -> z = Mth.HALF_PI;
            case EAST -> z = -Mth.HALF_PI;
            default -> { }
        }
        float rotationX = x;
        float rotationZ = z;
        bones.ifPresent("full", bone -> bone.setRotation(
                bone.getRotX() + rotationX, bone.getRotY(), bone.getRotZ() + rotationZ));
    }
}
