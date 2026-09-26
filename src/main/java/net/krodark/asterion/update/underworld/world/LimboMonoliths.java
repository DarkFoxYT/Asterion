package net.krodark.asterion.update.underworld.world;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;

/** Seeded sea landmarks, clipped per chunk so generation never writes into unloaded neighbours. */
public final class LimboMonoliths {
    private static final int SPACING = 192;
    private LimboMonoliths() { }
    private static final StructureProcessor WATERLOG = new StructureProcessor() {
        @SuppressWarnings("deprecation")
        @Override public StructureTemplate.StructureBlockInfo processBlock(
                net.minecraft.world.level.LevelReader world, BlockPos origin, BlockPos reference,
                StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo transformed,
                StructurePlaceSettings settings) {
            var state = transformed.state();
            if (state.isAir()) return null;
            if (state.hasProperty(BlockStateProperties.WATERLOGGED))
                state = state.setValue(BlockStateProperties.WATERLOGGED,
                        transformed.pos().getY() <= UnderworldTerrain.WATER_Y);
            return new StructureTemplate.StructureBlockInfo(transformed.pos(), state, transformed.nbt());
        }
        @Override protected StructureProcessorType<?> getType() { return StructureProcessorType.BLOCK_IGNORE; }
    };

    public static void place(WorldGenLevel world, ChunkPos chunk) {
        if (chunk.getMaxBlockZ() < 180) return;
        var level = world.getLevel();
        var template = level.getStructureManager().get(Asterion.id("limbo_monolith")).orElse(null);
        if (template == null) return;
        long terrainSeed = level.getChunkSource().randomState()
                .getOrCreateRandomFactory(Asterion.id("underworld_river")).at(0, 0, 0).nextLong();
        int cellX = Math.floorDiv(chunk.getMinBlockX(), SPACING);
        int cellZ = Math.floorDiv(chunk.getMinBlockZ(), SPACING);
        var clip = new BoundingBox(chunk.getMinBlockX(), UnderworldTerrain.MIN_Y, chunk.getMinBlockZ(),
                chunk.getMaxBlockX(), UnderworldTerrain.MAX_Y, chunk.getMaxBlockZ());
        for (int cx = cellX - 1; cx <= cellX + 1; cx++) for (int cz = cellZ - 1; cz <= cellZ + 1; cz++) {
            long seed = world.getSeed() ^ cx * 341873128712L ^ cz * 132897987541L ^ 0x4D4F4E4F4C495448L;
            var random = RandomSource.create(seed);
            if (random.nextFloat() > .7F) continue;
            int x = cx * SPACING + 48 + random.nextInt(96);
            int z = cz * SPACING + 48 + random.nextInt(96);
            if (z < 180 || Math.abs(x - UnderworldTerrain.riverCenter(z)) < 28) continue;
            // Cheap footprint rejection before querying terrain or allocating placement settings.
            if (x < chunk.getMinBlockX() - 40 || x > chunk.getMaxBlockX() + 40
                    || z < chunk.getMinBlockZ() - 40 || z > chunk.getMaxBlockZ() + 40) continue;
            int y = UnderworldTerrain.seaFloor(terrainSeed, x, z) - 2;
            if (y >= UnderworldTerrain.WATER_Y - 8 || y + template.getSize().getY() >= 145) continue;
            var origin = new BlockPos(x, y, z);
            var settings = new StructurePlaceSettings().setIgnoreEntities(true)
                    .setRotation(Rotation.values()[random.nextInt(4)]).setBoundingBox(clip).addProcessor(WATERLOG);
            if (!template.getBoundingBox(settings, origin).intersects(clip)) continue;
            template.placeInWorld(world, origin, origin, settings, RandomSource.create(seed), 2);
        }
    }
}
