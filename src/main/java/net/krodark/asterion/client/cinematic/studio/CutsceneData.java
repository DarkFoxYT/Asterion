package net.krodark.asterion.client.cinematic.studio;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;

/** Versioned, bounded on-disk contract; positions and matrix outputs are Minecraft blocks. */
public record CutsceneData(double duration, int fps, double aspect, boolean fullbright, double[] worldOrigin,
                           List<Key> camera, List<String> textures, List<Actor> actors) {
    public record Key(double time, double[] position, double[] target, double fov, double roll, String ease) { }
    public record Part(int texture, float[][] vertices) { }
    public record Actor(String name, List<Part> parts, float[][] samples) { }

    public static CutsceneData parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        require("asterion_cutscene".equals(root.get("format").getAsString()) && root.get("version").getAsInt() == 1,
                "Unsupported cutscene format/version");
        require("blocks".equals(root.get("units").getAsString()), "Scene must use blocks");
        double duration = number(root, "duration", .05, 3600);
        int fps = root.get("fps").getAsInt();
        require(fps == 20 || fps == 30 || fps == 60 || fps == 120, "Unsupported sample rate");
        double aspect = number(root, "aspect", .2, 5);
        List<Key> keys = new ArrayList<>();
        JsonArray camera = root.getAsJsonArray("camera");
        require(camera.size() > 0 && camera.size() <= 100000, "Invalid camera key count");
        double previous = -1;
        for (var entry : camera) {
            JsonObject k = entry.getAsJsonObject();
            double time = number(k, "time", 0, duration);
            require(time > previous, "Camera keys must have strictly increasing times");
            double[] position = vector(k.getAsJsonArray("position"), 3, 1e6), target = vector(k.getAsJsonArray("target"), 3, 1e6);
            require(distanceSquared(position, target) > 1e-12, "Camera target equals position");
            String ease = k.get("ease").getAsString();
            require(List.of("linear", "smooth", "hold").contains(ease), "Unsupported camera interpolation");
            keys.add(new Key(time, position, target, number(k, "fov", 1, 160), number(k, "roll", -1e6, 1e6), ease));
            previous = time;
        }
        List<String> textures = new ArrayList<>();
        require(root.getAsJsonArray("textures").size() <= 256, "Too many textures");
        for (var entry : root.getAsJsonArray("textures")) {
            String png = entry.getAsJsonObject().get("png").getAsString();
            require(png.length() <= 8 * 1024 * 1024, "Texture exceeds 8 MB");
            textures.add(png);
        }
        int frames = (int)Math.ceil(duration * fps) + 1, totalVertices = 0;
        JsonArray objects = root.getAsJsonArray("objects");
        require((long)objects.size() * frames <= 500000, "Too many actor samples");
        List<Actor> actors = new ArrayList<>();
        for (var entry : objects) {
            JsonObject o = entry.getAsJsonObject();
            List<Part> parts = new ArrayList<>();
            for (var part : o.getAsJsonArray("parts")) {
                JsonObject p = part.getAsJsonObject();
                int texture = p.get("texture").getAsInt();
                require(texture >= -1 && texture < textures.size(), "Invalid texture index");
                JsonArray vertices = p.getAsJsonArray("vertices");
                totalVertices += vertices.size();
                require(vertices.size() % 3 == 0 && totalVertices <= 1000000, "Invalid or excessive triangle count");
                float[][] points = new float[vertices.size()][];
                for (int i = 0; i < points.length; i++) points[i] = floats(vector(vertices.get(i).getAsJsonArray(), 8, 1e6));
                parts.add(new Part(texture, points));
            }
            JsonArray samples = o.getAsJsonArray("samples");
            require(samples.size() == frames, "Actor sample count differs from timeline");
            float[][] transforms = new float[frames][];
            for (int i = 0; i < frames; i++) {
                transforms[i] = floats(vector(samples.get(i).getAsJsonArray(), 16, 1e6));
                float[] m = transforms[i];
                require(m[3] == 0 && m[7] == 0 && m[11] == 0 && m[15] == 1, "Actor transform must be affine");
            }
            actors.add(new Actor(o.get("name").getAsString(), List.copyOf(parts), transforms));
        }
        double[] worldOrigin = root.has("world_origin") ? vector(root.getAsJsonArray("world_origin"), 3, 30000000) : null;
        return new CutsceneData(duration, fps, aspect, root.get("fullbright").getAsBoolean(), worldOrigin, List.copyOf(keys), List.copyOf(textures), List.copyOf(actors));
    }

    public Key cameraAt(double time) {
        if (time <= camera.getFirst().time) return camera.getFirst();
        if (time >= camera.getLast().time) return camera.getLast();
        int low = 0, high = camera.size() - 1;
        while (high - low > 1) { int mid = (low + high) >>> 1; if (camera.get(mid).time <= time) low = mid; else high = mid; }
        Key a = camera.get(low), b = camera.get(high);
        double t = (time - a.time) / (b.time - a.time);
        t = a.ease.equals("hold") ? 0 : a.ease.equals("smooth") ? t * t * (3 - 2 * t) : t;
        return new Key(time, mix(a.position, b.position, t), mix(a.target, b.target, t), lerp(a.fov, b.fov, t), lerp(a.roll, b.roll, t), a.ease);
    }

    public float[] matrixAt(Actor actor, double time) {
        time = Math.clamp(time, 0, duration);
        int a = Math.min((int)Math.floor(time * fps), actor.samples.length - 1), b = Math.min(a + 1, actor.samples.length - 1);
        double start = a / (double)fps, end = Math.min(duration, b / (double)fps);
        double t = end <= start ? 0 : Math.clamp((time - start) / (end - start), 0, 1);
        float[] result = new float[16];
        for (int i = 0; i < 16; i++) result[i] = (float)lerp(actor.samples[a][i], actor.samples[b][i], t);
        return result;
    }

    private static double number(JsonObject o, String name, double min, double max) {
        double value = o.get(name).getAsDouble(); require(Double.isFinite(value) && value >= min && value <= max, "Invalid " + name); return value;
    }
    private static double[] vector(JsonArray a, int size, double limit) {
        require(a.size() == size, "Invalid vector length"); double[] values = new double[size];
        for (int i = 0; i < size; i++) { values[i] = a.get(i).getAsDouble(); require(Double.isFinite(values[i]) && Math.abs(values[i]) <= limit, "Non-finite or excessive vector"); }
        return values;
    }
    private static float[] floats(double[] a) { float[] f = new float[a.length]; for (int i = 0; i < a.length; i++) f[i] = (float)a[i]; return f; }
    private static double distanceSquared(double[] a, double[] b) { double n = 0; for (int i = 0; i < 3; i++) n += (a[i] - b[i]) * (a[i] - b[i]); return n; }
    private static double[] mix(double[] a, double[] b, double t) { return new double[]{lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)}; }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
}
