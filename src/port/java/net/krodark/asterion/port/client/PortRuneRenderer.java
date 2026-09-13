package net.krodark.asterion.port.client;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.RuneBlock;
import net.krodark.asterion.block.RuneBlockEntity;
import net.minecraft.world.phys.Vec3;

/** Rune renderer with the original powered fade restored through Veil bloom. */
public final class PortRuneRenderer extends SimpleGeoBlockRenderer<RuneBlockEntity> {
    public PortRuneRenderer() {
        super(Asterion.id("block/rune"), rune -> Asterion.id("textures/block/runes/"
                + (rune.runeIndex() + 1) + ".png"), ignored -> Asterion.id("block/rune"));
        withEmissiveBones(PortRuneRenderer::glowColor, ignored -> true, "glow");
    }

    private static int glowColor(RuneBlockEntity rune) {
        int alpha = Math.clamp(Math.round(rune.glowPercent() * 2.55F), 0, 255);
        return alpha << 24 | (rune.glowColor() & 0x00FFFFFF);
    }

    @Override
    public boolean shouldRender(RuneBlockEntity rune, Vec3 camera) {
        return RuneBlock.isRoot(rune.getBlockState()) && super.shouldRender(rune, camera);
    }

    @Override public int getViewDistance() { return 96; }
}
