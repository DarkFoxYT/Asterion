package net.krodark.asterion.dev.verification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import net.krodark.asterion.client.light.EmissiveBoneMesh;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL33.*;

/** Tests the vanilla full-bright shader used locally by Asterion in a hidden GPU context. */
public final class EmissiveRegression {
    private static final int SIZE = 16;
    private static final Pattern IMPORT = Pattern.compile("#moj_import <([^>]+)>");
    private static int vao, vertexBuffer, sourceTexture, colorTexture, fogBuffer;

    public static void main(String[] args) throws Exception {
        EmissiveMeshRegression.run();
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW initialization failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window = GLFW.glfwCreateWindow(SIZE, SIZE, "Asterion emissive regression", 0, 0);
        if (window == 0) throw new AssertionError("No OpenGL 3.3 context");
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            System.out.println("GPU: " + glGetString(GL_RENDERER));
            run();
            ParticleRegression.run();
            if (glGetError() != GL_NO_ERROR) throw new AssertionError("OpenGL error");
            System.out.println("PASS: entity/block surface shader, restrained brightness, unchanged alpha, depth rejection, transparency, fog, sharp edges; surfaces remain independent of Amnetic bloom");
        } finally {
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }

    private static void run() throws Exception {
        String defines = "#version 330\n#define EMISSIVE\n#define NO_OVERLAY\n#define NO_CARDINAL_LIGHTING\n#define ALPHA_CUTOUT 0.1\n";
        int surface = program(defines + source("assets/minecraft/shaders/core/entity.vsh"),
                defines + source("assets/minecraft/shaders/core/entity.fsh"));
        initGeometry();
        colorTexture = texture(SIZE, SIZE, null);
        glBindFramebuffer(GL_FRAMEBUFFER, glGenFramebuffers());
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTexture, 0);
        int depth = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, depth);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT32F, SIZE, SIZE);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depth);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) throw new AssertionError("Incomplete framebuffer");
        glViewport(0, 0, SIZE, SIZE);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glEnable(GL_BLEND);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        uniforms(surface);
        sourceTexture = texture(1, 1, new float[] {1, .5f, .25f, 1});
        int eyeColor = EmissiveBoneMesh.dimColor(0xFFFFFFFF, .85f);
        float eyeBrightness = (eyeColor & 255) / 255f;
        clear(1);
        draw(surface, eyeBrightness, 1);
        near(pixel()[0], .85f, "eye brightness");
        near(pixel()[3], 1, "dimming preserves opacity");
        clear(1);
        draw(surface, .65f, 1);
        near(pixel()[0], .65f, "vine brightness");
        clear(.25);
        draw(surface, .85f, 1);
        near(pixel()[0], 0, "occluder blocks surface");
        clear(1);
        draw(surface, .85f, .5f);
        near(pixel()[0], .425f, "alpha applied once");
        glBindTexture(GL_TEXTURE_2D, sourceTexture);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 1, 1, GL_RGBA, GL_FLOAT, new float[] {1, 1, 1, 0});
        clear(1);
        draw(surface, .85f, 1);
        near(pixel()[0], 0, "transparent texel discarded");
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 1, 1, GL_RGBA, GL_FLOAT, new float[] {1, .5f, .25f, 1});
        fog(true);
        clear(1);
        draw(surface, .85f, 1);
        near(pixel()[0], 1, "ordinary fog preserved");
        fog(false);
        clear(1);
        glEnable(GL_SCISSOR_TEST);
        glScissor(0, 0, SIZE / 2, SIZE);
        draw(surface, .85f, 1);
        glDisable(GL_SCISSOR_TEST);
        near(pixel()[0], 0, "no halo outside emissive geometry");
    }
    private static void initGeometry() {
        vao = glGenVertexArrays();
        glBindVertexArray(vao);
        vertexBuffer = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
    }

    private static void draw(int program, float brightness, float alpha) {
        glUseProgram(program);
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
        glBufferData(GL_ARRAY_BUFFER, new float[] {
                -1,-1,0, brightness,brightness,brightness,alpha, 0,0,
                 3,-1,0, brightness,brightness,brightness,alpha, 1,0,
                -1, 3,0, brightness,brightness,brightness,alpha, 0,1}, GL_STATIC_DRAW);
        attribute(program, "Position", 3, 0);
        attribute(program, "Color", 4, 12);
        attribute(program, "UV0", 2, 28);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sourceTexture);
        glUniform1i(glGetUniformLocation(program, "Sampler0"), 0);
        glDepthMask(false);
        glDrawArrays(GL_TRIANGLES, 0, 3);
    }

    private static void attribute(int program, String name, int size, long offset) {
        int loc = glGetAttribLocation(program, name);
        if (loc >= 0) {
            glEnableVertexAttribArray(loc);
            glVertexAttribPointer(loc, size, GL_FLOAT, false, 36, offset);
        }
    }

    private static void uniforms(int program) {
        float[] dynamic = new float[40];
        for (int i : new int[] {0,5,10,15,24,29,34,39}) dynamic[i] = 1;
        for (int i = 16; i < 20; i++) dynamic[i] = 1;
        block(program, "DynamicTransforms", 0, dynamic);
        float[] projection = new float[16];
        for (int i : new int[] {0,5,10,15}) projection[i] = 1;
        block(program, "Projection", 1, projection);
        fogBuffer = block(program, "Fog", 2, new float[12]);
        fog(false);
    }

    private static void fog(boolean full) {
        glBindBuffer(GL_UNIFORM_BUFFER, fogBuffer);
        glBufferData(GL_UNIFORM_BUFFER, new float[] {1,1,1,1,
                full ? -2 : 1000, full ? -1 : 2000, 1000,2000,2000,2000,0,0}, GL_STATIC_DRAW);
    }

    private static int block(int program, String name, int binding, float[] values) {
        int buffer = glGenBuffers();
        glBindBuffer(GL_UNIFORM_BUFFER, buffer);
        glBufferData(GL_UNIFORM_BUFFER, values, GL_STATIC_DRAW);
        int index = glGetUniformBlockIndex(program, name);
        if (index != GL_INVALID_INDEX) glUniformBlockBinding(program, index, binding);
        glBindBufferBase(GL_UNIFORM_BUFFER, binding, buffer);
        return buffer;
    }

    private static int texture(int w, int h, float[] pixels) {
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, w, h, 0, GL_RGBA, GL_FLOAT, pixels);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return id;
    }

    private static void clear(double depth) {
        glClearColor(0, 0, 0, 0);
        glDepthMask(true);
        glClearDepth(depth);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    private static float[] pixel() {
        float[] result = new float[4];
        glReadPixels(SIZE / 2, SIZE / 2, 1, 1, GL_RGBA, GL_FLOAT, result);
        return result;
    }

    private static void near(float actual, float expected, String message) {
        if (Math.abs(actual - expected) > .02f) throw new AssertionError(message + ": " + actual + " != " + expected);
    }

    private static int program(String vertex, String fragment) {
        int p = glCreateProgram();
        for (int type : new int[] {GL_VERTEX_SHADER, GL_FRAGMENT_SHADER}) {
            int shader = glCreateShader(type);
            glShaderSource(shader, type == GL_VERTEX_SHADER ? vertex : fragment);
            glCompileShader(shader);
            if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) throw new AssertionError(glGetShaderInfoLog(shader));
            glAttachShader(p, shader);
            glDeleteShader(shader);
        }
        glLinkProgram(p);
        if (glGetProgrami(p, GL_LINK_STATUS) == GL_FALSE) throw new AssertionError(glGetProgramInfoLog(p));
        return p;
    }

    private static String source(String path) throws IOException {
        String text;
        try (var stream = EmissiveRegression.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing shader: " + path);
            text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        text = text.replaceAll("(?m)^#version[^\\n]*", "");
        var matcher = IMPORT.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String[] name = matcher.group(1).split(":", 2);
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(
                    source("assets/" + name[0] + "/shaders/include/" + name[1])));
        }
        return matcher.appendTail(result).toString();
    }
}

