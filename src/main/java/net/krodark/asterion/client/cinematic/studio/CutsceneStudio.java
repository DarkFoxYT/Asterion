package net.krodark.asterion.client.cinematic.studio;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.AsterionClient;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/** Local director playback. Actors are visual scene meshes; this never moves server entities. */
public final class CutsceneStudio {
    private static CutsceneData data;
    private static final List<Identifier> textures = new ArrayList<>();
    private static Identifier white;
    private static ClientLevel level;
    private static Vec3 origin = Vec3.ZERO;
    private static double seconds;
    private static boolean paused;
    private static CameraType previousCamera;
    private CutsceneStudio() { }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(CutsceneStudio::tick);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(literal("cutscene")
                .then(literal("play").then(argument("file", StringArgumentType.word()).executes(context -> {
                    try { play(StringArgumentType.getString(context, "file")); context.getSource().sendFeedback(Component.literal("Cutscene playing. /cutscene stop exits.")); return 1; }
                    catch (Exception error) { context.getSource().sendError(Component.literal("Cutscene: " + error.getMessage())); return 0; }
                })))
                .then(literal("stop").executes(context -> { stop(); return 1; }))
                .then(literal("reference").then(argument("name", StringArgumentType.word())
                        .then(argument("radius", IntegerArgumentType.integer(1, 24)).executes(context -> {
                            try {
                                Path path = exportReference(StringArgumentType.getString(context, "name"), IntegerArgumentType.getInteger(context, "radius"));
                                context.getSource().sendFeedback(Component.literal("World reference saved: " + path + ". Import it in Blockbench → Cutscene → World.")); return 1;
                            } catch (Exception error) { context.getSource().sendError(Component.literal("Reference: " + error.getMessage())); return 0; }
                        }))))
                .then(literal("pause").executes(context -> { paused = true; return 1; }))
                .then(literal("resume").executes(context -> { paused = false; return 1; }))
                .then(literal("seek").then(argument("seconds", DoubleArgumentType.doubleArg(0, 3600)).executes(context -> {
                    if (data != null) { seconds = Math.min(data.duration(), DoubleArgumentType.getDouble(context, "seconds")); paused = true; } return 1;
                })))
                .then(literal("origin").then(argument("x", DoubleArgumentType.doubleArg(-30000000, 30000000))
                        .then(argument("y", DoubleArgumentType.doubleArg(-30000000, 30000000))
                        .then(argument("z", DoubleArgumentType.doubleArg(-30000000, 30000000)).executes(context -> {
                            origin = new Vec3(DoubleArgumentType.getDouble(context, "x"), DoubleArgumentType.getDouble(context, "y"), DoubleArgumentType.getDouble(context, "z")); return 1;
                        })))))));
    }

    public static boolean active() { return data != null; }
    public static double time(float partial) { return data == null ? 0 : Math.min(data.duration(), seconds + (paused ? 0 : partial / 20.0)); }
    public static CutsceneData.Key camera(float partial) { return data == null ? null : data.cameraAt(time(partial)); }
    public static Vec3 position(CutsceneData.Key key) { return origin.add(key.position()[0], key.position()[1], key.position()[2]); }
    public static float yaw(CutsceneData.Key key) { return (float)Math.toDegrees(Math.atan2(-(key.target()[0] - key.position()[0]), key.target()[2] - key.position()[2])); }
    public static float pitch(CutsceneData.Key key) {
        double x = key.target()[0] - key.position()[0], z = key.target()[2] - key.position()[2];
        return (float)-Math.toDegrees(Math.atan2(key.target()[1] - key.position()[1], Math.hypot(x, z)));
    }
    private static void tick(Minecraft client) {
        if (!active()) return;
        if (client.level != level || client.player == null || AsterionClient.isPlayback(client)) { stop(); return; }
        if (client.screen instanceof net.minecraft.client.gui.screens.PauseScreen) { stop(); return; }
        if (!paused && !client.isPaused()) {
            if (seconds >= data.duration()) { stop(); return; }
            seconds = Math.min(data.duration(), seconds + .05);
        }
    }

    public static void play(String file) throws Exception {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) throw new IllegalStateException("Join a world first");
        if (AsterionClient.isPlayback(client)) throw new IllegalStateException("Leave replay playback first");
        if (!file.matches("[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,100}")) throw new IllegalArgumentException("Use a filename, without folders");
        Path folder = client.gameDirectory.toPath().resolve("cutscenes"); Files.createDirectories(folder);
        Path path = folder.resolve(file.endsWith(".json") ? file : file + ".cutscene.json");
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Place the export in " + folder + ": " + path.getFileName());
        if (Files.size(path) > 64 * 1024 * 1024) throw new IllegalArgumentException("Scene exceeds 64 MB");
        CutsceneData loaded = CutsceneData.parse(Files.readString(path));
        // Decode and bound all images before replacing the currently running scene.
        List<NativeImage> images = new ArrayList<>();
        boolean replaced = false;
        try {
            long pixels = 0;
            for (String png : loaded.textures()) {
                byte[] bytes = Base64.getDecoder().decode(png);
                if (bytes.length < 24 || bytes[0] != (byte)137 || bytes[1] != 80 || bytes[2] != 78 || bytes[3] != 71) throw new IllegalArgumentException("Invalid PNG texture");
                var header = java.nio.ByteBuffer.wrap(bytes);
                int width = header.getInt(16), height = header.getInt(20);
                if (width < 1 || height < 1 || width > 4096 || height > 4096 || (pixels += (long)width * height) > 32 * 1024 * 1024) throw new IllegalArgumentException("Textures exceed image size budget");
                images.add(NativeImage.read(new ByteArrayInputStream(bytes)));
            }
            stop();
            replaced = true;
            for (int i = 0; i < images.size(); i++) {
                Identifier id = Asterion.id("cutscene/texture_" + i);
                client.getTextureManager().register(id, new DynamicTexture(() -> "Cutscene texture", images.get(i)));
                textures.add(id); images.set(i, null);
            }
            if (white == null) {
                NativeImage image = new NativeImage(1, 1, false); image.setPixel(0, 0, -1);
                white = Asterion.id("cutscene/white"); client.getTextureManager().register(white, new DynamicTexture(() -> "Cutscene white", image));
            }
            data = loaded; level = client.level; origin = loaded.worldOrigin() == null ? client.player.position()
                    : new Vec3(loaded.worldOrigin()[0], loaded.worldOrigin()[1], loaded.worldOrigin()[2]); seconds = 0; paused = false;
            previousCamera = client.options.getCameraType(); client.options.setCameraType(CameraType.FIRST_PERSON);
        } catch (Exception error) { if (replaced) stop(); throw error; }
        finally { for (NativeImage image : images) if (image != null) image.close(); }
    }

    /** Exports only a bounded, already loaded client region; never requests or generates chunks. */
    private static Path exportReference(String name, int radius) throws Exception {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) throw new IllegalStateException("Join a world first");
        if (!name.matches("[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,80}")) throw new IllegalArgumentException("Use a short filename without folders");
        BlockPos center = client.player.blockPosition(), min = center.offset(-radius, -radius, -radius);
        int size = radius * 2 + 1;
        for (int x = min.getX() >> 4; x <= (min.getX() + size - 1) >> 4; x++)
            for (int z = min.getZ() >> 4; z <= (min.getZ() + size - 1) >> 4; z++)
                if (!client.level.hasChunk(x, z)) throw new IllegalArgumentException("Region includes unloaded chunks. Move closer or reduce the radius.");
        JsonObject root = new JsonObject(); root.addProperty("format", "asterion_reference"); root.addProperty("version", 1);
        JsonArray dimensions = new JsonArray(), anchor = new JsonArray();
        for (int value : new int[]{size, size, size}) dimensions.add(value);
        for (int value : new int[]{min.getX(), min.getY(), min.getZ()}) anchor.add(value);
        root.add("size", dimensions); root.add("worldOrigin", anchor);
        JsonArray palette = new JsonArray(), blocks = new JsonArray(); java.util.Map<String, Integer> ids = new java.util.LinkedHashMap<>();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int y = 0; y < size; y++) for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
            position.set(min.getX() + x, min.getY() + y, min.getZ() + z);
            var state = client.level.getBlockState(position); if (state.isAir()) continue;
            String block = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            int id = ids.computeIfAbsent(block, value -> { palette.add(value); return ids.size(); });
            JsonArray entry = new JsonArray(); entry.add(x); entry.add(y); entry.add(z); entry.add(id); blocks.add(entry);
        }
        root.add("palette", palette); root.add("blocks", blocks);
        Path folder = client.gameDirectory.toPath().resolve("cutscenes"); Files.createDirectories(folder);
        Path path = folder.resolve(name + ".reference.json");
        Files.writeString(path, root.toString(), java.nio.file.StandardOpenOption.CREATE_NEW);
        return path;
    }

    public static void stop() {
        Minecraft client = Minecraft.getInstance(); data = null; level = null; seconds = 0; paused = false;
        if (previousCamera != null) { client.options.setCameraType(previousCamera); previousCamera = null; }
        for (Identifier texture : textures) client.getTextureManager().release(texture);
        textures.clear();
    }

    public static void submit(PoseStack poses, LevelRenderState state, SubmitNodeCollector output) {
        Minecraft client = Minecraft.getInstance();
        if (data == null || client.level != level) return;
        double time = time(client.getDeltaTracker().getGameTimeDeltaPartialTick(true));
        Vec3 offset = origin.subtract(state.cameraRenderState.pos);
        for (CutsceneData.Actor actor : data.actors()) {
            Matrix4f matrix = new Matrix4f().set(data.matrixAt(actor, time));
            int light = data.fullbright() ? 0x00F000F0 : LevelRenderer.getLightCoords(level,
                    BlockPos.containing(origin.add(matrix.m30(), matrix.m31(), matrix.m32())));
            poses.pushPose(); poses.translate(offset.x, offset.y, offset.z); poses.mulPose(matrix);
            for (CutsceneData.Part part : actor.parts()) {
                Identifier texture = part.texture() < 0 ? white : textures.get(part.texture());
                output.submitCustomGeometry(poses, RenderTypes.entityTranslucent(texture, false), (pose, out) -> {
                    // Minecraft entity pipelines use quads; a duplicated final vertex represents each triangle.
                    float[][] vertices = part.vertices();
                    for (int i = 0; i < vertices.length; i += 3) for (int corner = 0; corner < 4; corner++) {
                        float[] v = vertices[i + Math.min(corner, 2)];
                        out.addVertex(pose, v[0], v[1], v[2]).setColor(-1).setUv(v[3], v[4])
                                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, v[5], v[6], v[7]);
                    }
                });
            }
            poses.popPose();
        }
    }
}
