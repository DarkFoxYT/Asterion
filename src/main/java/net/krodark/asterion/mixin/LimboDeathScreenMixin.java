package net.krodark.asterion.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the first-death Limbo handoff cinematic: no menu click interrupts the transition. */
@Mixin(DeathScreen.class)
abstract class LimboDeathScreenMixin {
    @Unique private int asterion$deathTicks;
    @Unique private static final String[] ASTERION_TIPS={
        "tip.asterion.death.chain","tip.asterion.death.pillars","tip.asterion.death.leap",
        "tip.asterion.death.axe","tip.asterion.death.light","tip.asterion.death.centipede"};
    @Inject(method="extractRenderState",at=@At("TAIL"))
    private void asterion$deathTip(net.minecraft.client.gui.GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partial,CallbackInfo ci) {
        Minecraft client=Minecraft.getInstance();
        if(client.player==null || client.level==null || !client.level.dimension().equals(net.krodark.asterion.Asterion.ASTERION_LEVEL))return;
        int selection=Math.floorMod(client.player.getUUID().hashCode()+client.player.tickCount/120,ASTERION_TIPS.length);
        var tip=net.minecraft.network.chat.Component.translatable(ASTERION_TIPS[selection]);
        var lines=client.font.split(tip,Math.min(420,graphics.guiWidth()-40));
        int y=graphics.guiHeight()-26-lines.size()*client.font.lineHeight;
        for(var line:lines)graphics.text(client.font,line,(graphics.guiWidth()-client.font.width(line))/2,y+=client.font.lineHeight,0xFFD6C6A5);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void asterion$automaticPassage(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (++asterion$deathTicks < 24 || client.player == null || client.level == null) return;
        // Labyrinth deaths have their own obelisk recovery. Limbo deaths remain conventional.
        if (client.level.dimension().equals(net.krodark.asterion.Asterion.ASTERION_LEVEL)
                || client.level.dimension().equals(net.krodark.asterion.Asterion.LIMBO_LEVEL)) return;
        client.player.respawn();
        asterion$deathTicks = Integer.MIN_VALUE / 2;
    }
}
