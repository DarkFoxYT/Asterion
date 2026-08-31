package net.krodark.asterion.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.GreekRune;
import net.krodark.asterion.WorldGenerator;
import net.krodark.asterion.block.RuneBlock;
import net.krodark.asterion.block.RuneDoorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public final class MazeNbtStructures {
    private static final Identifier CATALOG = Asterion.id("maze_structures.json");
    private static final Map<ServerLevel, Layout> LAYOUTS = new WeakHashMap<>();
    private static final Map<Long, Layout> GENERATION_LAYOUTS = new ConcurrentHashMap<>();
    private static final Layout EMPTY_LAYOUT = new Layout(List.of());

    private MazeNbtStructures() { }

    public static Layout emptyLayout() {
        return EMPTY_LAYOUT;
    }

    /** Returns the immutable reservation plan used by async chunk generation. */
    public static Layout generationLayout(long terrainSeed) {
        return GENERATION_LAYOUTS.getOrDefault(terrainSeed, EMPTY_LAYOUT);
    }

    public static Layout layout(ServerLevel level, int radiusCells, int cellSize, ReservationFilter filter) {
        synchronized (LAYOUTS) {
            return LAYOUTS.computeIfAbsent(level,
                    ignored -> createLayout(level, radiusCells, cellSize, filter));
        }
    }

    public static void tick(ServerLevel level) {
        Layout layout;
        synchronized (LAYOUTS) { layout = LAYOUTS.get(level); }
        if (layout != null) layout.placeNext(level);
    }

    public static BlockPos safeCheckpointNear(ServerLevel level, BlockPos position, double radius) {
        Layout layout;
        synchronized (LAYOUTS) { layout = LAYOUTS.get(level); }
        return layout == null ? null : layout.safeCheckpointNear(level, position, radius);
    }

    public static BlockPos nearestSafeCheckpoint(ServerLevel level, BlockPos position) {
        Layout layout;
        synchronized (LAYOUTS) { layout = LAYOUTS.get(level); }
        return layout == null ? null : layout.nearestSafeCheckpoint(level, position);
    }

    public static BlockPos nearestSafeHouse(ServerLevel level, BlockPos position) {
        Layout layout;
        synchronized (LAYOUTS) { layout = LAYOUTS.get(level); }
        return layout == null ? null : layout.nearestSafeHouse(position);
    }

    public static boolean isSafeCheckpoint(ServerLevel level, BlockPos checkpoint) {
        Layout layout;
        synchronized (LAYOUTS) { layout = LAYOUTS.get(level); }
        return layout != null && layout.isSafeCheckpoint(level, checkpoint);
    }

    public static void cleanLegacyCopper(LevelChunk chunk, int minY, int maxY) {
        BlockPos marker = new BlockPos(chunk.getPos().getMinBlockX(), 2, chunk.getPos().getMinBlockZ());
        if (chunk.getBlockState(marker).is(Blocks.REINFORCED_DEEPSLATE)) return;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    if (!isCopper(chunk.getBlockState(cursor).getBlock())) continue;
                    chunk.setBlockState(cursor, y <= 48
                            ? Asterion.ANCIENT_STONE.defaultBlockState()
                            : Asterion.ANCIENT_BRICKS.defaultBlockState(), 0);
                }
            }
        }
        chunk.setBlockState(marker, Blocks.REINFORCED_DEEPSLATE.defaultBlockState(), 0);
        chunk.markUnsaved();
    }

    public static void markCopperClean(LevelChunk chunk) {
        BlockPos marker = new BlockPos(chunk.getPos().getMinBlockX(), 2, chunk.getPos().getMinBlockZ());
        chunk.setBlockState(marker, Blocks.REINFORCED_DEEPSLATE.defaultBlockState(), 0);
        chunk.markUnsaved();
    }

    public static void clearRuntimeState() {
        synchronized (LAYOUTS) { LAYOUTS.clear(); }
        GENERATION_LAYOUTS.clear();
    }

    private static Layout createLayout(ServerLevel level, int radius, int cell, ReservationFilter filter) {
        Catalog catalog = readCatalog(level);
        List<ResolvedTemplate> templates = new ArrayList<>();
        for (TemplateEntry entry : catalog.templates) {
            Optional<StructureTemplate> template = level.getStructureManager().get(entry.id);
            if (template.isPresent()) templates.add(new ResolvedTemplate(entry, template.get()));
            else Asterion.LOGGER.warn("Skipping missing maze NBT template {}", entry.id);
        }
        if (templates.isEmpty()) {
            Asterion.LOGGER.warn("Maze NBT structure catalog contains no loadable templates");
            return new Layout(List.of());
        }

        int size = radius * 2;
        int limit = radius * cell;
        int spacing = Math.max(6, catalog.spacingCells);
        int margin = Math.max(5, spacing / 2);
        List<Placement> placements = new ArrayList<>();
        long seed = level.getChunkSource().randomState()
                .getOrCreateRandomFactory(Asterion.id("maze_layout")).at(0, 0, 0).nextLong();
        for (int baseZ = margin; baseZ < size - margin; baseZ += spacing) {
            for (int baseX = margin; baseX < size - margin; baseX += spacing) {
                long roll = mix(seed ^ (long) baseX * 0x9E3779B97F4A7C15L
                        ^ (long) baseZ * 0xD1B54A32D192ED03L);
                if (unitFloat(roll) >= catalog.chance) continue;
                int jitter = Math.max(1, spacing / 4);
                int cellX = baseX + (int) Math.floorMod(roll >>> 8, jitter * 2 + 1) - jitter;
                int cellZ = baseZ + (int) Math.floorMod(roll >>> 20, jitter * 2 + 1) - jitter;
                ResolvedTemplate selected = select(templates, roll >>> 32);
                Rotation rotation = Rotation.values()[(int) Math.floorMod(roll >>> 48, Rotation.values().length)];
                StructurePlaceSettings settings = new StructurePlaceSettings()
                        .setRotation(rotation).setIgnoreEntities(true);
                BoundingBox relative = selected.template.getBoundingBox(settings, BlockPos.ZERO);
                int centerX = -limit + cellX * cell + cell / 2;
                int centerZ = -limit + cellZ * cell + cell / 2;
                int originX = centerX - (relative.minX() + relative.maxX()) / 2;
                int originZ = centerZ - (relative.minZ() + relative.maxZ()) / 2;
                // NBT templates use y=0 as their floor. Replacing the maze floor here keeps
                // rooms level with their approaches instead of leaving every landmark on a step.
                int originY = WorldGenerator.mazeFloorHeight(seed, centerX, centerZ) - relative.minY();
                BlockPos origin = new BlockPos(originX, originY, originZ);
                BoundingBox box = relative.moved(originX, originY, originZ);
                BoundingBox reserved = box.inflatedBy(catalog.padding, 0, catalog.padding);
                int minCellX = Math.floorDiv(reserved.minX() + limit, cell);
                int maxCellX = Math.floorDiv(reserved.maxX() + limit, cell);
                int minCellZ = Math.floorDiv(reserved.minZ() + limit, cell);
                int maxCellZ = Math.floorDiv(reserved.maxZ() + limit, cell);
                if (!filter.allow(minCellX, minCellZ, maxCellX, maxCellZ)) continue;
                boolean overlaps = placements.stream().anyMatch(other -> other.reserved.intersects(reserved));
                if (!overlaps) placements.add(new Placement(selected.entry.id, selected.template,
                        origin, settings, box, reserved, roll));
            }
        }
        Layout layout = new Layout(placements);
        GENERATION_LAYOUTS.put(seed, layout);
        return layout;
    }

    private static Catalog readCatalog(ServerLevel level) {
        try {
            var resource = level.getServer().getResourceManager().getResource(CATALOG);
            if (resource.isPresent()) try (Reader reader = resource.get().openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                int spacing = root.has("spacing_cells") ? root.get("spacing_cells").getAsInt() : 18;
                int padding = root.has("padding_blocks") ? root.get("padding_blocks").getAsInt() : 4;
                float chance = root.has("chance") ? root.get("chance").getAsFloat() : 0.45F;
                List<TemplateEntry> entries = new ArrayList<>();
                JsonArray array = root.getAsJsonArray("templates");
                if (array != null) for (var value : array) {
                    JsonObject object = value.getAsJsonObject();
                    Identifier id = Identifier.tryParse(object.get("template").getAsString());
                    if (id != null) entries.add(new TemplateEntry(id,
                            Math.max(1, object.has("weight") ? object.get("weight").getAsInt() : 1)));
                }
                return new Catalog(spacing, Math.max(1, padding), Math.max(0.0F, Math.min(1.0F, chance)), entries);
            }
        } catch (Exception exception) {
            Asterion.LOGGER.error("Could not read {}", CATALOG, exception);
        }
        return new Catalog(18, 4, 0.0F, List.of());
    }

    private static ResolvedTemplate select(List<ResolvedTemplate> templates, long random) {
        int total = templates.stream().mapToInt(template -> template.entry.weight).sum();
        int choice = (int) Math.floorMod(random, total);
        for (ResolvedTemplate template : templates) {
            choice -= template.entry.weight;
            if (choice < 0) return template;
        }
        return templates.getLast();
    }

    private static void sanitize(ServerLevel level, BoundingBox box) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) for (int z = box.minZ(); z <= box.maxZ(); z++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                cursor.set(x, y, z);
                var block = level.getBlockState(cursor).getBlock();
                if (block == Blocks.JIGSAW || block == Blocks.STRUCTURE_BLOCK || block == Blocks.STRUCTURE_VOID)
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
                else if (isCopper(block))
                    level.setBlock(cursor, Asterion.ANCIENT_BRICKS.defaultBlockState(), 2);
            }
        }
    }

    private static boolean isCopper(net.minecraft.world.level.block.Block block) {
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        return id != null && id.getPath().contains("copper");
    }

    private static float unitFloat(long value) { return (value >>> 40) / (float) (1L << 24); }
    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    @FunctionalInterface
    public interface ReservationFilter {
        boolean allow(int minCellX, int minCellZ, int maxCellX, int maxCellZ);
    }

    public static final class Layout {
        private final List<Placement> placements;
        private final Map<Long, List<Placement>> reservationsByChunk = new HashMap<>();
        private final Map<Long, List<Placement>> anchorsByChunk = new HashMap<>();
        private final ArrayDeque<Placement> pending = new ArrayDeque<>();
        private final Set<BlockPos> queued = new HashSet<>();
        private final Set<Long> generatedChunks = ConcurrentHashMap.newKeySet();
        private final Map<BlockPos, BlockPos> safeCheckpoints = new HashMap<>();

        private Layout(List<Placement> placements) {
            this.placements = placements;
            for (Placement placement : placements) {
                placement.reserved.intersectingChunks().forEach(chunk -> reservationsByChunk
                        .computeIfAbsent(chunk.pack(), ignored -> new ArrayList<>()).add(placement));
                anchorsByChunk.computeIfAbsent(ChunkPos.pack(placement.origin), ignored -> new ArrayList<>())
                        .add(placement);
            }
        }

        public boolean reserved(int x, int z) {
            List<Placement> local = reservationsByChunk.get(ChunkPos.pack(x >> 4, z >> 4));
            if (local == null) return false;
            for (Placement placement : local)
                if (insideXZ(placement.box, x, z) || isApproach(placement, x, z)) return true;
            return false;
        }

        public int floorY(int x, int z, int fallback) {
            List<Placement> local = reservationsByChunk.get(ChunkPos.pack(x >> 4, z >> 4));
            if (local == null) return fallback;
            for (Placement placement : local)
                if (insideXZ(placement.box, x, z) || isApproach(placement, x, z))
                    return placement.box.minY();
            return fallback;
        }

        public void onChunkBuilt(LevelChunk chunk) {
            List<Placement> local = anchorsByChunk.get(chunk.getPos().pack());
            if (local == null) return;
            for (Placement placement : local) if (queued.add(placement.origin)) pending.addLast(placement);
        }

        public void markTerrainGenerated(ChunkPos chunk) {
            long key = chunk.pack();
            if (reservationsByChunk.containsKey(key)) generatedChunks.add(key);
        }

        private static boolean insideXZ(BoundingBox box, int x, int z) {
            return x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ();
        }

        private static boolean isApproach(Placement placement, int x, int z) {
            int centerX = (placement.box.minX() + placement.box.maxX()) / 2;
            int centerZ = (placement.box.minZ() + placement.box.maxZ()) / 2;
            int halfWidth = 1;
            boolean eastWest = Math.abs(z - centerZ) <= halfWidth
                    && x >= placement.reserved.minX() && x <= placement.reserved.maxX()
                    && (x < placement.box.minX() || x > placement.box.maxX());
            boolean northSouth = Math.abs(x - centerX) <= halfWidth
                    && z >= placement.reserved.minZ() && z <= placement.reserved.maxZ()
                    && (z < placement.box.minZ() || z > placement.box.maxZ());
            return eastWest || northSouth;
        }

        private void placeNext(ServerLevel level) {
            Placement placement = pending.pollFirst();
            if (placement == null) return;
            BlockPos marker = new BlockPos(placement.origin.getX(), 3, placement.origin.getZ());
            boolean footprintLoaded = placement.reserved.intersectingChunks().allMatch(
                    chunk -> level.getChunkSource().hasChunk(chunk.x(), chunk.z()));
            if (!footprintLoaded) {
                pending.addLast(placement);
                return;
            }
            if (level.getBlockState(marker).is(Blocks.REINFORCED_DEEPSLATE)) {
                carveAccessibilityBridges(level, placement);
                configureSafeRoom(level, placement, false);
                cacheSafeCheckpoint(level, placement);
                return;
            }
            boolean generatedAroundStructure = placement.reserved.intersectingChunks()
                    .allMatch(chunk -> generatedChunks.contains(chunk.pack()));
            if (!generatedAroundStructure) preparePlacementArea(level, placement);
            boolean placed = placement.template.placeInWorld(level, placement.origin, placement.origin,
                    placement.settings, RandomSource.create(placement.seed), 2);
            if (!placed) {
                Asterion.LOGGER.warn("Failed to place maze NBT template {} at {}", placement.id, placement.origin);
                queued.remove(placement.origin);
                return;
            }
            sanitize(level, placement.box);
            carveAccessibilityBridges(level, placement);
            configureSafeRoom(level, placement, true);
            cacheSafeCheckpoint(level, placement);
            level.setBlock(marker, Blocks.REINFORCED_DEEPSLATE.defaultBlockState(), 2);
        }

        // Rotated template doorways do not necessarily line up with the maze grid.
        private static void carveAccessibilityBridges(ServerLevel level, Placement placement) {
            int floorY = placement.box.minY();
            int centerX = (placement.box.minX() + placement.box.maxX()) / 2;
            int centerZ = (placement.box.minZ() + placement.box.maxZ()) / 2;
            carveBridge(level, placement.reserved.minX(), placement.box.minX() + 1,
                    centerZ, true, floorY);
            carveBridge(level, placement.box.maxX() - 1, placement.reserved.maxX(),
                    centerZ, true, floorY);
            carveBridge(level, placement.reserved.minZ(), placement.box.minZ() + 1,
                    centerX, false, floorY);
            carveBridge(level, placement.box.maxZ() - 1, placement.reserved.maxZ(),
                    centerX, false, floorY);
        }

        private static void carveBridge(ServerLevel level, int start, int end, int fixed,
                                        boolean alongX, int floorY) {
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int distance = start; distance <= end; distance++) {
                for (int lateral = -1; lateral <= 1; lateral++) {
                    int x = alongX ? distance : fixed + lateral;
                    int z = alongX ? fixed + lateral : distance;
                    cursor.set(x, floorY, z);
                    if (!level.getBlockState(cursor).isCollisionShapeFullBlock(level, cursor))
                        level.setBlock(cursor, Asterion.ANCIENT_STONE.defaultBlockState(), 2);
                    for (int y = 1; y <= 5; y++) {
                        cursor.set(x, floorY + y, z);
                        if (!level.getBlockState(cursor).isAir())
                            level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }

        /** Compatibility cleanup for chunks made before the reservation plan was available. */
        private static void preparePlacementArea(ServerLevel level, Placement placement) {
            int floorY = placement.box.minY();
            int top = placement.box.minY() + AsterionConfig.INSTANCE.wallHeight;
            int floorDepth = AsterionConfig.INSTANCE.floorThickness;
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int x = placement.reserved.minX(); x <= placement.reserved.maxX(); x++)
                for (int z = placement.reserved.minZ(); z <= placement.reserved.maxZ(); z++) {
                    boolean footprint = insideXZ(placement.box, x, z);
                    if (!footprint && !isApproach(placement, x, z)) continue;
                    if (!footprint) for (int depth = 0; depth < floorDepth; depth++) {
                        cursor.set(x, floorY - depth, z);
                        if (!level.getBlockState(cursor).is(Asterion.ANCIENT_STONE))
                            level.setBlock(cursor, Asterion.ANCIENT_STONE.defaultBlockState(), 2);
                    }
                    for (int y = floorY + 1; y <= top; y++) {
                        cursor.set(x, y, z);
                        if (!level.getBlockState(cursor).isAir())
                            level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
        }

        private void cacheSafeCheckpoint(ServerLevel level, Placement placement) {
            if (!isSafeRoom(placement.id) || safeCheckpoints.containsKey(placement.origin)) return;
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int y = placement.box.minY(); y <= placement.box.maxY(); y++)
                for (int x = placement.box.minX(); x <= placement.box.maxX(); x++)
                    for (int z = placement.box.minZ(); z <= placement.box.maxZ(); z++) {
                        cursor.set(x, y, z);
                        if (!level.getBlockState(cursor).is(Blocks.LODESTONE)) continue;
                        safeCheckpoints.put(placement.origin, cursor.above().immutable());
                        return;
                    }
        }

        private void configureSafeRoom(ServerLevel level, Placement placement, boolean removePlaques) {
            // Legacy templates still contain puzzle plaques and sealed gates. New placements omit
            // the plaques and leave those gates open now that their progression system is retired.
            for (BlockPos pos : BlockPos.betweenClosed(placement.box.minX(), placement.box.minY(), placement.box.minZ(),
                    placement.box.maxX(), placement.box.maxY(), placement.box.maxZ())) {
                var state = level.getBlockState(pos);
                if (removePlaques && state.getBlock() instanceof RuneBlock) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                else if (state.is(Asterion.RUNE_ZONE_DOOR)) level.setBlock(pos, state.setValue(RuneDoorBlock.OPEN, true), 3);
            }
        }

        private BlockPos safeCheckpointNear(ServerLevel level, BlockPos position, double radius) {
            double radiusSquared = radius * radius;
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            for (BlockPos checkpoint : safeCheckpoints.values()) {
                if (!isActivated(level, checkpoint)) continue;
                double distance = checkpoint.distSqr(position);
                if (distance <= radiusSquared && distance < bestDistance) {
                    best = checkpoint;
                    bestDistance = distance;
                }
            }
            return best;
        }

        private BlockPos nearestSafeCheckpoint(ServerLevel level, BlockPos position) {
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            for (BlockPos checkpoint : safeCheckpoints.values()) {
                if (!isActivated(level, checkpoint)) continue;
                double distance = checkpoint.distSqr(position);
                if (distance < bestDistance) {
                    best = checkpoint;
                    bestDistance = distance;
                }
            }
            return best;
        }

        private BlockPos nearestSafeHouse(BlockPos position) {
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            for (BlockPos checkpoint : safeCheckpoints.values()) {
                double distance = checkpoint.distSqr(position);
                if (distance < bestDistance) {
                    best = checkpoint;
                    bestDistance = distance;
                }
            }
            return best;
        }

        private boolean isSafeCheckpoint(ServerLevel level, BlockPos checkpoint) {
            if (!level.getBlockState(checkpoint.below()).is(Blocks.LODESTONE) || !isActivated(level, checkpoint)) return false;
            for (Placement placement : placements) {
                if (!isSafeRoom(placement.id) || !placement.box.isInside(checkpoint.below())) continue;
                BlockPos marker = new BlockPos(placement.origin.getX(), 3, placement.origin.getZ());
                if (level.getBlockState(marker).is(Blocks.REINFORCED_DEEPSLATE)) {
                    safeCheckpoints.put(placement.origin, checkpoint.immutable());
                    return true;
                }
            }
            return false;
        }

        private static boolean isActivated(ServerLevel level, BlockPos checkpoint) {
            for (BlockPos pos : BlockPos.betweenClosed(checkpoint.offset(-10, -4, -10), checkpoint.offset(10, 6, 10)))
                if (level.getBlockState(pos).getBlock() instanceof net.krodark.asterion.block.SanctuaryBlock && level.getBlockState(pos).getValue(net.krodark.asterion.block.SanctuaryBlock.CHARGE) > 0) return true;
            return false;
        }

        private static boolean isSafeRoom(Identifier id) {
            return id.getPath().contains("safe_room") || id.getPath().contains("sanctuary");
        }
    }

    private record Catalog(int spacingCells, int padding, float chance, List<TemplateEntry> templates) { }
    private record TemplateEntry(Identifier id, int weight) { }
    private record ResolvedTemplate(TemplateEntry entry, StructureTemplate template) { }
    private record Placement(Identifier id, StructureTemplate template, BlockPos origin,
                             StructurePlaceSettings settings, BoundingBox box, BoundingBox reserved, long seed) { }
}
