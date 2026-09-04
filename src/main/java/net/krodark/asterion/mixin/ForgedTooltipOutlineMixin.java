package net.krodark.asterion.mixin;

import java.util.List;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.CrucibleBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.joml.Vector2ic;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Composites forged metal artwork over the tooltip border, never inside its contents. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class ForgedTooltipOutlineMixin {
    @Unique private static final int ASTERION_TEXTURE_SIZE = 76;
    @Unique private static final Identifier[] ASTERION_BASE = asterion$textures("base");
    @Unique private static final Identifier[] ASTERION_ADDITION = asterion$textures("addition");
    @Unique private String asterion$metalSequence = "";

    @Inject(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At("HEAD"))
    private void asterion$captureForgedStack(Font font, ItemStack stack, int x, int y, CallbackInfo ci) {
        asterion$metalSequence = "";
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return;
        CompoundTag tag = data.copyTag();
        String sequence = tag.getStringOr("metal_sequence", "");
        if (!sequence.isEmpty() && sequence.chars().allMatch(value -> value >= '0' && value <= '8'))
            asterion$metalSequence = sequence;
    }

    @Inject(method = "tooltip", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/TooltipRenderUtil;extractTooltipBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIILnet/minecraft/resources/Identifier;)V",
            shift = At.Shift.AFTER))
    private void asterion$drawForgedOutline(Font font, List<ClientTooltipComponent> lines,
                                             int mouseX, int mouseY,
                                             ClientTooltipPositioner positioner,
                                             @Nullable Identifier style, CallbackInfo ci) {
        if (asterion$metalSequence.isEmpty()) return;
        int width = 0;
        int height = lines.size() == 1 ? -2 : 0;
        for (ClientTooltipComponent line : lines) {
            width = Math.max(width, line.getWidth(font));
            height += line.getHeight(font);
        }
        GuiGraphicsExtractor graphics = (GuiGraphicsExtractor)(Object)this;
        Vector2ic point = positioner.positionTooltip(graphics.guiWidth(), graphics.guiHeight(),
                mouseX, mouseY, width, height);
        int x = point.x() - 4, y = point.y() - 4;
        int frameWidth = width + 8, frameHeight = height + 8;
        asterion$draw(graphics, ASTERION_BASE[asterion$metalSequence.charAt(0) - '0'],
                x, y, frameWidth, frameHeight);
        for (int layer = 1; layer < asterion$metalSequence.length(); layer++)
            asterion$draw(graphics, ASTERION_ADDITION[asterion$metalSequence.charAt(layer) - '0'],
                    x, y, frameWidth, frameHeight);
        asterion$metalSequence = "";
    }

    @Unique
    private static void asterion$draw(GuiGraphicsExtractor graphics, Identifier texture,
                                       int x, int y, int width, int height) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0,
                width, height, ASTERION_TEXTURE_SIZE, ASTERION_TEXTURE_SIZE);
    }

    @Unique
    private static Identifier[] asterion$textures(String role) {
        Identifier[] textures = new Identifier[9];
        for (int metal = 0; metal < textures.length; metal++)
            textures[metal] = Asterion.id("textures/tooltips/" + CrucibleBlockEntity.metalId(metal)
                    + "_" + role + ".png");
        return textures;
    }
}
