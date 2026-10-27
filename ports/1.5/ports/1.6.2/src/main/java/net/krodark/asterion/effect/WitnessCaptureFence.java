package net.krodark.asterion.effect;

import java.util.concurrent.ConcurrentLinkedQueue;

/** Screenshot reads run after a complete ghost-free world frame, including while paused. */
public final class WitnessCaptureFence {
    private record Capture(long firstCleanFrame, Runnable operation) {}
    private static final ConcurrentLinkedQueue<Capture> WAITING = new ConcurrentLinkedQueue<>();
    private static volatile boolean observersPresent;
    private static volatile boolean executing;
    private static volatile boolean lastFrameHadWitnesses, drawingWitnesses;
    private static volatile int quietFrames;
    private static volatile long frame;
    private WitnessCaptureFence() {}

    public static void observersPresent(boolean present) { observersPresent = present; }
    public static boolean hidden() { return executing || quietFrames > 0 || !WAITING.isEmpty(); }
    public static void beginWorldFrame() { frame++; drawingWitnesses = false; }
    public static void witnessDrawn() { drawingWitnesses = true; }

    public static boolean defer(Runnable capture) {
        if (executing || !(observersPresent || lastFrameHadWitnesses || drawingWitnesses)) return false;
        // A request arriving after world geometry was drawn must wait for the following frame.
        WAITING.add(new Capture(frame + 1, capture));
        quietFrames = 2;
        return true;
    }

    public static void afterWorldFrame() {
        // The renderer sees hidden() throughout this frame, before any capture is allowed.
        lastFrameHadWitnesses = drawingWitnesses;
        Capture ready = WAITING.peek();
        if (ready != null && ready.firstCleanFrame() <= frame) {
            executing = true;
            try {
                while ((ready = WAITING.peek()) != null && ready.firstCleanFrame() <= frame) WAITING.remove().operation().run();
            } finally { executing = false; }
        }
        if (WAITING.isEmpty() && quietFrames > 0) quietFrames--;
    }

    public static void clearObservers() { observersPresent = false; }
}
