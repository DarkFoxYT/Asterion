package net.krodark.asterion.update.underworld.world;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/** One authored entrance, placed only in newly generated chunks and clipped at their edges. */
public final class LimboEntranceGate {
    private LimboEntranceGate() { }

    public static void place(WorldGenLevel world, ChunkPos chunk) {
        placeAt(world,chunk,UnderworldTerrain.GATE_X,UnderworldTerrain.GATE_Y,UnderworldTerrain.GATE_Z);
        placeAt(world,chunk,(int)LimboSeaRegions.CENTER_X,LimboCascades.waterYForTier(4)+2,(int)LimboSeaRegions.CENTER_Z);
    }

    private static void placeAt(WorldGenLevel world,ChunkPos chunk,int x,int y,int z) {
        if (chunk.getMaxBlockX() < x - 73
                || chunk.getMinBlockX() > x + 72
                || chunk.getMaxBlockZ() < z - 16
                || chunk.getMinBlockZ() > z + 17) return;
        var template = world.getLevel().getStructureManager().get(Asterion.id("limbo_gate"))
                .orElseThrow(() -> new IllegalStateException("Missing Limbo entrance gate template"));
        var size = template.getSize();
        if (size.getX() != UnderworldTerrain.GATE_DEPTH || size.getZ() != UnderworldTerrain.GATE_WIDTH
                || y + size.getY() - 1 > UnderworldTerrain.MAX_Y)
            throw new IllegalStateException("Limbo gate template does not fit its reserved courtyard");
        var origin = new BlockPos(x + 72, y,
                z - 16);
        var clip = new BoundingBox(chunk.getMinBlockX(), UnderworldTerrain.MIN_Y, chunk.getMinBlockZ(),
                chunk.getMaxBlockX(), UnderworldTerrain.MAX_Y, chunk.getMaxBlockZ());
        var settings = new StructurePlaceSettings().setRotation(Rotation.CLOCKWISE_90)
                .setIgnoreEntities(true).setKnownShape(true).setBoundingBox(clip);
        template.placeInWorld(world, origin, origin, settings, RandomSource.create(0), 2);
    }
}
