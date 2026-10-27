package net.krodark.asterion.client.light;

import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

/** Uploads into a capacity-managed, already-bound VBO without shrinking its storage. */
public final class StreamingInstanceUpload {
    private StreamingInstanceUpload() { }

    public static void upload(ByteBuffer source, long capacityBytes) {
        int bytes = source.remaining();
        if (bytes == 0) return;
        if (!source.isDirect() || bytes > capacityBytes)
            throw new IllegalArgumentException("Invalid instance upload buffer");
        // INVALIDATE lets the driver provide fresh storage when the previous draw is
        // still using it. No UNSYNCHRONIZED mapping, blocking fences, or VAO rebinding.
        ByteBuffer mapped = GL30.glMapBufferRange(GL15.GL_ARRAY_BUFFER, 0, bytes,
                GL30.GL_MAP_WRITE_BIT | GL30.GL_MAP_INVALIDATE_BUFFER_BIT);
        if (mapped != null) {
            MemoryUtil.memCopy(MemoryUtil.memAddress(source), MemoryUtil.memAddress(mapped), bytes);
            if (GL15.glUnmapBuffer(GL15.GL_ARRAY_BUFFER)) return;
        }
        // Conservative fallback for drivers that cannot provide a valid mapping.
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, capacityBytes, GL15.GL_STREAM_DRAW);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, source);
    }
}
