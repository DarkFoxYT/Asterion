package net.krodark.asterion.port.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public final class GroundHazeParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float angularSpeed;
    private final float opacity;
    private final float baseSize;
    private long lastTick;

    public GroundHazeParticle(ClientLevel level, double x, double y, double z,
                              double vx, double vy, double vz,
                              SpriteSet sprites, RandomSource random) {
        super(level, x, y, z, vx, vy, vz);
        setSprite(sprites.get(0,1));

        this.sprites = sprites;
        roll = oRoll = random.nextFloat() * (float)Math.PI * 2;
        angularSpeed = (random.nextFloat() - .5F) * .003F;

        xd = vx;
        yd = vy;
        zd = vz;

        hasPhysics = true;
        friction = .96F;
        gravity = -.006F;


        lifetime = 390 + random.nextInt(21);


        this.baseSize = 5F + random.nextFloat() * 20F;
        this.quadSize = baseSize;

        opacity = 1.0F;

        setColor(.5F, .6F, .5F);
        setAlpha(0);

        setSpriteFromAge(sprites);
    }

    public static Particle haze(ClientLevel level, double x, double y, double z,
                                double vx, double vy, double vz,
                                SpriteSet sprites, RandomSource random) {

        var smoke = new GroundHazeParticle(
                level, x, y, z,
                vx, vy, vz,
                sprites, random
        );

        smoke.setColor(.5F, .6F, .5F);

        smoke.quadSize =5F + random.nextFloat() * 2F;


        smoke.lifetime = 390 + random.nextInt(21);

        smoke.yd = -.035 + random.nextDouble() * -.025;
        smoke.gravity = .002F;

        return smoke;
    }

    @Override
    public void tick() {
        oRoll = roll; roll += angularSpeed;
        lastTick = level.getGameTime();

        super.tick();

        if (removed) return;

        if (age % 8 == 0 &&
                !level.getBlockState(BlockPos.containing(x, y, z)).isAir()) {
            remove();
            return;
        }

        xd += (random.nextFloat() - .5F) * .0007F;
        zd += (random.nextFloat() - .5F) * .0007F;

        yd = Mth.clamp(yd, -.002D, .004D);

        float life = age / (float) lifetime;


        float fadeIn = Mth.clamp(age / (lifetime * 0.20F), 0F, 1.0F);


        float fadeOut = Mth.clamp(
                (1F - life) / 0.20F,
                0F,
                1.0f
        );

        setAlpha(fadeIn * fadeOut);
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override public net.minecraft.client.particle.ParticleRenderType getRenderType(){return net.minecraft.client.particle.ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;}
}