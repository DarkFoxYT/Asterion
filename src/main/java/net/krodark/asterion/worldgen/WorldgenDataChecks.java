package net.krodark.asterion.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

final class WorldgenDataChecks {
    private WorldgenDataChecks() { }

    static void run(ServerLevel level, BlockPos sample) {
        checkBounds();
        checkLandmarks(level);
        var chunk = level.getChunkAt(sample);
        BlockPos base = new BlockPos(chunk.getPos().getMinBlockX() + 2, 120,
                chunk.getPos().getMinBlockZ() + 2);
        BlockPos barrel = base.east();
        BlockPos chest = base.east(2);
        BlockPos orphan = base.east(3);
        BlockPos savedChest = base.east(4);
        for (int i = 0; i < 5; i++) level.setBlock(base.east(i), Blocks.AIR.defaultBlockState(), 2);

        // Reproduce deferred worldgen entities without first creating live instances.
        var section = chunk.getSection(chunk.getSectionIndex(base.getY()));
        section.setBlockState(barrel.getX() & 15, barrel.getY() & 15, barrel.getZ() & 15,
                Blocks.BARREL.defaultBlockState(), false);
        section.setBlockState(chest.getX() & 15, chest.getY() & 15, chest.getZ() & 15,
                Blocks.CHEST.defaultBlockState(), false);
        section.setBlockState(savedChest.getX() & 15, savedChest.getY() & 15, savedChest.getZ() & 15,
                Blocks.CHEST.defaultBlockState(), false);
        chunk.setBlockEntityNbt(dummy(barrel));
        chunk.setBlockEntityNbt(dummy(chest));
        chunk.setBlockEntityNbt(dummy(orphan));
        var inventory = new ChestBlockEntity(savedChest, Blocks.CHEST.defaultBlockState());
        inventory.setItem(0, new ItemStack(Items.DIAMOND, 3));
        chunk.setBlockEntityNbt(inventory.saveWithFullMetadata(level.registryAccess()));
        var pois = level.getPoiManager();
        if (pois.getType(barrel).isPresent()) pois.remove(barrel);

        MazeChunkData.prepare(level, chunk);
        check(chunk.getBlockEntityNbt(orphan) == null, "Orphan DUMMY tag survived preparation");
        check(chunk.getBlockEntityNbt(chest) == null && chunk.getBlockEntity(chest) != null,
                "Valid DUMMY chest was not loaded before replacement");
        check(pois.existsAtPosition(PoiTypes.FISHERMAN, barrel), "Generated barrel has no POI");
        var loaded = (ChestBlockEntity) chunk.getBlockEntity(savedChest);
        check(loaded.getItem(0).is(Items.DIAMOND) && loaded.getItem(0).getCount() == 3,
                "Loading deferred chest data lost its inventory");
        MazeChunkData.prepare(level, chunk);
        check(chunk.getBlockEntity(savedChest) == loaded, "Preparation replaced a live chest");

        level.setBlock(chest, Blocks.COBBLESTONE.defaultBlockState(), 2);
        level.setBlock(barrel, Blocks.AIR.defaultBlockState(), 2);
        chunk.postProcessGeneration(level);
        check(chunk.getBlockEntity(chest) == null, "Replaced chest left a block entity behind");
        check(!OvergrowthFeatureSupport.canWrite(level, base.atY(level.getMaxY() + 1)),
                "Feature allowed a write above build height");
    }

    private static void checkLandmarks(ServerLevel level) {
        for (String name : AuthoredCatacombs.TEMPLATES)
            check(level.getStructureManager().get(net.krodark.asterion.Asterion.id("catacombs/" + name)).isPresent(), "Missing authored crypt " + name);
        for (int part = 1; part <= 9; part++)
            check(level.getStructureManager().get(net.krodark.asterion.Asterion.id("catacombs/arena_part" + part)).isPresent(), "Missing arena part " + part);
    }
    private static CompoundTag dummy(BlockPos pos) {
        var tag = new CompoundTag();
        tag.putString("id", "DUMMY");
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        return tag;
    }

    private static void checkBounds() {
        for (int centerX : new int[] {-3, -1, 0, 2}) {
            var center = new ChunkPos(centerX, -1);
            int minX = (centerX - 1) * 16;
            int maxX = (centerX + 2) * 16 - 1;
            check(OvergrowthFeatureSupport.withinWriteRadius(center, new BlockPos(minX, 50, -32), 1),
                    "Writable corner was rejected");
            check(OvergrowthFeatureSupport.withinWriteRadius(center, new BlockPos(maxX, 50, 15), 1),
                    "Writable corner was rejected");
            for (var outside : new BlockPos[] {new BlockPos(minX - 1, 50, -16),
                    new BlockPos(maxX + 1, 50, -16), new BlockPos(centerX * 16, 50, -33),
                    new BlockPos(centerX * 16, 50, 16)}) {
                check(!OvergrowthFeatureSupport.withinWriteRadius(center, outside, 1),
                        "Feature allowed a far-chunk write: " + outside);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
