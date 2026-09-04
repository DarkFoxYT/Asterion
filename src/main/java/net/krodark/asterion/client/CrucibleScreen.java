package net.krodark.asterion.client;

import net.minecraft.client.renderer.RenderPipelines;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.CrucibleBlockEntity;
import net.krodark.asterion.network.CrucibleControlPayload;
import net.krodark.asterion.network.CrucibleScreenPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

import java.util.ArrayList;
import java.util.List;

/** Roomy forge panel with direct, server-validated access to the player's inventory. */
public final class CrucibleScreen extends Screen {
    public static final Identifier GAUGE_TEXTURE = Asterion.id("textures/gui/temp_gauge.png");
    public static final Identifier BUTTON_UP_TEXTURE = Asterion.id("textures/gui/button_up.png");
    public static final Identifier BUTTON_DOWN_TEXTURE = Asterion.id("textures/gui/button_down.png");
    private final BlockPos pos;
    private int temperature;
    private int targetTemperature;
    private int fuelTicks;
    private int mold;
    private int mixColor;
    private int materialUnits;
    private String metalSequence = "";
    private int autoPourProgress;
    private float displayedTemperature;
    private int heldControl;
    private int heldTicks;
    private static final CrucibleBlockEntity.Mold[] MOLDS = CrucibleBlockEntity.Mold.values();
    private static final ItemStack[] CAST_ICONS = createCastIcons();
    private static final String[] MATERIAL_NAMES = {
            "Iron", "Copper", "Tarnished Gold", "Netherite", "Celestial Bronze",
            "Bone Steel", "Celestial Steel", "Celestial Gold", "Gold"
    };
    private ItemStack cachedPreview = ItemStack.EMPTY;
    private String cachedPreviewSequence = "";
    private int cachedPreviewMold = Integer.MIN_VALUE;

    public CrucibleScreen(CrucibleScreenPayload state) {
        super(Component.translatable("screen.asterion.crucible"));
        pos = state.pos();
        update(state);
    }

    public boolean matches(BlockPos candidate) { return pos.equals(candidate); }
    public void update(CrucibleScreenPayload state) {
        temperature = Mth.clamp(state.temperature(), 0, CrucibleBlockEntity.MAX_TEMPERATURE);
        targetTemperature = Mth.clamp(state.targetTemperature(), 0, CrucibleBlockEntity.MAX_TEMPERATURE);
        fuelTicks = Math.max(0, state.fuelTicks());
        mold = Mth.clamp(state.mold(), -1, MOLDS.length - 1);
        mixColor = state.mixColor() & 0xFFFFFF;
        materialUnits = Mth.clamp(state.materialUnits(), 0, 4);
        metalSequence = state.metalSequence();
        autoPourProgress = Mth.clamp(state.autoPourProgress(), 0, CrucibleBlockEntity.AUTO_POUR_TICKS);
        if (displayedTemperature == 0) displayedTemperature = temperature;
    }

    @Override public void tick() {
        if (minecraft.level != null
                && minecraft.level.getBlockEntity(pos) instanceof CrucibleBlockEntity crucible) {
            temperature = crucible.temperature();
            targetTemperature = crucible.targetTemperature();
            fuelTicks = crucible.fuelTicks();
            mold = crucible.selectedMoldIndex();
            mixColor = crucible.mixColor();
            materialUnits = crucible.materialUnits();
            metalSequence = crucible.metalSequence();
            autoPourProgress = crucible.autoPourProgress();
        }
        if (heldControl != 0 && ++heldTicks % 4 == 0) send(heldControl);
        displayedTemperature += (temperature - displayedTemperature) * 0.16F;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        int panelY = height / 2 - 119;
        int x = width / 2 - 72;
        int upY = panelY + 31;
        int downY = panelY + 83;
        if (event.button() == 0 && inside(event.x(), event.y(), x, upY, 32, 32)) {
            heldControl = CrucibleControlPayload.HEAT; heldTicks = 0; send(heldControl);
            return true;
        }
        if (event.button() == 0 && inside(event.x(), event.y(), x, downY, 32, 32)) {
            heldControl = CrucibleControlPayload.COOL; heldTicks = 0; send(heldControl);
            return true;
        }
        int material = materialAt(event.x(), event.y());
        if (event.button() == 0 && material >= 0) {
            send(CrucibleControlPayload.removeMaterial(material));
            return true;
        }
        int selectedMold = moldAt(event.x(), event.y());
        if (event.button() == 0 && selectedMold >= 0) {
            send(CrucibleControlPayload.selectMold(selectedMold));
            return true;
        }
        if (event.button() == 0) {
            int slot = inventorySlotAt(event.x(), event.y());
            if (slot >= 0 && minecraft.player != null
                    && !minecraft.player.getInventory().getItem(slot).isEmpty()) {
                send(CrucibleControlPayload.insertSlot(slot));
                return true;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        heldControl = 0;
        heldTicks = 0;
        return super.mouseReleased(event);
    }

    private int materialAt(double mouseX, double mouseY) {
        int panelX = width / 2 - 177, panelY = height / 2 - 119;
        for (int layer = 0; layer < metalSequence.length(); layer++)
            if (inside(mouseX, mouseY, panelX + 190, panelY + 74 + layer * 18, 154, 17)) return layer;
        return -1;
    }

    private int moldAt(double mouseX, double mouseY) {
        int panelX = width / 2 - 177, panelY = height / 2 - 119;
        for (int index = 0; index < MOLDS.length; index++)
            if (inside(mouseX, mouseY, panelX + 190 + index * 30, panelY + 39, 28, 20)) return index;
        return -1;
    }

    private int inventorySlotAt(double mouseX, double mouseY) {
        int left = width / 2 - 177, top = height / 2 - 119;
        int inventoryX = left + 9, inventoryY = top + 151;
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            if (inside(mouseX, mouseY, inventoryX + column * 18, inventoryY + row * 18, 18, 18))
                return 9 + row * 9 + column;
        int hotbarY = inventoryY + 58;
        for (int column = 0; column < 9; column++)
            if (inside(mouseX, mouseY, inventoryX + column * 18, hotbarY, 18, 18)) return column;
        return -1;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void send(int action) {
        if (ClientPlayNetworking.canSend(CrucibleControlPayload.TYPE))
            ClientPlayNetworking.send(new CrucibleControlPayload(pos, action));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int center = width / 2, panelX = center - 177, panelY = height / 2 - 119;
        graphics.fill(panelX, panelY, panelX + 354, panelY + 238, 0xF0141010);
        graphics.outline(panelX, panelY, 354, 238, 0xFF8B765E);
        graphics.centeredText(font, title, center, panelY + 10, 0xFFEAD6B7);

        int buttonX = center - 72;
        int upY = panelY + 31, downY = panelY + 83;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BUTTON_UP_TEXTURE, buttonX, upY,
                0, 0, 32, 32, 32, 32);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BUTTON_DOWN_TEXTURE, buttonX, downY,
                0, 0, 32, 32, 32, 32);
        if (inside(mouseX, mouseY, buttonX, upY, 32, 32)) graphics.outline(buttonX, upY, 32, 32, 0xFFD3B878);
        if (inside(mouseX, mouseY, buttonX, downY, 32, 32)) graphics.outline(buttonX, downY, 32, 32, 0xFFD3B878);

        // Native 48x96 draw: resource-pack replacements remain pixel accurate.
        int gaugeX = center - 28, gaugeY = panelY + 28;
        graphics.blit(RenderPipelines.GUI_TEXTURED, GAUGE_TEXTURE, gaugeX, gaugeY,
                0, 0, 48, 96, 48, 96);
        float ratio = displayedTemperature / CrucibleBlockEntity.MAX_TEMPERATURE;
        int trackBottom = gaugeY + 78, trackTop = gaugeY + 17;
        int markerY = trackBottom - Math.round((trackBottom - trackTop) * ratio);
        graphics.fill(gaugeX + 18, markerY - 1, gaugeX + 29, markerY + 2, 0xFFFFB12B);

        CrucibleBlockEntity.Mold selected = mold < 0 ? null : MOLDS[mold];
        boolean ready = selected != null
                && Math.abs(temperature - selected.target()) <= CrucibleBlockEntity.TOLERANCE;
        graphics.centeredText(font, temperature + "°", center - 4, panelY + 128, 0xFFFFFFFF);
        graphics.text(font, selected == null ? "No cast inserted" : selected.label(), panelX + 10, panelY + 34, 0xFFE5D1AE);
        graphics.text(font, selected == null ? "Insert cast on top" : "Target: " + selected.target() + "°",
                panelX + 10, panelY + 49, 0xFFB9AB94);
        graphics.text(font, "Regulator: " + targetTemperature + "°", panelX + 10, panelY + 64, 0xFFB9AB94);

        drawFeedPort(graphics, panelX + 190, panelY + 150, "FUEL",
                fuelTicks > 0 ? (fuelTicks / 20 + 1) + "s" : "EMPTY",
                fuelTicks > 0 ? 0xFFFFB347 : 0xFF9A9187);
        String status = selected == null ? "WAITING FOR CAST" : ready ? "CALIBRATED"
                : temperature < selected.target() ? "TOO COLD" : "TOO HOT";
        int statusColor = selected == null ? 0xFF9A9187 : ready ? 0xFF65E083
                : temperature < selected.target() ? 0xFF66B9FF : 0xFFFF725C;
        String process = ready && materialUnits > 0 ? "SMELTING "
                + Math.round(autoPourProgress / (float)CrucibleBlockEntity.AUTO_POUR_TICKS * 100F) + "%" : status;
        graphics.centeredText(font, process, center - 4, panelY + 139, statusColor);

        graphics.text(font, "MOLD (requires item)", panelX + 190, panelY + 27, 0xFF9E8C76);
        for (int index = 0; index < MOLDS.length; index++) {
            int moldX = panelX + 190 + index * 30;
            boolean hovered = inside(mouseX, mouseY, moldX, panelY + 39, 28, 20);
            graphics.fill(moldX, panelY + 39, moldX + 28, panelY + 59, mold == index ? 0xFF594A34 : 0xFF29231E);
            graphics.outline(moldX, panelY + 39, 28, 20, hovered ? 0xFFE0BD72 : mold == index ? 0xFFFFD078 : 0xFF554A3D);
            ItemStack cast = CAST_ICONS[index];
            graphics.item(cast, moldX + 6, panelY + 41);
            if (hovered) graphics.setTooltipForNextFrame(font,
                    Component.literal(MOLDS[index].label()), mouseX, mouseY);
        }
        graphics.text(font, "MIX " + materialUnits + "/4 · click to return", panelX + 190, panelY + 63, 0xFFCAB99F);
        for (int layer = 0; layer < metalSequence.length(); layer++) {
            int rowY = panelY + 74 + layer * 18;
            graphics.fill(panelX + 190, rowY, panelX + 344, rowY + 17, layer == 0 ? 0xFF41372C : 0xFF2B2520);
            graphics.outline(panelX + 190, rowY, 154, 17,
                    inside(mouseX, mouseY, panelX + 190, rowY, 154, 17) ? 0xFFFF7868 : 0xFF655746);
            String materialName = materialName(metalSequence.charAt(layer) - '0');
            graphics.text(font, (layer == 0 ? "100%  " : "50%  ") + materialName,
                    panelX + 195, rowY + 4, layer == 0 ? 0xFFFFDA91 : 0xFFD6C5AD);
            graphics.text(font, "×", panelX + 331, rowY + 4, 0xFFFF7868);
        }
        if (metalSequence.isEmpty()) graphics.text(font, "Click an ingot below", panelX + 196, panelY + 80, 0xFF887C6E);
        ItemStack preview = mixturePreview();
        graphics.text(font, "RESULT", panelX + 198, panelY + 181, 0xFF9E8C76);
        if (!preview.isEmpty()) {
            graphics.item(preview, panelX + 242, panelY + 177);
            graphics.itemDecorations(font, preview, panelX + 242, panelY + 177);
        }

        int inventoryX = panelX + 9, inventoryY = panelY + 151;
        graphics.text(font, "Inventory — click metal or fuel to feed crucible", inventoryX, inventoryY - 12, 0xFFCAB99F);
        if (minecraft.player != null) {
            for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
                drawInventorySlot(graphics, minecraft.player.getInventory().getItem(9 + row * 9 + column),
                        inventoryX + column * 18, inventoryY + row * 18, mouseX, mouseY);
            int hotbarY = inventoryY + 58;
            for (int column = 0; column < 9; column++)
                drawInventorySlot(graphics, minecraft.player.getInventory().getItem(column),
                        inventoryX + column * 18, hotbarY, mouseX, mouseY);
        }
    }

    private ItemStack mixturePreview() {
        if (metalSequence.isEmpty() || mold < 0) return ItemStack.EMPTY;
        if (metalSequence.equals(cachedPreviewSequence) && mold == cachedPreviewMold) return cachedPreview;
        net.minecraft.world.item.Item output = switch (MOLDS[mold]) {
            case INGOT -> Asterion.FORGED_INGOT;
            case SWORD_GUARD -> Asterion.FORGED_SWORD_GUARD;
            case SWORD_POMMEL -> Asterion.FORGED_SWORD_POMMEL;
            case SWORD_BLADE -> Asterion.FORGED_SWORD_BLADE;
            case AXE_HEAD -> Asterion.FORGED_AXE_HEAD;
        };
        ItemStack preview = new ItemStack(output);
        ArrayList<String> materials = new ArrayList<>(4);
        ArrayList<Integer> colors = new ArrayList<>(4);
        for (int layer = 0; layer < 4; layer++) {
            materials.add(layer < metalSequence.length()
                    ? CrucibleBlockEntity.metalId(metalSequence.charAt(layer) - '0') : "none");
            colors.add(layer >= metalSequence.length() ? 0x00FFFFFF
                    : layer == 0 ? 0xFFFFFFFF : 0x80FFFFFF);
        }
        preview.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), materials, colors));
        cachedPreviewSequence = metalSequence;
        cachedPreviewMold = mold;
        cachedPreview = preview;
        return cachedPreview;
    }

    private static String materialName(int metal) {
        return metal >= 0 && metal < MATERIAL_NAMES.length ? MATERIAL_NAMES[metal] : "Unknown";
    }

    private static ItemStack[] createCastIcons() {
        ItemStack[] icons = new ItemStack[MOLDS.length];
        for (int index = 0; index < icons.length; index++)
            icons[index] = new ItemStack(CrucibleBlockEntity.moldItem(index));
        return icons;
    }

    private void drawFeedPort(GuiGraphicsExtractor graphics, int x, int y, String label, String value, int color) {
        graphics.fill(x, y, x + 115, y + 23, 0xFF241E1A);
        graphics.outline(x, y, 115, 23, 0xFF655746);
        graphics.text(font, label, x + 5, y + 4, 0xFF9E8C76);
        graphics.text(font, value, x + 78, y + 4, color);
    }

    private void drawInventorySlot(GuiGraphicsExtractor graphics, net.minecraft.world.item.ItemStack stack,
                                   int x, int y, int mouseX, int mouseY) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF29231E);
        graphics.outline(x, y, 18, 18, inside(mouseX, mouseY, x, y, 18, 18) ? 0xFFE0BD72 : 0xFF554A3D);
        if (!stack.isEmpty()) {
            graphics.item(stack, x + 1, y + 1);
            graphics.itemDecorations(font, stack, x + 1, y + 1);
            if (inside(mouseX, mouseY, x, y, 18, 18))
                graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
}
