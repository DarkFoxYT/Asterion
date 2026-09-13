package net.krodark.asterion.port.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** 1.21.1 implementations of Asterion's authored particle profiles. */
public final class PortParticles {
    public enum Style {
        GREEK_FIRE(1.00F, .35F, 16, 9, -.001F, .94F, false, .012F, .88F, 1.08F, 1.04F, .80F, true),
        BELCH_FIRE(2.40F, .80F, 24, 12, -.001F, .94F, false, .075F, .88F, 1.08F, 1.04F, .80F, true),
        BRAZIER_FIRE(.58F, .16F, 28, 9, -.001F, .99F, false, .105F, .78F, 1.08F, 1.04F, .80F, true),
        GAS_FIRE(.62F, .42F, 24, 12, -.0015F, .94F, false, .018F, .88F, 1.0F, .42F, .08F, true),
        STENCH(.90F, .22F, 90, 50, .000003F, .985F, false, .006F, .72F, .80F, .72F, .57F, false),
        BELCH_SMOKE(1.80F, .80F, 70, 35, -.001F, .975F, false, .035F, .78F, .46F, .40F, .32F, false),
        FLAMETHROWER_GAS(.55F, .30F, 28, 10, 0F, .97F, false, .004F, .82F, .72F, .63F, .48F, false),
        FIREFLY(.105F, .018F, 360, 240, 0F, 1F, true, 0F, 1F, 1F, .94F, .72F, true),
        HOSTILE_FIREFLY(.12F, .015F, 10, 5, 0F, .96F, false, 0F, 1F, 1F, .045F, .025F, true),
        SOOT(.55F, .40F, 55, 25, -.002F, .96F, true, .035F, .65F, .065F, .07F, .06F, false),
        TEAR(.075F, 0F, 48, 1, .22F, .98F, true, -.025F, .82F, .48F, .72F, .86F, false),
        DOOR_SMOKE(1.45F, .85F, 55, 35, -.006F, .96F, true, .035F, .64F, .62F, .56F, .46F, false),
        DOOR_DUST(.22F, .30F, 32, 30, .009F, .94F, true, -.005F, .68F, .34F, .25F, .16F, false),
        FLY(.085F, .015F, 360, 240, 0F, 1F, true, 0F, 1F, .70F, .66F, .48F, false),
        WALL_DUST(.055F, .19F, 48, 54, .075F, .965F, true, -.012F, .68F, .42F, .37F, .31F, false),
        RUMBLE(1.15F, .85F, 44, 30, .00001F, .965F, false, .012F, .76F, .42F, .37F, .31F, false);

        final float size, sizeSpread, gravity, friction, verticalBias, alpha, red, green, blue;
        final int lifetime, lifetimeSpread;
        final boolean physics, fullBright;

        Style(float size, float sizeSpread, int lifetime, int lifetimeSpread, float gravity,
              float friction, boolean physics, float verticalBias, float alpha,
              float red, float green, float blue, boolean fullBright) {
            this.size = size;
            this.sizeSpread = sizeSpread;
            this.lifetime = lifetime;
            this.lifetimeSpread = lifetimeSpread;
            this.gravity = gravity;
            this.friction = friction;
            this.physics = physics;
            this.verticalBias = verticalBias;
            this.alpha = alpha;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.fullBright = fullBright;
        }
    }

    private PortParticles() {}

    public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites, Style style) {
        return (type, level, x, y, z, vx, vy, vz) -> new AuthoredParticle(
                level, x, y, z, vx, vy, vz, sprites, style);
    }

    private static final class AuthoredParticle extends TextureSheetParticle {
        private final SpriteSet sprites;
        private final Style style;
        private final float baseAlpha;
        private final float initialSize;
        private final float rollSpeed;

        private AuthoredParticle(ClientLevel level, double x, double y, double z,
                                 double vx, double vy, double vz, SpriteSet sprites, Style style) {
            super(level, x, y, z, vx, vy, vz);
            this.sprites = sprites;
            this.style = style;
            this.xd = vx;
            this.yd = vy + style.verticalBias;
            this.zd = vz;
            this.gravity = style.gravity;
            this.friction = style.friction;
            this.hasPhysics = style.physics;
            this.lifetime = style.lifetime + random.nextInt(Math.max(1, style.lifetimeSpread));
            this.quadSize = style.size + random.nextFloat() * style.sizeSpread;
            this.initialSize = quadSize;
            this.baseAlpha = style.alpha;
            this.roll = random.nextFloat() * Mth.TWO_PI;
            this.oRoll = roll;
            this.rollSpeed = (random.nextFloat() - .5F) * (style == Style.DOOR_DUST || style == Style.WALL_DUST ? .06F : .025F);
            setSize(Math.max(.02F, quadSize * .18F), Math.max(.02F, quadSize * .18F));
            float shade = .88F + random.nextFloat() * .18F;
            setColor(Math.min(1F, style.red * shade), Math.min(1F, style.green * shade), Math.min(1F, style.blue * shade));
            setAlpha(style == Style.BRAZIER_FIRE || style == Style.DOOR_SMOKE || style == Style.DOOR_DUST ? 0F : baseAlpha);
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) return;
            oRoll = roll;
            roll += rollSpeed;
            setSpriteFromAge(sprites);
            float life = age / (float)Math.max(1, lifetime);
            float appear = Mth.clamp(age / (style == Style.DOOR_SMOKE ? 4F : 3F), 0F, 1F);
            float fade = Mth.clamp((1F - life) / fadeFraction(), 0F, 1F);

            if (style == Style.STENCH || style == Style.BELCH_SMOKE)
                quadSize = Mth.lerp(.12F, quadSize, initialSize * 2.25F);
            else if (style == Style.DOOR_SMOKE) quadSize += .012F;
            else if (style == Style.DOOR_DUST) quadSize += .007F;
            else if (style == Style.GAS_FIRE) quadSize += .006F;
            else if (style == Style.RUMBLE) quadSize = Mth.lerp(.055F, quadSize, 2.35F);
            else if (style == Style.BRAZIER_FIRE)
                quadSize = initialSize * (1F - .45F * Mth.clamp(life, 0F, 1F));

            if (style == Style.HOSTILE_FIREFLY)
                setColor(1F, .025F + random.nextFloat() * .05F, .015F);
            setAlpha(baseAlpha * appear * fade);
            if (style == Style.TEAR && onGround) remove();
        }

        private float fadeFraction() {
            return switch (style) {
                case DOOR_SMOKE, SOOT -> .45F;
                case STENCH, BELCH_SMOKE, RUMBLE -> .52F;
                case FIREFLY, FLY -> .08F;
                default -> .24F;
            };
        }

        @Override
        protected int getLightColor(float partialTick) {
            return style.fullBright ? 0xF000F0 : super.getLightColor(partialTick);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }
}
