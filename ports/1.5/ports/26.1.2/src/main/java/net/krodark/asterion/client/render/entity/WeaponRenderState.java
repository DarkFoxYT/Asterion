package net.krodark.asterion.client.render.entity;

/** World-bound animation caches must release their worlds even when no weapons render. */
public final class WeaponRenderState {
    private WeaponRenderState() { }
    public static void tick(net.minecraft.client.multiplayer.ClientLevel level) {
        WeaponChainAttachments.tick(level);
        WeaponFollowThrough.tick(level);
        MinotaurHandAttachment.tick(level);
    }
}
