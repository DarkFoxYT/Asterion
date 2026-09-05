package net.krodark.asterion.dev.verification;

import com.meekdev.amnetic.client.instanced.internal.CullTargets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.krodark.asterion.client.render.OrderedParticleCuller;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL43.*;

/** Real GPU checks: stable compaction and pixel comparisons with the pre-optimization shaders. */
public final class RenderPerformanceRegression {
    private static final int SIZE = 64;
    public static void main(String[] args) throws Exception {
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window = GLFW.glfwCreateWindow(SIZE, SIZE, "Renderer parity", 0, 0);
        if (window == 0) throw new AssertionError("OpenGL 4.3 required for GPU culling verification");
        try {
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
            com.mojang.blaze3d.systems.RenderSystem.initRenderThread();
            System.out.println("GPU: " + glGetString(GL_RENDERER));
            boolean sunOnly = Arrays.asList(args).contains("--dead-sun-only");
            if (!sunOnly) culling();
            shaders(sunOnly ? List.of("dead_sun") : List.of("dead_sun", "volume_integrate"));
            if (glGetError() != GL_NO_ERROR) throw new AssertionError("OpenGL error");
        } finally { GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate(); }
    }

    private static void culling() throws Exception {
        int scan = compute("cull_scan"), scatter = compute("cull_scatter");
        int bounds = glGenBuffers(), input = glGenBuffers(), output = glGenBuffers(), command = glGenBuffers();
        try (var culler = new OrderedParticleCuller(scan, scatter)) {
            var targets = new CullTargets(bounds, input, output, command);
            for (int count : new int[]{1, 63, 64, 65, 257, 1024, 2048}) for (int mode = 0; mode < 3; mode++) {
                Matrix4f matrix = new Matrix4f().perspective(1.2F, 1, .1F, 128).rotateY(.2F);
                var frustum = new FrustumIntersection(matrix);
                float[] spheres = new float[count * 4];
                int[] payload = new int[count * 12];
                List<Integer> expected = new ArrayList<>();
                Random random = new Random(771);
                for (int i = 0; i < count; i++) {
                    float x = mode == 0 ? random.nextFloat() * 100 - 50 : 0;
                    float y = mode == 0 ? random.nextFloat() * 40 - 20 : 0;
                    float z = mode == 2 ? 50 : -20;
                    float radius = .5F + i % 4;
                    // Use the same large-coordinate subtraction that fills the production payload.
                    x = (float)((29_000_000D + x) - 29_000_000D);
                    spheres[i * 4] = x; spheres[i * 4 + 1] = y;
                    spheres[i * 4 + 2] = z; spheres[i * 4 + 3] = radius;
                    if (frustum.testSphere(x, y, z, radius)) expected.add(i);
                    for (int word = 0; word < 12; word++) payload[i * 12 + word] = i * 101 + word;
                }
                glBindBuffer(GL_SHADER_STORAGE_BUFFER, bounds); glBufferData(GL_SHADER_STORAGE_BUFFER, spheres, GL_DYNAMIC_DRAW);
                glBindBuffer(GL_SHADER_STORAGE_BUFFER, input); glBufferData(GL_SHADER_STORAGE_BUFFER, payload, GL_DYNAMIC_DRAW);
                glBindBuffer(GL_SHADER_STORAGE_BUFFER, output); glBufferData(GL_SHADER_STORAGE_BUFFER, (long)payload.length * 4, GL_DYNAMIC_DRAW);
                glBindBuffer(GL_SHADER_STORAGE_BUFFER, command); glBufferData(GL_SHADER_STORAGE_BUFFER, new int[]{6, 987, 0, 0, 0}, GL_DYNAMIC_DRAW);
                if (count >= 65) glBindBufferRange(GL_SHADER_STORAGE_BUFFER, 4, input, 256, 256);
                for (int repeat = 0; repeat < 4; repeat++) {
                    culler.cull(targets, count, 48, matrix);
                    if (count >= 65) check(glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, 4) == input
                            && glGetInteger64i(GL_SHADER_STORAGE_BUFFER_START, 4) == 256
                            && glGetInteger64i(GL_SHADER_STORAGE_BUFFER_SIZE, 4) == 256, "Indexed buffer range leaked");
                    glBindBuffer(GL_SHADER_STORAGE_BUFFER, command);
                    int[] indirect = new int[5]; glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, indirect);
                    check(indirect[0] == 6 && indirect[1] == expected.size(), "Indirect count/geometry corrupted");
                    glBindBuffer(GL_SHADER_STORAGE_BUFFER, output);
                    int[] actual = new int[payload.length]; glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, actual);
                    for (int i = 0; i < expected.size(); i++) for (int word = 0; word < 12; word++)
                        check(actual[i * 12 + word] == payload[expected.get(i) * 12 + word], "Alpha order/payload changed");
                }
            }
            System.out.println("PASS: GPU stable compaction, 64-lane boundaries, 2048 instances, empty/full results, buffer reuse and camera-relative bounds");
        } finally { glDeleteBuffers(bounds); glDeleteBuffers(input); glDeleteBuffers(output); glDeleteBuffers(command); }
    }

    private static void shaders(List<String> names) throws Exception {
        String vertex = """
                #version 330
                out vec2 texCoord;
                void main() { vec2 p=vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                    texCoord=p; gl_Position=vec4(p*2.0-1.0,0,1); }
                """;
        int vao = glGenVertexArrays(), fbo = glGenFramebuffers(), color = glGenTextures(), depth = glGenTextures();
        glBindVertexArray(vao); glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glBindTexture(GL_TEXTURE_2D, color);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, SIZE, SIZE, 0, GL_RGBA, GL_FLOAT, 0L);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0);
        glViewport(0, 0, SIZE, SIZE); glDisable(GL_DEPTH_TEST); glDisable(GL_BLEND); glDisable(GL_CULL_FACE);
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, depth);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        float maxError = 0;
        try {
            for (String name : names) {
                int before = program(vertex, Files.readString(Path.of("docs/verification/shader-baselines", name + ".fsh")));
                int after = program(vertex, source("post/dimension/" + name + ".fsh"));
                try {
                    for (int scenario = 0; scenario < 96; scenario++) {
                        float[] depths = new float[SIZE * SIZE];
                        Arrays.fill(depths, scenario % 3 == 0 ? 1 : scenario % 3 == 1 ? .5F : .999F);
                        glBindTexture(GL_TEXTURE_2D, depth);
                        glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, SIZE, SIZE, 0, GL_RED, GL_FLOAT, depths);
                        Map<String, float[]> uniforms = uniforms(scenario);
                        float[] reference = render(before, uniforms), actual = render(after, uniforms);
                        for (int pixel = 0; pixel < actual.length; pixel++) {
                            float error = Math.abs(reference[pixel] - actual[pixel]);
                            check(Float.isFinite(error) && error <= .00001F, name + " changed a pixel: " + error + " scenario " + scenario);
                            maxError = Math.max(maxError, error);
                        }
                    }
                    if (name.equals("dead_sun")) oppositeSun(before, after, depth);
                } finally { glDeleteProgram(before); glDeleteProgram(after); }
            }
            System.out.println("PASS: " + names.size() * 96 + " original/optimized shader comparisons; max RGBA error = " + maxError);
        } finally { glDeleteTextures(depth); glDeleteTextures(color); glDeleteFramebuffers(fbo); glDeleteVertexArrays(vao); }
    }

    private static void oppositeSun(int before, int after, int depth) {
        float[] sky = new float[SIZE * SIZE];
        Arrays.fill(sky, 1F);
        glBindTexture(GL_TEXTURE_2D, depth);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, SIZE, SIZE, 0, GL_RED, GL_FLOAT, sky);
        int comparisons = 0;
        for (int quality = 0; quality < 3; quality++) for (float eclipse : new float[]{0, .5F, 1})
            for (float yaw : new float[]{-.5F, 0, .5F}) for (float x : new float[]{0, 29_000_000}) {
                var values = uniforms(0);
                float[] world = values.get("WorldData");
                new Matrix4f().perspective(1.2F, 1, .1F, 256).rotateY(yaw).invert().get(world);
                world[16] = x;
                values.put("DeadSunData", new float[]{x, 64, 60, 18});
                values.put("AsterionQuality", new float[]{quality});
                values.put("EclipseData", new float[]{eclipse});
                float[] reference = render(before, values), actual = render(after, values);
                boolean reproduced = false;
                for (int pixel = 0; pixel < actual.length; pixel++) {
                    reproduced |= reference[pixel] > .001F;
                    check(Float.isFinite(actual[pixel]) && Math.abs(actual[pixel]) < .000001F,
                            "Opposite-side sun survived: quality=" + quality + ", eclipse=" + eclipse + ", yaw=" + yaw);
                }
                check(reproduced, "Opposite-side test did not reproduce the original mirage");
                comparisons++;
            }
        System.out.println("PASS: " + comparisons + " opposite-side sun renders are transparent across quality, eclipse, camera rotation and distant coordinates");
    }

    private static Map<String, float[]> uniforms(int scenario) {
        var result = new LinkedHashMap<String, float[]>();
        float[] world = new float[24];
        new Matrix4f().perspective(1.2F, 1, .1F, 256).rotateY(scenario >= 48 ? .3F : 0).invert().get(world);
        world[16] = scenario >= 48 ? 29_000_000 : 0;
        world[17] = 64; world[19] = (scenario / 24) % 2; world[22] = -1;
        result.put("WorldData", world);
        result.put("SamplerInfo", new float[]{SIZE, SIZE, SIZE, SIZE});
        result.put("DustTime", new float[]{163.25F + scenario * 17.7F});
        result.put("DeadSunData", new float[]{world[16], 76, -60, 18});
        result.put("DeadSunTuning", new float[]{2.2F, 1, 1.5F, 1});
        result.put("DeadSunOpacity", new float[]{scenario == 23 ? 0 : .9F});
        result.put("DeadSunCoreColor", new float[]{1, .2F, .1F});
        result.put("DeadSunCoronaColor", new float[]{.7F, .1F, .05F});
        result.put("EclipseData", new float[]{(scenario / 3) % 2});
        result.put("EntryRadiance", new float[]{scenario >= 12 ? .7F : 0});
        result.put("FinaleProgress", new float[]{scenario >= 18 ? .65F : 0});
        result.put("Intensity", new float[]{1});
        result.put("AsterionStrength", new float[]{scenario == 22 ? 0 : 1});
        result.put("AsterionQuality", new float[]{(scenario / 3) % 3});
        result.put("AtmosphereSettings", new float[]{scenario == 23 ? 0 : 1.2F, .9F, 1});
        result.put("DustColor", new float[]{.6F, .3F, .2F});
        result.put("FogColor", new float[]{.2F, .15F, .1F});
        return result;
    }

    private static float[] render(int program, Map<String, float[]> uniforms) {
        glUseProgram(program); glUniform1i(glGetUniformLocation(program, "DepthSampler"), 0);
        List<Integer> buffers = new ArrayList<>();
        int binding = 0;
        try {
            for (var entry : uniforms.entrySet()) {
                int block = glGetUniformBlockIndex(program, entry.getKey());
                if (block == GL_INVALID_INDEX) continue;
                int buffer = glGenBuffers(); buffers.add(buffer);
                glBindBuffer(GL_UNIFORM_BUFFER, buffer);
                int size = glGetActiveUniformBlocki(program, block, GL_UNIFORM_BLOCK_DATA_SIZE) / 4;
                glBufferData(GL_UNIFORM_BUFFER, Arrays.copyOf(entry.getValue(), size), GL_STATIC_DRAW);
                glBindBufferBase(GL_UNIFORM_BUFFER, binding, buffer);
                glUniformBlockBinding(program, block, binding++);
            }
            glDrawArrays(GL_TRIANGLES, 0, 3);
            float[] pixels = new float[SIZE * SIZE * 4]; glReadPixels(0, 0, SIZE, SIZE, GL_RGBA, GL_FLOAT, pixels);
            return pixels;
        } finally { for (int buffer : buffers) glDeleteBuffers(buffer); }
    }
    private static int compute(String name) throws Exception {
        int program = glCreateProgram(); attach(program, GL_COMPUTE_SHADER, source("particle/" + name + ".comp"));
        link(program); return program;
    }
    private static int program(String vertex, String fragment) {
        int program = glCreateProgram(); attach(program, GL_VERTEX_SHADER, vertex); attach(program, GL_FRAGMENT_SHADER, fragment);
        link(program); return program;
    }
    private static void attach(int program, int kind, String source) {
        int shader = glCreateShader(kind); glShaderSource(shader, source); glCompileShader(shader);
        check(glGetShaderi(shader, GL_COMPILE_STATUS) == GL_TRUE, glGetShaderInfoLog(shader));
        glAttachShader(program, shader); glDeleteShader(shader);
    }
    private static void link(int program) { glLinkProgram(program); check(glGetProgrami(program, GL_LINK_STATUS) == GL_TRUE, glGetProgramInfoLog(program)); }
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/resources/assets/asterion/shaders", path));
    }
    private static void check(boolean pass, String message) { if (!pass) throw new AssertionError(message); }
}
