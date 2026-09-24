package net.krodark.asterion.update.underworld.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.krodark.asterion.network.FerryControlPayload;
import net.krodark.asterion.update.underworld.entity.CharonsFerryEntity;

/** Sends the actual movement keys, not camera yaw, to the server-owned ferry. */
public final class FerryControls {
    private static int previousId = -1, previousThrottle, previousTurn, interval;
    private FerryControls() { }
    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || !(client.player.getVehicle() instanceof CharonsFerryEntity ferry)) {
                previousId = -1; interval = 0; return;
            }
            int throttle = client.screen == null ? (client.options.keyUp.isDown() ? 1 : 0)
                    - (client.options.keyDown.isDown() ? 1 : 0) : 0;
            int turn = client.screen == null ? (client.options.keyRight.isDown() ? 1 : 0)
                    - (client.options.keyLeft.isDown() ? 1 : 0) : 0;
            if (!ClientPlayNetworking.canSend(FerryControlPayload.TYPE)) return;
            if (ferry.getId() != previousId || throttle != previousThrottle || turn != previousTurn || ++interval >= 5) {
                ClientPlayNetworking.send(new FerryControlPayload(ferry.getId(), throttle, turn));
                previousId = ferry.getId(); previousThrottle = throttle; previousTurn = turn; interval = 0;
            }
        });
    }
}
