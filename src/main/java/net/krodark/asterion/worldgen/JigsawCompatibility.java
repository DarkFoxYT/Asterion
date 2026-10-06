package net.krodark.asterion.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class JigsawCompatibility {
    private JigsawCompatibility() {}

    public static BlockPos pos(StructureTemplate.JigsawBlockInfo jigsaw) {
        //? if >=26.3 {
        /*return jigsaw.pos();
        *///?} else {
        return jigsaw.info().pos();
        //?}
    }

    public static BlockState state(StructureTemplate.JigsawBlockInfo jigsaw) {
        //? if >=26.3 {
        /*return jigsaw.state();
        *///?} else {
        return jigsaw.info().state();
        //?}
    }
}
