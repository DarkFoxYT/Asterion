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
                int shader = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
                GL20.glShaderSource(shader, ShaderIncludes.resolve(Files.readString(Path.of(file))));
                GL20.glCompileShader(shader);
                String log = GL20.glGetShaderInfoLog(shader);
                if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0)
                    throw new AssertionError(file + ": " + log);
                GL20.glDeleteShader(shader);
                System.out.println("PASS " + file);
            }
        } finally {
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }
}
