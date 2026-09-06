package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import java.nio.file.*;
import java.util.*;

/** Keeps independent failures visible without abandoning the rest of a full mod audit. */
public final class ModAuditGameTest implements FabricClientGameTest {
    private static final String DEFAULT_TESTS = "GameplayFixesGameTest,ForgeInteractionGameTest,ForgeRecipesGameTest,ManualForgeGameTest,ForgeScreenGameTest,ForgedPoseGameTest,ArmorGameTest,BossMechanicsAuditGameTest,QueenTreeGameTest,RagdollMultiplayerGameTest,CatacombLayoutGameTest,DoorGameTest,CinematicPreviewGameTest,MinotaurBodyGameTest,MinotaurGrabGameTest,MinotaurRemainsGameTest,GameplayGameTest,RunePuzzleGameTest,PedestalGameTest,LamenterGameTest,BarrelDoorGameTest,ChainLiftGameTest,LiftForgeFixesGameTest,CentipedeRideGameTest,ForgeGenerationGameTest,CavernGameTest,ShaleFormationGameTest,GatewayRuinsGameTest,HeavyWaterloggingGameTest,HeavyWaterGameTest,RenderPerformanceGameTest,PortalEmissionGameTest,VineEmissionGameTest,WorldPerformanceGameTest,NormalWorldCreationGameTest,net.krodark.asterion.worldgen.BiomeFeatureGameTest";
    @Override public void runTest(ClientGameTestContext context) {
        String selected = System.getProperty("asterion.audit.tests", "");
        if (selected.isBlank()) selected = DEFAULT_TESTS;
        Path report = Path.of("audit-results.tsv");
        write(report, "test\tresult\tmilliseconds\tdetail\n", false);
        var failures = new ArrayList<String>();
        for (String name : selected.split(",")) {
            name = name.trim();
            long start = System.nanoTime();
            Asterion.LOGGER.info("AUDIT START: {}", name);
            String result = "PASS", detail = "";
            try {
                context.restoreDefaultGameOptions();
                context.runOnClient(client -> {
                    client.options.hideGui = false;
                    // Automated input must not be mistaken for a player AFK for ten minutes.
                    client.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
                    client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                });
                String type = name.contains(".") ? name : "net.krodark.asterion.dev.verification." + name;
                ((FabricClientGameTest)Class.forName(type).getDeclaredConstructor().newInstance()).runTest(context);
            } catch (Exception | AssertionError error) {
                result = "FAIL";
                detail = error.toString().replace('\n', ' ').replace('\t', ' ');
                failures.add(name + ": " + detail);
                Asterion.LOGGER.error("AUDIT FAIL: {}", name, error);
            }
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            write(report, name + "\t" + result + "\t" + elapsed + "\t" + detail + "\n", true);
            Asterion.LOGGER.info("AUDIT {}: {} ({} ms)", result, name, elapsed);
        }
        if (!failures.isEmpty()) throw new AssertionError("Audit failures: " + String.join("; ", failures));
        Asterion.LOGGER.info("PASS: full selected mod audit");
    }
    private static void write(Path path, String text, boolean append) {
        try { Files.writeString(path, text, StandardOpenOption.CREATE,
                append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING); }
        catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
}
