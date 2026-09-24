import net.krodark.asterion.update.underworld.world.UnderworldTerrain;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import net.krodark.asterion.update.underworld.client.WaterSurfaceMesh;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Numeric swell regression plus real driver compilation/linking of the water material. */
public final class LimboWaterSmoke {
    public static void main(String[] args) throws Exception {
        checkMesh();
        WakeFieldSmoke.check();
        checkShoreAndDeck();
        if (net.krodark.asterion.event.LimboTempest.strength(12000) != 0
                || net.krodark.asterion.event.LimboTempest.strength(13200) < .99
                || net.krodark.asterion.event.LimboTempest.strength(15600) != 0)
            throw new AssertionError("Limbo tempest did not ramp in and out on schedule");
        if (net.krodark.asterion.event.LimboWhirlpool.strength(20000) != 0
                || net.krodark.asterion.event.LimboWhirlpool.strength(22000) < .99
                || net.krodark.asterion.event.LimboWhirlpool.strength(48000) != 0
                || net.krodark.asterion.event.LimboWhirlpool.funnel(16, 352, 22000) > -9.9
                || net.krodark.asterion.event.LimboWhirlpool.funnel(142, 352, 22000) != 0)
            throw new AssertionError("Limbo whirlpool envelope or footprint changed");
        net.krodark.asterion.event.LimboSeaCommands.receive(-1, 1000);
        if (net.krodark.asterion.event.LimboWhirlpool.strength(100000) < .99)
            throw new AssertionError("Manual whirlpool should persist until stopped");
        net.krodark.asterion.event.LimboSeaCommands.receive(1000, -2);
        if (net.krodark.asterion.event.LimboTempest.strength(1500) < .99
                || net.krodark.asterion.event.LimboWhirlpool.strength(22000) != 0)
            throw new AssertionError("Manual Limbo event override did not apply");
        net.krodark.asterion.event.LimboSeaCommands.receive(-1, -1);
        double min = 0, max = 0, dockMax = 0, maxStep = 0;
        for (int tick = 0; tick < 2400; tick += 3) {
            for (int x = -64; x <= 64; x += 4) {
                double h = UnderworldTerrain.waveHeight(x, 280, tick);
                if (!Double.isFinite(h) || Math.abs(h) > 4) throw new AssertionError("Unbounded swell: " + h);
                min = Math.min(min, h); max = Math.max(max, h);
                dockMax = Math.max(dockMax, Math.abs(UnderworldTerrain.waveHeight(x, 58, tick)));
                maxStep = Math.max(maxStep, Math.abs(h - UnderworldTerrain.waveHeight(x, 280, tick + .333333)));
            }
        }
        if (min > -1.6 || max < 1.8 || dockMax > 1.2 || maxStep > .08)
            throw new AssertionError("Swell range/continuity: " + min + ", " + max + ", " + dockMax + ", " + maxStep);
        System.out.printf("PASS waves: trough %.2f, crest %.2f blocks; dock max %.2f; frame motion %.3f%n", min, max, dockMax, maxStep);
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW initialization failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long window = GLFW.glfwCreateWindow(32, 32, "Water shader validation", 0, 0);
        if (window == 0) throw new AssertionError("No OpenGL context");
        try {
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
            FerryHullSmoke.check();
            int vertex = shader("vsh", GL20.GL_VERTEX_SHADER), fragment = shader("fsh", GL20.GL_FRAGMENT_SHADER);
            int program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertex); GL20.glAttachShader(program, fragment);
            GL30.glTransformFeedbackVaryings(program, new String[]{"surfacePosition"}, GL30.GL_INTERLEAVED_ATTRIBS);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0)
                throw new AssertionError(GL20.glGetProgramInfoLog(program));
            checkGpuWaves(program);
            WaterVisualSmoke.render(program);
            GL20.glDeleteProgram(program); GL20.glDeleteShader(vertex); GL20.glDeleteShader(fragment);
            System.out.println("PASS water vertex/fragment shaders compile and link on the OpenGL driver.");
        } finally { GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate(); }
    }
    private static void checkMesh() {
        boolean[] wet = new boolean[256]; int[] shore = new int[289];
        java.util.Arrays.fill(wet, true); java.util.Arrays.fill(shore, 255);
        if (WaterSurfaceMesh.vertices(wet, shore).length != 256) throw new AssertionError("Open-water mesh not reduced 4x");
        var random = new java.util.Random(83);
        for (int trial = 0; trial < 100; trial++) {
            for (int i = 0; i < wet.length; i++) wet[i] = random.nextDouble() > .2;
            for (int i = 0; i < shore.length; i++) shore[i] = random.nextBoolean() ? 255 : 0;
            int[] vertices = WaterSurfaceMesh.vertices(wet, shore), coverage = new int[256];
            for (int i = 0; i < vertices.length; i += 4) {
                int x = vertices[i] % 17, z = vertices[i] / 17;
                int endX = vertices[i+2] % 17, endZ = vertices[i+2] / 17;
                for (int dz = z; dz < endZ; dz++) for (int dx = x; dx < endX; dx++) coverage[dz * 16 + dx]++;
            }
            for (int i = 0; i < wet.length; i++) if (coverage[i] != (wet[i] ? 1 : 0))
                throw new AssertionError("Shoreline hole or overlap at " + i);
        }
        System.out.println("PASS mesh: 75% fewer open-water vertices; 100 shoreline coverage cases.");
    }
    private static void checkShoreAndDeck() {
        int[] depths = new int[34*34];
        for (int z=0;z<34;z++) for(int x=0;x<34;x++) depths[z*34+x]=x<9?0:6;
        float previous = -1;
        for(int x=9;x<=17;x++) {
            float factor=net.krodark.asterion.update.underworld.world.WaterShoreline.attenuation(depths,34,x,17);
            if(factor<previous || x==9 && factor!=0 || x==17 && factor!=1)
                throw new AssertionError("Shoreline must rise smoothly from calm to open sea");
            previous=factor;
        }
        for(float yaw:new float[]{0,47,135,270}) for(float pitch:new float[]{-12,0,12}) for(float roll:new float[]{-10,0,10}) {
            double y=Math.toRadians(yaw),p=Math.toRadians(pitch),r=Math.toRadians(roll);
            var rotation=new org.joml.Quaternionf().rotationY((float)(Math.PI-y)).rotateX((float)p).rotateZ((float)r);
            var point=rotation.transform(new org.joml.Vector3f(.8F,0,1.7F));
            double localX=-point.x*Math.cos(y)-point.z*Math.sin(y);
            double localZ=point.x*Math.sin(y)-point.z*Math.cos(y);
            double plane=localX*Math.tan(r)/Math.cos(p)-localZ*Math.tan(p);
            if(Math.abs(plane-point.y)>1e-5)throw new AssertionError("Walking plane disagrees with rendered boat");
        }
        System.out.println("PASS eight-block shoreline falloff and tilted-deck plane across headings.");
    }
    private static void checkGpuWaves(int program) {
        GL20.glUseProgram(program);
        int wakeTexture=WakeFieldSmoke.upload(program,false);
        int vao = GL30.glGenVertexArrays(); GL30.glBindVertexArray(vao);
        int buffer = GL15.glGenBuffers(); GL15.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, buffer);
        GL15.glBufferData(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 12L, GL15.GL_STREAM_READ);
        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, buffer);
        org.lwjgl.opengl.GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);
        GL20.glVertexAttrib3f(GL20.glGetAttribLocation(program, "Position"), 0, 0, 0);
        double maxError = 0;
        for (double ticks : new double[]{0, 1234.5, 13200.25, 22000.25, 65535.9, 65536.1, 180000.25}) {
            long whole = (long)ticks;
            int fraction = (int)((ticks - whole) * 15);
            float partial = fraction / 16F;
            GL20.glVertexAttrib4f(GL20.glGetAttribLocation(program, "Color"), 1, fraction / 255F, 1, 0);
            int tempest = (int)Math.round(net.krodark.asterion.event.LimboTempest.strength(whole + partial) * 255);
            int whirlpool = (int)Math.round(net.krodark.asterion.event.LimboWhirlpool.strength(whole + partial) * 255);
            GL30.glVertexAttribI2i(GL20.glGetAttribLocation(program, "UV1"), tempest << 8, whirlpool << 8);
            GL30.glVertexAttribI2i(GL20.glGetAttribLocation(program, "UV2"), (short)(whole & 65535), (short)(whole >>> 16));
            for (int x : new int[]{-32, 0, 14, 18, 80, 110}) for (int z : new int[]{58, 180, 280, 350, 410}) {
                GL20.glVertexAttrib2f(GL20.glGetAttribLocation(program, "UV0"), x, z);
                GL30.glBeginTransformFeedback(org.lwjgl.opengl.GL11.GL_POINTS);
                org.lwjgl.opengl.GL11.glDrawArrays(org.lwjgl.opengl.GL11.GL_POINTS, 0, 1);
                GL30.glEndTransformFeedback();
                float[] position = new float[3];
                GL15.glGetBufferSubData(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, position);
                double error = Math.abs(position[1] - net.krodark.asterion.update.underworld.world.UnderworldWaves.sample(x, z, whole + partial).height());
                maxError = Math.max(maxError, error);
                if (!Float.isFinite(position[1]) || error > .008) throw new AssertionError("GPU/boat mismatch: " + error);
                double worldHeight = UnderworldTerrain.waveHeight(x + position[0], z + position[2], whole + partial);
                if (Math.abs(worldHeight - position[1]) > .012) throw new AssertionError("Deformed surface buoyancy mismatch");
            }
        }
        net.krodark.asterion.event.LimboWhirlpool.setCenter(32, 200);
        GL20.glVertexAttrib4f(GL20.glGetAttribLocation(program, "Color"), 1, 0, 1, 0);
        GL30.glVertexAttribI2i(GL20.glGetAttribLocation(program, "UV2"), 22000, 0);
        GL30.glVertexAttribI2i(GL20.glGetAttribLocation(program, "UV1"), 0,
                (int)Math.round(net.krodark.asterion.event.LimboWhirlpool.strength(22000) * 255) << 8);
        GL20.glVertexAttrib2f(GL20.glGetAttribLocation(program, "UV0"),
                32 + (32 / 4 + 128.5F) / 512F, 200 + (200 / 4 + .5F) / 512F);
        GL30.glBeginTransformFeedback(org.lwjgl.opengl.GL11.GL_POINTS);
        org.lwjgl.opengl.GL11.glDrawArrays(org.lwjgl.opengl.GL11.GL_POINTS, 0, 1);
        GL30.glEndTransformFeedback();
        float[] moved = new float[3];
        GL15.glGetBufferSubData(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, moved);
        if (Math.abs(moved[1] - net.krodark.asterion.update.underworld.world.UnderworldWaves.sample(32, 200, 22000).height()) > .008)
            throw new AssertionError("Relocated whirlpool mesh and buoyancy differ");
        net.krodark.asterion.event.LimboWhirlpool.setCenter(16, 352);
        org.lwjgl.opengl.GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);
        GL15.glDeleteBuffers(buffer); GL30.glDeleteVertexArrays(vao); org.lwjgl.opengl.GL11.glDeleteTextures(wakeTexture); GL20.glUseProgram(0);
        System.out.printf("PASS GPU displacement matches boat waves across clock rollover: max error %.5f blocks.%n", maxError);
    }
    private static int shader(String suffix, int type) throws Exception {
        String source = Files.readString(Path.of("src/main/resources/assets/asterion/shaders/core/limbo_water." + suffix));
        String resolved = ShaderIncludes.resolve(source);
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, resolved); GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0)
            throw new AssertionError(suffix + ": " + GL20.glGetShaderInfoLog(shader));
        return shader;
    }
}
