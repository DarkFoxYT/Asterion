package net.krodark.labyrinth.client;

import com.meekdev.amnetic.client.ui.AmneticEditor;
import com.meekdev.amnetic.client.ui.Inspector;
import imgui.ImGui;
import imgui.type.ImBoolean;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.krodark.labyrinth.LabyrinthConfig;
import net.krodark.labyrinth.client.render.post.LabyrinthPostEffects;
import net.krodark.labyrinth.client.render.portal.LabyrinthPortalRenderer;
import net.krodark.labyrinth.client.lightning.MazeZapRenderer;
import net.krodark.labyrinth.client.render.entity.MinotaurPlaceholderRenderer;
import net.krodark.labyrinth.network.DimensionTransitionPayload;
import net.krodark.labyrinth.network.GatewayPortalPayload;
import net.krodark.labyrinth.network.MazeZapPayload;
import net.krodark.labyrinth.network.DeadSunEventPayload;
import net.krodark.labyrinth.client.event.DeadSunClientEvents;
import net.krodark.labyrinth.client.light.HeldItemDynamicLights;
import net.krodark.labyrinth.client.light.LedAmneticLight;
import net.krodark.labyrinth.client.ragdoll.DismembermentEngine;
import net.krodark.labyrinth.client.ragdoll.RagdollClientController;
import net.krodark.labyrinth.network.ragdoll.*;
import net.krodark.labyrinth.Labyrinth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import org.lwjgl.glfw.GLFW;

public class LabyrinthClient implements ClientModInitializer {
    private final LabyrinthInspector inspector = new LabyrinthInspector();
    private boolean keyWasDown;

    @Override
    public void onInitializeClient() {
        LabyrinthPostEffects.register();
        LabyrinthPortalRenderer.register();
        DimensionTransitionOverlay.register();
        EclipseOverlay.register();
        MazeZapRenderer.register();
        RagdollClientController.initialize();
        EntityRenderers.register(Labyrinth.MINOTAUR, MinotaurPlaceholderRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(DimensionTransitionPayload.TYPE, (payload, context) ->
                context.client().execute(() ->
                        DimensionTransitionOverlay.begin(payload.fadeInTicks(), payload.holdTicks())));
        ClientPlayNetworking.registerGlobalReceiver(GatewayPortalPayload.TYPE, (payload, context) ->
                context.client().execute(() -> LabyrinthPortalRenderer.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(MazeZapPayload.TYPE, (payload, context) ->
                context.client().execute(() -> MazeZapRenderer.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(DeadSunEventPayload.TYPE, (payload, context) ->
                context.client().execute(() -> DeadSunClientEvents.receive(payload)));
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
        AmneticEditor.register(inspector);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        DimensionTransitionOverlay.tick(client);
        DeadSunClientEvents.tick(client);
        HeldItemDynamicLights.tick(client);
        LedAmneticLight.tickCleanup(client);
        boolean down = GLFW.glfwGetKey(client.getWindow().handle(), GLFW.GLFW_KEY_F8) == GLFW.GLFW_PRESS;
        if (down && !keyWasDown) {
            inspector.open().set(true);
            if (!AmneticEditor.isEnabled()) AmneticEditor.toggle();
        }
        keyWasDown = down;
    }

    private static final class LabyrinthInspector extends Inspector {
        private LabyrinthInspector() {
            super("Labyrinth", "Labyrinth", false);
        }

        @Override
        public void render() {
            LabyrinthConfig c = LabyrinthConfig.INSTANCE;
            if (ImGui.beginTabBar("LabyrinthTabs")) {
                if (ImGui.beginTabItem("Generation")) {
                    renderGeneration(c);
                    ImGui.endTabItem();
                }
                if (ImGui.beginTabItem("Shaders")) {
                    renderShaders(c);
                    ImGui.endTabItem();
                }
                if (ImGui.beginTabItem("Minotaur")) {
                    renderMinotaur(c);
                    ImGui.endTabItem();
                }
                ImGui.endTabBar();
            }
        }

    private static void renderGeneration(LabyrinthConfig c) {
        int[] ruinChance = {c.underwaterRuinChance};
        float[] itemChance = {c.mechanismChance};
        int[] distance = {c.gatewayDistance};
        int[] radius = {c.mazeRadiusCells};
        int[] cell = {c.cellSize};
        int[] thickness = {c.wallThickness};
        int[] height = {c.wallHeight};
        int[] floorThickness = {c.floorThickness};
        int[] loopChance = {c.mazeLoopChance};
        int[] landmarkChance = {c.mazeLandmarkChance};
        int[] decaySeconds = {c.playerBlockDecayTicks / 20};
        int[] zapSeconds = {c.wallZapDelayTicks / 20};

        ImGui.text("World generation");
        ImGui.separator();
        if (ImGui.sliderInt("Gateway distance", distance, 1_000, 50_000)) c.gatewayDistance = distance[0];
        if (ImGui.sliderInt("Maze radius (cells)", radius, 16, 160)) c.mazeRadiusCells = radius[0];
        if (ImGui.sliderInt("Cell size", cell, 9, 21)) c.cellSize = cell[0] | 1;
        if (ImGui.sliderInt("Wall thickness", thickness, 2, 6)) c.wallThickness = thickness[0];
        if (ImGui.sliderInt("Wall height", height, 16, 64)) c.wallHeight = height[0];
        if (ImGui.sliderInt("Floor thickness", floorThickness, 2, 8)) c.floorThickness = floorThickness[0];
        if (ImGui.sliderInt("Extra loop: 1 in N edges", loopChance, 16, 96)) c.mazeLoopChance = loopChance[0];
        if (ImGui.sliderInt("Landmark: 1 in N cells", landmarkChance, 12, 96)) c.mazeLandmarkChance = landmarkChance[0];

        ImGui.spacing();
        ImGui.text("Maze rules");
        ImGui.separator();
        if (ImGui.sliderInt("Placed block lifetime (seconds)", decaySeconds, 2, 180))
            c.playerBlockDecayTicks = decaySeconds[0] * 20;
        if (ImGui.sliderInt("Wall-top warning (seconds)", zapSeconds, 1, 10))
            c.wallZapDelayTicks = zapSeconds[0] * 20;

        ImGui.spacing();
        ImGui.text("Underwater ruins");
        ImGui.separator();
        if (ImGui.sliderInt("Ruin: 1 in N chunks", ruinChance, 16, 4096)) c.underwaterRuinChance = ruinChance[0];
        if (ImGui.sliderFloat("Mechanism loot chance", itemChance, 0.01f, 1.0f)) c.mechanismChance = itemChance[0];

        ImGui.spacing();
        ImGui.textWrapped("Changes affect newly generated chunks. Existing maze blocks are not replaced.");
        if (ImGui.button("Save settings")) c.save();
    }

    private static void renderMinotaur(LabyrinthConfig c) {
        int[] stalkDistance = {c.minotaurStalkDistance};
        int[] approachDistance = {c.minotaurApproachDistance};
        int[] gazeMin = {c.minotaurGazeMinTicks / 20};
        int[] gazeMax = {c.minotaurGazeMaxTicks / 20};
        int[] windupMin = {c.minotaurWindupMinTicks / 20};
        int[] windupMax = {c.minotaurWindupMaxTicks / 20};
        int[] escapeTime = {c.minotaurEscapeTicks / 20};
        int[] escapeDistance = {c.minotaurEscapeDistance};
        int[] damageMin = {c.minotaurDamageMin};
        int[] damageMax = {c.minotaurDamageMax};

        ImGui.text("Eclipse hunting phase");
        ImGui.separator();
        if (ImGui.sliderInt("Stalking distance", stalkDistance, 28, 56)) c.minotaurStalkDistance = stalkDistance[0];
        if (ImGui.sliderInt("Approach trigger distance", approachDistance, 14, 40)) c.minotaurApproachDistance = approachDistance[0];
        if (ImGui.sliderInt("Minimum gaze (seconds)", gazeMin, 3, 15)) c.minotaurGazeMinTicks = gazeMin[0] * 20;
        if (ImGui.sliderInt("Maximum gaze (seconds)", gazeMax, 4, 20)) c.minotaurGazeMaxTicks = gazeMax[0] * 20;

        ImGui.spacing();
        ImGui.text("Chase phase");
        ImGui.separator();
        if (ImGui.sliderInt("Charge warning min (seconds)", windupMin, 2, 6)) c.minotaurWindupMinTicks = windupMin[0] * 20;
        if (ImGui.sliderInt("Charge warning max (seconds)", windupMax, 3, 8)) c.minotaurWindupMaxTicks = windupMax[0] * 20;
        if (ImGui.sliderInt("Escape time (seconds)", escapeTime, 60, 240)) c.minotaurEscapeTicks = escapeTime[0] * 20;
        if (ImGui.sliderInt("Safe escape distance", escapeDistance, 20, 56)) c.minotaurEscapeDistance = escapeDistance[0];
        if (ImGui.sliderInt("Damage threshold min", damageMin, 20, 100)) c.minotaurDamageMin = damageMin[0];
        if (ImGui.sliderInt("Damage threshold max", damageMax, 30, 140)) c.minotaurDamageMax = damageMax[0];

        ImGui.spacing();
        ImGui.textWrapped("Boss mode is reserved and remains disabled until its attack design is finalized.");
        if (ImGui.button("Save Minotaur settings")) c.save();
    }

    private static void renderShaders(LabyrinthConfig c) {
        ImBoolean deadSun = new ImBoolean(c.deadSunEnabled);
        ImBoolean dustyAir = new ImBoolean(c.dustyAirEnabled);
        float[] deadSunStrength = {c.deadSunStrength};
        float[] dustyAirStrength = {c.dustyAirStrength};
        float[] sunHeight = {c.deadSunHeight};
        float[] sunSize = {c.deadSunSize};
        float[] sunBrightness = {c.deadSunBrightness};
        float[] animationSpeed = {c.shaderAnimationSpeed};
        float[] dustDensity = {c.dustDensity};
        float[] fogStrength = {c.fogStrength};
        float[] sunX = {c.deadSunX};
        float[] sunZ = {c.deadSunZ};
        float[] corona = {c.deadSunCorona};
        float[] sunDensity = {c.deadSunDensity};
        float[] sunOpacity = {c.deadSunOpacity};
        float[] coreColor = {c.deadSunCoreR, c.deadSunCoreG, c.deadSunCoreB};
        float[] coronaColor = {c.deadSunCoronaR, c.deadSunCoronaG, c.deadSunCoronaB};
        float[] dustColor = {c.dustR, c.dustG, c.dustB};
        float[] fogColor = {c.fogR, c.fogG, c.fogB};

        ImGui.text("Dead Sun");
        ImGui.separator();
        if (ImGui.checkbox("Dead sun", deadSun)) c.deadSunEnabled = deadSun.get();
        if (ImGui.sliderFloat("Dead sun strength", deadSunStrength, 0.0f, 2.0f)) c.deadSunStrength = deadSunStrength[0];
        if (ImGui.sliderFloat("Sun height", sunHeight, 100.0f, 240.0f)) c.deadSunHeight = sunHeight[0];
        if (ImGui.sliderFloat("Sun radius", sunSize, 8.0f, 48.0f)) c.deadSunSize = sunSize[0];
        if (ImGui.sliderFloat("Sun emission", sunBrightness, 0.25f, 4.0f)) c.deadSunBrightness = sunBrightness[0];
        if (ImGui.sliderFloat("Sun density", sunDensity, 0.1f, 3.0f)) c.deadSunDensity = sunDensity[0];
        if (ImGui.sliderFloat("Sun opacity", sunOpacity, 0.0f, 1.0f)) c.deadSunOpacity = sunOpacity[0];
        if (ImGui.sliderFloat("Corona size", corona, 0.0f, 3.0f)) c.deadSunCorona = corona[0];
        if (ImGui.sliderFloat("Sun position X", sunX, -1024.0f, 1024.0f)) c.deadSunX = sunX[0];
        if (ImGui.sliderFloat("Sun position Z", sunZ, -1024.0f, 1024.0f)) c.deadSunZ = sunZ[0];
        if (ImGui.colorEdit3("Core color", coreColor)) {
            c.deadSunCoreR = coreColor[0]; c.deadSunCoreG = coreColor[1]; c.deadSunCoreB = coreColor[2];
        }
        if (ImGui.colorEdit3("Corona color", coronaColor)) {
            c.deadSunCoronaR = coronaColor[0]; c.deadSunCoronaG = coronaColor[1]; c.deadSunCoronaB = coronaColor[2];
        }

        ImGui.spacing();
        ImGui.text("Volumetric atmosphere");
        ImGui.separator();
        if (ImGui.checkbox("Dusty air", dustyAir)) c.dustyAirEnabled = dustyAir.get();
        if (ImGui.sliderFloat("Dust strength", dustyAirStrength, 0.0f, 2.0f)) c.dustyAirStrength = dustyAirStrength[0];
        if (ImGui.sliderFloat("Dust density", dustDensity, 0.0f, 2.5f)) c.dustDensity = dustDensity[0];
        if (ImGui.sliderFloat("Fog strength", fogStrength, 0.0f, 2.5f)) c.fogStrength = fogStrength[0];
        if (ImGui.sliderFloat("Animation speed", animationSpeed, 0.0f, 2.0f)) c.shaderAnimationSpeed = animationSpeed[0];
        if (ImGui.colorEdit3("Dust color", dustColor)) {
            c.dustR = dustColor[0]; c.dustG = dustColor[1]; c.dustB = dustColor[2];
        }
        if (ImGui.colorEdit3("Fog color", fogColor)) {
            c.fogR = fogColor[0]; c.fogG = fogColor[1]; c.fogB = fogColor[2];
        }

        ImGui.spacing();
        ImGui.textWrapped("F8 opens this window. Visual changes preview immediately inside the Labyrinth and persist when saved.");
        if (ImGui.button("Reset visual defaults")) {
            c.deadSunEnabled = true;
            c.dustyAirEnabled = true;
            c.deadSunStrength = 0.661f;
            c.dustyAirStrength = 1.0f;
            c.deadSunHeight = 240.0f; c.deadSunSize = 48.0f; c.deadSunBrightness = 4.0f;
            c.shaderAnimationSpeed = 1.0f; c.dustDensity = 1.499f; c.fogStrength = 1.508f;
            c.deadSunX = 0.0f; c.deadSunZ = 0.0f; c.deadSunCorona = 1.423f;
            c.deadSunDensity = 3.0f; c.deadSunOpacity = 1.0f;
            c.deadSunCoreR = 1.0f; c.deadSunCoreG = 0.055f; c.deadSunCoreB = 0.025f;
            c.deadSunCoronaR = 1.0f; c.deadSunCoronaG = 0.025f; c.deadSunCoronaB = 0.012f;
            c.dustR = 0.2607004f; c.dustG = 0.07607989f; c.dustB = 0.07607989f;
            c.fogR = 0.15294118f; c.fogG = 0.1364837f; c.fogB = 0.049780853f;
        }
        ImGui.sameLine();
        if (ImGui.button("Save shader settings")) c.save();
    }
    }
}
