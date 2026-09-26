package net.krodark.asterion.update.underworld.entity;

/** One size contract for the model, collision body and procedural legs. */
public final class SpiderDimensions {
    public static final float MIN_SIZE = 1.25F;
    public static final float MAX_SIZE = 1.5F;
    public static final float RENDER_SCALE = .7F * MAX_SIZE;
    public static final float WIDTH = 1.45F * MAX_SIZE;
    public static final float HEIGHT = 1.3F * MAX_SIZE;
    public static float renderScale(float size) { return .7F * size; }
    public static float width(float size) { return 1.45F * size; }
    public static float height(float size) { return 1.3F * size; }
    private SpiderDimensions() { }
}
