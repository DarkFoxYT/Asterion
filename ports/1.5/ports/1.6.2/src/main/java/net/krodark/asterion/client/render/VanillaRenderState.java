package net.krodark.asterion.client.render;

/** Raw OpenGL passes can invalidate the command encoder's cached pipeline/program. */
public final class VanillaRenderState {
    private static long generation;
    private VanillaRenderState() { }
    public static void changed() { generation++; }
    public static long generation() { return generation; }
}
