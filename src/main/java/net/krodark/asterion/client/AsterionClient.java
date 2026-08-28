package net.krodark.asterion.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.event.DeadSunClientEvents;
import net.krodark.asterion.client.light.HeldItemDynamicLights;
import net.krodark.asterion.client.light.LedAmneticLight;
import net.krodark.asterion.client.lightning.MazeZapRenderer;
import net.krodark.asterion.client.ragdoll.DismembermentEngine;
import net.krodark.asterion.client.ragdoll.RagdollClientController;
import net.krodark.asterion.client.render.entity.MinotaurGeoRenderer;
import net.krodark.asterion.client.render.portal.AsterionPortalRenderer;
import net.krodark.asterion.client.render.post.AsterionPostEffects;
import net.krodark.asterion.network.*;
import net.krodark.asterion.network.ragdoll.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

/** Client bootstrap. Runtime debug UI was removed; supported controls live in Mod Menu. */
public final class AsterionClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AsterionPostEffects.register();
        AsterionPortalRenderer.register();
        DimensionTransitionOverlay.register();
        BossFinaleOverlay.register();
        MazeObjectiveOverlay.register();
        MazeZapRenderer.register();
        DazeOverlay.register();
        RagdollClientController.initialize();
        EntityRenderers.register(Asterion.MINOTAUR, MinotaurGeoRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(DimensionTransitionPayload.TYPE, (payload, context) ->
                context.client().execute(() ->
                        DimensionTransitionOverlay.begin(payload.fadeInTicks(), payload.holdTicks())));
        ClientPlayNetworking.registerGlobalReceiver(EntryOmenPayload.TYPE, (payload, context) ->
                context.client().execute(() -> context.client().getSoundManager().play(
                        SimpleSoundInstance.forUI(Asterion.MINOTAUR_ROAR, 0.72F, 4.0F))));
        ClientPlayNetworking.registerGlobalReceiver(BossFinalePayload.TYPE, (payload, context) ->
                context.client().execute(BossFinaleOverlay::begin));
        ClientPlayNetworking.registerGlobalReceiver(GatewayPortalPayload.TYPE, (payload, context) ->
                context.client().execute(() -> AsterionPortalRenderer.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(MazeZapPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    MazeZapRenderer.receive(payload);
                    DeadSunClientEvents.receiveWardZap(payload);
                    RagdollClientController.suppressAutomaticFallRagdoll(payload.durationTicks() + 80);
                }));
        ClientPlayNetworking.registerGlobalReceiver(DeadSunEventPayload.TYPE, (payload, context) ->
                context.client().execute(() -> DeadSunClientEvents.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(MazeShiftPayload.TYPE, (payload, context) ->
                context.client().execute(() -> DeadSunClientEvents.receiveShift(payload)));
        ClientPlayNetworking.registerGlobalReceiver(DeadSunStrikePayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    DeadSunClientEvents.receiveStrike(payload);
                    MazeZapRenderer.receiveGroundStrike(payload);
                }));
        ClientPlayNetworking.registerGlobalReceiver(BossTelegraphPayload.TYPE, (payload, context) ->
                context.client().execute(() -> MazeZapRenderer.receiveTelegraph(payload)));
        ClientPlayNetworking.registerGlobalReceiver(DazePayload.TYPE, (payload, context) ->
                context.client().execute(() -> DazeOverlay.begin(payload)));
        ClientPlayNetworking.registerGlobalReceiver(RagdollImpulsePayload.TYPE, (payload, context) ->
                context.client().execute(() -> DismembermentEngine.INSTANCE.forcePlayerTumble(
                        context.client(), payload.source(), payload.impulse(), payload.force())));
        ClientPlayNetworking.registerGlobalReceiver(RagdollExplosionPayload.TYPE, (payload, context) ->
                context.client().execute(() -> DismembermentEngine.INSTANCE.applyExplosion(
                        context.client(), payload.center(), payload.radius())));
        ClientPlayNetworking.registerGlobalReceiver(RagdollAuthorityPayload.TYPE, (payload, context) ->
                context.client().execute(() -> DismembermentEngine.INSTANCE.reconcilePlayerAuthority(
                        context.client(), payload.position(), payload.velocity(), payload.serverTick())));
        ClientPlayNetworking.registerGlobalReceiver(RagdollPosePayload.TYPE, (payload, context) ->
                context.client().execute(() -> DismembermentEngine.INSTANCE.applyRemotePose(context.client(), payload)));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        DimensionTransitionOverlay.tick(client);
        DeadSunEntryCinematic.tick(client);
        BossFinaleOverlay.tick(client);
        MazeObjectiveOverlay.tick(client);
        DeadSunClientEvents.tick(client);
        DazeOverlay.tick(client);
        HeldItemDynamicLights.tick(client);
        LedAmneticLight.tickCleanup(client);
    }
}
