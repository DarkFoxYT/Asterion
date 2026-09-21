import com.meekdev.amnetic.client.framebuffer.*;
import com.meekdev.amnetic.client.pipeline.*;
import com.meekdev.amnetic.client.pipeline.internal.GpuTimer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.joml.Matrix4f;
import java.nio.file.*;
import java.util.*;

/** Synthetic GPU pass benchmark using the installed Amnetic framebuffer, timer and profiler APIs. */
public final class AmneticShaderProfile {
    private static final Path ROOT = Path.of("src/main/resources/assets/asterion/shaders/post");
    public static void main(String[] args) throws Exception {
        // Validate real Minecraft post-chain schemas, including externally supplied targets.
        try (var paths = Files.walk(Path.of("src/main/resources/assets/asterion/post_effect"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                var json = com.google.gson.JsonParser.parseString(Files.readString(path));
                net.minecraft.client.renderer.PostChainConfig.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
            }
        }
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW unavailable");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long window = GLFW.glfwCreateWindow(64, 64, "Amnetic shader profile", 0, 0);
        if (window == 0) throw new AssertionError("No graphics context");
        try {
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities(); com.mojang.blaze3d.systems.RenderSystem.initRenderThread();
            System.out.println("Synthetic GPU benchmark: " + GL11.glGetString(GL11.GL_RENDERER));
            int vao = GL30.glGenVertexArrays(); GL30.glBindVertexArray(vao);
            int texture = GL11.glGenTextures(); GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, 1, 1, 0, GL11.GL_RGBA, GL11.GL_FLOAT, new float[]{1,1,1,1});
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            var record = PassProfiler.class.getDeclaredMethod("recordGpu", RenderStage.class, String.class, float.class);
            record.setAccessible(true);
            List<String> lines = new ArrayList<>();
            lines.add("shader,width,height,gpu_ms");
            try (var paths = Files.walk(ROOT)) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".fsh")).sorted().toList()) {
                    int vs = compile(GL20.GL_VERTEX_SHADER, "#version 330\nout vec2 texCoord; void main(){vec2 p=vec2((gl_VertexID<<1)&2, gl_VertexID&2); texCoord=p; gl_Position=vec4(p*2.0-1.0,0,1);}");
                    int fs = compile(GL20.GL_FRAGMENT_SHADER, ShaderIncludes.resolve(Files.readString(path)));
                    int program = GL20.glCreateProgram(); GL20.glAttachShader(program, vs); GL20.glAttachShader(program, fs); GL20.glLinkProgram(program);
                    if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) throw new AssertionError(path + GL20.glGetProgramInfoLog(program));
                    GL20.glUseProgram(program);
                    for (int width : new int[]{1280, 640, 426, 320}) {
                        int height = width * 9 / 16;
                        List<Integer> buffers = uniforms(program, width, height);
                        Framebuffer target = Framebuffers.fixed("profile/" + path.getFileName(), width, height,
                                FramebufferSpec.builder().color(ColorFormat.RGBA16F).build());
                        GpuTimer timer = new GpuTimer();
                        try {
                            // The editor's viewport restoration needs a live Minecraft window.
                            // Bind the actual Amnetic allocation directly in this standalone harness.
                            var glField = Framebuffer.class.getDeclaredField("gl"); glField.setAccessible(true);
                            var gl = (com.meekdev.amnetic.client.framebuffer.internal.GlFramebuffer)glField.get(target);
                            gl.allocate(width, height);
                            var fboField = gl.getClass().getDeclaredField("fbo"); fboField.setAccessible(true);
                            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboField.getInt(gl));
                            GL11.glViewport(0,0,width,height); target.clear(0,0,0,0);
                            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_BLEND);
                            GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                            float total = 0; int samples = 0;
                            for (int i = 0; i < 40; i++) {
                                timer.begin(); GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3); timer.end();
                                // A blocking wait is confined to this offline benchmark, never gameplay.
                                GL11.glFinish();
                                if (i >= 12 && timer.lastMs() >= 0) { total += timer.lastMs(); samples++; }
                            }
                            if (samples == 0) throw new AssertionError("No GPU timer samples");
                            float ms = total / samples;
                            String label = ROOT.relativize(path) + "@" + width;
                            record.invoke(PassProfiler.INSTANCE, RenderStage.POST, label, ms);
                            String line = String.format(Locale.ROOT, "%s,%d,%d,%.4f", ROOT.relativize(path), width, height, ms);
                            lines.add(line); System.out.println(line);
                            if (GL11.glGetError() != GL11.GL_NO_ERROR) throw new AssertionError("OpenGL error: " + path);
                            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
                        } finally { timer.dispose(); target.dispose(); for (int b : buffers) GL15.glDeleteBuffers(b); }
                    }
                    GL20.glDeleteProgram(program); GL20.glDeleteShader(vs); GL20.glDeleteShader(fs);
                }
            }
            Files.createDirectories(Path.of("build/reports"));
            Files.write(Path.of(args.length > 0 ? args[0] : "build/reports/amnetic-shaders.csv"), lines);
            System.out.println("PASS Amnetic profiler entries: " + PassProfiler.INSTANCE.snapshot().get(RenderStage.POST).size());
            GL11.glDeleteTextures(texture); GL30.glDeleteVertexArrays(vao);
        } finally { GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate(); }
    }
    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type); GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) throw new AssertionError(GL20.glGetShaderInfoLog(shader));
        return shader;
    }
    private static List<Integer> uniforms(int program, int width, int height) {
        List<Integer> buffers = new ArrayList<>();
        for (int i = 0; i < GL20.glGetProgrami(program, GL31.GL_ACTIVE_UNIFORM_BLOCKS); i++) {
            String name = GL31.glGetActiveUniformBlockName(program, i);
            int size = GL31.glGetActiveUniformBlocki(program, i, GL31.GL_UNIFORM_BLOCK_DATA_SIZE);
            float[] data = new float[size / 4];
            Arrays.fill(data, 1F);
            if (name.equals("SamplerInfo")) { data[0]=width; data[1]=height; data[2]=width; data[3]=height; }
            else if (name.equals("WorldData")) {
                Arrays.fill(data, 0); float[] matrix = new float[16];
                new Matrix4f().perspective((float)Math.toRadians(70),16F/9F,.05F,200F).invert().get(matrix);
                System.arraycopy(matrix,0,data,0,16); data[17]=50; data[22]=-1;
            } else if (name.equals("RiverData")) { data[0]=47.8889F; data[1]=3.6F; }
            else if (name.equals("DeadSunData")) { data[0]=0; data[1]=65; data[2]=-90; data[3]=8; }
            else if (name.equals("AsterionQuality")) data[0]=2;
            else if (name.equals("EclipseData") || name.equals("FinaleProgress") || name.equals("WorldDarkness") || name.equals("EntryRadiance")) data[0]=0;
            else if (name.equals("DustTime") || name.equals("UnderworldTime")) data[0]=1234;
            else if (name.contains("Color")) Arrays.fill(data,.35F);
            else if (name.equals("BlurDirection")) { data[0]=1; data[1]=0; }
            int buffer = GL15.glGenBuffers(); buffers.add(buffer);
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, buffer); GL15.glBufferData(GL31.GL_UNIFORM_BUFFER,data,GL15.GL_STATIC_DRAW);
            GL31.glUniformBlockBinding(program,i,i); GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER,i,buffer);
        }
        return buffers;
    }
}
