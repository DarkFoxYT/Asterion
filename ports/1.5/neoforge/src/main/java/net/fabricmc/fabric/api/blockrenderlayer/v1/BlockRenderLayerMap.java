package net.fabricmc.fabric.api.blockrenderlayer.v1;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

public final class BlockRenderLayerMap {
    public static final BlockRenderLayerMap INSTANCE = new BlockRenderLayerMap();

    private BlockRenderLayerMap() {}

    public void putBlocks(RenderType type, Block... blocks) {
        for (var block : blocks)
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(block, type);
    }

    public void putFluids(RenderType type, Fluid... fluids) {
        for (var fluid : fluids)
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(fluid, type);
    }
}
