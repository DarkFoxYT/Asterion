package net.krodark.asterion.worldgen;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.PhysicsChainEntity;
import net.krodark.asterion.game.ChainLiftContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.*;
import java.util.*;

/** Independent spans; authored mazesteel blocks are never converted or removed. */
public final class GeneratedPhysicsChains {
    private static final Map<ServerLevel,Set<Long>> PENDING=new WeakHashMap<>();
    private static final Map<ServerLevel,Set<Long>> SEEN=new WeakHashMap<>();
    private GeneratedPhysicsChains() { }
    public static void enqueue(ServerLevel level,LevelChunk chunk) {
        if(!level.getServer().isSameThread()){level.getServer().execute(()->enqueue(level,chunk));return;}
        if(level.dimension().equals(Asterion.ASTERION_LEVEL))PENDING.computeIfAbsent(level,k->new LinkedHashSet<>()).add(chunk.getPos().toLong());
    }
    public static void initialize() {
        ServerChunkEvents.CHUNK_LOAD.register((level,chunk)->enqueue(level,chunk));
        ServerChunkEvents.CHUNK_UNLOAD.register((level,chunk)->{var seen=SEEN.get(level);if(seen!=null)seen.remove(chunk.getPos().toLong());});
        ServerTickEvents.END_SERVER_TICK.register(server->{
            var budget=new net.krodark.asterion.physics.TickWorkBudget(4,2_000_000L);
            for(var level:new ArrayList<>(PENDING.keySet())) {
                if(level.getServer()!=server)continue;
                for(long packed:drainPending(PENDING.get(level),4)) {
                    if(!budget.tryStep()){PENDING.get(level).add(packed);continue;}
                    var chunk=level.getChunkSource().getChunkNow(net.minecraft.world.level.ChunkPos.getX(packed),net.minecraft.world.level.ChunkPos.getZ(packed));
                    if(chunk!=null && SEEN.computeIfAbsent(level,k->new HashSet<>()).add(packed))generate(level,chunk);
                }
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server->{PENDING.clear();SEEN.clear();});
    }
    static List<Long> drainPending(Set<Long> pending,int budget) {
        List<Long> batch=new ArrayList<>(budget);var iterator=pending.iterator();
        while(iterator.hasNext() && batch.size()<budget){batch.add(iterator.next());iterator.remove();}
        return batch;
    }
    private static boolean solid(ServerLevel level,BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null && level.getBlockState(pos).isCollisionShapeFullBlock(level,pos);
    }
    private static boolean open(ServerLevel level,Vec3 point) {
        BlockPos pos=BlockPos.containing(point);
        return level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null && level.getBlockState(pos).getCollisionShape(level,pos).isEmpty();
    }
    /** Trim masonry and its foliage together, using the same biome heights as generation. */
    private static void repairCenterDecorations(ServerLevel level, LevelChunk chunk) {
        ArenaSurfaceSeam.repair(level, chunk, false);
        var cp = chunk.getPos();
        if (Math.hypot(cp.getMiddleBlockX(), cp.getMiddleBlockZ()) > 126) return;
        long seed = MazeChunkGenerator.terrainSeed(level.getChunkSource().randomState());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = cp.getMinBlockX(); x <= cp.getMaxBlockX(); x++)
            for (int z = cp.getMinBlockZ(); z <= cp.getMaxBlockZ(); z++) {
                if (Math.hypot(x, z) > 110) continue;
                int floor = WorldGenerator.mazeFloorHeight(seed, x, z);
                cursor.set(x, floor, z);
                var ground = chunk.getBlockState(cursor);
                if (!ground.isAir() && !ground.hasBlockEntity()
                        && ground.getFluidState().isEmpty())
                    level.setBlock(cursor, net.krodark.asterion.Asterion.ANCIENT_STONE.defaultBlockState(), 18);
                var biome = WorldGenerator.mazeBiomeAt(seed, x, z, net.krodark.asterion.AsterionConfig.INSTANCE.cellSize);
                int ceiling = floor + WorldGenerator.mazeWallHeight(seed, x, z, floor, biome);
                int highest = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
                for (int y = ceiling + 1; y <= highest; y++) {
                    cursor.set(x, y, z);
                    var state = chunk.getBlockState(cursor);
                    if (state.isAir() || state.hasBlockEntity()) continue;
                    var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    String path = id.getPath();
                    if (id.getNamespace().equals("asterion") && (state.is(Asterion.ANCIENT_LEAVES)
                            || state.is(Asterion.TAINTED_LEAVES) || path.equals("maze_wall_core")
                            || path.startsWith("ancient_") && (path.contains("brick") || path.contains("plank") || path.contains("stone"))))
                        level.setBlock(cursor, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 18);
                }
            }
    }

    private static void generate(ServerLevel level,LevelChunk chunk) {
        repairCenterDecorations(level, chunk);
        var random=new Random(level.getSeed()^chunk.getPos().toLong()*0x9E3779B97F4A7C15L);
        if(random.nextInt(5)!=0 || Math.hypot(chunk.getPos().getMiddleBlockX(),chunk.getPos().getMiddleBlockZ())<110)return;
        int height=Math.max(8,net.krodark.asterion.AsterionConfig.INSTANCE.wallHeight);
        for(int attempt=0;attempt<4;attempt++) {
            int x=chunk.getPos().getMinBlockX()+random.nextInt(16),z=chunk.getPos().getMinBlockZ()+random.nextInt(16);
            int y=LabyrinthLevels.MAZE_FLOOR_Y+5+random.nextInt(Math.max(1,height-6));
            BlockPos origin=new BlockPos(x,y,z);if(!open(level,Vec3.atCenterOf(origin)))continue;
            int[][] directions={{1,0},{0,1},{1,1},{1,-1}};int[] d=directions[random.nextInt(directions.length)];
            int slope=random.nextBoolean()?0:random.nextInt(3)-1;BlockPos first=null,last=null;
            for(int side:new int[]{-1,1})for(int distance=1;distance<=18;distance++) {
                BlockPos pos=origin.offset(d[0]*distance*side,slope*distance*side/3,d[1]*distance*side);
                if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null)break;
                if(solid(level,pos)){if(side<0)first=pos;else last=pos;break;}
            }
            if(first==null || last==null)continue;
            Vec3 delta=Vec3.atCenterOf(last).subtract(Vec3.atCenterOf(first)).normalize();
            Vec3 start=Vec3.atCenterOf(first).add(delta.scale(.76)),end=Vec3.atCenterOf(last).subtract(delta.scale(.76));
            if(random.nextInt(4)==0){last=first.below(4+random.nextInt(7));if(!solid(level,last))continue;end=new Vec3(start.x,last.getY()+.5,start.z);}
            double distance=start.distanceTo(end);if(distance<3 || distance>40)continue;
            double slack=distance*.10+.5;
            var preview=new net.krodark.asterion.physics.SegmentedChain(start,end,Math.min(96,(int)(distance*4)),Math.ceil(distance+slack));
            preview.sag(start,end);boolean clear=true;
            for(Vec3 point:preview.rendered(1))if(!open(level,point) || point.y<LabyrinthLevels.MAZE_FLOOR_Y+3){clear=false;break;}
            if(!clear)continue;
            if(!level.getEntitiesOfClass(PhysicsChainEntity.class,new AABB(start,end).inflate(distance*.5+2),p->p.isSpan() && p.position().distanceToSqr(start)<.01).isEmpty())return;
            var chain=new PhysicsChainEntity(ChainLiftContent.PHYSICS_CHAIN,level);chain.configureSpan(first,start,last,end,slack);
            level.addFreshEntity(chain);return;
        }
    }
}
