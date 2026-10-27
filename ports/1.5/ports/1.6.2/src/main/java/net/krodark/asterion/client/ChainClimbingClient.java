package net.krodark.asterion.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.krodark.asterion.network.ChainHoldPayload;
import net.krodark.asterion.physics.ChainGrip;

public final class ChainClimbingClient {
    private static boolean holding;
    private static int ticks;
    private ChainClimbingClient() { }
    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.player==null) {holding=false;ticks=0;return;}
            boolean selected=ChainGrip.target(client.player)!=null;
            boolean active=client.screen==null && !client.isPaused() && client.options.keyUse.isDown() && selected;
            if(!active)ChainGrip.release(client.player);
            else client.player.setSprinting(false);
            if((selected && !active || active!=holding || active && ++ticks>=5) && ClientPlayNetworking.canSend(ChainHoldPayload.TYPE)) {
                ClientPlayNetworking.send(new ChainHoldPayload(active));ticks=0;
            }
            holding=active;
        });
    }
}
