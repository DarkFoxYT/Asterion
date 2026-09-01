package net.krodark.asterion.worldgen;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.mixin.StructureTemplateAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import java.util.*;

/** Places the author's full-size modules on a deterministic, connected grid. */
public final class AuthoredCatacombs {
    public static final int BASE_Y = 19, SIZE = 19, CONNECTOR_Y = BASE_Y + 5;
    public static final int ARENA_BASE_Y = 1, ARENA_FLOOR_Y = 6, ARENA_RADIUS = 61;
    private static final int ARENA_CHUNK_MARKER_Y = 0;
    public static final List<String> TEMPLATES = List.of("corridor_cross_01", "corridor_cross_02",
            "corridor_deadend_01", "corridor_deadend_02", "corridor_straight_01", "corridor_straight_02",
            "corridor_straight_03", "corridor_straight_04", "corridor_t_01", "corridor_t_02",
            "crossing_01", "crossing_02", "ossuary_01", "parkour", "puzzleroom");
    private AuthoredCatacombs() { }
    public static boolean enabled() { return true; }
    public record Module(String name, Rotation rotation, int exits, int blocked) { }
    private static int bit(Direction side) { return switch(side) {
        case NORTH -> 1; case EAST -> 2; case SOUTH -> 4; case WEST -> 8; default -> 0;
    }; }
    public static int exits(long seed, int tx, int tz) {
        int mask = 0;
        for (Direction side : Direction.Plane.HORIZONTAL)
            if (CatacombLayout.connected(seed, tx, tz, side)) mask |= bit(side);
        // The root also accepts the arena approach from the west.
        if (tx == CatacombLayout.ROOT_X && tz == CatacombLayout.ROOT_Z) mask |= 8;
        return mask;
    }
    public static Module module(long seed, int tx, int tz) {
        int exits = exits(seed, tx, tz), degree = Integer.bitCount(exits);
        long hash = (seed ^ 0xA0761D6478BD642FL) ^ tx * 0x632BE59BD9B4E019L ^ tz * 0x9E3779B97F4A7C15L;
        hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
        hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
        hash ^= hash >>> 31;
        String name;
        int nativeMask;
        if (degree == 1) {
            name = Math.floorMod(hash, 32) == 0 ? "puzzleroom" : ((hash >>> 8) & 1) == 0 ? "corridor_deadend_02" : "corridor_deadend_01";
            nativeMask = 1;
        } else if (degree == 2 && (exits == 5 || exits == 10)) {
            String[] choices = {"corridor_straight_01", "corridor_straight_02", "corridor_straight_03", "corridor_straight_04", "ossuary_01", "parkour"};
            int roll = Math.floorMod(hash, 64);
            name = roll < 4 ? "ossuary_01" : roll < 6 ? "parkour" : choices[(int)((hash >>> 8) & 3)]; nativeMask = 5;
        } else if (degree <= 3) {
            name = (hash & 1) == 0 ? "corridor_t_01" : "corridor_t_02";
            nativeMask = (hash & 1) == 0 ? 13 : 7;
        } else {
            name = (hash & 1) == 0 ? "corridor_cross_01" : "corridor_cross_02"; nativeMask = 15;
        }
        // Crossings are the authored surface-entry modules, not generic puzzle rooms.
        if ((tx == CatacombLayout.ROOT_X && tz == CatacombLayout.ROOT_Z)
                || degree >= 3 && Math.floorMod(hash, 24) == 0) {
            name = (hash & 1) == 0 ? "crossing_01" : "crossing_02"; nativeMask = 15;
        }
        Rotation[] rotations = Rotation.values();
        for (int i=0;i<rotations.length;i++) {
            Rotation rotation=rotations[(i+(int)((hash>>>16)&3))&3];
            int rotated = 0;
            for (Direction side : Direction.Plane.HORIZONTAL)
                if ((nativeMask & bit(side)) != 0) rotated |= bit(rotation.rotate(side));
            if ((rotated & exits) == exits) return new Module(name, rotation, exits, rotated & ~exits);
        }
        throw new IllegalStateException("No module fits exits " + exits);
    }
    public static void place(WorldGenLevel world, ChunkPos chunk) {
        ServerLevel level = world.getLevel();
        long seed = MazeChunkGenerator.terrainSeed(level.getChunkSource().randomState());
        BoundingBox clip = new BoundingBox(chunk.getMinBlockX(), BASE_Y, chunk.getMinBlockZ(),
                chunk.getMaxBlockX(), BASE_Y + 30, chunk.getMaxBlockZ());
        for (int tx = Math.floorDiv(chunk.getMinBlockX(), SIZE); tx <= Math.floorDiv(chunk.getMaxBlockX(), SIZE); tx++)
            for (int tz = Math.floorDiv(chunk.getMinBlockZ(), SIZE); tz <= Math.floorDiv(chunk.getMaxBlockZ(), SIZE); tz++) {
                if (!CatacombLayout.occupied(seed, tx, tz)) continue;
                Module module = module(seed, tx, tz);
                BlockPos origin = new BlockPos(tx * SIZE, BASE_Y, tz * SIZE);
                var template = level.getStructureManager().get(Asterion.id("catacombs/" + module.name()))
                        .orElseThrow(() -> new IllegalStateException("Missing authored crypt: " + module.name()));
                if (!template.getSize().equals(new net.minecraft.core.Vec3i(19, 31, 19)))
                    throw new IllegalStateException("Unexpected crypt size: " + module.name());
                // The last two layers of ordinary modules are an exterior roof cap/air.
                // Keep the existing maze floor and walls there; only crossings break the surface.
                BoundingBox roomClip = module.name().startsWith("crossing_") ? clip
                        : new BoundingBox(clip.minX(), clip.minY(), clip.minZ(), clip.maxX(), 46, clip.maxZ());
                var placement=placementSettings(roomClip,module.name().startsWith("crossing_"))
                        .setRotation(module.rotation()).setRotationPivot(new BlockPos(9,0,9));
                template.placeInWorld(world,origin,origin,placement,
                        RandomSource.create(seed^origin.asLong()),18);
                markTemplateRunes(world,template,origin,placement,roomClip);
                if (module.name().startsWith("crossing_")) surfaceApproach(world, chunk, origin, seed);
                // No corner asset was supplied: rotate a T and close only its unused connector.
                for (Direction side : Direction.Plane.HORIZONTAL) if ((module.blocked() & bit(side)) != 0) {
                    BlockPos door = origin.offset(9, 5, 9).relative(side, 9);
                    for (int across = -3; across <= 3; across++) for (int y = -1; y <= 6; y++) {
                        BlockPos pos = door.relative(side.getClockWise(), across).above(y);
                        if (clip.isInside(pos)) world.setBlock(pos, Asterion.ANCIENT_BRICKS.defaultBlockState(), 2);
                    }
                }
            }
    }
    private static void markTemplateRunes(WorldGenLevel world,StructureTemplate template,BlockPos origin,
                                          StructurePlaceSettings placement,BoundingBox clip) {
        for(var block:Asterion.RUNE_BLOCKS)
            for(var info:template.filterBlocks(origin,placement,block,true))
                if(clip.isInside(info.pos())&&net.krodark.asterion.block.RuneBlock.isRoot(info.state())
                        && world.getBlockEntity(info.pos()) instanceof net.krodark.asterion.block.RuneBlockEntity rune)
                    rune.setWorldGenerated(true);
    }
    public static StructurePlaceSettings settings(BoundingBox clip) {
        return new StructurePlaceSettings().setBoundingBox(clip).setIgnoreEntities(true)
                // Preserve saved circuitry and shape across chunk boundaries; do not flood
                // dry components with the destination's old fluid or notify every brick.
                .setKnownShape(true).setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK).addProcessor(JigsawReplacementProcessor.INSTANCE)
                .addProcessor(CLOSED_BARREL_DOORS);
    }
    // Runtime-only processor: upper template air must never erase the surrounding maze.
    private static final StructureProcessor CROSSING_SURFACE = new StructureProcessor() {
        @Override public StructureTemplate.StructureBlockInfo processBlock(
                net.minecraft.world.level.LevelReader world, BlockPos origin, BlockPos reference,
                StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo transformed,
                StructurePlaceSettings settings) {
            BlockPos p=original.pos();
            return p.getY()>=29 && Math.max(Math.abs(p.getX()-9),Math.abs(p.getZ()-9))>2 ? null : transformed;
        }
        @Override protected StructureProcessorType<?> getType() { return StructureProcessorType.BLOCK_IGNORE; }
    };
    private static final StructureProcessor CLOSED_BARREL_DOORS = new StructureProcessor() {
        @Override public StructureTemplate.StructureBlockInfo processBlock(
                net.minecraft.world.level.LevelReader world, BlockPos origin, BlockPos reference,
                StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo transformed,
                StructurePlaceSettings settings) {
            var state = transformed.state();
            // Multipart template NBT can target a section that another rotated piece
            // later occupies. Both entities only need runtime defaults, so strip their
            // saved tags and initialize surviving rune roots after the chunk is placed.
            if (state.getBlock() instanceof net.krodark.asterion.block.RuneBlock)
                return new StructureTemplate.StructureBlockInfo(transformed.pos(), state, null);
            if (!state.is(Asterion.BARREL_DOOR)) return transformed;
            // Open doors save a second 3x4 collision wing. Drop that moved copy and
            // retain the original plane below as the closed door.
            if (state.getValue(net.krodark.asterion.block.BarrelDoorBlock.WING)) return null;
            net.minecraft.world.level.block.state.BlockState closed = state
                    .setValue(net.krodark.asterion.block.BarrelDoorBlock.OPEN, false)
                    .setValue(net.krodark.asterion.block.BarrelDoorBlock.WING, false);
            return new StructureTemplate.StructureBlockInfo(transformed.pos(), closed, null);
        }
        @Override protected StructureProcessorType<?> getType() { return StructureProcessorType.BLOCK_IGNORE; }
    };
    private static final StructureProcessor REMOVE_ARENA_MARKERS = new StructureProcessor() {
        @Override public StructureTemplate.StructureBlockInfo processBlock(
                net.minecraft.world.level.LevelReader world, BlockPos origin, BlockPos reference,
                StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo transformed,
                StructurePlaceSettings settings) {
            return transformed.state().is(Blocks.CYAN_WOOL)
                    ? new StructureTemplate.StructureBlockInfo(transformed.pos(), Blocks.AIR.defaultBlockState(), null)
                    : transformed;
        }
        @Override protected StructureProcessorType<?> getType() { return StructureProcessorType.BLOCK_IGNORE; }
    };
    private static final StructureProcessor ARENA_NBT_ONLY = new StructureProcessor() {
        @Override public StructureTemplate.StructureBlockInfo processBlock(
                net.minecraft.world.level.LevelReader world, BlockPos origin, BlockPos reference,
                StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo transformed,
                StructurePlaceSettings settings) {
            return transformed.nbt()==null ? null : transformed;
        }
        @Override protected StructureProcessorType<?> getType() { return StructureProcessorType.BLOCK_IGNORE; }
    };
    public static StructurePlaceSettings placementSettings(BoundingBox clip, boolean crossing) {
        StructurePlaceSettings settings=settings(clip);
        return crossing ? settings.addProcessor(CROSSING_SURFACE) : settings;
    }
    public static void surfaceApproach(net.minecraft.world.level.ServerLevelAccessor world, ChunkPos chunk, BlockPos origin, long seed) {
        // Only visit the current chunk's intersection with the compact approach. No
        // neighboring chunk loads, topology searches, or whole-room clearance pass.
        int minX=Math.max(1,chunk.getMinBlockX()-origin.getX());
        int maxX=Math.min(17,chunk.getMaxBlockX()-origin.getX());
        int minZ=Math.max(1,chunk.getMinBlockZ()-origin.getZ());
        int maxZ=Math.min(17,chunk.getMaxBlockZ()-origin.getZ());
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        var brick=Asterion.ANCIENT_BRICKS.defaultBlockState();
        var air=Blocks.AIR.defaultBlockState();
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++) {
            int wx=origin.getX()+x,wz=origin.getZ()+z;
            int radius=Math.max(Math.abs(x-9),Math.abs(z-9));
            int surface=net.krodark.asterion.WorldGenerator.mazeFloorHeight(seed,wx,wz);
            if(radius<=2) {
                // A small entrance recess, not a cleared plaza. Keep the wall/ceiling
                // above two-block headroom and retain the authored winch and lever at 49.
                for(int y=50;y<=Math.max(50,surface+2);y++) {
                    pos.set(wx,y,wz);
                    if(!world.getBlockState(pos).isAir())world.setBlock(pos,air,18);
                }
                continue;
            }
            // Inspect the existing column before grading. Solid wall/decor columns
            // remain entirely untouched, including their foundations at the maze floor.
            pos.set(wx,surface+1,wz);
            if(!world.getBlockState(pos).getCollisionShape(world,pos).isEmpty())continue;
            pos.set(wx,surface+2,wz);
            if(!world.getBlockState(pos).getCollisionShape(world,pos).isEmpty())continue;
            int deck=Math.min(surface,48+radius-2);
            for(int y=48;y<=deck;y++) {
                pos.set(wx,y,wz);
                if(world.getBlockState(pos)!=brick)world.setBlock(pos,brick,18);
            }
            // Lower ground only; never clear the walls or decorations above it.
            for(int y=deck+1;y<=surface;y++) {
                pos.set(wx,y,wz);
                if(!world.getBlockState(pos).isAir())world.setBlock(pos,air,18);
            }
        }
    }
    public static void placeArena(ServerLevel level) {
        // Arena chunks install themselves when their FULL chunk callback runs. Forcing
        // even the center chunk from SERVER_STARTED can wait on the same chunk future.
    }
    public static void placeArenaChunk(ServerLevel level,LevelChunk chunk) {
        ChunkPos cp=chunk.getPos();
        boolean arena=cp.x()>=-4&&cp.x()<=3&&cp.z()>=-4&&cp.z()<=3;
        boolean approach=cp.getMaxBlockX()>=-1&&cp.getMinBlockX()<=CatacombLayout.ROOT_CENTER
                && cp.getMaxBlockZ()>=62&&cp.getMinBlockZ()<=CatacombLayout.ROOT_CENTER+1;
        if(!arena&&!approach)return;
        BlockPos marker=new BlockPos(cp.getMinBlockX(),ARENA_CHUNK_MARKER_Y,cp.getMinBlockZ());
        if(chunk.getBlockState(marker).is(Blocks.BARRIER))return;
        BoundingBox chunkBounds=new BoundingBox(cp.getMinBlockX(),ARENA_BASE_Y,cp.getMinBlockZ(),
                cp.getMaxBlockX(),ARENA_BASE_Y+47,cp.getMaxBlockZ());
        if(arena)for(int part=1;part<=9;part++) {
            BlockPos origin=new BlockPos(-61+((part-1)%3)*41,ARENA_BASE_Y,-61+((part-1)/3)*41);
            int maxX=origin.getX()+40,maxZ=origin.getZ()+40;
            if(maxX<chunkBounds.minX()||origin.getX()>chunkBounds.maxX()
                    ||maxZ<chunkBounds.minZ()||origin.getZ()>chunkBounds.maxZ())continue;
            var template=level.getStructureManager().get(Asterion.id("catacombs/arena_part"+part)).orElseThrow();
            if(!template.getSize().equals(new net.minecraft.core.Vec3i(41,48,41)))
                throw new IllegalStateException("Arena part "+part+" must be 41x48x41");
            BoundingBox clip=new BoundingBox(Math.max(origin.getX(),chunkBounds.minX()),ARENA_BASE_Y,
                    Math.max(origin.getZ(),chunkBounds.minZ()),Math.min(maxX,chunkBounds.maxX()),
                    ARENA_BASE_Y+47,Math.min(maxZ,chunkBounds.maxZ()));
            placeArenaPart(level,template,origin,clip,part);
        }
        placeArenaApproach(level,chunk);
        markGeneratedRunes(chunk,chunkBounds);
        configureArenaLoot(level,chunk);
        MinotaurArenaEntrances.buildForChunk(level,cp);
        chunk.setBlockState(new BlockPos(cp.getMinBlockX(),1,cp.getMinBlockZ()),
                Blocks.BEDROCK.defaultBlockState(),0);
        chunk.setBlockState(marker,Blocks.BARRIER.defaultBlockState(),0);
        MazeNbtStructures.markCopperClean(chunk);
        chunk.markUnsaved();
    }
    private static void placeArenaPart(ServerLevel level, StructureTemplate template, BlockPos origin,
                                       BoundingBox bounds, int part) {
        var palettes=((StructureTemplateAccessor)(Object)template).asterion$getPalettes();
        if(palettes.isEmpty())return;
        Map<Long,net.minecraft.world.level.chunk.LevelChunk> chunks=new HashMap<>();
        for(var info:palettes.getFirst().blocks()) {
            var state=info.state();
            if(state.isAir())continue;
            if(state.is(Blocks.STRUCTURE_BLOCK)||state.is(Blocks.STRUCTURE_VOID)
                    ||state.is(Blocks.JIGSAW)||state.is(Blocks.CYAN_WOOL))state=Blocks.AIR.defaultBlockState();
            if(state.is(Asterion.BARREL_DOOR)) {
                if(state.getValue(net.krodark.asterion.block.BarrelDoorBlock.WING))continue;
                state=state.setValue(net.krodark.asterion.block.BarrelDoorBlock.OPEN,false)
                        .setValue(net.krodark.asterion.block.BarrelDoorBlock.WING,false);
            }
            BlockPos pos=origin.offset(info.pos());
            if(!bounds.isInside(pos))continue;
            // Only the small set requiring lifecycle/light work uses Level#setBlock.
            // Plain masonry goes directly into its already-loaded chunk, avoiding
            // hundreds of thousands of repeated world lookups and neighbor checks.
            if(state.hasBlockEntity()||state.getLightEmission()>0||!state.getFluidState().isEmpty())
                level.setBlock(pos,state,18);
            else {
                long key=ChunkPos.pack(pos.getX()>>4,pos.getZ()>>4);
                var chunk=chunks.computeIfAbsent(key,ignored->level.getChunk(pos.getX()>>4,pos.getZ()>>4));
                chunk.setBlockState(pos,state,0);
            }
        }
        // Let vanilla deserialize the few authored block entities after the fast
        // block batch. Multipart rune/barrel tags were intentionally stripped above.
        template.placeInWorld(level,origin,origin,settings(bounds)
                .addProcessor(REMOVE_ARENA_MARKERS).addProcessor(ARENA_NBT_ONLY),
                RandomSource.create(part),18);
        for(var chunk:chunks.values())chunk.markUnsaved();
    }
    private static void configureArenaLoot(ServerLevel level,LevelChunk chunk) {
        var common=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                Asterion.id("chests/arena_vault_common"));
        var treasure=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                Asterion.id("chests/arena_vault_treasure"));
        for(var entry:chunk.getBlockEntities().entrySet()) {
                BlockPos pos=entry.getKey();
                if(pos.getY()<ARENA_BASE_Y||pos.getY()>48||Math.abs(pos.getX())>61||Math.abs(pos.getZ())>61)continue;
                if(entry.getValue() instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity container
                        && container.getLootTable()==null)
                    container.setLootTable(Math.floorMod(pos.asLong()^level.getSeed(),4)==0?treasure:common);
        }
    }
    public static void markGeneratedRunes(LevelChunk chunk,BoundingBox bounds) {
        for(var entry:chunk.getBlockEntities().entrySet())
            if(bounds.isInside(entry.getKey())
                    && entry.getValue() instanceof net.krodark.asterion.block.RuneBlockEntity rune
                    && !rune.isWorldGenerated())rune.setWorldGenerated(true);
    }
    private static void placeArenaApproach(ServerLevel level,LevelChunk chunk) {
        ChunkPos cp=chunk.getPos();
        for(int x=cp.getMinBlockX();x<=cp.getMaxBlockX();x++)for(int z=cp.getMinBlockZ();z<=cp.getMaxBlockZ();z++) {
            boolean vertical=Math.abs(x)<=1&&z>=62&&z<=CatacombLayout.ROOT_CENTER+1;
            boolean horizontal=x>=-1&&x<=CatacombLayout.ROOT_CENTER-9
                    && Math.abs(z-CatacombLayout.ROOT_CENTER)<=1;
            if((!vertical&&!horizontal)||(Math.abs(x)<=ARENA_RADIUS&&Math.abs(z)<=ARENA_RADIUS))continue;
            level.setBlock(new BlockPos(x,CONNECTOR_Y-1,z),Asterion.ANCIENT_BRICKS.defaultBlockState(),18);
            for(int y=0;y<4;y++)level.setBlock(new BlockPos(x,CONNECTOR_Y+y,z),Blocks.AIR.defaultBlockState(),18);
        }
    }
}
