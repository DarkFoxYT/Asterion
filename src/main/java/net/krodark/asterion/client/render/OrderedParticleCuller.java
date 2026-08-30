package net.krodark.asterion.client.render;

import com.meekdev.amnetic.client.instanced.internal.CullTargets;
import com.mojang.blaze3d.opengl.GlStateManager;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.opengl.GL43.*;

/** Stable compaction into Amnetic's existing instance and indirect-draw buffers. Render thread only. */
public final class OrderedParticleCuller implements AutoCloseable {
    private final int scan, scatter, scanCount, planes, scatterCount, stride;
    private final int scratch = glGenBuffers();
    private final int[] previousBindings = new int[5];
    private final long[] previousOffsets = new long[5], previousSizes = new long[5];
    private final Vector4f plane = new Vector4f();
    private int capacity;

    public OrderedParticleCuller(int scan, int scatter) {
        this.scan = scan; this.scatter = scatter;
        scanCount = glGetUniformLocation(scan, "Count");
        planes = glGetUniformLocation(scan, "Planes");
        scatterCount = glGetUniformLocation(scatter, "Count");
        stride = glGetUniformLocation(scatter, "StrideUints");
    }

    public void cull(CullTargets targets, int count, int strideBytes, Matrix4fc projectionView) {
        if (count <= 0) throw new IllegalArgumentException("Empty batches must skip dispatch");
        int oldProgram = glGetInteger(GL_CURRENT_PROGRAM);
        int oldStorage = glGetInteger(GL_SHADER_STORAGE_BUFFER_BINDING);
        for (int i = 0; i < 5; i++) {
            previousBindings[i] = glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, i);
            if (previousBindings[i] != 0) {
                previousOffsets[i] = glGetInteger64i(GL_SHADER_STORAGE_BUFFER_START, i);
                previousSizes[i] = glGetInteger64i(GL_SHADER_STORAGE_BUFFER_SIZE, i);
            }
        }
        try {
            int groups = (count + 63) / 64;
            int required = (count + groups) * Integer.BYTES;
            if (required > capacity) {
                capacity = Math.max(required, Math.max(1024, capacity * 2));
                glBindBuffer(GL_SHADER_STORAGE_BUFFER, scratch);
                glBufferData(GL_SHADER_STORAGE_BUFFER, capacity, GL_DYNAMIC_DRAW);
            }
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, targets.bounds());
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, targets.payloadIn());
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, targets.payloadOut());
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, targets.command());
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, scratch);
            GlStateManager._glUseProgram(scan);
            glUniform1i(scanCount, count);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var values = stack.mallocFloat(24);
                for (int i = 0; i < 6; i++) {
                    projectionView.frustumPlane(i, plane);
                    float length = (float)Math.sqrt(plane.x * plane.x + plane.y * plane.y + plane.z * plane.z);
                    plane.mul(length > 0 ? 1 / length : 0);
                    values.put(plane.x).put(plane.y).put(plane.z).put(plane.w);
                }
                glUniform4fv(planes, values.flip());
            }
            glDispatchCompute(groups, 1, 1);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
            GlStateManager._glUseProgram(scatter);
            glUniform1i(scatterCount, count);
            glUniform1i(stride, strideBytes / Integer.BYTES);
            glDispatchCompute(groups, 1, 1);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_COMMAND_BARRIER_BIT | GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);
        } finally {
            for (int i = 0; i < 5; i++) {
                if (previousBindings[i] != 0 && previousSizes[i] > 0)
                    glBindBufferRange(GL_SHADER_STORAGE_BUFFER, i, previousBindings[i], previousOffsets[i], previousSizes[i]);
                else glBindBufferBase(GL_SHADER_STORAGE_BUFFER, i, previousBindings[i]);
            }
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, oldStorage);
            GlStateManager._glUseProgram(oldProgram);
        }
    }

    @Override public void close() {
        glDeleteBuffers(scratch);
        glDeleteProgram(scan);
        glDeleteProgram(scatter);
    }
}
