package net.krodark.asterion.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashSet;
import java.util.Map;
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
    public static final int RISE_PER_STEP = 1;
    public static final int STEP_TICKS = 20;
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
        state.endsAt = level.getGameTime() + Math.max(durationTicks, RISE_DURATION_TICKS + 800);
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
            loaded.chunks.remove(packed);loaded.pending.remove(packed);
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
        // Rise one eighth of a block per second; background chunks catch up to the shared tide.
        if (state.rise != target && now >= state.nextStep) {
            state.rise += Integer.signum(target-state.rise)
                    * Math.min(RISE_PER_STEP,Math.abs(target-state.rise));
            state.nextStep = now + STEP_TICKS;
            loaded.pending.addAll(loaded.chunks);
            state.setDirty();
        }
        // Revisit the flooded volume even at a steady tide: opened doors and new chunks can admit water.
        if (state.rise > 0 && now % 100 == 0) loaded.pending.addAll(loaded.chunks);
        if (now % 4 == 0) spread(level, state.rise);
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
        reconcile(level,chunk,rise);
        return true;
    }

    /** Reconcile existing water and seed the advancing edge; dry rooms fill through their openings. */
    public static void reconcile(ServerLevel level, LevelChunk chunk, int riseSteps) {
        var loaded = LOADED.computeIfAbsent(level, ignored -> new LoadedTide());
        int surface = (CatacombLayout.WATER_Y + 1) * 8 + Math.clamp(riseSteps, 0, MAX_RISE);
        for (BlockPos pos : BlockPos.betweenClosed(chunk.getPos().getMinBlockX(), MIN_FLOOD_Y,
                chunk.getPos().getMinBlockZ(), chunk.getPos().getMaxBlockX(), FLOOD_TOP_Y, chunk.getPos().getMaxBlockZ())) {
            if (!inFloodArea(pos)) continue;
            BlockState old = chunk.getBlockState(pos);
            int amount = riseSteps == 0 ? 0 : Math.clamp(surface - pos.getY() * 8, 0, 8);
            if (HeavyWaterlogging.isTidal(old)) {
                BlockState next = amount == 0 ? HeavyWaterlogging.dry(old)
                        : HeavyWaterlogging.withFluid(old, HeavyWater.FLUID.getFlowing(amount, false));
                if (next != old) level.setBlock(pos, next, 2);
            } else if (old.is(HeavyWater.BLOCK)) {
                BlockState next = amount == 0 ? Blocks.AIR.defaultBlockState()
                        : old.setValue(TidalWaterBlock.LEVEL, amount);
                if (next != old) level.setBlock(pos, next, 2);
            } else if (pos.getY() == CatacombLayout.WATER_Y && old.is(Blocks.WATER)) {
                level.setBlock(pos, HeavyWater.WATER_BLOCK.defaultBlockState(), 2);
            } else if (amount > 0 && pos.getY() == CatacombLayout.WATER_Y && fillable(level, pos, old)) {
                // Distributed floor-level inlets also let completely dry galleries join the event.
                fill(level, pos, old, amount);
            }
            if (amount > 0 && wet(chunk.getBlockState(pos))) enqueueNeighbours(level, loaded, pos, surface);
            else if (amount > 0 && fillable(level, pos, chunk.getBlockState(pos)) && hasWetNeighbour(level, pos))
                loaded.frontier.add(pos.asLong());
        }
    }

    private static final int MIN_FLOOD_Y = net.krodark.asterion.worldgen.AuthoredCatacombs.BASE_Y;
    private static final int SPREAD_BUDGET = 1024;

    /** One bounded wave; never loads a neighbouring chunk or replaces a solid block. */
    public static void spread(ServerLevel level, int riseSteps) {
        var loaded = LOADED.computeIfAbsent(level, ignored -> new LoadedTide());
        if (riseSteps <= 0) { loaded.frontier.clear(); return; }
        int surface = (CatacombLayout.WATER_Y + 1) * 8 + Math.clamp(riseSteps, 0, MAX_RISE);
        // Newly discovered neighbours wait for the next wave, so the edge visibly travels.
        int count = Math.min(SPREAD_BUDGET, loaded.frontier.size());
        for (int i = 0; i < count; i++) {
            BlockPos pos = BlockPos.of(loaded.frontier.removeFirst());
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null || !inFloodArea(pos)) continue;
            int amount = Math.clamp(surface - pos.getY() * 8, 0, 8);
            BlockState old = chunk.getBlockState(pos);
            if (amount == 0 || !fillable(level, pos, old) || !hasWetNeighbour(level, pos)) continue;
            fill(level, pos, old, amount);
            enqueueNeighbours(level, loaded, pos, surface);
        }
    }

    private static boolean inFloodArea(BlockPos pos) {
        return pos.getY() >= MIN_FLOOD_Y && pos.getY() <= FLOOD_TOP_Y
                && !isArenaColumn(pos.getX(), pos.getZ()) && CatacombLayout.contains(pos);
    }

    private static boolean wet(BlockState state) {
        return state.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
    }

    private static boolean fillable(ServerLevel level, BlockPos pos, BlockState state) {
        return state.isAir() || HeavyWaterlogging.canFill(null, level, pos, state);
    }

    private static void fill(ServerLevel level, BlockPos pos, BlockState old, int amount) {
        if (old.isAir()) level.setBlock(pos, HeavyWater.BLOCK.defaultBlockState().setValue(TidalWaterBlock.LEVEL, amount), 2);
        else HeavyWaterlogging.fill(level, pos, old, HeavyWater.FLUID.getFlowing(amount, false));
    }

    private static boolean hasWetNeighbour(ServerLevel level, BlockPos pos) {
        for (var direction : net.minecraft.core.Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            if (!inFloodArea(neighbour)) continue;
            var chunk = level.getChunkSource().getChunkNow(neighbour.getX() >> 4, neighbour.getZ() >> 4);
            if (chunk != null && wet(chunk.getBlockState(neighbour))) return true;
        }
        return false;
    }

    private static void enqueueNeighbours(ServerLevel level, LoadedTide loaded, BlockPos pos, int surface) {
        for (var direction : net.minecraft.core.Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            if (!inFloodArea(neighbour) || neighbour.getY() * 8 >= surface) continue;
            var chunk = level.getChunkSource().getChunkNow(neighbour.getX() >> 4, neighbour.getZ() >> 4);
            if (chunk != null && fillable(level, neighbour, chunk.getBlockState(neighbour)))
                loaded.frontier.add(neighbour.asLong());
        }
    }
    private static boolean isArenaColumn(int x, int z) {
        return Math.abs((long)x) <= net.krodark.asterion.worldgen.AuthoredCatacombs.ARENA_RADIUS
                && Math.abs((long)z) <= net.krodark.asterion.worldgen.AuthoredCatacombs.ARENA_RADIUS;
    }
    private static final class LoadedTide {
        final LinkedHashSet<Long> chunks = new LinkedHashSet<>(), pending = new LinkedHashSet<>();
        final LinkedHashSet<Long> frontier = new LinkedHashSet<>();
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
                            ? "Catacomb tide rising slowly through block Y=" + FLOOD_TOP_Y + "."
                            : "Catacomb tide receding to its normal level."), true);
                    return 1;
                }));
            dispatcher.register(Commands.literal("asterion")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .then(Commands.literal("catacombs").then(flood)));
        });
    }
}
