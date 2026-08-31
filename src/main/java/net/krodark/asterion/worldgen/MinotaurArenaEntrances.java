package net.krodark.asterion.worldgen;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.MinotaurDoorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.krodark.asterion.block.DirectionalGateBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;

/** One player stairway faces an enclosed Minotaur staging room across the arena. */
public final class MinotaurArenaEntrances {
    public static final int DOOR_RADIUS = 34, FLOOR_Y = 36;
    public static final Direction PLAYER_ENTRANCE = Direction.SOUTH, BOSS_ENTRANCE = Direction.NORTH;
    public static final java.util.List<Direction> DOORS = java.util.List.of(PLAYER_ENTRANCE, BOSS_ENTRANCE);
    public static final int BOSS_ROOM_BACK = 44;
    private MinotaurArenaEntrances() { }
    public static BlockPos door(Direction outward) {
        return new BlockPos(outward.getStepX() * DOOR_RADIUS, FLOOR_Y + 1, outward.getStepZ() * DOOR_RADIUS);
    }
    public static BlockPos gate(Direction outward) { return door(outward).relative(outward.getOpposite(), 1); }
    public static int gateHeight() { return Math.max(7, (int)Math.ceil(2.75 * net.krodark.asterion.AsterionConfig.INSTANCE.minotaurScale) + 1); }
    public static AABB gateBounds(Direction facing) { return panelBounds(gate(facing), facing, gateHeight()); }
    public static AABB doorBounds(Direction facing) { return panelBounds(door(facing), facing, 5); }
    private static AABB panelBounds(BlockPos root, Direction facing, int height) {
        return AABB.encapsulatingFullBlocks(root.relative(facing.getClockWise(), -3),
                root.relative(facing.getClockWise(), 3).above(height - 1));
    }
    public static boolean isGate(BlockPos pos) {
        for (Direction facing : DOORS) if (gateBounds(facing).contains(Vec3.atCenterOf(pos))) return true;
        return false;
    }
    public static Direction corridorAt(Vec3 position) {
        if (position.y < FLOOR_Y + .5 || position.y > FLOOR_Y + 8) return null;
        for (Direction facing : java.util.List.of(PLAYER_ENTRANCE)) {
            Vec3 offset = position.subtract(Vec3.atBottomCenterOf(door(facing)));
            double depth = offset.dot(facing.getUnitVec3());
            if (depth >= -4 && depth <= 8 && Math.abs(offset.dot(facing.getClockWise().getUnitVec3())) < 3.5) return facing;
        }
        return null;
    }
    public static void setGates(ServerLevel level, int closedRows, Direction except) {
        for (Direction facing : DOORS) if (facing != except) setGate(level, facing, closedRows);
    }
    public static void setGate(ServerLevel level, Direction facing, int closedRows) {
        var state = Asterion.MAZESTEEL_GATE.defaultBlockState().setValue(DirectionalGateBlock.FACE, AttachFace.FLOOR)
                .setValue(DirectionalGateBlock.FACING, facing);
        for (int row = 0; row < gateHeight(); row++) for (int side = -3; side <= 3; side++) {
            BlockPos pos = gate(facing).relative(facing.getClockWise(), side).above(row);
            var next = state.setValue(DirectionalGateBlock.OPEN, row < gateHeight() - closedRows);
            if (!level.getBlockState(pos).equals(next)) level.setBlock(pos, next, 2);
        }
        // A raised portcullis remains visible above the passage on both entrances.
        for (int side = -3; side <= 3; side++)
            level.setBlock(gate(facing).relative(facing.getClockWise(), side).above(gateHeight()),
                    state.setValue(DirectionalGateBlock.OPEN, false), 2);
    }
    public static boolean entranceLane(int x, int z) { return Math.abs(x) <= 4; }

    /** Keep the complete five-block pillar bases away from the two nine-block entry lanes. */
    public static java.util.List<BlockPos> pillarCenters(int count) {
        var centers = new java.util.ArrayList<BlockPos>(count);
        for (int quadrant = 0; quadrant < 4; quadrant++) {
            int inQuadrant = count / 4 + (quadrant < count % 4 ? 1 : 0);
            for (int slot = 0; slot < inQuadrant; slot++) {
                double angle = quadrant * Math.PI / 2 + .30
                        + (slot + .5) / inQuadrant * (Math.PI / 2 - .60);
                centers.add(new BlockPos((int)Math.round(Math.cos(angle) * 26), FLOOR_Y,
                        (int)Math.round(Math.sin(angle) * 26)));
            }
        }
        return centers;
    }

    /** Detect entry through the actual doorway plane, never from the player's later location. */
    public static Direction crossedEntrance(Vec3 previous, Vec3 current) {
        if (previous == null || previous.distanceToSqr(current) > 16 * 16) return null;
        for (Direction facing : java.util.List.of(PLAYER_ENTRANCE)) {
            Vec3 center = Vec3.atBottomCenterOf(door(facing));
            Vec3 outward = facing.getUnitVec3();
            double before = previous.subtract(center).dot(outward), after = current.subtract(center).dot(outward);
            if (before < 0 || after >= 0) continue;
            Vec3 crossing = previous.lerp(current, before / (before - after)).subtract(center);
            if (Math.abs(crossing.dot(facing.getClockWise().getUnitVec3())) <= 3.5
                    && crossing.y >= -.25 && crossing.y < 4.5) return facing;
        }
        return null;
    }
    public static int floorAt(int radius) { return FLOOR_Y + Math.clamp(radius - 42, 0, 12); }
    public static void build(ServerLevel level) {
        int heightLimit = Math.max(8, (int)Math.ceil(2.75 * net.krodark.asterion.AsterionConfig.INSTANCE.minotaurScale) + 2);
        // Fill obsolete corridors too, so upgrading a four-door save cannot leave side entrances.
        for (Direction removed : java.util.List.of(Direction.EAST, Direction.WEST, BOSS_ENTRANCE)) {
            int start = removed == BOSS_ENTRANCE ? BOSS_ROOM_BACK : DOOR_RADIUS + 1;
            for (int radius = start; radius <= 56; radius++) for (int side = -4; side <= 4; side++)
                for (int y = FLOOR_Y; y <= FLOOR_Y + 29; y++)
                    level.setBlock(new BlockPos(removed.getStepX() * radius, y, removed.getStepZ() * radius)
                            .relative(removed.getClockWise(), side), Asterion.ANCIENT_BRICKS.defaultBlockState(), 2);
        }
        for (Direction outward : DOORS) {
            Direction across = outward.getClockWise();
            for (int radius = DOOR_RADIUS - 1; radius <= (outward == BOSS_ENTRANCE ? BOSS_ROOM_BACK : 56); radius++) {
                int floor = outward == BOSS_ENTRANCE ? FLOOR_Y : floorAt(radius);
                BlockPos center = new BlockPos(outward.getStepX() * radius, floor, outward.getStepZ() * radius);
                for (int side = -4; side <= 4; side++) {
                    BlockPos base = center.relative(across, side);
                    level.setBlock(base, Asterion.ANCIENT_STONE.defaultBlockState(), 2);
                    for (int height = 1; height <= heightLimit + 1; height++) {
                        boolean frame = Math.abs(side) == 4 || height >= heightLimit
                                || outward == BOSS_ENTRANCE && radius == BOSS_ROOM_BACK
                                || radius == DOOR_RADIUS && height > 5;
                        level.setBlock(base.above(height), frame ? Asterion.ANCIENT_BRICKS.defaultBlockState()
                                : Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            MinotaurDoorBlock.place(level, door(outward), outward);
        }
        buildCatacombApproach(level);
        setGates(level, 0, null);
    }

    private static void buildCatacombApproach(ServerLevel level) {
        // Both routes meet OUTSIDE the single keyed player door: surface stair and undercroft stair.
        // The lower landing joins the catacomb gallery at x=32, without another arena opening.
        for (int x = 3; x <= 34; x++) {
            int floor = Math.max(CatacombLayout.floor(level.getSeed(), 32, 42), FLOOR_Y + 3 - x);
            for (int z = 40; z <= 44; z++) {
                level.setBlock(new BlockPos(x, floor, z), Asterion.ANCIENT_STONE.defaultBlockState(), 2);
                for (int y = 1; y <= 5; y++)
                    level.setBlock(new BlockPos(x, floor + y, z), y == 5 || z == 40 || z == 44
                            ? Asterion.ANCIENT_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    public static void breakLintel(ServerLevel level, Direction facing, double bossHeight) {
        int height = (int)Math.ceil(bossHeight) + 1;
        BlockPos root = door(facing);
        for (int depth = -1; depth <= 1; depth++) for (int side = -3; side <= 3; side++)
            for (int y = 5; y < height; y++) {
                BlockPos pos = root.relative(facing, depth).relative(facing.getClockWise(), side).above(y);
                if (level.getBlockState(pos).is(Asterion.ANCIENT_BRICKS))
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
    }
}
