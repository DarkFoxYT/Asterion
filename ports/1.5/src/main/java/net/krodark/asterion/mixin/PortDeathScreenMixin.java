package net.krodark.asterion.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Labyrinth death-screen hints, independent of the credits HUD. */
@Mixin(DeathScreen.class)
abstract class PortDeathScreenMixin {
    @Unique private static final String[] ASTERION_TIPS={
        "tip.asterion.death.chain","tip.asterion.death.pillars","tip.asterion.death.leap",
        "tip.asterion.death.axe","tip.asterion.death.light","tip.asterion.death.centipede"};
    @Inject(method="render",at=@At("TAIL"))
    private void asterion$deathTip(net.minecraft.client.gui.GuiGraphics graphics,int mouseX,int mouseY,float partial,CallbackInfo ci) {
        Minecraft client=Minecraft.getInstance();
        if(client.player==null || client.level==null || !client.level.dimension().equals(net.krodark.asterion.Asterion.ASTERION_LEVEL))return;
        int selection=Math.floorMod(client.player.getUUID().hashCode()+client.player.tickCount/120,ASTERION_TIPS.length);
        var tip=net.minecraft.network.chat.Component.translatable(ASTERION_TIPS[selection]);
        var lines=client.font.split(tip,Math.min(420,graphics.guiWidth()-40));
        int y=graphics.guiHeight()-26-lines.size()*client.font.lineHeight;
        for(var line:lines)graphics.drawString(client.font,line,(graphics.guiWidth()-client.font.width(line))/2,y+=client.font.lineHeight,0xFFD6C6A5);
    }

}
