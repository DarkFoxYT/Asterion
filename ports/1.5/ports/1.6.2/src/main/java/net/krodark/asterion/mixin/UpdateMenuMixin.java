package net.krodark.asterion.mixin;

import net.krodark.asterion.update.UpdateMenuButton;
import net.krodark.asterion.update.UpdateNotifications;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class UpdateMenuMixin extends Screen {
    @Unique private UpdateMenuButton asterion$update;
    @Unique private UpdateMenuButton asterion$dismiss;

    protected UpdateMenuMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void asterion$addUpdateMenu(CallbackInfo ci) {
        UpdateNotifications.start(Minecraft.getInstance().gameDirectory.toPath());
        asterion$update = addRenderableWidget(new UpdateMenuButton(this, Math.max(2, width - 228), false));
        asterion$dismiss = addRenderableWidget(new UpdateMenuButton(this, width - 24, true));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void asterion$refreshUpdateMenu(CallbackInfo ci) {
        boolean show = UpdateNotifications.available() != null;
        if (asterion$update != null) asterion$update.visible = asterion$update.active = show;
        if (asterion$dismiss != null) asterion$dismiss.visible = asterion$dismiss.active = show;
    }
}
