package net.krodark.asterion.worldgen;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Builds the lower Forge district from the author's jigsaw-marked rooms.
 * Unlike the ordinary crypt grid, every transform and intersection is calculated
 * from the NBT's real bounds; Forge rooms do not have a prescribed block size.
 */
public final class AuthoredForge {
    private static final int DISTRICT_ROOMS = 52;
    public static final List<String> PIECES = List.of(
            "forge", "t_junction_1", "t_junction_2", "t_junction_3",
            "corner_1", "corner_2", "hallway_1", "hallway_2", "gold_reserves");
    public static final Identifier DOOR = Identifier.fromNamespaceAndPath("asterion", "catacombs/door");
    private static final Map<ServerLevel, Layout> LAYOUTS = new WeakHashMap<>();
    private static final Map<ServerLevel, java.util.ArrayDeque<ChunkPos>> REPAIRS = new WeakHashMap<>();

    private AuthoredForge() { }

    /** Older saves already passed decoration before Forge rooms were installed.
     * Fill only wholly empty slices of the planned district, preserving occupied rooms. */
    public static void onChunkLoad(ServerLevel level, net.minecraft.world.level.chunk.LevelChunk chunk, boolean newlyGenerated) {
        if (!level.dimension().equals(Asterion.ASTERION_LEVEL)) return;
        REPAIRS.computeIfAbsent(level, ignored -> new java.util.ArrayDeque<>()).add(chunk.getPos());
    }

    public static void tickRepairs(ServerLevel level) {
        var pending = REPAIRS.get(level);
        if (pending == null || pending.isEmpty()) return;
        // CHUNK_LOAD precedes completion of the FULL future: place on the next tick.
        ChunkPos pos = pending.removeFirst();
        var chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
        if (chunk == null) return;
        repairEmptyChunk(level, chunk);
        ShaleCaves.repairEmptyChunk(chunk, MazeChunkGenerator.terrainSeed(level.getChunkSource().randomState()));
        ForgeDepths.repairAccess(level, chunk);
    }

    public static void repairEmptyChunk(ServerLevel level, net.minecraft.world.level.chunk.LevelChunk chunk) {
        Layout layout;
        synchronized (LAYOUTS) { layout = LAYOUTS.computeIfAbsent(level, AuthoredForge::createLayout); }
        for (Placement placement : placements(level, layout, chunk.getPos())) {
            BoundingBox bounds = placement.bounds();
            BoundingBox slice = new BoundingBox(Math.max(chunk.getPos().getMinBlockX(), bounds.minX()), bounds.minY() + 1,
                    Math.max(chunk.getPos().getMinBlockZ(), bounds.minZ()), Math.min(chunk.getPos().getMaxBlockX(), bounds.maxX()),
                    bounds.maxY() - 3, Math.min(chunk.getPos().getMaxBlockZ(), bounds.maxZ()));
            boolean empty = true;
            for (BlockPos pos : BlockPos.betweenClosed(slice.minX(), slice.minY(), slice.minZ(),
                    slice.maxX(), slice.maxY(), slice.maxZ())) {
                if (!chunk.getBlockState(pos).isAir()) { empty = false; break; }
            }
            if (!empty) continue;
            BoundingBox clip = new BoundingBox(slice.minX(), bounds.minY(), slice.minZ(), slice.maxX(), bounds.maxY(), slice.maxZ());
            placeRoom(level, placement, clip);
            for (Port seam : layout.seams()) openSeam(level, seam, clip);
            for (Port cap : layout.caps()) sealPort(level, cap, clip);
            capOutpost(level, placement, clip);
            chunk.markUnsaved();
        }
    }

    public static void place(ServerLevelAccessor world, ChunkPos chunk) {
        ServerLevel level = world instanceof ServerLevel server ? server : ((WorldGenLevel) world).getLevel();
        Layout layout;
        synchronized (LAYOUTS) {
            layout = LAYOUTS.computeIfAbsent(level, AuthoredForge::createLayout);
        }
        if (layout.placements().isEmpty()) return;

        BoundingBox clip = new BoundingBox(chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(),
                chunk.getMaxBlockX(), level.getMaxY() - 1, chunk.getMaxBlockZ());
        for (Placement placement : placements(level, layout, chunk)) {
            placeRoom(world, placement, clip);
            capOutpost(world, placement, clip);
        }
        for (Port seam : layout.seams()) openSeam(world, seam, clip);
        for (Port cap : layout.caps()) sealPort(world, cap, clip);
    }

    private static void placeRoom(ServerLevelAccessor world, Placement placement, BoundingBox clip) {
        var settings = AuthoredCatacombs.settings(clip).setRotation(placement.rotation()).addProcessor(CRUCIBLE_PART_DATA);
        placement.template().placeInWorld(world, placement.origin(), placement.origin(), settings,
                RandomSource.create(placement.origin().asLong()), 18);
    }

    public static int districtCenter(int coordinate) {
        return CatacombLayout.ROOT_CENTER + Math.floorDiv(coordinate - CatacombLayout.ROOT_CENTER + 285, 570) * 570;
    }

    /** Remote outposts sit below guaranteed catacomb junctions, every thirty modules. */
    private static Placement outpost(ServerLevel level, Layout layout, int x, int z) {
        int cx = districtCenter(x), cz = districtCenter(z);
        if (cx == CatacombLayout.ROOT_CENTER && cz == cx) return null;
        var root = layout.placements().getFirst();
        BlockPos origin = root.origin().offset(cx - CatacombLayout.ROOT_CENTER, 0, cz - CatacombLayout.ROOT_CENTER);
        var bounds = root.template().getBoundingBox(new StructurePlaceSettings(), origin);
        if (layout.placements().stream().anyMatch(p -> p.bounds().intersects(bounds))) return null;
        return new Placement(root.id(), root.template(), Rotation.NONE, origin, bounds);
    }

    public static BlockPos entranceCenter(ServerLevel level, ChunkPos chunk) {
        Layout layout;
        synchronized (LAYOUTS) { layout = LAYOUTS.computeIfAbsent(level, AuthoredForge::createLayout); }
        if (layout.placements().isEmpty()) return null;
        int x = districtCenter(chunk.getMiddleBlockX()), z = districtCenter(chunk.getMiddleBlockZ());
        if (x == CatacombLayout.ROOT_CENTER && z == x || outpost(level, layout, x, z) != null)
            return new BlockPos(x, LabyrinthLevels.FORGE_FLOOR_Y + 13, z);
        return null;
    }

    private static List<Placement> placements(ServerLevel level, Layout layout, ChunkPos chunk) {
        BoundingBox column = new BoundingBox(chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(),
                chunk.getMaxBlockX(), level.getMaxY() - 1, chunk.getMaxBlockZ());
        var result = new ArrayList<Placement>();
        for (var placement : layout.placements()) if (placement.bounds().intersects(column)) result.add(placement);
        if (!layout.placements().isEmpty()) {
            var remote = outpost(level, layout, chunk.getMiddleBlockX(), chunk.getMiddleBlockZ());
            if (remote != null && remote.bounds().intersects(column)) result.add(remote);
        }
        return result;
    }

    private static void capOutpost(ServerLevelAccessor world, Placement placement, BoundingBox clip) {
        if (!placement.id().getPath().endsWith("/forge")) return;
        int cx = placement.origin().getX() + 18, cz = placement.origin().getZ() + 12;
        if (cx == CatacombLayout.ROOT_CENTER && cz == cx) return;
        // Repeated rooms inside the original district keep their assembled seams.
        if (cx != districtCenter(cx) || cz != districtCenter(cz)) return;
        var piece = new ResolvedPiece("forge", placement.id(), placement.template());
        for (var port : ports(piece, placement.rotation(), placement.origin()))
            if (port.front() != Direction.WEST) sealPort(world, port, clip);
    }

    public static void clearRuntimeState() {
        synchronized (LAYOUTS) { LAYOUTS.clear(); }
        REPAIRS.clear();
    }

    /** True only inside one of the authored rooms that make up this world's Forge district. */
    public static boolean contains(ServerLevel level, BlockPos pos) {
        Layout layout;
        synchronized (LAYOUTS) {
            layout = LAYOUTS.computeIfAbsent(level, AuthoredForge::createLayout);
        }
        return placements(level, layout, ChunkPos.containing(pos)).stream().anyMatch(placement -> placement.bounds().isInside(pos));
    }

    private static Layout createLayout(ServerLevel level) {
        Map<String, ResolvedPiece> loaded = new LinkedHashMap<>();
        for (String name : PIECES) resolve(level, name).ifPresent(piece -> loaded.put(name, piece));
        ResolvedPiece root = loaded.remove("forge");
        if (root == null) {
            Asterion.LOGGER.warn("Forge room NBTs are not installed yet; expected data/asterion/structure/forge/forge.nbt");
            return new Layout(List.of(), List.of(), List.of());
        }

        List<Placement> placed = new ArrayList<>();
        List<Port> open = new ArrayList<>();
        List<Port> seams = new ArrayList<>();
        StructurePlaceSettings rootSettings = new StructurePlaceSettings().setRotation(Rotation.NONE);
        BoundingBox relative = root.template().getBoundingBox(rootSettings, BlockPos.ZERO);
        int center = CatacombLayout.ROOT_CENTER;
        BlockPos rootOrigin = new BlockPos(center - (relative.minX() + relative.maxX()) / 2,
                LabyrinthLevels.FORGE_FLOOR_Y - relative.minY(),
                center - (relative.minZ() + relative.maxZ()) / 2);
        Placement rootPlacement = placement(root, Rotation.NONE, rootOrigin);
        placed.add(rootPlacement);
        open.addAll(ports(root, Rotation.NONE, rootOrigin));
        open.removeIf(port -> port.front() == Direction.WEST); // Reserved for the catacomb stairway.

        // Junctions go first so the graph gains enough free ends for all authored rooms.
        List<ResolvedPiece> remaining = new ArrayList<>(loaded.values());
        remaining.sort(Comparator.comparingInt((ResolvedPiece piece) -> connectorCount(piece.template())).reversed());
        long seed = MazeChunkGenerator.terrainSeed(level.getChunkSource().randomState());
        int salt = 0;
        for (ResolvedPiece piece : remaining) {
            Attachment attachment = findAttachment(piece, open, placed, seed ^ ++salt * 0x9E3779B97F4A7C15L);
            if (attachment == null) {
                Asterion.LOGGER.warn("Could not attach Forge room {} without overlapping another real NBT bound", piece.id());
                continue;
            }
            placed.add(attachment.placement());
            open.remove(attachment.parent());
            seams.add(attachment.parent());
            List<Port> children = new ArrayList<>(ports(piece, attachment.placement().rotation(),
                    attachment.placement().origin()));
            children.removeIf(port -> port.position().equals(attachment.childPosition()));
            open.addAll(children);
        }
        // These NBTs are the biome's actual room palette, not decorations over the
        // old generated grid. Reuse the authored variants to grow a proper district;
        // working forges stay rare and gold reserves are rarer still.
        List<ResolvedPiece> palette = new ArrayList<>(loaded.values());
        palette.add(root);
        int attempts = 0;
        while (placed.size() < DISTRICT_ROOMS && !open.isEmpty() && attempts++ < DISTRICT_ROOMS * 24) {
            long roll = CatacombLayout.hash(seed ^ 0xF0A63D15L, attempts, placed.size());
            ResolvedPiece piece = repeatedPiece(palette, roll);
            Attachment attachment = findAttachment(piece, open, placed, roll);
            if (attachment == null) continue;
            placed.add(attachment.placement());
            open.remove(attachment.parent());
            seams.add(attachment.parent());
            List<Port> children = new ArrayList<>(ports(piece, attachment.placement().rotation(),
                    attachment.placement().origin()));
            children.removeIf(port -> port.position().equals(attachment.childPosition()));
            open.addAll(children);
        }
        List<Port> caps = open.stream().distinct().toList();
        Asterion.LOGGER.info("Built Forge biome from {} authored NBT rooms (all {}/{} variants, {} sealed ends)",
                placed.size(), PIECES.size(), PIECES.size(), caps.size());
        return new Layout(List.copyOf(placed), caps, List.copyOf(seams));
    }

    private static ResolvedPiece repeatedPiece(List<ResolvedPiece> palette, long roll) {
        int chance = (int)Math.floorMod(roll, 100);
        String family = chance < 2 ? "gold_reserves" : chance < 7 ? "forge"
                : chance < 76 ? "hallway" : chance < 89 ? "corner" : "t_junction";
        List<ResolvedPiece> choices = palette.stream().filter(piece -> family.equals("forge")
                ? piece.name().equals("forge") : family.equals("gold_reserves")
                ? piece.name().equals("gold_reserves") : piece.name().startsWith(family)).toList();
        if (choices.isEmpty()) choices = palette.stream()
                .filter(piece -> piece.name().startsWith("hallway")).toList();
        return choices.get((int)Math.floorMod(roll >>> 8, choices.size()));
    }

    /** A connected jigsaw seam is always a five-wide, six-high traversable opening. */
    private static void openSeam(ServerLevelAccessor world, Port port, BoundingBox clip) {
        Direction across = port.front().getClockWise();
        for (int depth = 0; depth <= 1; depth++) for (int side = -2; side <= 2; side++)
            for (int y = 0; y <= 5; y++) {
                BlockPos pos = port.position().relative(port.front(), depth).relative(across, side).above(y);
                if (clip.isInside(pos)) world.setBlock(pos,
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 18);
            }
    }

    /** Unused authored exits receive a complete wall instead of the old one-block plug. */
    private static void sealPort(ServerLevelAccessor world, Port port, BoundingBox clip) {
        Direction across = port.front().getClockWise();
        for (int depth = 0; depth <= 1; depth++) for (int side = -3; side <= 3; side++)
            for (int y = -1; y <= 6; y++) {
                BlockPos pos = port.position().relative(port.front(), depth).relative(across, side).above(y);
                if (clip.isInside(pos)) world.setBlock(pos, Asterion.MAZESTEEL_BRICKS.defaultBlockState(), 18);
            }
    }

    private static Optional<ResolvedPiece> resolve(ServerLevel level, String name) {
        Set<String> candidates = new HashSet<>();
        candidates.add(name);
        candidates.add(name.replace("_1", "1").replace("_2", "2").replace("_3", "3"));
        if (name.matches(".*_[123]$")) candidates.add(name.substring(0, name.length() - 1) + "0" + name.charAt(name.length() - 1));
        for (String path : candidates) {
            for (String prefix : List.of("forge/", "catacombs/", "")) {
                Identifier id = Asterion.id(prefix + path);
                Optional<StructureTemplate> template = level.getStructureManager().get(id);
                if (template.isPresent()) return Optional.of(new ResolvedPiece(name, id, template.get()));
            }
        }
        Asterion.LOGGER.warn("Missing authored Forge piece {} (looked under forge/ and catacombs/)", name);
        return Optional.empty();
    }

    private static Attachment findAttachment(ResolvedPiece piece, List<Port> open, List<Placement> placed,
                                             long seed) {
        if (open.isEmpty()) return null;
        int portOffset = (int) Math.floorMod(seed, open.size());
        Rotation[] rotations = Rotation.values();
        int rotationOffset = (int) Math.floorMod(seed >>> 8, rotations.length);
        Attachment best = null;
        long bestScore = Long.MIN_VALUE;
        for (int oi = 0; oi < open.size(); oi++) {
            Port parent = open.get((oi + portOffset) % open.size());
            for (int ri = 0; ri < rotations.length; ri++) {
                Rotation rotation = rotations[(ri + rotationOffset) % rotations.length];
                List<Port> localPorts = ports(piece, rotation, BlockPos.ZERO);
                int childOffset = localPorts.isEmpty() ? 0 : (int) Math.floorMod(seed >>> 16, localPorts.size());
                for (int ci = 0; ci < localPorts.size(); ci++) {
                    Port child = localPorts.get((ci + childOffset) % localPorts.size());
                    if (!compatible(parent, child)) continue;
                    BlockPos desired = parent.position().relative(parent.front());
                    BlockPos origin = desired.subtract(child.position());
                    Placement candidate = placement(piece, rotation, origin);
                    if (placed.stream().anyMatch(other -> other.bounds().intersects(candidate.bounds()))) continue;
                    long centerX = (long)candidate.bounds().minX() + candidate.bounds().maxX();
                    long centerZ = (long)candidate.bounds().minZ() + candidate.bounds().maxZ();
                    long root = CatacombLayout.ROOT_CENTER * 2L;
                    long dx = centerX - root, dz = centerZ - root;
                    // Grow toward the frontier instead of accepting the first inward fit;
                    // this avoids surrounding all remaining ports with earlier rooms.
                    long score = (dx * dx + dz * dz) * 1024L
                            + Math.floorMod(CatacombLayout.hash(seed, origin.getX(), origin.getZ()), 1024L);
                    if (score > bestScore) {
                        bestScore = score;
                        best = new Attachment(parent, desired, candidate);
                    }
                }
            }
        }
        return best;
    }

    private static boolean compatible(Port parent, Port child) {
        return parent.front().getAxis().isHorizontal()
                && child.front() == parent.front().getOpposite()
                && parent.target().equals(child.name())
                && child.target().equals(parent.name());
    }

    private static Placement placement(ResolvedPiece piece, Rotation rotation, BlockPos origin) {
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
        return new Placement(piece.id(), piece.template(), rotation, origin,
                piece.template().getBoundingBox(settings, origin));
    }

    private static List<Port> ports(ResolvedPiece piece, Rotation rotation, BlockPos origin) {
        List<Port> ports = new ArrayList<>();
        for (StructureTemplate.JigsawBlockInfo jigsaw : piece.template().getJigsaws(origin, rotation)) {
            Direction front = JigsawBlock.getFrontFacing(jigsaw.info().state());
            if (!front.getAxis().isHorizontal()) continue;
            // This intentionally ignores target_pool. The supplied in-game setup uses
            // minecraft:empty; our bounded assembler owns selection and still honors
            // the configured name, target, orientation and final-state replacement.
            if (!jigsaw.name().equals(DOOR) || !jigsaw.target().equals(DOOR)) continue;
            ports.add(new Port(jigsaw.info().pos(), front, jigsaw.name(), jigsaw.target()));
        }
        return ports;
    }

    private static int connectorCount(StructureTemplate template) {
        return (int) template.getJigsaws(BlockPos.ZERO, Rotation.NONE).stream()
                .filter(jigsaw -> jigsaw.name().equals(DOOR) && jigsaw.target().equals(DOOR))
                .filter(jigsaw -> JigsawBlock.getFrontFacing(jigsaw.info().state()).getAxis().isHorizontal())
                .count();
    }

    /** Only the 5x4x5 crucible controller owns a block entity; structure saves tag every part. */
    private static final StructureProcessor CRUCIBLE_PART_DATA = new StructureProcessor() {
        @Override public StructureTemplate.StructureBlockInfo processBlock(
                net.minecraft.world.level.LevelReader world, BlockPos origin, BlockPos reference,
                StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo transformed,
                StructurePlaceSettings settings) {
            if (!(transformed.state().getBlock() instanceof net.krodark.asterion.block.CrucibleBlock)
                    || net.krodark.asterion.block.CrucibleBlock.isRoot(transformed.state())) return transformed;
            return new StructureTemplate.StructureBlockInfo(transformed.pos(), transformed.state(), null);
        }
        @Override protected StructureProcessorType<?> getType() { return StructureProcessorType.BLOCK_IGNORE; }
    };

    private record ResolvedPiece(String name, Identifier id, StructureTemplate template) { }
    private record Port(BlockPos position, Direction front, Identifier name, Identifier target) { }
    private record Placement(Identifier id, StructureTemplate template, Rotation rotation,
                             BlockPos origin, BoundingBox bounds) { }
    private record Attachment(Port parent, BlockPos childPosition, Placement placement) { }
    private record Layout(List<Placement> placements, List<Port> caps, List<Port> seams) { }
}
