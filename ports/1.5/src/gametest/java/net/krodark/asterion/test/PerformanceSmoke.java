package net.krodark.asterion.test;

import net.krodark.asterion.Asterion;
import net.minecraft.client.Minecraft;
import java.lang.management.ManagementFactory;

/** Opt-in, local Spark profiles; never included in release jars. */
final class PerformanceSmoke {
    private static final java.util.List<Double> frames = new java.util.ArrayList<>();
    private static long previous;
    private static boolean recording;
    private static java.util.concurrent.CompletableFuture<Void> reload;
    private static int reloads;
    private static boolean finished;
    private static int finishingAt;
    private static java.lang.ref.WeakReference<net.minecraft.client.multiplayer.ClientLevel> observed = new java.lang.ref.WeakReference<>(null);
    private static final java.util.List<java.lang.ref.WeakReference<net.minecraft.client.multiplayer.ClientLevel>> oldWorlds = new java.util.ArrayList<>();

    static {
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.END.register(context -> {
            long now = System.nanoTime();
            if (recording && previous != 0) frames.add((now - previous) / 1_000_000.0);
            previous = now;
        });
    }

    static void tick(Minecraft client, int tick) {
        if (finished) return;
        var previousWorld = observed.get();
        if (previousWorld != client.level) {
            if (previousWorld != null) oldWorlds.add(observed);
            observed = new java.lang.ref.WeakReference<>(client.level);
        }
        if (finishingAt != 0) {
            if (tick - finishingAt >= 10) {
                long retained = oldWorlds.stream().filter(ref -> ref.get() != null).count();
                Asterion.LOGGER.info("ASTERION_PERF old client worlds retained after GC: {}/{}", retained, oldWorlds.size());
                if (retained != 0) throw new AssertionError("Previous client world remains reachable after transition/reload/GC");
                finished = true;
                client.stop();
            }
            return;
        }
        if (tick == 1) {
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 3440, 1440);
            client.options.renderDistance().set(12);
            client.options.simulationDistance().set(12);
            client.options.enableVsync().set(false);
            client.options.framerateLimit().set(260);
            client.getWindow().updateVsync(false);
            net.krodark.asterion.AsterionConfig.INSTANCE.adaptivePerformance = false;
            Asterion.LOGGER.info("ASTERION_PERF VSync OFF, Minecraft unlimited frame setting, adaptive quality OFF");
        }
        if (tick == 40) {
            RenderCacheLifecycleRegression.run(client);
            VoxelCacheLifecycleRegression.run();
            GpuCacheLifecycleRegression.run();
            NativeTextureCacheRegression.run();
        }
        if (tick == 200 || tick == 1700) {
            frames.clear(); previous = 0; recording = true;
            command(client, "spark profiler start --thread * --interval 10");
            Asterion.LOGGER.info("ASTERION_PERF framebuffer={}x{} renderDistance={} simulationDistance={} vsync={} limit={} gpu={}",
                    client.getMainRenderTarget().width, client.getMainRenderTarget().height,
                    client.options.renderDistance().get(), client.options.simulationDistance().get(),
                    client.options.enableVsync().get(), client.options.framerateLimit().get(),
                    org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));
            memory("start-" + tick);
        }
        if (tick == 1400 || tick == 2900) {
            recording = false;
            command(client, "spark profiler stop --save-to-file");
            double totalMs = 0, windowMs = 0, bestSecondFps = 0;
            int first = 0;
            for (int i = 0; i < frames.size(); i++) {
                double dt = frames.get(i); totalMs += dt; windowMs += dt;
                while (windowMs >= 1000 && first <= i) {
                    bestSecondFps = Math.max(bestSecondFps, (i - first + 1) * 1000.0 / windowMs);
                    windowMs -= frames.get(first++);
                }
            }
            Asterion.LOGGER.info("ASTERION_PERF {} meanFps={} bestOneSecondFps={}",
                    tick == 1400 ? "overworld" : "labyrinth", frames.size() * 1000.0 / totalMs, bestSecondFps);
            frames.sort(Double::compare);
            if (frames.isEmpty()) throw new AssertionError("No rendered frame samples");
            Asterion.LOGGER.info("ASTERION_PERF {} frames={} median={}ms p95={}ms p99={}ms",
                    tick == 1400 ? "overworld" : "labyrinth", frames.size(),
                    percentile(.50), percentile(.95), percentile(.99));
            memory("end-" + tick);
        }
        if (tick == 1450) {
            command(client, "execute in asterion:labyrinth run tp @a 0 80 0");
        }
        if (tick >= 2950) {
            if (reload == null) reload = client.reloadResourcePacks();
            else if (reload.isDone()) {
                reload.join();
                GpuCacheLifecycleRegression.run();
                if (++reloads == 3) {
                    System.gc();
                    memory("after-three-reloads");
                    Asterion.LOGGER.info("ASTERION_PERF PASS three resource reloads with GPU target recreation");
                    finishingAt = tick;
                } else reload = client.reloadResourcePacks();
            }
        }
    }

    private static double percentile(double p) {
        return frames.get(Math.min(frames.size() - 1, (int)(frames.size() * p)));
    }

    private static void command(Minecraft client, String command) {
        var server = client.getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    private static void memory(String phase) {
        Asterion.LOGGER.info("ASTERION_PERF memory {} heap={} nonheap={} gc={}", phase,
                ManagementFactory.getMemoryMXBean().getHeapMemoryUsage(),
                ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage(),
                ManagementFactory.getGarbageCollectorMXBeans().stream()
                        .map(gc -> gc.getName() + ":" + gc.getCollectionCount() + "/" + gc.getCollectionTime()).toList());
    }
}


