package net.krodark.asterion.port.compat;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class JigsawCompat {
    private static final ResourceLocation EMPTY = ResourceLocation.withDefaultNamespace("empty");

    private JigsawCompat() {}

    public static List<JigsawInfo> getJigsaws(StructureTemplate template, BlockPos origin, Rotation rotation) {
        var settings = new StructurePlaceSettings().setRotation(rotation);
        return template.filterBlocks(origin, settings, Blocks.JIGSAW, true).stream()
                .map(info -> new JigsawInfo(info, readLocation(info, "name"), readLocation(info, "target")))
                .toList();
    }

    private static ResourceLocation readLocation(StructureTemplate.StructureBlockInfo info, String key) {
        if (info.nbt() == null) return EMPTY;
        ResourceLocation parsed = ResourceLocation.tryParse(NbtCompat.getString(info.nbt(), key, "minecraft:empty"));
        return parsed == null ? EMPTY : parsed;
    }

    public record JigsawInfo(StructureTemplate.StructureBlockInfo info,
                             ResourceLocation name,
                             ResourceLocation target) {}
}
