package net.krodark.asterion.port.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.CrucibleBlockEntity;
import net.krodark.asterion.network.CrucibleControlPayload;
import net.krodark.asterion.network.CrucibleScreenPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** Functional 1.21.1 forge UI shared by Fabric and Forgified Fabric API on NeoForge. */
public final class PortCrucibleScreen extends Screen {
    private static final ResourceLocation LEFT = texture("left/border.png");
    private static final ResourceLocation RIGHT = texture("right/border.png");
    private static final ResourceLocation CENTER = texture("right/center_fill.png");
    private static final ResourceLocation GAUGE = texture("left/temp_gauge.png");
    private static final ResourceLocation GAUGE_FILL = texture("left/temp_gauge_fill.png");
    private static final ResourceLocation UP = texture("left/button_up.png");
    private static final ResourceLocation DOWN = texture("left/button_down.png");
    private static final ResourceLocation SMELT = texture("right/smelt_button.png");
    private static final ResourceLocation POUR = texture("right/pour_button.png");
    private static final ResourceLocation MOLD_BORDER = texture("bottom_center/border.png");
    private static final ResourceLocation[] MOLDS = {
            texture("bottom_center/ingot_mold.png"), texture("bottom_center/blade_mold.png"),
            texture("bottom_center/guard_mold.png"), texture("bottom_center/pomel_mold.png"),
            texture("bottom_center/minotaur_key_mold.png")
    };

    private CrucibleScreenPayload state;
    private final BlockPos pos;

    public PortCrucibleScreen(CrucibleScreenPayload state) {
        super(Component.translatable("screen.asterion.crucible"));
        this.state = state;
        this.pos = state.pos();
    }

    public boolean matches(BlockPos other) {
        return pos.equals(other);
    }

    public void update(CrucibleScreenPayload next) {
        if (matches(next.pos())) state = next;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int x = (width - 384) / 2;
        int y = Math.max(8, (height - 296) / 2);
        graphics.blit(LEFT, x, y, 0, 0, 128, 208, 128, 208);
        graphics.blit(CENTER, x + 128, y, 0, 0, 128, 208, 128, 208);
        graphics.blit(RIGHT, x + 256, y, 0, 0, 128, 208, 128, 208);

        graphics.blit(GAUGE, x + 32, y, 0, 0, 64, 208, 64, 208);
        int gaugeHeight = Mth.clamp(Math.round(state.temperature() / 1200.0F * 142.0F), 0, 142);
        if (gaugeHeight > 0)
            graphics.blit(GAUGE_FILL, x + 32, y + 181 - gaugeHeight,
                    0, 181 - gaugeHeight, 64, gaugeHeight, 64, 208);
        graphics.blit(UP, x + 92, y + 45, 0, 0, 32, 32, 32, 32);
        graphics.blit(DOWN, x + 92, y + 130, 0, 0, 32, 32, 32, 32);
        graphics.blit(SMELT, x + 272, y + 72, 0, 0, 96, 32, 96, 32);
        graphics.blit(POUR, x + 280, y + 116, 0, 0, 80, 32, 80, 32);

        graphics.drawCenteredString(font, title, width / 2, y + 11, 0xFFE9D2A1);
        graphics.drawString(font, state.temperature() + "° / " + state.targetTemperature() + "°",
                x + 12, y + 184, temperatureColor(), false);
        graphics.drawCenteredString(font, Component.literal("Heat " + signed(state.heatControl())),
                x + 320, y + 48, 0xFFE7C98E);
        graphics.drawCenteredString(font, Component.literal(materialName()), x + 192, y + 46, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.literal(state.materialUnits() + "/5 units"),
                x + 192, y + 61, 0xFFD8D8D8);
        graphics.drawCenteredString(font, Component.literal(state.metalSequence().isEmpty()
                ? "Empty" : "Alloy " + state.metalSequence()), x + 192, y + 78, state.mixColor() | 0xFF000000);
        if (state.autoPourProgress() > 0) {
            int progress = Mth.clamp(state.autoPourProgress(), 0, 100);
            graphics.fill(x + 145, y + 102, x + 239, y + 112, 0xFF24180F);
            graphics.fill(x + 147, y + 104, x + 147 + Math.round(progress * .90F), y + 110, 0xFFE77824);
        }

        renderMolds(graphics, x + 64, y + 208, mouseX, mouseY);
        renderInventory(graphics, x + 104, y + 252, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderMolds(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        graphics.blit(MOLD_BORDER, x, y, 0, 0, 256, 64, 256, 64);
        for (int i = 0; i < MOLDS.length; i++) {
            int bx = x + 40 + i * 36;
            graphics.blit(MOLDS[i], bx, y + 16, 0, 0, 32, 32, 32, 32);
            if (state.mold() == i) graphics.renderOutline(bx, y + 16, 32, 32, 0xFFFFB347);
            if (inside(mouseX, mouseY, bx, y + 16, 32, 32))
                graphics.renderTooltip(font, Component.literal(CrucibleBlockEntity.Mold.values()[i].name()
                        .toLowerCase(java.util.Locale.ROOT).replace('_', ' ')), mouseX, mouseY);
        }
    }

    private void renderInventory(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.player == null) return;
        graphics.fill(x - 5, y - 5, x + 181, y + 73, 0xC018120D);
        for (int visual = 0; visual < 36; visual++) {
            int slot = visual < 27 ? visual + 9 : visual - 27;
            int sx = x + (visual % 9) * 20;
            int sy = y + (visual / 9) * 18;
            graphics.fill(sx, sy, sx + 18, sy + 18, 0xA04A3B2A);
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            graphics.renderItem(stack, sx + 1, sy + 1);
            graphics.renderItemDecorations(font, stack, sx + 1, sy + 1);
            if (!stack.isEmpty() && inside(mouseX, mouseY, sx, sy, 18, 18))
                graphics.renderTooltip(font, stack, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int x = (width - 384) / 2;
        int y = Math.max(8, (height - 296) / 2);
        if (inside(mouseX, mouseY, x + 92, y + 45, 32, 32)) send(CrucibleControlPayload.HEAT);
        else if (inside(mouseX, mouseY, x + 92, y + 130, 32, 32)) send(CrucibleControlPayload.COOL);
        else if (inside(mouseX, mouseY, x + 272, y + 72, 96, 32)) send(CrucibleControlPayload.SMELT);
        else if (inside(mouseX, mouseY, x + 280, y + 116, 80, 32)) send(CrucibleControlPayload.POUR);
        else {
            int moldX = x + 104;
            for (int i = 0; i < MOLDS.length; i++)
                if (inside(mouseX, mouseY, moldX + i * 36, y + 224, 32, 32)) {
                    send(CrucibleControlPayload.selectMold(i));
                    return true;
                }
            int inventoryX = x + 104;
            int inventoryY = y + 252;
            for (int visual = 0; visual < 36; visual++) {
                int sx = inventoryX + (visual % 9) * 20;
                int sy = inventoryY + (visual / 9) * 18;
                if (inside(mouseX, mouseY, sx, sy, 18, 18)) {
                    int slot = visual < 27 ? visual + 9 : visual - 27;
                    send(CrucibleControlPayload.insertSlot(slot));
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        return true;
    }

    private void send(int action) {
        if (ClientPlayNetworking.canSend(CrucibleControlPayload.TYPE))
            ClientPlayNetworking.send(new CrucibleControlPayload(pos, action));
    }

    private int temperatureColor() {
        int difference = Math.abs(state.temperature() - state.targetTemperature());
        return difference <= 8 ? 0xFF61E58A : difference <= 30 ? 0xFFFFD166 : 0xFFFF665E;
    }

    private String materialName() {
        if (state.mold() < 0 || state.mold() >= CrucibleBlockEntity.Mold.values().length) return "No mold";
        return CrucibleBlockEntity.Mold.values()[state.mold()].name().toLowerCase(java.util.Locale.ROOT)
                .replace('_', ' ') + " mold";
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static ResourceLocation texture(String path) {
        return Asterion.id("textures/gui/forge/" + path);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
