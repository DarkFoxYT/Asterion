package net.krodark.asterion.dev.verification;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.RuneBlock;
import net.krodark.asterion.block.RuneBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

final class RuneOwnershipCheck {
    static void run(ServerLevel level) {
        int index = 0;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos root = new BlockPos(15 + index++ * 32, 160, 15);
            var block = Asterion.RUNE_BLOCKS[0];
            block.place(level, root, facing);
            var anchor = level.getBlockEntity(root);
            check(anchor instanceof RuneBlockEntity, "Rune anchor was not created");
            for (int column = 0; column < 3; column++) for (int row = 0; row < 3; row++) {
                BlockPos pos = RuneBlock.part(root, facing, column, row);
                boolean owner = pos.equals(root);
                check(level.getBlockState(pos).hasBlockEntity() == owner, "Wrong rune entity ownership");
                if (owner) continue;
                check(level.getBlockEntity(pos) == null, "Outer rune section created a duplicate entity");
                var chunk = level.getChunkAt(pos);
                for (String id : new String[]{"DUMMY", "asterion:rune"}) {
                    var legacy = new CompoundTag();
                    legacy.putString("id", id);
                    legacy.putInt("x", pos.getX()); legacy.putInt("y", pos.getY()); legacy.putInt("z", pos.getZ());
                    chunk.setBlockEntityNbt(legacy);
                    check(chunk.getBlockEntity(pos) == null, "Legacy outer-section data created an entity");
                    check(chunk.getBlockEntityNbt(pos) == null, "Invalid pending rune data was retained");
                }
            }
            RuneBlock.setPowered(level, root, facing, true);
            check(level.getBlockEntity(root) == anchor, "Cleanup replaced the rune anchor");
            check(level.getBlockState(root).getValue(RuneBlock.POWERED), "Rune no longer works");
        }
        Asterion.LOGGER.info("PASS: rune anchor ownership, all rotations/chunk seams, legacy placeholder cleanup and powered state");
    }
    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
