package net.krodark.asterion.port.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.krodark.asterion.port.client.particle.*;

/** 1.21.1 implementations of Asterion's authored particle profiles. */
public final class PortParticles {
    public enum Style {
        GREEK_FIRE, BELCH_FIRE, BRAZIER_FIRE, GAS_FIRE, FLAMETHROWER_FIRE, STENCH,
        BELCH_SMOKE, FLAMETHROWER_GAS, FIREFLY, HOSTILE_FIREFLY, SOOT, TEAR,
        DOOR_SMOKE, DOOR_DUST, FLY, WALL_DUST, RUMBLE
    }
    private PortParticles() {}
    public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites, Style style) {
        return (type, level, x, y, z, vx, vy, vz) -> {
            var random = level.random;
            return switch (style) {
                case GREEK_FIRE -> GreekFireParticle.create(level,x,y,z,vx,vy,vz,sprites,random);
                case BELCH_FIRE -> GreekFireParticle.createBelch(level,x,y,z,vx,vy,vz,sprites,random);
                case BRAZIER_FIRE -> GreekFireParticle.createBrazier(level,x,y,z,vx,vy,vz,sprites,random);
                case GAS_FIRE -> BombardierGasFireParticle.create(level,x,y,z,vx,vy,vz,sprites,random);
                case FLAMETHROWER_FIRE -> BombardierGasFireParticle.createFlamethrower(level,x,y,z,vx,vy,vz,sprites,random);
                case STENCH -> BombardierStenchParticle.create(level,x,y,z,vx,vy,vz,sprites,random);
                case BELCH_SMOKE -> BombardierStenchParticle.createBelch(level,x,y,z,vx,vy,vz,sprites,random);
                case FLAMETHROWER_GAS -> BombardierStenchParticle.createFlamethrower(level,x,y,z,vx,vy,vz,sprites,random);
                case FIREFLY -> FlyingInsectParticle.createFirefly(level,x,y,z,vx,vy,vz,sprites,random);
                case HOSTILE_FIREFLY -> HostileFireflyParticle.create(level,x,y,z,vx,vy,vz,sprites,random);
                case FLY -> FlyingInsectParticle.createFly(level,x,y,z,vx,vy,vz,sprites,random);
                case SOOT -> DoorSmokeParticle.soot(level,x,y,z,vx,vy,vz,sprites,random);
                case TEAR -> new LamenterTearParticle(level,x,y,z,vx,vz,sprites);
                case DOOR_SMOKE -> new DoorSmokeParticle(level,x,y,z,vx,vy,vz,sprites,random);
                case DOOR_DUST -> new DoorDustParticle(level,x,y,z,vx,vy,vz,sprites,random);
                case WALL_DUST -> AncientWallDustParticle.create(level,x,y,z,vx,vy,vz,sprites,random);
                case RUMBLE -> RumbleSmokeParticle.create(level,x,y,z,vx,vy,vz,sprites,random);
            };
        };
    }
}
