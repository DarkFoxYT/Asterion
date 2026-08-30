package net.krodark.asterion.dev.verification;

import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import static org.lwjgl.opengl.GL33.*;

/** Pixel checks against the supplied sprites, using the production shader in a hidden GPU context. */
final class ParticleRegression {
    static void run() throws Exception {
        int program = glCreateProgram();
        attach(program, GL_VERTEX_SHADER, "assets/asterion/shaders/particle/animated_emissive.vsh");
        attach(program, GL_FRAGMENT_SHADER, "assets/amnetic/shaders/particle/default_textured.fsh");
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE)
            throw new AssertionError(glGetProgramInfoLog(program));
        int vao = glGenVertexArrays(), vbo = glGenBuffers(), fbo = glGenFramebuffers();
        int output = glGenTextures(), atlas = glGenTextures(), depth = glGenRenderbuffers();
        try {
            glUseProgram(program);
            float[] identity = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
            glUniformMatrix4fv(glGetUniformLocation(program, "ProjectionMatrix"), false, identity);
            glUniformMatrix4fv(glGetUniformLocation(program, "ViewMatrix"), false, identity);
            glUniform1i(glGetUniformLocation(program, "TextureSampler"), 0);
            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferData(GL_ARRAY_BUFFER, new float[] {
                    -.5F, -.5F, 0, 0, 1, -.5F, .5F, 0, 0, 0,
                    .5F, -.5F, 0, 1, 1, .5F, .5F, 0, 1, 0}, GL_STATIC_DRAW);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 20, 0L);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(1, 2, GL_FLOAT, false, 20, 12L);
            glVertexAttrib3f(2, 0, 0, 0);
            glVertexAttrib1f(3, 2);
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glDisable(GL_BLEND);
            glDisable(GL_CULL_FACE);
            glDisable(GL_SCISSOR_TEST);
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LEQUAL);
            glColorMask(true, true, true, true);
            glActiveTexture(GL_TEXTURE0);

            for (String particle : new String[] {"greek_fire", "bombardier_stench", "bombardier_gas_fire"}) {
                var frames = JsonParser.parseString(text("assets/asterion/particles/" + particle + ".json"))
                        .getAsJsonObject().getAsJsonArray("textures");
                if (frames.size() != 8) throw new AssertionError(particle + " must contain all eight frames");
                BufferedImage[] images = new BufferedImage[8];
                var unique = new HashSet<String>();
                for (int i = 0; i < 8; i++) {
                    String name = frames.get(i).getAsString();
                    if (!unique.add(name) || !name.endsWith(Integer.toString(i + 1)))
                        throw new AssertionError("Duplicate or unordered frame: " + name);
                    String[] id = name.split(":", 2);
                    try (var stream = ParticleRegression.class.getClassLoader().getResourceAsStream(
                            "assets/" + id[0] + "/textures/particle/" + id[1] + ".png")) {
                        if (stream == null) throw new AssertionError("Missing sprite: " + name);
                        images[i] = ImageIO.read(stream);
                    }
                }
                int size = images[0].getWidth();
                ByteBuffer pixels = BufferUtils.createByteBuffer(size * size * 8 * 4);
                for (int y = 0; y < size; y++) for (var image : images) {
                    if (image.getWidth() != size || image.getHeight() != size)
                        throw new AssertionError("Sprite dimensions disagree: " + particle);
                    for (int x = 0; x < size; x++) {
                        int argb = image.getRGB(x, y);
                        pixels.put((byte)(argb >> 16)).put((byte)(argb >> 8)).put((byte)argb).put((byte)(argb >> 24));
                    }
                }
                pixels.flip();
                texture(output, size, size, null);
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, output, 0);
                glBindRenderbuffer(GL_RENDERBUFFER, depth);
                glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, size, size);
                glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depth);
                if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
                    throw new AssertionError("Particle framebuffer incomplete");
                texture(atlas, size * 8, size, pixels);
                glViewport(0, 0, size, size);
                for (int frame = 0; frame < 8; frame++) {
                    glVertexAttrib4f(5, frame / 8.0F, 0, 1.0F / 8, 1);
                    for (float alpha : new float[] {1.0F, 0.5F}) {
                        glVertexAttrib4f(4, 1, 1, 1, alpha);
                        clear(1.0);
                        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
                        ByteBuffer actual = BufferUtils.createByteBuffer(size * size * 4);
                        glReadPixels(0, 0, size, size, GL_RGBA, GL_UNSIGNED_BYTE, actual);
                        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
                            int expected = images[frame].getRGB(x, size - 1 - y);
                            int expectedAlpha = Math.round((expected >>> 24) * alpha);
                            int index = (y * size + x) * 4;
                            int[] channels = {expected >> 16 & 255, expected >> 8 & 255, expected & 255, expectedAlpha};
                            for (int channel = 0; channel < 4; channel++) {
                                int wanted = expectedAlpha == 0 ? 0 : channels[channel];
                                if (Math.abs(Byte.toUnsignedInt(actual.get(index + channel)) - wanted) > 1)
                                    throw new AssertionError(particle + " frame " + (frame + 1)
                                            + " pixel " + x + "," + y + " channel " + channel);
                            }
                        }
                    }
                }
                clear(0.25);
                glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
                ByteBuffer hidden = BufferUtils.createByteBuffer(size * size * 4);
                glReadPixels(0, 0, size, size, GL_RGBA, GL_UNSIGNED_BYTE, hidden);
                while (hidden.hasRemaining()) if (hidden.get() != 0)
                    throw new AssertionError("Particle rendered through depth occluder");
            }
            if (glGetError() != GL_NO_ERROR) throw new AssertionError("Particle OpenGL error");
            System.out.println("PASS: all 24 particle frame references, atlas UV orientation, exact sprite colors, alpha fading and depth occlusion");
        } finally {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glDeleteFramebuffers(fbo);
            glDeleteRenderbuffers(depth);
            glDeleteTextures(output);
            glDeleteTextures(atlas);
            glDeleteBuffers(vbo);
            glDeleteVertexArrays(vao);
            glDeleteProgram(program);
        }
    }

    private static void texture(int id, int width, int height, ByteBuffer pixels) {
        glBindTexture(GL_TEXTURE_2D, id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    private static void clear(double depth) {
        glClearColor(0, 0, 0, 0);
        glDepthMask(true);
        glClearDepth(depth);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glDepthMask(false);
    }

    private static void attach(int program, int type, String path) throws IOException {
        int shader = glCreateShader(type);
        glShaderSource(shader, text(path));
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE)
            throw new AssertionError(glGetShaderInfoLog(shader));
        glAttachShader(program, shader);
        glDeleteShader(shader);
    }

    private static String text(String path) throws IOException {
        try (var stream = ParticleRegression.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing resource: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
