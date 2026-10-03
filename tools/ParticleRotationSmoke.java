import java.nio.file.Files;
import java.nio.file.Path;
import com.meekdev.amnetic.client.instanced.InstanceLayout;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;

/** Compiles the real particle vertex shader and verifies rotation on this machine's graphics driver. */
public final class ParticleRotationSmoke {
    public static void main(String[] args) throws Exception {
        var layout = InstanceLayout.builder().vec3(2).float1(3).vec4(4).vec4(5).float1(6).build();
        if (layout.stride() != 52) throw new AssertionError("Particle instance stride differs from writer");
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW initialization failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        long window = GLFW.glfwCreateWindow(32, 32, "Particle rotation check", 0, 0);
        if (window == 0) throw new AssertionError("No graphics context");
        try {
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
            checkEmissiveShader();
            for(String path:new String[]{"dead_sun","volume_integrate"}) {
                int shader=compile(GL20.GL_FRAGMENT_SHADER,ShaderIncludes.resolve(Files.readString(Path.of("src/main/resources/assets/asterion/shaders/post/dimension/"+path+".fsh"))));
                GL20.glDeleteShader(shader);
            }
            System.out.println("PASS sun relief, volumetric dust and fog-neutral emissive shaders compile on the graphics driver");
            int vs=compile(GL20.GL_VERTEX_SHADER, Files.readString(Path.of("src/main/resources/assets/asterion/shaders/particle/animated_emissive.vsh")));
            int fs=compile(GL20.GL_FRAGMENT_SHADER,"#version 330 core\nin vec4 vColor; out vec4 color; void main(){color=vColor;}");
            int program=GL20.glCreateProgram(); GL20.glAttachShader(program,vs); GL20.glAttachShader(program,fs); GL20.glLinkProgram(program);
            if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0)throw new AssertionError(GL20.glGetProgramInfoLog(program));
            GL20.glUseProgram(program);
            float[] identity={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
            GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program,"ProjectionMatrix"),false,identity);
            GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program,"ViewMatrix"),false,identity);
            int vao=GL30.glGenVertexArrays(); GL30.glBindVertexArray(vao);
            int buffer=GL15.glGenBuffers(); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,buffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER,new float[]{-.8F,-.15F,0,.8F,-.15F,0,.8F,.15F,0,-.8F,-.15F,0,.8F,.15F,0,-.8F,.15F,0},GL15.GL_STATIC_DRAW);
            GL20.glEnableVertexAttribArray(0);GL20.glVertexAttribPointer(0,3,GL11.GL_FLOAT,false,12,0);
            GL20.glVertexAttrib3f(2,0,0,0);GL20.glVertexAttrib1f(3,1);GL20.glVertexAttrib4f(4,1,1,1,1);
            GL11.glViewport(0,0,32,32);GL11.glDisable(GL11.GL_DEPTH_TEST);
            for(int turn=0;turn<2;turn++) {
                GL11.glClearColor(0,0,0,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                GL20.glVertexAttrib1f(6,turn*(float)Math.PI/2);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,6);
                float[] horizontal=new float[4],vertical=new float[4];
                GL11.glReadPixels(24,16,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,horizontal);
                GL11.glReadPixels(16,24,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,vertical);
                if(turn==0 && (horizontal[0]<.9 || vertical[0]>.1)
                        || turn==1 && (horizontal[0]>.1 || vertical[0]<.9))
                    throw new AssertionError("Particle roll does not rotate the billboard");
            }
            GL15.glDeleteBuffers(buffer);GL30.glDeleteVertexArrays(vao);GL20.glDeleteProgram(program);GL20.glDeleteShader(vs);GL20.glDeleteShader(fs);
            System.out.println("PASS particle layout, real shader compile/link, and rendered 90-degree rotation");
        } finally { GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate(); }
    }
    private static void checkEmissiveShader() throws Exception {
        String vertex;
        try(var stream=ParticleRotationSmoke.class.getResourceAsStream("/assets/minecraft/shaders/core/entity.vsh")) {
            vertex=new String(java.util.Objects.requireNonNull(stream).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        vertex=vertex.replaceFirst("(#version[^\\n]*\\n)","$1#define EMISSIVE\n#define NO_OVERLAY\n");
        int vs=compile(GL20.GL_VERTEX_SHADER,ShaderIncludes.resolve(vertex));
        int fs=compile(GL20.GL_FRAGMENT_SHADER,ShaderIncludes.resolve(Files.readString(Path.of("src/main/resources/assets/asterion/shaders/core/enhanced_emissive.fsh"))));
        int program=GL20.glCreateProgram();GL20.glAttachShader(program,vs);GL20.glAttachShader(program,fs);GL20.glLinkProgram(program);
        if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0)throw new AssertionError("Emissive surface: "+GL20.glGetProgramInfoLog(program));
        GL20.glDeleteProgram(program);GL20.glDeleteShader(vs);GL20.glDeleteShader(fs);
    }
    private static int compile(int type,String source) {
        int shader=GL20.glCreateShader(type);GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);
        if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0)throw new AssertionError(GL20.glGetShaderInfoLog(shader));
        return shader;
    }
}
