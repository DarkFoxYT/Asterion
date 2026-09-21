import java.nio.ByteBuffer;
import net.krodark.asterion.client.light.StreamingInstanceUpload;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;

/** Real-driver regression check: storage stays reusable as the visible instance count changes. */
public final class EmissiveUploadSmoke {
    public static void main(String[] args) {
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW initialization failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long window = GLFW.glfwCreateWindow(32, 32, "Emissive upload check", 0, 0);
        if (window == 0) throw new AssertionError("No graphics context");
        ByteBuffer source = MemoryUtil.memAlloc(1024 * 1024 + 16);
        ByteBuffer result = MemoryUtil.memAlloc(1024 * 1024);
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            int buffer = GL15.glGenBuffers();
            try {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
                int capacity = 0;
                for (int frame = 0; frame < 96; frame++) {
                    int bytes = new int[]{64, 1024, 32, 65536, 0, 1048576, 7, 4096}[frame % 8];
                    if (bytes > capacity) {
                        capacity = Math.max(bytes, capacity * 2);
                        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, capacity, GL15.GL_STREAM_DRAW);
                    }
                    source.clear();
                    // Nonzero position catches uploads accidentally starting at the buffer base.
                    source.position(9);
                    for (int i = 0; i < bytes; i++) source.put((byte)(i * 31 + frame));
                    source.limit(9 + bytes).position(9);
                    StreamingInstanceUpload.upload(source, capacity);
                    if (source.position() != 9 || source.limit() != 9 + bytes)
                        throw new AssertionError("Upload changed the source buffer cursor");
                    if (GL15.glGetBufferParameteri(GL15.GL_ARRAY_BUFFER, GL15.GL_BUFFER_SIZE) != capacity)
                        throw new AssertionError("Upload shrank buffer storage");
                    if (bytes > 0) {
                        result.clear().limit(bytes);
                        GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, 0, result);
                        for (int i = 0; i < bytes; i++)
                            if (result.get(i) != (byte)(i * 31 + frame))
                                throw new AssertionError("Corrupt instance data at frame " + frame + ", byte " + i);
                    }
                    if (GL11.glGetError() != GL11.GL_NO_ERROR) throw new AssertionError("OpenGL upload error");
                }
                System.out.println("PASS: 96 mapped uploads, growth/shrink/empty batches, exact contents, stable capacity.");
            } finally { GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0); GL15.glDeleteBuffers(buffer); }
        } finally {
            MemoryUtil.memFree(source);
            MemoryUtil.memFree(result);
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }
}
