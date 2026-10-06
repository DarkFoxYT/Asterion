import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20;
import java.nio.file.Files;
import java.nio.file.Path;

/** Compile the actual GLSL resources using a hidden driver context. */
public class LimboShaderSmoke {
    public static void main(String[] args) throws Exception {
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW initialization failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long window = GLFW.glfwCreateWindow(32, 32, "Limbo shader check", 0, 0);
        if (window == 0) throw new AssertionError("No OpenGL context");
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            for (String file : args) {
                int shader = GL20.glCreateShader(file.endsWith(".vsh")?GL20.GL_VERTEX_SHADER:GL20.GL_FRAGMENT_SHADER);
                GL20.glShaderSource(shader, ShaderIncludes.resolve(Files.readString(Path.of(file))));
                GL20.glCompileShader(shader);
                String log = GL20.glGetShaderInfoLog(shader);
                if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0)
                    throw new AssertionError(file + ": " + log);
                GL20.glDeleteShader(shader);
                System.out.println("PASS " + file);
            }
            checkCascadeVolume();
            // The supplied crash occurred during linking the actual water pipeline.
            for (String material : new String[]{"limbo_water","limbo_cascade","limbo_cascade_mist"}) {
            String root="src/main/resources/assets/asterion/shaders/core/"+material;
            int vertex=compile(root+".vsh",GL20.GL_VERTEX_SHADER),fragment=compile(root+".fsh",GL20.GL_FRAGMENT_SHADER);
            int program=GL20.glCreateProgram();
            try {
                GL20.glAttachShader(program,vertex);GL20.glAttachShader(program,fragment);GL20.glLinkProgram(program);
                if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0)throw new AssertionError(GL20.glGetProgramInfoLog(program));
                System.out.println("PASS actual "+material+" vertex/fragment pipeline link");
            } finally { GL20.glDeleteProgram(program);GL20.glDeleteShader(vertex);GL20.glDeleteShader(fragment); }
            }
        } finally {
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }

    private static void checkCascadeVolume() throws Exception {
        String atmosphere=Files.readString(Path.of("src/main/resources/assets/asterion/shaders/post/underworld/river_atmosphere.fsh"));
        String noise=atmosphere.substring(atmosphere.indexOf("float hash31"),atmosphere.indexOf("#moj_import <asterion:limbo_cascade_volume"));
        String source=ShaderIncludes.resolve("#version 330\n#moj_import <asterion:limbo_seas.glsl>\n"+noise+
                "\n#moj_import <asterion:limbo_cascade_volume.glsl>\nuniform vec3 Eye; uniform float Travel; out vec4 color; void main(){color=limboCascadeVolume(Eye,vec3(0,0,1),Travel,1800.0,8);}");
        int vertex=GL20.glCreateShader(GL20.GL_VERTEX_SHADER),fragment=GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(vertex,"#version 330\nvoid main(){gl_Position=vec4(0,0,0,1);gl_PointSize=1.0;}");
        GL20.glShaderSource(fragment,source);
        for(int shader:new int[]{vertex,fragment}) {GL20.glCompileShader(shader);if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0)throw new AssertionError(GL20.glGetShaderInfoLog(shader));}
        int program=GL20.glCreateProgram();GL20.glAttachShader(program,vertex);GL20.glAttachShader(program,fragment);GL20.glLinkProgram(program);
        if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0)throw new AssertionError(GL20.glGetProgramInfoLog(program));
        int vao=org.lwjgl.opengl.GL30.glGenVertexArrays();org.lwjgl.opengl.GL30.glBindVertexArray(vao);
        GL20.glUseProgram(program);org.lwjgl.opengl.GL11.glViewport(0,0,1,1);
        for(int tier=1;tier<=4;tier++) {
            float low=47-tier*18-tier*(tier-1)+8F/9F;
            float z=18+tier*3200+5;
            GL20.glUniform3f(GL20.glGetUniformLocation(program,"Eye"),0,low+1,z);
            GL20.glUniform1f(GL20.glGetUniformLocation(program,"Travel"),12);
            org.lwjgl.opengl.GL11.glDrawArrays(org.lwjgl.opengl.GL11.GL_POINTS,0,1);
            float[] pixel=new float[4];org.lwjgl.opengl.GL11.glReadPixels(0,0,1,1,org.lwjgl.opengl.GL11.GL_RGBA,org.lwjgl.opengl.GL11.GL_FLOAT,pixel);
            if(pixel[3]>=.99 || pixel[0]<=.01)throw new AssertionError("No volumetric spray inside tier "+tier);
            GL20.glUniform1f(GL20.glGetUniformLocation(program,"Travel"),0);
            org.lwjgl.opengl.GL11.glDrawArrays(org.lwjgl.opengl.GL11.GL_POINTS,0,1);
            org.lwjgl.opengl.GL11.glReadPixels(0,0,1,1,org.lwjgl.opengl.GL11.GL_RGBA,org.lwjgl.opengl.GL11.GL_FLOAT,pixel);
            if(pixel[3]<.99 || pixel[0]>.01)throw new AssertionError("Mist penetrates foreground depth");
        }
        GL20.glUniform3f(GL20.glGetUniformLocation(program,"Eye"),0,48,200);GL20.glUniform1f(GL20.glGetUniformLocation(program,"Travel"),136);
        org.lwjgl.opengl.GL11.glDrawArrays(org.lwjgl.opengl.GL11.GL_POINTS,0,1);
        float[] pixel=new float[4];org.lwjgl.opengl.GL11.glReadPixels(0,0,1,1,org.lwjgl.opengl.GL11.GL_RGBA,org.lwjgl.opengl.GL11.GL_FLOAT,pixel);
        if(pixel[3]<.99 || pixel[0]>.01)throw new AssertionError("Spray leaks away from waterfalls");
        org.lwjgl.opengl.GL30.glDeleteVertexArrays(vao);GL20.glDeleteProgram(program);GL20.glDeleteShader(vertex);GL20.glDeleteShader(fragment);
        System.out.println("PASS actual GPU cascade volumes: camera inside all four tiers, foreground depth occlusion, distant rejection");
    }
    private static int compile(String file,int type) throws Exception {
        int shader=GL20.glCreateShader(type);
        GL20.glShaderSource(shader,ShaderIncludes.resolve(Files.readString(Path.of(file))));
        GL20.glCompileShader(shader);
        if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0)throw new AssertionError(file+": "+GL20.glGetShaderInfoLog(shader));
        return shader;
    }
}
