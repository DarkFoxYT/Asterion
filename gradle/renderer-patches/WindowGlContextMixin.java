package com.meekdev.amnetic.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Compiled into the embedded 26.1.2 renderer, never the Asterion module. */
@Mixin(targets = "com.mojang.blaze3d.platform.Window")
public final class WindowGlContextMixin {
    @Unique private static final Logger ASTERION_GL = LoggerFactory.getLogger("Amnetic/GL");

    @WrapOperation(method = "createGlfwWindow", require = 0,
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"))
    private static long amnetic$createWindowWithGlVersion(int width, int height, CharSequence title,
            long monitor, long share, Operation<Long> original) {
        int major = Integer.getInteger("amnetic.opengl.major", 4);
        int minor = Integer.getInteger("amnetic.opengl.minor", 6);
        boolean debug = Boolean.getBoolean("amnetic.opengl.debug");
        GLFWErrorCallback probe = GLFWErrorCallback.create((code, description) ->
                ASTERION_GL.warn("Context probe {}: {}", code, GLFWErrorCallback.getDescription(description)));
        GLFWErrorCallback previous = GLFW.glfwSetErrorCallback(probe);
        try {
            for (int[] version : new int[][]{{major, minor}, {4, 3}, {3, 3}}) {
                GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, version[0]);
                GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, version[1]);
                GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
                GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
                GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_DEBUG_CONTEXT, debug ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
                if (debug) GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_NO_ERROR, GLFW.GLFW_FALSE);
                long handle = original.call(width, height, title, monitor, share);
                if (handle != 0L) return handle;
            }
            throw new IllegalStateException("Unable to create an OpenGL 3.3 or newer context");
        } finally {
            GLFW.glfwSetErrorCallback(previous);
            probe.free();
        }
    }
}
