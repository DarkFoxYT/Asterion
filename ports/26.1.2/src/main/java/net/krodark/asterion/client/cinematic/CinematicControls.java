package net.krodark.asterion.client.cinematic;

import net.minecraft.client.Minecraft;

 
public final class CinematicControls {
    private CinematicControls() { }
    public static boolean locked() {
        if (net.krodark.asterion.client.AsterionClient.isPlayback(Minecraft.getInstance())) return false;
        return BossEntranceCinematic.isActive()
                || CursedBrazierCinematic.isActive()
                || DeadSunEntryCinematic.isActive()
                || RoofCollapseCinematic.isActive()
                || BossFinaleOverlay.isActive();
    }
    public static void tick(Minecraft client) {
        if (!locked() || client.player == null) return;
        for (var key : new net.minecraft.client.KeyMapping[]{client.options.keyUp, client.options.keyDown,
                client.options.keyLeft, client.options.keyRight, client.options.keyJump, client.options.keyShift,
                client.options.keySprint, client.options.keyAttack, client.options.keyUse}) key.setDown(false);
        client.player.setDeltaMovement(DeadSunEntryCinematic.isActive()
                ? new net.minecraft.world.phys.Vec3(0, client.player.getDeltaMovement().y, 0)
                : net.minecraft.world.phys.Vec3.ZERO);
        client.player.setSprinting(false);
        client.player.setJumping(false);
    }
}
