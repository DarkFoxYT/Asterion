import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

/** Standalone hidden-window driver regression probe; run with LWJGL core/GLFW/OpenGL + natives. */
public final class DepthBlitSmoke {
    public static void main(String[] args) {
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW initialization failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long window = GLFW.glfwCreateWindow(32, 32, "Depth blit regression", 0, 0);
        if (window == 0) throw new AssertionError("GL context creation failed");
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            System.out.println(GL11.glGetString(GL11.GL_RENDERER));
            // Minecraft DEPTH32 vs the old Amnetic DEPTH24 destination.
            int oldError = copy(GL14.GL_DEPTH_COMPONENT24, 16, false);
            System.out.println("Old mismatched-format blit error: 0x" + Integer.toHexString(oldError));
            for (int size : new int[] {32, 30, 16, 8}) {
                int error = copy(GL14.GL_DEPTH_COMPONENT32, size, true);
                if (error != GL11.GL_NO_ERROR) throw new AssertionError("Depth32 copy error: " + error);
            }
            System.out.println("PASS: matching depth copies preserve terrain depth at full and reduced resolutions");
        } finally {
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }

    private static int copy(int format, int size, boolean verify) {
        int sourceTexture = texture(GL14.GL_DEPTH_COMPONENT32, 32);
        int targetTexture = texture(format, size);
        int source = framebuffer(sourceTexture);
        GL11.glClearDepth(0.25);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        int target = framebuffer(targetTexture);
        GL11.glClearDepth(1.0);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target);
        GL30.glBlitFramebuffer(0, 0, 32, 32, 0, 0, size, size, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        int error = GL11.glGetError();
        if (verify && error == GL11.GL_NO_ERROR) {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target);
            float[] depth = new float[size * size];
            GL11.glReadPixels(0, 0, size, size, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
            for (float value : depth) {
                if (Math.abs(value - 0.25f) > 0.00001f) throw new AssertionError("Lost terrain depth: " + value);
            }
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GL30.glDeleteFramebuffers(source);
        GL30.glDeleteFramebuffers(target);
        GL11.glDeleteTextures(sourceTexture);
        GL11.glDeleteTextures(targetTexture);
        return error;
    }

    private static int texture(int format, int size) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, size, size, 0,
                GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, 0L);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        return texture;
    }

    private static int framebuffer(int texture) {
        int framebuffer = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, texture, 0);
        GL11.glReadBuffer(GL11.GL_NONE);
        GL11.glDrawBuffer(GL11.GL_NONE);
        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE)
            throw new AssertionError("Incomplete depth framebuffer");
        return framebuffer;
    }
}
