package net.krodark.asterion.port.compat;

import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;

public final class ParticleCompat {
    private ParticleCompat() {}

    public static DustParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(new Vector3f(
                ((rgb >> 16) & 0xFF) / 255.0F,
                ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F), scale);
    }
}
