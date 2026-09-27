package net.krodark.asterion.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import org.lwjgl.system.MemoryUtil;

/** Copies ordinary RGBA frame rectangles without per-pixel foreign-memory calls. */
public final class TextureFrameCopy {
    private TextureFrameCopy() { }

    public static boolean tryCopy(NativeImage source, NativeImage target,
            int sx, int sy, int tx, int ty, int width, int height, boolean flipX, boolean flipY) {
        if (flipX || flipY || source.format() != NativeImage.Format.RGBA
                || target.format() != NativeImage.Format.RGBA || width <= 0 || height <= 0
                || sx < 0 || sy < 0 || tx < 0 || ty < 0
                || (long)sx + width > source.getWidth() || (long)sy + height > source.getHeight()
                || (long)tx + width > target.getWidth() || (long)ty + height > target.getHeight()) return false;
        long from = source.getPointer(), to = target.getPointer();
        if (from == 0 || to == 0 || from == to) return false;
        long sourceStride = (long)source.getWidth() * 4, targetStride = (long)target.getWidth() * 4;
        from += sy * sourceStride + (long)sx * 4;
        to += ty * targetStride + (long)tx * 4;
        long rowBytes = (long)width * 4;
        long sourceEnd = from + (height - 1L) * sourceStride + rowBytes;
        long targetEnd = to + (height - 1L) * targetStride + rowBytes;
        if (from < targetEnd && to < sourceEnd) return false;
        if (rowBytes == sourceStride && rowBytes == targetStride) {
            MemoryUtil.memCopy(from, to, rowBytes * height);
        } else {
            for (int row = 0; row < height; row++)
                MemoryUtil.memCopy(from + row * sourceStride, to + row * targetStride, rowBytes);
        }
        return true;
    }
}
