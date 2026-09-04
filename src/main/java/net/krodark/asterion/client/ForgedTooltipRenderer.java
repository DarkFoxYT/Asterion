package net.krodark.asterion.client;

import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.CrucibleBlockEntity;
import net.krodark.asterion.item.ForgedTooltipComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Draws one opaque authored base plate and each subsequent material's addition above it. */
public final class ForgedTooltipRenderer implements ClientTooltipComponent {
    private static final int SIZE = 76;
    private static final Identifier[] BASE_TEXTURES = textures("base");
    private static final Identifier[] ADDITION_TEXTURES = textures("addition");
    private final String sequence;

    private ForgedTooltipRenderer(String sequence) {
        this.sequence = sequence;
    }

    public static void initialize() {
        ClientTooltipComponentCallback.EVENT.register(component -> component instanceof ForgedTooltipComponent forged
                ? new ForgedTooltipRenderer(forged.metalSequence()) : null);
    }

    @Override public int getHeight(Font font) { return SIZE + 2; }
    @Override public int getWidth(Font font) { return SIZE; }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height,
                             GuiGraphicsExtractor graphics) {
        draw(graphics, texture(sequence.charAt(0) - '0', "base"), x, y);
        for (int layer = 1; layer < sequence.length(); layer++)
            draw(graphics, texture(sequence.charAt(layer) - '0', "addition"), x, y);
    }

    private static void draw(GuiGraphicsExtractor graphics, Identifier texture, int x, int y) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0,
                SIZE, SIZE, SIZE, SIZE);
    }

    private static Identifier texture(int metal, String role) {
        Identifier[] textures = role.equals("base") ? BASE_TEXTURES : ADDITION_TEXTURES;
        return textures[Math.clamp(metal, 0, textures.length - 1)];
    }

    private static Identifier[] textures(String role) {
        Identifier[] result = new Identifier[9];
        for (int metal = 0; metal < result.length; metal++)
            result[metal] = Asterion.id("textures/tooltips/" + CrucibleBlockEntity.metalId(metal)
                    + "_" + role + ".png");
        return result;
    }
}
