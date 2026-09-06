package net.krodark.asterion.dev.verification;

import com.mojang.blaze3d.platform.NativeImage;
import net.krodark.asterion.client.render.TextureFrameCopy;

final class TextureFrameCopyCheck {
    static void run() {
        try (var source = new NativeImage(8, 11, false);
             var expected = new NativeImage(13, 9, false);
             var actual = new NativeImage(13, 9, false);
             var full = new NativeImage(8, 11, false);
             var rgb = new NativeImage(NativeImage.Format.RGB, 8, 11, false)) {
            var random = new java.util.Random(9182);
            for (int y = 0; y < 11; y++) for (int x = 0; x < 8; x++) source.setPixelABGR(x, y, random.nextInt());
            for (int y = 0; y < 9; y++) for (int x = 0; x < 13; x++) {
                expected.setPixelABGR(x, y, 0x12345678); actual.setPixelABGR(x, y, 0x12345678);
            }
            source.copyRect(expected, 2, 3, 4, 2, 4, 6, false, false);
            check(TextureFrameCopy.tryCopy(source, actual, 2, 3, 4, 2, 4, 6, false, false), "Strided copy rejected");
            equal(expected, actual);
            check(TextureFrameCopy.tryCopy(source, full, 0, 0, 0, 0, 8, 11, false, false), "Full copy rejected");
            equal(source, full);
            check(!TextureFrameCopy.tryCopy(source, source, 0, 0, 1, 1, 4, 4, false, false), "Aliased copy accepted");
            check(!TextureFrameCopy.tryCopy(source, full, 0, 0, 0, 0, 8, 11, true, false), "Flipped copy accepted");
            check(!TextureFrameCopy.tryCopy(source, full, Integer.MAX_VALUE, 0, 0, 0, 8, 11, false, false), "Overflowing rectangle accepted");
            check(!TextureFrameCopy.tryCopy(source, rgb, 0, 0, 0, 0, 8, 11, false, false), "Different format accepted");
        }
        try (var source = new NativeImage(2, 2, false); var target = new NativeImage(2, 2, false)) {
            source.close();
            check(!TextureFrameCopy.tryCopy(source, target, 0, 0, 0, 0, 2, 2, false, false), "Freed image accepted");
        }
        System.out.println("PASS: animation frame copy matches every RGBA byte; strides, untouched borders and fallback guards verified");
    }
    private static void equal(NativeImage expected, NativeImage actual) {
        for (int y = 0; y < expected.getHeight(); y++) for (int x = 0; x < expected.getWidth(); x++)
            check(expected.getPixel(x, y) == actual.getPixel(x, y), "Frame pixel changed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}

