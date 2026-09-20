package net.krodark.asterion.update.underworld.client;

import net.minecraft.client.renderer.rendertype.AmneticRenderTypeAccess;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.PerformanceGovernor;
import net.krodark.asterion.update.underworld.world.UnderworldTerrain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Cached shoreline geometry. Wave displacement and normals are evaluated on the GPU. */
public final class LimboWaterRenderer {
    private static final RenderType SURFACE = AmneticRenderTypeAccess.create("asterion/limbo_water",
            RenderSetup.builder(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET, RenderPipelines.FOG_SNIPPET)
                    .withLocation(Asterion.id("pipeline/limbo_water"))
                    .withVertexShader(Asterion.id("core/limbo_water"))
                    .withFragmentShader(Asterion.id("core/limbo_water"))
                    .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
                    .withSampler("Sampler0")
                    .withSampler("Sampler1")
                    .withCull(false).withDepthStencilState(DepthStencilState.DEFAULT).build())
                    .withTexture("Sampler0", net.minecraft.resources.Identifier.withDefaultNamespace("textures/block/water_still.png"))
                    .withTexture("Sampler1", FerryWakeTexture.ID)
                    .createRenderSetup());
    private static final Map<Long, Tile> TILES = new HashMap<>();
    private static ClientLevel trackedLevel;
    private static List<Tile> frame = List.of();
    private static double frameTicks;
    private static int frameQuality;
    private static Vec3 frameBoat;
    private static float boatCos, boatSin;
    private static int boatActivity;
    private static int boatPitch = 32, boatRoll = 32;
    private static volatile boolean enabled;
    private record Layer(int y, int[] vertices, int[] fineVertices, int[] shore) { }
    private record Tile(int x, int z, List<Layer> layers, int minY, int maxY, long refreshed) { }
    private LimboWaterRenderer() { }

    public static boolean replacesSurface(BlockAndTintGetter level, BlockPos pos) {
        return enabled && surface(level, pos);
    }

    private static boolean surface(BlockAndTintGetter level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.WATER) && level.getFluidState(pos).isSource()
                && !level.getFluidState(pos.above()).is(FluidTags.WATER)
                && !level.getBlockState(pos.above()).isSolidRender();
    }

    public static void initialize() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            TILES.clear(); frame = List.of(); trackedLevel = null; enabled = false;
            FerryWakeTexture.clear();
        });
        LevelRenderEvents.END_EXTRACTION.register(context -> {
            var client = Minecraft.getInstance();
            var level = context.level();
            boolean active = level.dimension().equals(Asterion.LIMBO_LEVEL) && !ShaderPackCompatibility.active();
            if (trackedLevel != level || enabled != active) {
                TILES.clear(); frame = List.of(); trackedLevel = level;
                boolean changed = enabled != active;
                enabled = active;
                if (changed) client.levelRenderer.allChanged();
            }
            if (!active) return;
            Vec3 camera = context.levelState().cameraRenderState.pos;
            frameTicks = level.getGameTime() + context.deltaTracker().getGameTimeDeltaPartialTick(false);
            frameQuality = PerformanceGovernor.quality();
            frameBoat = null;
            net.krodark.asterion.update.underworld.entity.CharonsFerryEntity frameFerry = null;
            float partial = context.deltaTracker().getGameTimeDeltaPartialTick(false);
            for (var ferry : level.getEntitiesOfClass(net.krodark.asterion.update.underworld.entity.CharonsFerryEntity.class,
                    new AABB(camera, camera).inflate(192))) {
                frameBoat = ferry.getPosition(partial);
                frameFerry = ferry;
                double yaw = Math.toRadians(ferry.getYRot(partial));
                boatCos = (float)Math.cos(yaw); boatSin = (float)Math.sin(yaw);
                boatActivity = 128 | (ferry.sailing() ? 64 : 0);
                boatPitch = Math.clamp(Math.round(ferry.rockingPitch(partial) * 2) + 32, 0, 63);
                boatRoll = Math.clamp(Math.round(ferry.rockingRoll(partial) * 2) + 32, 0, 63);
                break; // There is one shared ferry per Limbo instance.
            }
            FerryWakeTexture.prepare(level, frameFerry);
            // Limbo's native distance fog is fully opaque at 48 blocks. Four chunks
            // leave a full chunk of padding even when the camera crosses a boundary.
            // Do not stream thousands of invisible water quads at large view distances.
            int radius = Math.min(client.options.getEffectiveRenderDistance(), 4);
            int cx = ((int)Math.floor(camera.x)) >> 4, cz = ((int)Math.floor(camera.z)) >> 4;
            List<Tile> next = new ArrayList<>();
            int refreshBudget = 2, creationBudget = 3;
            var frustum = context.levelState().cameraRenderState.cullFrustum;
            for (int x = cx - radius; x <= cx + radius; x++) for (int z = cz - radius; z <= cz + radius; z++) {
                if (!level.getChunkSource().hasChunk(x, z)) continue;
                long key = BlockPos.asLong(x, 0, z);
                Tile tile = TILES.get(key);
                if (frustum != null && !frustum.isVisible(new AABB(x * 16, tile == null ? camera.y - 32 : tile.minY - 4,
                        z * 16, x * 16 + 16, tile == null ? camera.y + 20 : tile.maxY + 5, z * 16 + 16))) continue;
                // Refresh edits gradually, rather than rescanning all visible seabed in one frame.
                if (tile == null || (refreshBudget > 0 && level.getGameTime() - tile.refreshed > 100)) {
                    if (tile == null) {
                        if (creationBudget-- <= 0) continue;
                    } else refreshBudget--;
                    tile = topology(level, x * 16, z * 16, (int)Math.floor(camera.y)); TILES.put(key, tile);
                }
                if (!tile.layers.isEmpty()) next.add(tile);
            }
            TILES.entrySet().removeIf(e -> Math.abs((e.getValue().x >> 4) - cx) > radius
                    || Math.abs((e.getValue().z >> 4) - cz) > radius
                    || !level.getChunkSource().hasChunk(e.getValue().x >> 4, e.getValue().z >> 4));
            frame = List.copyOf(next);
        });
        LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(context -> {
            if (!enabled || frame.isEmpty()) return;
            var out = context.bufferSource().getBuffer(SURFACE);
            var pose = context.poseStack().last();
            Vec3 camera = context.levelState().cameraRenderState.pos;
            long wholeTick = (long)frameTicks;
            int fraction = (int)((frameTicks - wholeTick) * 255);
            // UV2 is a pair of raw shorts, used as the integer world clock, not a lightmap lookup.
            // Color carries shoreline attenuation, sub-tick time, and adaptive detail quality.
            int timeLow = (int)(wholeTick & 65535), timeHigh = (int)((wholeTick >>> 16) & 65535);
            for (Tile tile : frame) {
                boolean fine = frameQuality > 0 && Math.abs(tile.x + 8 - camera.x) < 40
                        && Math.abs(tile.z + 8 - camera.z) < 40;
                for (Layer layer : tile.layers) for (int vertex : fine ? layer.fineVertices : layer.vertices) {
                    int x = tile.x + vertex % 17, z = tile.z + vertex / 17;
                    // Standard overlay/normal attributes carry hull-relative coordinates and heading.
                    // No extra render pass, per-vertex wave evaluation, or per-frame GPU allocation.
                    int bx = frameBoat == null ? 0 : (int)Math.clamp(Math.round((x - frameBoat.x) * 128), -32767, 32767);
                    int bz = frameBoat == null ? 0 : (int)Math.clamp(Math.round((z - frameBoat.z) * 128), -32767, 32767);
                    float boatHeight = frameBoat == null ? 0 : (float)Math.clamp(
                            (frameBoat.y - layer.y - 8.0 / 9.0) / 8, -1, 1);
                    out.addVertex(pose, (float)(x - camera.x),
                                    (float)(layer.y + 8.0 / 9.0 - camera.y), (float)(z - camera.z))
                            .setUv(x, z).setUv2(timeLow, timeHigh)
                            .setUv1(bx, bz).setNormal(boatCos, boatHeight, boatSin)
                            .setColor(layer.shore[vertex], fraction,
                                    (fine ? 128 : 0) | (frameQuality > 0 ? 64 : 0) | boatPitch,
                                    frameBoat == null ? 0 : boatActivity | boatRoll);
                }
            }
            context.bufferSource().endBatch(SURFACE);
        });
    }

    private static Tile topology(ClientLevel level, int x, int z, int cameraY) {
        // Discover nearby exposed source surfaces once, then build one independent
        // mesh per Y level so separated pools never slope or stitch into each other.
        int[] surfaceY = new int[34 * 34];
        java.util.Arrays.fill(surfaceY, Integer.MIN_VALUE);
        var pos = new BlockPos.MutableBlockPos();
        int minScan = Math.max(level.getMinY(), Math.min(UnderworldTerrain.WATER_Y - 8, cameraY - 32));
        int maxScan = Math.min(level.getMaxY() - 1, Math.max(UnderworldTerrain.WATER_Y + 8, cameraY + 20));
        for (int dz = 0; dz < 34; dz++) for (int dx = 0; dx < 34; dx++) {
            int wx=x+dx-9,wz=z+dz-9;
            if (!level.getChunkSource().hasChunk(wx >> 4, wz >> 4)) continue;
            for (int y=maxScan;y>=minScan;y--) if (surface(level,pos.set(wx,y,wz))) {surfaceY[dz*34+dx]=y;break;}
        }
        Set<Integer> elevations=new HashSet<>();
        for(int dz=0;dz<16;dz++)for(int dx=0;dx<16;dx++){int y=surfaceY[(dz+9)*34+dx+9];if(y!=Integer.MIN_VALUE)elevations.add(y);}
        List<Layer> layers=new ArrayList<>();
        int minY=Integer.MAX_VALUE,maxY=Integer.MIN_VALUE;
        var below=new BlockPos.MutableBlockPos();
        for(int elevation:elevations){
            int[] depths=new int[34*34];
            for(int dz=0;dz<34;dz++)for(int dx=0;dx<34;dx++)if(surfaceY[dz*34+dx]==elevation){
                int depth=dx<8||dx>25||dz<8||dz>25?6:1;
                while(depth<6&&level.getFluidState(below.set(x+dx-9,elevation-depth,z+dz-9)).is(FluidTags.WATER))depth++;
                depths[dz*34+dx]=depth;
            }
            boolean[] wet=new boolean[256];boolean any=false;
            for(int i=0;i<wet.length;i++){wet[i]=depths[(i/16+9)*34+i%16+9]>0;any|=wet[i];}
            if(!any)continue;
            int[] shore=new int[17*17];
            for(int dz=0;dz<=16;dz++)for(int dx=0;dx<=16;dx++)shore[dz*17+dx]=Math.round(255*net.krodark.asterion.update.underworld.world.WaterShoreline.attenuation(depths,34,dx+9,dz+9));
            layers.add(new Layer(elevation,WaterSurfaceMesh.vertices(wet,shore),WaterSurfaceMesh.vertices(wet,new int[289]),shore));
            minY=Math.min(minY,elevation);maxY=Math.max(maxY,elevation);
        }
        if(layers.isEmpty()){minY=UnderworldTerrain.WATER_Y;maxY=minY;}
        return new Tile(x,z,List.copyOf(layers),minY,maxY,level.getGameTime());
    }
}
