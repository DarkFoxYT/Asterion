package net.krodark.labyrinth.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.krodark.labyrinth.Labyrinth;
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

/** Data-driven NBT landmarks whose measured footprints are reserved by the maze. */
public final class MazeNbtStructures {
    private static final Identifier CATALOG = Labyrinth.id("maze_structures.json");
    private static final Map<ServerLevel, Layout> LAYOUTS = new WeakHashMap<>();
    private static final Layout EMPTY_LAYOUT = new Layout(List.of());

    private MazeNbtStructures() { }

    /** Reservation-free layout used while optional NBT landmark generation is disabled. */
    public static Layout emptyLayout() {
        return EMPTY_LAYOUT;
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

    /** Removes legacy copper once per maze chunk, including old generated decorations. */
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
                            ? Labyrinth.ANCIENT_STONE.defaultBlockState()
                            : Labyrinth.ANCIENT_BRICKS.defaultBlockState(), 0);
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
    }

    private static Layout createLayout(ServerLevel level, int radius, int cell, ReservationFilter filter) {
        Catalog catalog = readCatalog(level);
        List<ResolvedTemplate> templates = new ArrayList<>();
        for (TemplateEntry entry : catalog.templates) {
            Optional<StructureTemplate> template = level.getStructureManager().get(entry.id);
            if (template.isPresent()) templates.add(new ResolvedTemplate(entry, template.get()));
            else Labyrinth.LOGGER.warn("Skipping missing maze NBT template {}", entry.id);
        }
        if (templates.isEmpty()) {
            Labyrinth.LOGGER.warn("Maze NBT structure catalog contains no loadable templates");
            return new Layout(List.of());
        }

        int size = radius * 2;
        int limit = radius * cell;
        int spacing = Math.max(6, catalog.spacingCells);
        int margin = Math.max(5, spacing / 2);
        List<Placement> placements = new ArrayList<>();
        long seed = level.getSeed();
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
                int originY = 49 - relative.minY();
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
        Labyrinth.LOGGER.info("Reserved {} size-aware NBT landmarks in the Labyrinth", placements.size());
        return new Layout(placements);
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
            Labyrinth.LOGGER.error("Could not read {}", CATALOG, exception);
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
                    level.setBlock(cursor, Labyrinth.ANCIENT_BRICKS.defaultBlockState(), 2);
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
                if (x >= placement.reserved.minX() && x <= placement.reserved.maxX()
                        && z >= placement.reserved.minZ() && z <= placement.reserved.maxZ()) return true;
            return false;
        }

        public void onChunkBuilt(LevelChunk chunk) {
            List<Placement> local = anchorsByChunk.get(chunk.getPos().pack());
            if (local == null) return;
            for (Placement placement : local) if (queued.add(placement.origin)) pending.addLast(placement);
        }

        private void placeNext(ServerLevel level) {
            Placement placement = pending.pollFirst();
            if (placement == null) return;
            BlockPos marker = new BlockPos(placement.origin.getX(), 3, placement.origin.getZ());
            if (level.getBlockState(marker).is(Blocks.REINFORCED_DEEPSLATE)) return;
            // Landmarks must not synchronously generate their entire footprint. Normal player
            // streaming loads those chunks; placement runs only once the complete area is ready.
            boolean footprintLoaded = placement.box.intersectingChunks().allMatch(
                    chunk -> level.getChunkSource().hasChunk(chunk.x(), chunk.z()));
            if (!footprintLoaded) {
                pending.addLast(placement);
                return;
            }
            boolean placed = placement.template.placeInWorld(level, placement.origin, placement.origin,
                    placement.settings, RandomSource.create(placement.seed), 2);
            if (placed) sanitize(level, placement.box);
            else Labyrinth.LOGGER.warn("Failed to place maze NBT template {} at {}", placement.id, placement.origin);
            level.setBlock(marker, Blocks.REINFORCED_DEEPSLATE.defaultBlockState(), 2);
        }
    }

    private record Catalog(int spacingCells, int padding, float chance, List<TemplateEntry> templates) { }
    private record TemplateEntry(Identifier id, int weight) { }
    private record ResolvedTemplate(TemplateEntry entry, StructureTemplate template) { }
    private record Placement(Identifier id, StructureTemplate template, BlockPos origin,
                             StructurePlaceSettings settings, BoundingBox box, BoundingBox reserved, long seed) { }
}
