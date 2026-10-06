package net.krodark.asterion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import java.util.function.Supplier;
import net.krodark.asterion.client.ragdoll.EssentialSharedWorldSkins;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerInfo.class)
abstract class EssentialSharedWorldSkinMixin {
    @WrapOperation(method = "createSkinLookup", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/SkinManager;createLookup(Lcom/mojang/authlib/GameProfile;Z)Ljava/util/function/Supplier;"))
    private static Supplier<PlayerSkin> asterion$sharedWorldLookup(SkinManager manager, GameProfile profile,
            boolean requireSecure, Operation<Supplier<PlayerSkin>> original) {
        Supplier<PlayerSkin> verified = original.call(manager, profile, requireSecure);
        return new Supplier<>() {
            private Supplier<PlayerSkin> fallback;
            @Override public PlayerSkin get() {
                PlayerSkin skin = verified.get();
                if (!requireSecure || !EssentialSharedWorldSkins.isDefaultSkin(skin)
                        || !EssentialSharedWorldSkins.isSharedWorld()) return skin;
                return EssentialSharedWorldSkins.select(skin, () -> {
                    if (fallback == null) fallback = manager.createLookup(profile, false);
                    return fallback.get();
                }, true);
            }
        };
    }
}
