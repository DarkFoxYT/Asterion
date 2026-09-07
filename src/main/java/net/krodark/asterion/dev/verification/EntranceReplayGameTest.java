package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.AsterionClient;
import net.krodark.asterion.client.ReplayCompatibility;
import net.krodark.asterion.client.cinematic.*;
import net.krodark.asterion.entity.*;
import net.krodark.asterion.network.BossEntrancePayload;
import net.krodark.asterion.worldgen.BossArenaEncounter;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;

public final class EntranceReplayGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                var boss = Asterion.MINOTAUR.create(server.overworld(), EntitySpawnReason.COMMAND);
                try {
                    var geometry = GameplayFixesGameTest.class.getDeclaredMethod("checkEntrance", net.minecraft.server.MinecraftServer.class);
                    geometry.setAccessible(true);
                    geometry.invoke(null, server);
                    var data = MinotaurEntity.class.getDeclaredField("DATA_DOOR_ENTRY_TICKS");
                    data.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    var accessor = (net.minecraft.network.syncher.EntityDataAccessor<Integer>)data.get(null);
                    boss.getEntityData().set(accessor, MinotaurAnimationTiming.ENTRY_BREAK_TICK);
                    check(boss.animationState() == MinotaurEntity.AnimationState.IDLE, "Pre-breach pose");
                    for (int tick = MinotaurAnimationTiming.ENTRY_BREAK_TICK;
                            tick < MinotaurAnimationTiming.ENTRY_CROUCH_TICK; tick++) {
                        boss.getEntityData().set(accessor, tick + 1);
                        check(boss.animationState() == MinotaurEntity.AnimationState.CHASE, "Burst missing running animation");
                        check(MinotaurAnimationTiming.entryWalkDistance(tick + 1, 10)
                                > MinotaurAnimationTiming.entryWalkDistance(tick, 10), "Walk stopped early");
                    }
                    check(MinotaurAnimationTiming.entryWalkDistance(MinotaurAnimationTiming.ENTRY_BREAK_TICK + 10, 1) > .5, "Burst lacks immediate forward momentum");
                    boss.getEntityData().set(accessor, MinotaurAnimationTiming.ENTRY_TAKEOFF_TICK + 1);
                    check(boss.animationState() == MinotaurEntity.AnimationState.LEAP, "Airborne pose missing");
                    boss.getEntityData().set(accessor, MinotaurAnimationTiming.ENTRY_LAND_TICK + 1);
                    check(boss.animationState() == MinotaurEntity.AnimationState.LAND, "Landing pose missing");
                    boss.getEntityData().set(accessor, MinotaurAnimationTiming.ENTRY_CROUCH_TICK + 1);
                    check(boss.animationState() == MinotaurEntity.AnimationState.LEAP, "Missing grounded jump windup");
                    check(MinotaurEntranceMotion.point(MinotaurAnimationTiming.ENTRY_CROUCH_TICK, boss.getBbWidth()).equals(
                            MinotaurEntranceMotion.point(MinotaurAnimationTiming.ENTRY_TAKEOFF_TICK, boss.getBbWidth())), "Feet slide during crouch");
                    check(MinotaurEntranceMotion.animationSeconds(MinotaurAnimationTiming.ENTRY_TAKEOFF_TICK + 20) > .875,
                            "Crouching clip played at flight apex");
                    check(Math.abs(MinotaurEntranceMotion.animationSeconds(MinotaurAnimationTiming.ENTRY_LAND_TICK) - .1667) < 1e-8,
                            "Landing compression is not aligned to contact");
                    var landing = MinotaurEntranceMotion.point(MinotaurAnimationTiming.ENTRY_LAND_TICK, boss.getBbWidth());
                    check(Math.abs(landing.x - .5) < 1e-8 && Math.abs(landing.z - .5) < 1e-8, "Leap missed arena center");
                    var apex = MinotaurEntranceMotion.point(MinotaurAnimationTiming.ENTRY_TAKEOFF_TICK + 20, boss.getBbWidth());
                    check(apex.y > landing.y + 11, "Missing leap arc");
                    for (int boundary : new int[]{MinotaurAnimationTiming.ENTRY_BREAK_TICK, MinotaurAnimationTiming.ENTRY_TAKEOFF_TICK,
                            MinotaurAnimationTiming.ENTRY_LAND_TICK})
                        check(MinotaurEntranceMotion.point(boundary - .001, boss.getBbWidth()).distanceTo(
                                MinotaurEntranceMotion.point(boundary + .001, boss.getBbWidth())) < .01, "Entrance position jumps");
                    boss.getEntityData().set(accessor, MinotaurAnimationTiming.ENTRY_WALK_END_TICK + 1);
                    check(boss.animationState() == MinotaurEntity.AnimationState.ROAR_START, "No planted roar");
                    check(Math.abs(MinotaurAnimationTiming.entryWalkDistance(MinotaurAnimationTiming.ENTRY_END_TICK, 10) - 10) < .00001,
                            "Wrong entrance distance");
                    check(MinotaurAnimationTiming.ENTRY_ROAR.roarSoundTick()
                            + Math.round(120 / MinotaurAnimationTiming.ENTRY_ROAR_PITCH)
                            == MinotaurAnimationTiming.ENTRY_END_TICK, "Roar audio duration drift");
                    check(BossArenaEncounter.INTRO_TICKS < 420, "Intro not shortened");
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            context.waitTicks(10);
            context.runOnClient(client -> {
                var payload = new BossEntrancePayload(Direction.NORTH, 0, BossArenaEncounter.INTRO_TICKS);
                BossEntranceCinematic.receive(payload);
                check(BossEntranceCinematic.isActive(), "Normal cinematic suppressed");
                try {
                    int userFov = client.options.fov().get();
                    var cinematicTicks = BossEntranceCinematic.class.getDeclaredField("ticks");
                    cinematicTicks.setAccessible(true);
                    cinematicTicks.setInt(null, MinotaurAnimationTiming.ENTRY_TAKEOFF_TICK + 15);
                    check(Math.abs(BossEntranceCinematic.fov(70, .5F) - 96) < .01, "Flight FOV did not widen");
                    cinematicTicks.setInt(null, MinotaurAnimationTiming.ENTRY_LAND_TICK + 30);
                    check(Math.abs(BossEntranceCinematic.fov(70, .5F) - 90) < .01, "Frontal FOV did not settle");
                    var frontal = BossEntranceCinematic.cameraPose(client.player.getEyePosition(), .5F);
                    check(Math.abs(frontal.position().x - .5) < 3 && Math.abs(frontal.roll()) <= 3,
                            "Frontal shot or subtle roll lost");
                    check(client.options.fov().get() == userFov, "Cinematic overwrote user FOV preference");
                    var camera = client.gameRenderer.getMainCamera();
                    var field = java.util.Arrays.stream(camera.getClass().getDeclaredFields())
                            .filter(f -> f.getType() == Entity.class).findFirst().orElseThrow();
                    field.setAccessible(true);
                    var original = field.get(camera);
                    var observer = Asterion.MINOTAUR.create(client.level, EntitySpawnReason.COMMAND);
                    try {
                        field.set(camera, observer);
                        check(AsterionClient.isPlayback(client), "Detached replay camera not detected");
                        ReplayCompatibility.cancelCinematics(client);
                        check(BossEntranceCinematic.fov(73, .5F) == 73, "Replay cancellation retained forced FOV");
                        check(!BossEntranceCinematic.isActive() && !CinematicHud.isHidden()
                                && !CinematicControls.locked(), "Replay cancellation left locks");
                        BossEntranceCinematic.receive(payload);
                        DimensionTransitionOverlay.begin(20, 20);
                        DeadSunEntryCinematic.begin();
                        CrucibleCamera.begin(net.minecraft.core.BlockPos.ZERO);
                        check(!BossEntranceCinematic.isActive() && !DimensionTransitionOverlay.isActive()
                                && !DeadSunEntryCinematic.isActive() && !CrucibleCamera.active(),
                                "Replay restarted cinematic or GUI camera");
                    } finally { field.set(camera, original); }
                    BossEntranceCinematic.receive(payload);
                    check(BossEntranceCinematic.isActive(), "Playback exit left cinematics disabled");
                    BossEntranceCinematic.finish(client);
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            Asterion.LOGGER.info("PASS: entrance walking/roar phases, audio timing, replay cancellation and normal-play recovery");
        }
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
