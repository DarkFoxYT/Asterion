package net.krodark.asterion.port.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.CrucibleBlockEntity;
import net.krodark.asterion.network.CrucibleControlPayload;
import net.krodark.asterion.network.CrucibleScreenPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

/** The original world-backed forge interface, adapted to Minecraft 1.21.1. */
public final class PortCrucibleScreen extends Screen {
    private static final int GUI_Y = 4;
    private static final ResourceLocation LEFT = texture("left/border.png");
    private static final ResourceLocation RIGHT = texture("right/border.png");
    private static final ResourceLocation CENTER = texture("right/center.png");
    private static final ResourceLocation CENTER_FILL = texture("right/center_fill.png");
    private static final ResourceLocation GAUGE = texture("left/temp_gauge.png");
    private static final ResourceLocation GAUGE_FILL = texture("left/temp_gauge_fill.png");
    private static final ResourceLocation STATUS = texture("left/too_hot.png");
    private static final ResourceLocation UP = texture("left/button_up.png");
    private static final ResourceLocation DOWN = texture("left/button_down.png");
    private static final ResourceLocation INPUT = texture("right/material_input.png");
    private static final ResourceLocation SMELT = texture("right/smelt_button.png");
    private static final ResourceLocation POUR = texture("right/pour_button.png");
    private static final ResourceLocation BOTTOM = texture("bottom_center/border.png");
    private static final ResourceLocation[] MOLD_TEXTURES = {
            texture("bottom_center/ingot_mold.png"), texture("bottom_center/guard_mold.png"),
            texture("bottom_center/pomel_mold.png"), texture("bottom_center/blade_mold.png"),
            texture("bottom_center/minotaur_key_mold.png")
    };
    private static final ItemStack[] MATERIAL_ICONS = java.util.stream.IntStream.range(0, 13)
            .mapToObj(CrucibleBlockEntity::returnedMetal).toArray(ItemStack[]::new);
    private static final ItemStack[] MOLD_OUTPUT_ICONS = createMoldOutputIcons();

    private final BlockPos pos;
    private final boolean previousHideGui;
    private CrucibleScreenPayload state;
    private boolean inventoryOpen;
    private boolean recipesOpen;
    private boolean heatPanelOpen = true;
    private boolean forgePanelOpen = true;
    private boolean moldPanelOpen = true;
    private int heldAction;
    private int heldTicks;
    private float displayedTemperature;
    private float displayedPourProgress;

    public PortCrucibleScreen(CrucibleScreenPayload state) {
        super(Component.translatable("screen.asterion.crucible"));
        this.state = state;
        this.pos = state.pos();
        this.displayedTemperature = state.temperature();
        this.displayedPourProgress = state.autoPourProgress();
        this.previousHideGui = net.minecraft.client.Minecraft.getInstance().options.hideGui;
        net.minecraft.client.Minecraft.getInstance().options.hideGui = true;
        PortCrucibleCamera.begin(pos);
    }

    public boolean matches(BlockPos other) { return pos.equals(other); }

    public void update(CrucibleScreenPayload next) {
        if (matches(next.pos())) state = next;
    }

    @Override
    public void tick() {
        if (minecraft != null) minecraft.options.hideGui = true;
        displayedTemperature += (state.temperature() - displayedTemperature) * .16F;
        displayedPourProgress += (state.autoPourProgress() - displayedPourProgress) * .13F;
        if (heldAction != 0 && ++heldTicks % 2 == 0) send(heldAction, false);
    }

    private float scale() { return Math.min(1.5F, Math.min(width / 544.0F, height / 224.0F)); }
    private int logicalWidth() { return Math.round(width / scale()); }
    private int logicalHeight() { return Math.round(height / scale()); }
    private int leftX() { return heatPanelOpen ? 4 : -124; }
    private int rightX() { return forgePanelOpen ? logicalWidth() - 132 : logicalWidth() - 4; }
    private int bottomX() { return logicalWidth() / 2 - 128; }
    private int bottomY() { return moldPanelOpen ? logicalHeight() - GUI_Y - 68 : logicalHeight() - GUI_Y - 4; }
    private int moldSlotX(int index) { return bottomX() + 32 + index * 40; }
    private int inventoryX() { return logicalWidth() / 2 - 85; }

    private int ingredientX(int index) {
        int right = rightX();
        return right + (index == 0 || index == 4 ? 56 : 16 + (index - 1) * 40);
    }

    private int ingredientY(int index) {
        return index == 4 ? 116 : index == 0 ? 16 : index == 2 ? 91 : 86;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float guiScale = scale();
        int mx = Math.round(mouseX / guiScale);
        int my = Math.round(mouseY / guiScale) - GUI_Y;
        ItemStack hoveredStack = ItemStack.EMPTY;
        Component hoveredText = null;

        graphics.pose().pushPose();
        graphics.pose().scale(guiScale, guiScale, 1.0F);
        graphics.pose().translate(0.0F, GUI_Y, 0.0F);

        renderHeatPanel(graphics, mx, my);
        hoveredStack = renderForgePanel(graphics, mx, my);
        ItemStack moldHover = renderMoldPanel(graphics, mx, my);
        if (!moldHover.isEmpty()) hoveredStack = moldHover;

        int left = leftX();
        int right = rightX();
        tab(graphics, left + 128, 90, 18, 34, heatPanelOpen ? "‹" : "›");
        tab(graphics, right - 18, 90, 18, 34, forgePanelOpen ? "›" : "‹");
        tab(graphics, bottomX() + 95, bottomY() - 14, 66, 14, moldPanelOpen ? "MOLDS ▾" : "MOLDS ▴");
        int inventoryTabX = logicalWidth() / 2 - 22;
        tab(graphics, inventoryTabX, 0, 44, 14, inventoryOpen ? "INV ▾" : "INV ▴");
        tab(graphics, inventoryTabX + 48, 0, 60, 14, recipesOpen ? "RECIPES ▾" : "RECIPES");

        if (inventoryOpen) {
            ItemStack inventoryHover = renderInventory(graphics, inventoryX(), 18, mx, my);
            if (!inventoryHover.isEmpty()) hoveredStack = inventoryHover;
        }
        if (recipesOpen) renderRecipeReference(graphics);
        if (inside(mx, my, left + 12, 8, 53, 162))
            hoveredText = Component.literal(state.temperature() + "° / " + state.targetTemperature() + "°");
        graphics.pose().popPose();

        if (!hoveredStack.isEmpty()) graphics.renderTooltip(font, hoveredStack, mouseX, mouseY);
        else if (hoveredText != null) graphics.renderTooltip(font, hoveredText, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeatPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftX();
        graphics.fill(x, 4, x + 128, 212, 0xE8100E0D);
        image(graphics, LEFT, x, 4, 128, 208);
        graphics.pose().pushPose();
        graphics.pose().translate(x + 12, 12, 0);
        graphics.pose().scale(.82F, .78F, 1.0F);
        int first = Math.round(192 - Mth.clamp(displayedTemperature
                / CrucibleBlockEntity.MAX_TEMPERATURE, 0, 1) * 180);
        if (first < 192)
            graphics.blit(GAUGE_FILL, 0, first, 0, first, 64, 192 - first, 64, 208);
        image(graphics, GAUGE, 0, 0, 64, 208);
        graphics.pose().popPose();
        centered(graphics, Math.round(displayedTemperature) + "°", x + 47, 176, 0xFFD4BE9E);
        image(graphics, UP, x + 84, 36, 32, 32);
        image(graphics, INPUT, x + 84, 84, 32, 32);
        image(graphics, DOWN, x + 84, 132, 32, 32);
        for (int i = 0; i < 3; i++) {
            int buttonY = 36 + i * 48;
            if (inside(mouseX, mouseY, x + 84, buttonY, 32, 32))
                graphics.renderOutline(x + 84, buttonY, 32, 32, 0xFFD0B68C);
        }
        centered(graphics, Integer.toString(state.heatControl()), x + 100, 96, 0xFFBDA88A);
        image(graphics, STATUS, x + 28, 180, 80, 32);
        centered(graphics, status(), x + 68, 192, temperatureColor());
    }

    private ItemStack renderForgePanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = rightX();
        graphics.fill(x, 4, x + 128, 212, 0xE8100E0D);
        image(graphics, RIGHT, x, 4, 128, 208);
        image(graphics, CENTER, x, 4, 128, 208);
        if (state.materialUnits() > 0) {
            int color = state.mixColor();
            graphics.setColor(((color >> 16) & 255) / 255.0F, ((color >> 8) & 255) / 255.0F,
                    (color & 255) / 255.0F, 1.0F);
            image(graphics, CENTER_FILL, x, 4, 128, 208);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        graphics.fill(x + 46, 78, x + 82, 116, 0xA0100E0D);
        image(graphics, INPUT, x + 48, 8, 32, 32);
        image(graphics, POUR, x + 24, 44, 80, 32);
        for (int i = 0; i < 3; i++) image(graphics, INPUT, x + 8 + i * 40, i == 1 ? 83 : 78, 32, 32);
        image(graphics, SMELT, x + 16, 138, 96, 32);
        centered(graphics, "POUR", x + 64, 56, 0xFFC5AE8E);
        centered(graphics, "SMELT", x + 64, 150, 0xFFC5AE8E);
        if (inside(mouseX, mouseY, x + 24, 44, 80, 32))
            graphics.renderOutline(x + 24, 44, 80, 32, 0xFFD0B68C);
        if (inside(mouseX, mouseY, x + 16, 138, 96, 32))
            graphics.renderOutline(x + 16, 138, 96, 32, 0xFFD0B68C);
        image(graphics, INPUT, x + 48, 174, 32, 32);
        if (validMold()) image(graphics, MOLD_TEXTURES[state.mold()], x + 48, 174, 32, 32);
        ItemStack preview = mixturePreview();
        if (!preview.isEmpty()) graphics.renderItem(preview, x + 56, 182);
        if (state.autoPourProgress() > 0)
            centered(graphics, Math.round(displayedPourProgress * 100F
                    / CrucibleBlockEntity.AUTO_POUR_TICKS) + "%", x + 64, 126, 0xFFBDA88A);

        ItemStack hovered = ItemStack.EMPTY;
        String sequence = state.metalSequence();
        for (int i = 0; i < sequence.length() && i < 5; i++) {
            int material = sequence.charAt(i) - '0';
            if (material < 0 || material >= MATERIAL_ICONS.length) continue;
            int ix = ingredientX(i);
            int iy = ingredientY(i);
            ItemStack icon = MATERIAL_ICONS[material];
            graphics.renderItem(icon, ix, iy);
            if (inside(mouseX, mouseY, ix, iy, 16, 16)) {
                graphics.renderOutline(ix, iy, 16, 16, 0xFFD0B68C);
                hovered = icon;
            }
        }
        return hovered;
    }

    private ItemStack renderMoldPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelX = bottomX();
        int panelY = bottomY();
        graphics.fill(panelX, panelY, panelX + 256, panelY + 64, 0xE8100E0D);
        image(graphics, BOTTOM, panelX, panelY, 256, 64);
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < MOLD_TEXTURES.length; i++) {
            int x = moldSlotX(i);
            int y = panelY + 16;
            image(graphics, MOLD_TEXTURES[i], x, y, 32, 32);
            boolean owned = state.mold() == i || hasMoldInInventory(i);
            if (owned) graphics.renderItem(MOLD_OUTPUT_ICONS[i], x + 8, y + 8);
            else graphics.fill(x + 4, y + 4, x + 28, y + 28, 0xA0100E0D);
            if (state.mold() == i || inside(mouseX, mouseY, x, y, 32, 32))
                graphics.renderOutline(x, y, 32, 32, 0xFFD0B68C);
            if (inside(mouseX, mouseY, x, y, 32, 32))
                hovered = new ItemStack(CrucibleBlockEntity.moldItem(i));
        }
        return hovered;
    }

    private ItemStack renderInventory(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.player == null) return ItemStack.EMPTY;
        graphics.fill(x - 5, y - 4, x + 176, y + 94, 0xF0181513);
        graphics.renderOutline(x - 5, y - 4, 181, 98, 0xFF817361);
        graphics.drawString(font, "Add ingots or a mold", x, y + 2, 0xFFBDA88A, true);
        ItemStack hovered = ItemStack.EMPTY;
        for (int row = 0; row < 4; row++) for (int col = 0; col < 9; col++) {
            int slot = row == 3 ? col : 9 + row * 9 + col;
            int slotX = x + col * 19;
            int slotY = y + 16 + row * 18 + (row == 3 ? 4 : 0);
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            graphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF29231E);
            graphics.renderOutline(slotX, slotY, 18, 18,
                    inside(mouseX, mouseY, slotX, slotY, 18, 18) ? 0xFFE0BD72 : 0xFF554A3D);
            graphics.renderItem(stack, slotX + 1, slotY + 1);
            graphics.renderItemDecorations(font, stack, slotX + 1, slotY + 1);
            if (!stack.isEmpty() && inside(mouseX, mouseY, slotX, slotY, 18, 18)) hovered = stack;
        }
        return hovered;
    }

    private void renderRecipeReference(GuiGraphics graphics) {
        int x = logicalWidth() / 2 - 116;
        int y = 20;
        graphics.fill(x, y, x + 232, y + 104, 0xF5100E0D);
        graphics.renderOutline(x, y, 232, 104, 0xFF817361);
        graphics.fill(x + 1, y + 1, x + 231, y + 23, 0xFF25211D);
        centered(graphics, "FORGE RECIPES", x + 116, y + 8, 0xFFE1C99F);
        graphics.drawString(font, "Steel: 2 iron + 2 coal · 700° ±8", x + 12, y + 34, 0xFFC5B69F, true);
        graphics.drawString(font, "Bonesteel: celestial steel + 3 bones", x + 12, y + 49, 0xFFC5B69F, true);
        graphics.drawString(font, "900° ±8", x + 12, y + 64, 0xFFC5B69F, true);
        graphics.drawString(font, "Gold ore: ingot mold · 350° ±12", x + 12, y + 76, 0xFFC5B69F, true);
        graphics.drawString(font, "Match the mold temperature, then POUR.", x + 12, y + 91, 0xFFE5B77B, true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float guiScale = scale();
        double x = mouseX / guiScale;
        double y = mouseY / guiScale - GUI_Y;
        int left = leftX();
        int right = rightX();
        int inventoryTabX = logicalWidth() / 2 - 22;

        if (button == 0 && inside(x, y, left + 128, 90, 18, 34)) {
            heatPanelOpen = !heatPanelOpen;
            heldAction = 0;
            clickSound();
            return true;
        }
        if (button == 0 && inside(x, y, right - 18, 90, 18, 34)) {
            forgePanelOpen = !forgePanelOpen;
            clickSound();
            return true;
        }
        if (button == 0 && inside(x, y, bottomX() + 95, bottomY() - 14, 66, 14)) {
            moldPanelOpen = !moldPanelOpen;
            clickSound();
            return true;
        }
        if (button == 0 && inside(x, y, inventoryTabX, 0, 44, 14)) {
            inventoryOpen = !inventoryOpen;
            recipesOpen = false;
            clickSound();
            return true;
        }
        if (button == 0 && inside(x, y, inventoryTabX + 48, 0, 60, 14)) {
            recipesOpen = !recipesOpen;
            inventoryOpen = false;
            clickSound();
            return true;
        }
        if (!inventoryOpen && (button == 0 || button == 1)) {
            for (int i = state.metalSequence().length() - 1; i >= 0; i--)
                if (inside(x, y, ingredientX(i), ingredientY(i), 16, 16)) {
                    send(CrucibleControlPayload.removeMaterial(i), true);
                    return true;
                }
        }
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (inventoryOpen) {
            int inventoryX = inventoryX();
            int inventoryY = 18;
            for (int row = 0; row < 4; row++) for (int col = 0; col < 9; col++) {
                int slotX = inventoryX + col * 19;
                int slotY = inventoryY + 16 + row * 18 + (row == 3 ? 4 : 0);
                if (inside(x, y, slotX, slotY, 18, 18)) {
                    int slot = row == 3 ? col : 9 + row * 9 + col;
                    send(CrucibleControlPayload.insertSlot(slot), true);
                    inventoryOpen = false;
                    return true;
                }
            }
        }
        if (inside(x, y, left + 84, 36, 32, 32)) heldAction = CrucibleControlPayload.HEAT;
        else if (inside(x, y, left + 84, 132, 32, 32)) heldAction = CrucibleControlPayload.COOL;
        else if (inside(x, y, left + 84, 84, 32, 32)) {
            send(CrucibleControlPayload.NEXT_MOLD, true);
            return true;
        } else if (inside(x, y, right + 24, 44, 80, 32)) {
            send(CrucibleControlPayload.POUR, true);
            return true;
        } else if (inside(x, y, right + 16, 138, 96, 32)) {
            send(CrucibleControlPayload.SMELT, true);
            return true;
        } else if (inside(x, y, right + 8, 78, 112, 37) || inside(x, y, right + 48, 8, 32, 32)) {
            inventoryOpen = !inventoryOpen;
            recipesOpen = false;
            clickSound();
            return true;
        } else {
            for (int i = 0; i < MOLD_TEXTURES.length; i++)
                if (inside(x, y, moldSlotX(i), bottomY() + 16, 32, 32)) {
                    send(CrucibleControlPayload.selectMold(i), true);
                    return true;
                }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        heldTicks = 0;
        send(heldAction, true);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        heldAction = heldTicks = 0;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The interface is composited directly over the live forge camera.
    }

    @Override
    public void removed() {
        if (minecraft != null) minecraft.options.hideGui = previousHideGui;
        PortCrucibleCamera.end();
        super.removed();
    }

    private void send(int action, boolean sound) {
        if (sound) clickSound();
        if (ClientPlayNetworking.canSend(CrucibleControlPayload.TYPE))
            ClientPlayNetworking.send(new CrucibleControlPayload(pos, action));
    }

    private void clickSound() {
        if (minecraft != null) minecraft.getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private ItemStack mixturePreview() {
        if (!validMold()) return ItemStack.EMPTY;
        if (state.metalSequence().isEmpty()) return MOLD_OUTPUT_ICONS[state.mold()];
        int material = state.metalSequence().charAt(0) - '0';
        if (state.mold() == CrucibleBlockEntity.Mold.MINOTAUR_KEY.ordinal())
            return new ItemStack(Asterion.MINOTAUR_KEY);
        if (state.mold() == CrucibleBlockEntity.Mold.INGOT.ordinal())
            return material >= 0 && material < MATERIAL_ICONS.length
                    ? CrucibleBlockEntity.returnedMetal(material) : ItemStack.EMPTY;
        ItemStack result = MOLD_OUTPUT_ICONS[state.mold()].copy();
        if (material >= 0 && material <= 8)
            result.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(material + 1));
        return result;
    }

    private boolean validMold() { return state.mold() >= 0 && state.mold() < MOLD_TEXTURES.length; }

    private String status() {
        if (!validMold()) return "NO MOLD";
        int difference = state.temperature() - state.targetTemperature();
        return Math.abs(difference) <= tolerance() ? "READY" : difference > 0 ? "TOO HOT" : "TOO COLD";
    }

    private int tolerance() {
        return state.mold() == CrucibleBlockEntity.Mold.MINOTAUR_KEY.ordinal() ? 20
                : state.targetTemperature() >= 700 ? 8 : CrucibleBlockEntity.TOLERANCE;
    }

    private int temperatureColor() {
        int difference = Math.abs(Math.round(displayedTemperature) - state.targetTemperature());
        return difference <= tolerance() ? 0xFF8EBB79
                : displayedTemperature > state.targetTemperature() ? 0xFFFF4840 : 0xFFBDA88A;
    }

    private boolean hasMoldInInventory(int index) {
        if (minecraft == null || minecraft.player == null) return false;
        Item wanted = CrucibleBlockEntity.moldItem(index);
        for (int slot = 0; slot < 36; slot++)
            if (minecraft.player.getInventory().getItem(slot).is(wanted)) return true;
        return false;
    }

    private static ItemStack[] createMoldOutputIcons() {
        ItemStack[] icons = new ItemStack[CrucibleBlockEntity.Mold.values().length];
        for (int index = 0; index < icons.length; index++) {
            Item item = switch (CrucibleBlockEntity.Mold.values()[index]) {
                case INGOT -> net.minecraft.world.item.Items.IRON_INGOT;
                case SWORD_GUARD -> Asterion.FORGED_SWORD_GUARD;
                case SWORD_POMMEL -> Asterion.FORGED_SWORD_POMMEL;
                case SWORD_BLADE -> Asterion.FORGED_SWORD_BLADE;
                case MINOTAUR_KEY -> Asterion.MINOTAUR_KEY;
            };
            icons[index] = new ItemStack(item);
        }
        return icons;
    }

    private void tab(GuiGraphics graphics, int x, int y, int width, int height, String label) {
        graphics.fill(x, y, x + width, y + height, 0xF025211D);
        graphics.renderOutline(x, y, width, height, 0xFF817361);
        centered(graphics, label, x + width / 2, y + (height - 8) / 2, 0xFFCFB993);
    }

    private void centered(GuiGraphics graphics, String text, int centerX, int y, int color) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, true);
    }

    private static void image(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height) {
        graphics.blit(texture, x, y, 0, 0, width, height, width, height);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static ResourceLocation texture(String path) {
        return Asterion.id("textures/gui/forge/" + path);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
