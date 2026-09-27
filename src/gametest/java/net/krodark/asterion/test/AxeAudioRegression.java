package net.krodark.asterion.test;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.MinotaurAxeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;

final class AxeAudioRegression {
    static void run(Minecraft client) {
        try {
            var cls=Class.forName("net.krodark.asterion.port.client.AxeFlightAudio$Flight");
            var ctor=cls.getDeclaredConstructor(MinotaurAxeEntity.class);ctor.setAccessible(true);
            var axe=new MinotaurAxeEntity(Asterion.MINOTAUR_AXE,client.level);
            axe.setPos(client.player.getX()+3,client.player.getY()+1,client.player.getZ());
            var sound=(AbstractTickableSoundInstance)ctor.newInstance(axe);
            if(sound.isRelative() || sound.getAttenuation()!=SoundInstance.Attenuation.LINEAR || !sound.isLooping())
                throw new AssertionError("Axe flight is not a positioned looping sound");
            axe.setPos(axe.getX()+2,axe.getY(),axe.getZ()+1);sound.tick();
            if(sound.getX()!=axe.getX() || sound.getZ()!=axe.getZ() || sound.isStopped())
                throw new AssertionError("Flight sound failed to follow the axe");
            for(int i=0;i<6;i++)sound.tick();
            if(!sound.isStopped())throw new AssertionError("Flight sound continued after axe stopped");
            var removed=(AbstractTickableSoundInstance)ctor.newInstance(axe);axe.discard();removed.tick();
            if(!removed.isStopped())throw new AssertionError("Flight sound survived axe removal");
            Asterion.LOGGER.info("ASTERION_AXE_AUDIO PASSED: positional attenuation, follows axe, stops on rest/removal");
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
}
