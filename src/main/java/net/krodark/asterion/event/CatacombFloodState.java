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

/** A shared catacomb tide: two blocks of rise, in exact eighth-block steps. */
public final class CatacombFloodState extends SavedData {
    public static final int MAX_RISE = 16;
    public static final int STEP_TICKS = 40;
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
        boolean arena = pos.getY() >= net.krodark.asterion.worldgen.AuthoredCatacombs.ARENA_FLOOR_Y && pos.getY() <= 49
                && Math.abs((long)pos.getX()) <= 61 && Math.abs((long)pos.getZ()) <= 61;
        return (catacombs || arena) && get(level).active;
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
        if (loaded != null) { loaded.chunks.remove(chunk.getPos().pack()); loaded.pending.remove(chunk.getPos().pack()); }
    }
    public static void clear() { LOADED.clear(); }

    public static void tick(ServerLevel level) {
        var state = get(level);
        long now = level.getGameTime();
        if (state.active && (now >= state.endsAt || net.krodark.asterion.WorldGenerator.isBossEncounterActive(level)))
            setActive(level, false);
        var loaded = LOADED.computeIfAbsent(level, ignored -> new LoadedTide());
        int target = state.active ? MAX_RISE : 0;
        // Complete a bounded pass before advancing again, including freshly loaded chunks.
        if (state.rise != target && now >= state.nextStep && loaded.pending.isEmpty()) {
            state.rise += Integer.signum(target - state.rise);
            state.nextStep = now + STEP_TICKS;
            loaded.pending.addAll(loaded.chunks);
            state.setDirty();
        }
        for (int budget = 0; budget < 8 && !loaded.pending.isEmpty(); budget++) {
            long packed = loaded.pending.removeFirst();
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed));
            if (chunk != null) reconcile(level, chunk, state.rise);
        }
    }

    /** Only replace liquid or clear air over an existing basin; never erase gates, props or player blocks. */
    public static void reconcile(ServerLevel level, LevelChunk chunk, int riseSteps) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int surfaceEighths = (CatacombLayout.WATER_Y + 1) * 8 + Math.clamp(riseSteps, 0, MAX_RISE);
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++)
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                // Authored crypts are wider than the procedural passage mask: include their wet floors too.
                BlockState base = chunk.getBlockState(cursor.set(x, CatacombLayout.WATER_Y, z));
                boolean basin = base.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
                if (!basin && HeavyWaterlogging.canFill(null, level, cursor, base)) {
                    for (var side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                        BlockPos neighbor = cursor.relative(side);
                        if (level.hasChunkAt(neighbor) && level.getFluidState(neighbor).is(net.minecraft.tags.FluidTags.WATER)) {
                            basin = true;
                            break;
                        }
                    }
                }
                for (int y = CatacombLayout.FLOOR_Y; y <= CatacombLayout.WATER_Y + 2; y++) {
                    cursor.set(x, y, z);
                    BlockState old = chunk.getBlockState(cursor);
                    int amount = Math.clamp(surfaceEighths - y * 8, 0, 8);
                    if (HeavyWaterlogging.isTidal(old)) {
                        BlockState next = amount == 0 ? HeavyWaterlogging.dry(old)
                                : HeavyWaterlogging.withFluid(old, riseSteps == 0 ? HeavyWater.STILL.defaultFluidState()
                                : HeavyWater.FLUID.getFlowing(amount, false));
                        if (next != old) level.setBlock(cursor, next, 2);
                        if (amount > 0 && riseSteps == 0)
                            level.scheduleTick(cursor, HeavyWater.STILL, HeavyWater.STILL.getTickDelay(level));
                        continue;
                    }
                    if (basin && amount > 0 && HeavyWaterlogging.canFill(null, level, cursor, old)) {
                        HeavyWaterlogging.fill(level, cursor, old, riseSteps == 0 ? HeavyWater.STILL.defaultFluidState()
                                : HeavyWater.FLUID.getFlowing(amount, false));
                        continue;
                    }
                    boolean tidal = old.is(HeavyWater.BLOCK);
                    boolean originalWater = y <= CatacombLayout.WATER_Y && (old.is(Blocks.WATER) || old.is(HeavyWater.WATER_BLOCK));
                    boolean fill = basin && y > CatacombLayout.WATER_Y && old.isAir();
                    if (!tidal && !originalWater && !fill) continue;
                    BlockState next = amount == 0 ? Blocks.AIR.defaultBlockState()
                            : riseSteps == 0 ? HeavyWater.WATER_BLOCK.defaultBlockState()
                            : HeavyWater.BLOCK.defaultBlockState().setValue(TidalWaterBlock.LEVEL, amount);
                    if (old != next) level.setBlock(cursor, next, 2);
                }
            }
    }
    private static final class LoadedTide {
        final LinkedHashSet<Long> chunks = new LinkedHashSet<>(), pending = new LinkedHashSet<>();
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
                            ? "Catacomb tide rising two blocks in shared eighth-block steps."
                            : "Catacomb tide receding to its normal level."), true);
                    return 1;
                }));
            dispatcher.register(Commands.literal("asterion")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .then(Commands.literal("catacombs").then(flood)));
        });
    }
}
