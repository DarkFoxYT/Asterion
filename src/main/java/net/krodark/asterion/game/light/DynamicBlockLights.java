package net.krodark.asterion.game.light;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.krodark.asterion.AsterionConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;

public final class DynamicBlockLights {
    private static final Map<ResourceKey<Level>, Set<BlockPos>> PLACED = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<BlockPos, Integer>> WANTED = new HashMap<>();
    private static final Map<ResourceKey<Level>, Set<Integer>> SEEN_ITEMS = new HashMap<>();
    private static int updateTicker;

    private DynamicBlockLights() {
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(DynamicBlockLights::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(DynamicBlockLights::clear);
    }

    private static void tick(MinecraftServer server) {
        AsterionConfig config = AsterionConfig.INSTANCE;
        if (!config.dynamicLightsEnabled) {
            if (!PLACED.isEmpty()) clear(server);
            return;
        }
        int quality = config.dynamicLightQuality;
        int interval = quality == 0 ? 6 : quality == 1 ? 4 : 2;
        if (++updateTicker % interval != 0) return;
        double baseRange = quality == 0 ? 28.0D : quality == 1 ? 46.0D : 64.0D;
        double itemRange = baseRange * config.dynamicLightRangePercent / 100.0D;
        for (ServerLevel level : server.getAllLevels()) {
            Map<BlockPos, Integer> wanted = WANTED.computeIfAbsent(level.dimension(), ignored -> new HashMap<>());
            Set<Integer> seenItems = SEEN_ITEMS.computeIfAbsent(level.dimension(), ignored -> new HashSet<>());
            wanted.clear();
            seenItems.clear();
            level.players().forEach(player -> {
                add(level, wanted, player.getMainHandItem(), BlockPos.containing(
                        player.getX(), player.getY() + 1.2D, player.getZ()));
                add(level, wanted, player.getOffhandItem(), BlockPos.containing(
                        player.getX(), player.getY() + 1.2D, player.getZ()));
                if (config.droppedItemLights)
                    level.getEntitiesOfClass(ItemEntity.class,
                                    player.getBoundingBox().inflate(itemRange), ItemEntity::isAlive)
                            .forEach(item -> {
                                if (seenItems.add(item.getId())) add(level, wanted, item.getItem(), BlockPos.containing(
                                        item.getX(), item.getY() + 0.55D, item.getZ()));
                            });
            });
            update(level, wanted);
        }
    }

    private static void add(ServerLevel world, Map<BlockPos, Integer> wanted,
                            ItemStack stack, BlockPos preferred) {
        int light = lightLevel(stack);
        if (light <= 0) return;
        BlockPos position = findLightPosition(world, preferred);
        if (position != null) wanted.merge(position, light, Math::max);
    }

    private static BlockPos findLightPosition(ServerLevel level, BlockPos preferred) {
        Set<BlockPos> owned = PLACED.getOrDefault(level.dimension(), Set.of());
        BlockPos found = usableLightPosition(level, owned, preferred);
        if (found != null) return found;
        found = usableLightPosition(level, owned, preferred.above());
        if (found != null) return found;
        found = usableLightPosition(level, owned, preferred.below());
        if (found != null) return found;
        found = usableLightPosition(level, owned, preferred.north());
        if (found != null) return found;
        found = usableLightPosition(level, owned, preferred.south());
        if (found != null) return found;
        found = usableLightPosition(level, owned, preferred.east());
        return found != null ? found : usableLightPosition(level, owned, preferred.west());
    }

    private static BlockPos usableLightPosition(ServerLevel level, Set<BlockPos> owned, BlockPos candidate) {
        if (!level.isLoaded(candidate)) return null;
        var state = level.getBlockState(candidate);
        return state.isAir() || (owned.contains(candidate) && state.is(Blocks.LIGHT))
                ? candidate.immutable() : null;
    }

    private static void update(ServerLevel level, Map<BlockPos, Integer> wanted) {
        Set<BlockPos> owned = PLACED.computeIfAbsent(level.dimension(), ignored -> new HashSet<>());
        for (Map.Entry<BlockPos, Integer> entry : wanted.entrySet()) {
            BlockPos position = entry.getKey();
            if (!level.isLoaded(position)) continue;
            int light = entry.getValue();
            var state = level.getBlockState(position);
            if (state.isAir()) {
                level.setBlock(position, Blocks.LIGHT.defaultBlockState()
                        .setValue(LightBlock.LEVEL, light), 3);
                owned.add(position);
            } else if (owned.contains(position) && state.is(Blocks.LIGHT)
                    && state.getValue(LightBlock.LEVEL) != light) {
                level.setBlock(position, state.setValue(LightBlock.LEVEL, light), 3);
            }
        }

        var iterator = owned.iterator();
        while (iterator.hasNext()) {
            BlockPos old = iterator.next();
            if (wanted.containsKey(old)) continue;
            if (level.isLoaded(old) && level.getBlockState(old).is(Blocks.LIGHT)) {
                level.removeBlock(old, false);
            }
            iterator.remove();
        }
    }

    private static int lightLevel(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (stack.is(Items.SOUL_TORCH) || stack.is(Items.SOUL_LANTERN)
                || stack.is(Items.SOUL_CAMPFIRE)) return 12;
        if (stack.is(Items.TORCH) || stack.is(Items.LANTERN)
                || stack.is(Items.CAMPFIRE)) return 15;
        if (stack.getItem() instanceof BlockItem blockItem)
            return blockItem.getBlock().defaultBlockState().getLightEmission();
        return 0;
    }

    private static void clear(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            Set<BlockPos> positions = PLACED.getOrDefault(level.dimension(), Set.of());
            for (BlockPos position : positions) {
                if (level.isLoaded(position) && level.getBlockState(position).is(Blocks.LIGHT))
                    level.removeBlock(position, false);
            }
        }
        PLACED.clear();
        WANTED.clear();
        SEEN_ITEMS.clear();
        updateTicker = 0;
    }
}
