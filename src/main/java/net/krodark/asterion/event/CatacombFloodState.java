package net.krodark.asterion.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.worldgen.CatacombLayout;
import net.krodark.asterion.fluid.HeavyWater;
import net.krodark.asterion.fluid.HeavyWaterlogging;
import net.krodark.asterion.fluid.TidalWaterBlock;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** A shared catacomb flash flood, filling dry galleries beneath the raised maze. */
public final class CatacombFloodState extends SavedData {
    public static final int FLOOD_TOP_Y = net.krodark.asterion.worldgen.LabyrinthLevels.MAZE_FLOOR_Y - 6;
    public static final int MAX_RISE = (FLOOD_TOP_Y - CatacombLayout.WATER_Y) * 8;
    public static final int RISE_PER_STEP = 8;
    public static final int STEP_TICKS = 5;
    public static final int RISE_DURATION_TICKS = MAX_RISE / RISE_PER_STEP * STEP_TICKS;
    public static final Codec<CatacombFloodState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("active", false).forGetter(s -> s.active),
            Codec.intRange(0, MAX_RISE).optionalFieldOf("rise", 0).forGetter(s -> s.rise),
            Codec.LONG.optionalFieldOf("ends_at", 0L).forGetter(s -> s.endsAt),
            Codec.LONG.optionalFieldOf("next_step", 0L).forGetter(s -> s.nextStep)
    ).apply(instance, CatacombFloodState::new));
    private static final SavedDataType<CatacombFloodState> TYPE = new SavedDataType<>(
            Asterion.id("catacomb_flood"), CatacombFloodState::new, CODEC, null);
    private static final Map<ServerLevel, LoadedTide> LOADED = new WeakHashMap<>();
    private boolean active;
    private int rise;
    private long endsAt, nextStep;
    private CatacombFloodState() { }
    private CatacombFloodState(boolean active, int rise, long endsAt, long nextStep) {
        this.active = active; this.rise = rise; this.endsAt = endsAt; this.nextStep = nextStep;
    }

    public static CatacombFloodState get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    public int riseSteps() { return rise; }
    public double surfaceHeight() { return CatacombLayout.WATER_Y + 1 + rise / 8.0; }

    public static boolean isFlooding(ServerLevel level, BlockPos pos) {
        if (!level.dimension().equals(Asterion.ASTERION_LEVEL)) return false;
        boolean catacombs = CatacombLayout.contains(pos);
        return catacombs && !isArenaColumn(pos.getX(), pos.getZ()) && get(level).active;
    }

    public static void setActive(ServerLevel level, boolean active) {
        if (active) start(level, 20 * 180);
        else { var state = get(level); state.active = false; state.endsAt = 0; state.setDirty(); }
    }

    public static void start(ServerLevel level, int durationTicks) {
        var state = get(level);
        state.active = true;
        state.endsAt = level.getGameTime() + durationTicks;
        state.nextStep = level.getGameTime() + STEP_TICKS;
        var loaded=LOADED.computeIfAbsent(level,ignored->new LoadedTide());
        loaded.pending.addAll(loaded.chunks);
        state.setDirty();
    }

    public static void ensureRemainingTicks(ServerLevel level, int ticks) {
        var state = get(level);
        long until = level.getGameTime() + ticks;
        if (state.active && until > state.endsAt) { state.endsAt = until; state.setDirty(); }
    }

    public static void onChunkLoad(ServerLevel level, LevelChunk chunk, boolean newlyGenerated) {
        if (!level.dimension().equals(Asterion.ASTERION_LEVEL)) return;
        var loaded = LOADED.computeIfAbsent(level, ignored -> new LoadedTide());
        loaded.chunks.add(chunk.getPos().pack());
        loaded.pending.add(chunk.getPos().pack());
    }
    public static void onChunkUnload(ServerLevel level, LevelChunk chunk) {
        var loaded = LOADED.get(level);
        if (loaded != null) {
            long packed=chunk.getPos().pack();
            loaded.chunks.remove(packed);loaded.pending.remove(packed);loaded.appliedRise.remove(packed);
        }
    }
    public static void clear() { LOADED.clear(); }

    public static void tick(ServerLevel level) {
        if (!level.dimension().equals(Asterion.ASTERION_LEVEL)) return;
        var state = get(level);
        long now = level.getGameTime();
        if (state.active && (now >= state.endsAt || net.krodark.asterion.WorldGenerator.isBossEncounterActive(level)))
            setActive(level, false);
        var loaded = LOADED.computeIfAbsent(level, ignored -> new LoadedTide());
        int target = state.active ? MAX_RISE : 0;
        // Advance quickly; lagging/background chunks jump directly to the newest shared height.
        if (state.rise != target && now >= state.nextStep) {
            state.rise += Integer.signum(target-state.rise)
                    * Math.min(RISE_PER_STEP,Math.abs(target-state.rise));
            state.nextStep = now + STEP_TICKS;
            loaded.pending.addAll(loaded.chunks);
            state.setDirty();
        }
        int budget=0;
        // Keep the flood visibly synchronized around players before background chunks.
        for(var player:level.players())for(int dx=-1;dx<=1 && budget<8;dx++)for(int dz=-1;dz<=1 && budget<8;dz++) {
            long packed=ChunkPos.pack(player.chunkPosition().x()+dx,player.chunkPosition().z()+dz);
            if(loaded.pending.remove(packed) && reconcileLoaded(level,loaded,packed,state.rise))budget++;
        }
        while(budget++<10 && !loaded.pending.isEmpty()) {
            long packed=loaded.pending.removeFirst();
            reconcileLoaded(level,loaded,packed,state.rise);
        }
    }

    private static boolean reconcileLoaded(ServerLevel level,LoadedTide loaded,long packed,int rise) {
        LevelChunk chunk=level.getChunkSource().getChunkNow(ChunkPos.getX(packed),ChunkPos.getZ(packed));
        if(chunk==null)return false;
        int previous=loaded.appliedRise.getOrDefault(packed,-1);
        reconcile(level,chunk,rise,previous);
        loaded.appliedRise.put(packed,rise);
        return true;
    }

    /** Only replace liquid or clear air over an existing basin; never erase gates, props or player blocks. */
    public static void reconcile(ServerLevel level, LevelChunk chunk, int riseSteps) {
        reconcile(level,chunk,riseSteps,-1);
    }

    private static void reconcile(ServerLevel level,LevelChunk chunk,int riseSteps,int previousRise) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int surfaceEighths = (CatacombLayout.WATER_Y + 1) * 8 + Math.clamp(riseSteps, 0, MAX_RISE);
        int fromY=CatacombLayout.WATER_Y;
        int toY=FLOOD_TOP_Y;
        if(previousRise>=0 && Math.abs(riseSteps-previousRise)<=RISE_PER_STEP) {
            fromY=Math.max(CatacombLayout.WATER_Y,
                    CatacombLayout.WATER_Y+Math.min(previousRise,riseSteps)/8);
            toY=Math.min(FLOOD_TOP_Y,
                    CatacombLayout.WATER_Y+Math.max(previousRise,riseSteps)/8+1);
        }
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++)
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                BlockState base = chunk.getBlockState(cursor.set(x, CatacombLayout.WATER_Y, z));
                // Flood every open gallery column, including rooms which started completely dry.
                boolean basin = !isArenaColumn(x, z) && CatacombLayout.contains(cursor)
                        && (base.isAir() || base.getFluidState().is(net.minecraft.tags.FluidTags.WATER)
                        || HeavyWaterlogging.isTidal(base)
                        || HeavyWaterlogging.canFill(null,level,cursor,base));
                for (int y = fromY; y <= toY; y++) {
                    cursor.set(x, y, z);
                    BlockState old = chunk.getBlockState(cursor);
                    int amount = Math.clamp(surfaceEighths - y * 8, 0, 8);
                    if (HeavyWaterlogging.isTidal(old)) {
                        BlockState next = amount == 0 || riseSteps==0 ? HeavyWaterlogging.dry(old)
                                : HeavyWaterlogging.withFluid(old, HeavyWater.FLUID.getFlowing(amount, false));
                        if (next != old) level.setBlock(cursor, next, 2);
                        continue;
                    }
                    if (basin && riseSteps>0 && amount > 0 && HeavyWaterlogging.canFill(null, level, cursor, old)) {
                        HeavyWaterlogging.fill(level, cursor, old, HeavyWater.FLUID.getFlowing(amount, false));
                        continue;
                    }
                    boolean tidal = old.is(HeavyWater.BLOCK);
                    boolean originalWater = y == CatacombLayout.WATER_Y
                            && (old.is(Blocks.WATER) || old.is(HeavyWater.WATER_BLOCK));
                    boolean fill = basin && riseSteps>0 && old.isAir();
                    if (!tidal && !originalWater && !fill) continue;
                    BlockState next = originalWater ? HeavyWater.WATER_BLOCK.defaultBlockState()
                            : amount == 0 || riseSteps==0 ? Blocks.AIR.defaultBlockState()
                            : HeavyWater.BLOCK.defaultBlockState().setValue(TidalWaterBlock.LEVEL, amount);
                    if (old != next) level.setBlock(cursor, next, 2);
                }
            }
    }
    private static boolean isArenaColumn(int x, int z) {
        return Math.abs((long)x) <= net.krodark.asterion.worldgen.AuthoredCatacombs.ARENA_RADIUS
                && Math.abs((long)z) <= net.krodark.asterion.worldgen.AuthoredCatacombs.ARENA_RADIUS;
    }
    private static final class LoadedTide {
        final LinkedHashSet<Long> chunks = new LinkedHashSet<>(), pending = new LinkedHashSet<>();
        final Map<Long,Integer> appliedRise=new HashMap<>();
    }

    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> {
            var flood = Commands.literal("flooding");
            for (boolean active : new boolean[]{true, false})
                flood.then(Commands.literal(active ? "start" : "stop").executes(command -> {
                    ServerLevel maze = command.getSource().getServer().getLevel(Asterion.ASTERION_LEVEL);
                    if (maze == null) return 0;
                    if (active) {
                        if (!DeadSunEventSystem.trigger(maze, DeadSunEventSystem.FLOOD)) {
                            command.getSource().sendFailure(Component.literal("Flooding is disabled during the boss encounter."));
                            return 0;
                        }
                    } else {
                        if (DeadSunEventSystem.isActive(maze, DeadSunEventSystem.FLOOD)) DeadSunEventSystem.stop(maze);
                        setActive(maze, false);
                    }
                    command.getSource().sendSuccess(() -> Component.literal(active
                            ? "Catacomb flash flood rising rapidly through block Y=" + FLOOD_TOP_Y + "."
                            : "Catacomb tide receding to its normal level."), true);
                    return 1;
                }));
            dispatcher.register(Commands.literal("asterion")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .then(Commands.literal("catacombs").then(flood)));
        });
    }
}
