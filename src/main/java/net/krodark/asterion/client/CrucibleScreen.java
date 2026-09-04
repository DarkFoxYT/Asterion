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
    private static final int PANEL_HEIGHT = 236;
    public static final Identifier GAUGE_TEXTURE = Asterion.id("textures/gui/temp_gauge.png");
    public static final Identifier BUTTON_UP_TEXTURE = Asterion.id("textures/gui/button_up.png");
    public static final Identifier BUTTON_DOWN_TEXTURE = Asterion.id("textures/gui/button_down.png");
    private final BlockPos pos;
    private int temperature;
    private int targetTemperature;
    private int heatControl;
    private int fuelTicks;
    private int mold;
    private int mixColor;
    private int materialUnits;
    private String metalSequence = "";
    private int autoPourProgress;
    private float displayedTemperature;
    private float displayedHeatControl;
    private float displayedPourProgress;
    private int heldControl;
    private int heldTicks;
    private int screenTicks;
    private int moldPulseTicks;
    private int mixPulseTicks;
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
        CrucibleCamera.begin(pos);
    }

    public boolean matches(BlockPos candidate) { return pos.equals(candidate); }
    public void update(CrucibleScreenPayload state) {
        int previousMold = mold;
        String previousSequence = metalSequence;
        temperature = Mth.clamp(state.temperature(), 0, CrucibleBlockEntity.MAX_TEMPERATURE);
        targetTemperature = Mth.clamp(state.targetTemperature(), 0, CrucibleBlockEntity.MAX_TEMPERATURE);
        heatControl = Mth.clamp(state.heatControl(), CrucibleBlockEntity.MIN_HEAT_CONTROL,
                CrucibleBlockEntity.MAX_HEAT_CONTROL);
        fuelTicks = Math.max(0, state.fuelTicks());
        mold = Mth.clamp(state.mold(), -1, MOLDS.length - 1);
        mixColor = state.mixColor() & 0xFFFFFF;
        materialUnits = Mth.clamp(state.materialUnits(), 0, 4);
        metalSequence = state.metalSequence();
        autoPourProgress = Mth.clamp(state.autoPourProgress(), 0, CrucibleBlockEntity.AUTO_POUR_TICKS);
        if (displayedTemperature == 0) displayedTemperature = temperature;
        if (previousMold != mold) moldPulseTicks = 12;
        if (!previousSequence.equals(metalSequence)) mixPulseTicks = 12;
    }

    @Override public void tick() {
        screenTicks++;
        if (minecraft.level != null
                && minecraft.level.getBlockEntity(pos) instanceof CrucibleBlockEntity crucible) {
            int previousMold = mold;
            String previousSequence = metalSequence;
            temperature = crucible.temperature();
            targetTemperature = crucible.targetTemperature();
            heatControl = crucible.heatControl();
            fuelTicks = crucible.fuelTicks();
            mold = crucible.selectedMoldIndex();
            mixColor = crucible.mixColor();
            materialUnits = crucible.materialUnits();
            metalSequence = crucible.metalSequence();
            autoPourProgress = crucible.autoPourProgress();
            if (previousMold != mold) moldPulseTicks = 12;
            if (!previousSequence.equals(metalSequence)) mixPulseTicks = 12;
        }
        if (heldControl != 0 && ++heldTicks % 2 == 0) send(heldControl);
        displayedTemperature += (temperature - displayedTemperature) * 0.16F;
        displayedHeatControl += (heatControl - displayedHeatControl) * 0.22F;
        displayedPourProgress += (autoPourProgress - displayedPourProgress) * 0.13F;
        if (moldPulseTicks > 0) moldPulseTicks--;
        if (mixPulseTicks > 0) mixPulseTicks--;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        int x = heatButtonX();
        int upY = panelY() + 48;
        int downY = panelY() + 86;
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
        int panelX = rightPanelX(), panelY = panelY();
        for (int layer = 0; layer < metalSequence.length(); layer++)
            if (inside(mouseX, mouseY, panelX + 8, panelY + 88 + layer * 21,
                    panelWidth() - 16, 19)) return layer;
        return -1;
    }

    private int moldAt(double mouseX, double mouseY) {
        int panelX = rightPanelX(), panelY = panelY();
        int step = moldStep();
        for (int index = 0; index < MOLDS.length; index++) {
            int moldX = panelX + 8 + index * step;
            if (inside(mouseX, mouseY, moldX, panelY + 43, step - 2, 25)) return index;
        }
        return -1;
    }

    private int inventorySlotAt(double mouseX, double mouseY) {
        int size = inventorySlotSize();
        int inventoryX = inventoryX(), inventoryY = panelY() + 151;
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            if (inside(mouseX, mouseY, inventoryX + column * size, inventoryY + row * size, size, size))
                return 9 + row * 9 + column;
        int hotbarY = inventoryY + size * 3 + 5;
        for (int column = 0; column < 9; column++)
            if (inside(mouseX, mouseY, inventoryX + column * size, hotbarY, size, size)) return column;
        return -1;
    }

    private int panelWidth() {
        return Mth.clamp((width - 32) / 2, 144, 194);
    }

    private int panelY() {
        return Math.max(4, (height - PANEL_HEIGHT) / 2);
    }

    private int leftPanelX() { return 8; }
    private int rightPanelX() { return width - panelWidth() - 8; }
    private int inventorySlotSize() { return panelWidth() >= 178 ? 18 : 16; }
    private int inventoryX() {
        return leftPanelX() + (panelWidth() - inventorySlotSize() * 9) / 2;
    }
    private int moldStep() { return (panelWidth() - 16) / MOLDS.length; }
    private int gaugeWidth() { return 48; }
    private int heatButtonX() { return leftPanelX() + panelWidth() - gaugeWidth() - 40; }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void send(int action) {
        if (ClientPlayNetworking.canSend(CrucibleControlPayload.TYPE))
            ClientPlayNetworking.send(new CrucibleControlPayload(pos, action));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        float reveal = smootherstep(Mth.clamp((screenTicks + delta) / 10.0F, 0.0F, 1.0F));
        int panelW = panelWidth(), panelY = panelY();
        int panelX = leftPanelX() - Math.round((1.0F - reveal) * 7.0F);
        int rightX = rightPanelX() + Math.round((1.0F - reveal) * 7.0F);
        drawPanel(graphics, panelX, panelY, panelW, title);
        drawPanel(graphics, rightX, panelY, panelW,
                Component.translatable("screen.asterion.crucible.forge_loadout"));

        CrucibleBlockEntity.Mold selected = mold < 0 ? null : MOLDS[mold];
        boolean calibrated = selected != null
                && Math.abs(temperature - selected.target()) <= CrucibleBlockEntity.TOLERANCE;
        boolean activelyMelting = calibrated && fuelTicks > 0 && materialUnits > 0;

        // Heat station: text, controls and gauge each have their own lane.
        graphics.text(font, Component.translatable("screen.asterion.crucible.heat"),
                panelX + 10, panelY + 31, 0xFFAA967B);
        Component fuel = Component.translatable("screen.asterion.crucible.fuel",
                fuelTicks > 0 ? (fuelTicks / 20 + 1) + "s" : "—");
        int fuelColor = fuelTicks > 0 ? pulseColor(0xFFFFB347, 0xFFFFD17A, 0.18F) : 0xFF847A70;
        graphics.text(font, fuel, panelX + panelW - 10 - font.width(fuel), panelY + 31, fuelColor);

        int buttonX = panelX + panelW - gaugeWidth() - 40;
        int upY = panelY + 48, downY = panelY + 86;
        drawHeatButton(graphics, BUTTON_UP_TEXTURE, buttonX, upY, mouseX, mouseY,
                heldControl == CrucibleControlPayload.HEAT);
        drawHeatButton(graphics, BUTTON_DOWN_TEXTURE, buttonX, downY, mouseX, mouseY,
                heldControl == CrucibleControlPayload.COOL);

        int gaugeX = panelX + panelW - gaugeWidth() - 8, gaugeY = panelY + 39;
        graphics.blit(RenderPipelines.GUI_TEXTURED, GAUGE_TEXTURE, gaugeX, gaugeY,
                0, 0, 48, 96, 48, 96);
        if (selected != null) {
            float targetRatio = Mth.clamp(selected.target() / (float)CrucibleBlockEntity.MAX_TEMPERATURE, 0F, 1F);
            int targetY = gaugeY + 78 - Math.round(61 * targetRatio);
            // The green gate is the temperature window the player must actively hold.
            graphics.fill(gaugeX + 13, targetY - 2, gaugeX + 34, targetY + 3, 0x905FCB78);
            graphics.fill(gaugeX + 10, targetY, gaugeX + 37, targetY + 1, 0xFFE5D48B);
        }
        float ratio = Mth.clamp(displayedTemperature / CrucibleBlockEntity.MAX_TEMPERATURE, 0.0F, 1.0F);
        int markerY = gaugeY + 78 - Math.round(61 * ratio);
        graphics.fill(gaugeX + 18, markerY - 1, gaugeX + 29, markerY + 2, 0xFFFFB12B);

        graphics.text(font, Component.literal(Math.round(displayedTemperature) + "°"),
                panelX + 10, panelY + 51, 0xFFF5E8D2);
        Component target = Component.literal("TARGET  " + (selected == null ? "—" : selected.target() + "°"));
        graphics.text(font, target, panelX + 10, panelY + 68, 0xFFB7A78E);

        String pressure = (displayedHeatControl > 0.4F ? "+" : "") + Math.round(displayedHeatControl);
        graphics.text(font, Component.literal("AIRFLOW  " + pressure), panelX + 10, panelY + 85,
                heatControl > 0 ? 0xFFFFB55A : heatControl < 0 ? 0xFF79BDE8 : 0xFF9E9589);
        int airflowLeft = panelX + 10, airflowRight = Math.max(airflowLeft + 30, buttonX - 7);
        int airflowMiddle = (airflowLeft + airflowRight) / 2;
        graphics.fill(airflowLeft, panelY + 101, airflowRight, panelY + 105, 0xFF29231E);
        graphics.fill(airflowMiddle, panelY + 100, airflowMiddle + 1, panelY + 106, 0xFF8B8175);
        int airflowMarker = Mth.clamp(Math.round(Mth.map(displayedHeatControl,
                CrucibleBlockEntity.MIN_HEAT_CONTROL, CrucibleBlockEntity.MAX_HEAT_CONTROL,
                airflowLeft, airflowRight - 2)), airflowLeft, airflowRight - 2);
        graphics.fill(airflowMarker, panelY + 99, airflowMarker + 3, panelY + 107, 0xFFE8C881);

        String status;
        int statusColor;
        if (selected == null) { status = "INSERT A MOLD"; statusColor = 0xFF9A9187; }
        else if (materialUnits == 0) { status = "ADD METAL"; statusColor = 0xFFD5B56F; }
        else if (fuelTicks == 0) { status = "ADD FUEL"; statusColor = 0xFFD5B56F; }
        else if (activelyMelting) { status = "HOLD THE BAND  " + Math.round(displayedPourProgress
                / CrucibleBlockEntity.AUTO_POUR_TICKS * 100F) + "%"; statusColor = 0xFF78D18B; }
        else if (temperature < selected.target()) { status = "TOO COLD — OPEN BELLOWS"; statusColor = 0xFF73BCEE; }
        else { status = "TOO HOT — VENT HEAT"; statusColor = 0xFFEC7965; }
        graphics.text(font, status, panelX + 10, panelY + 120, statusColor);
        int processLeft = panelX + 10, processRight = panelX + panelW - 10;
        graphics.fill(processLeft, panelY + 133, processRight, panelY + 137, 0xFF29231E);
        float processRatio = Mth.clamp(displayedPourProgress / CrucibleBlockEntity.AUTO_POUR_TICKS, 0F, 1F);
        graphics.fill(processLeft, panelY + 133,
                processLeft + Math.round((processRight - processLeft) * processRatio), panelY + 137,
                activelyMelting ? 0xFF69C57C : 0xFFC36B45);

        // Inventory stays in a dedicated lower section and scales down cleanly at high GUI scales.
        graphics.text(font, Component.translatable("screen.asterion.crucible.inventory_hint"),
                panelX + 10, panelY + 141, 0xFFBFAE94);
        int slotSize = inventorySlotSize();
        int inventoryX = panelX + (panelW - slotSize * 9) / 2, inventoryY = panelY + 151;
        if (minecraft.player != null) {
            for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
                drawInventorySlot(graphics, minecraft.player.getInventory().getItem(9 + row * 9 + column),
                        inventoryX + column * slotSize, inventoryY + row * slotSize, slotSize, mouseX, mouseY);
            int hotbarY = inventoryY + slotSize * 3 + 5;
            for (int column = 0; column < 9; column++)
                drawInventorySlot(graphics, minecraft.player.getInventory().getItem(column),
                        inventoryX + column * slotSize, hotbarY, slotSize, mouseX, mouseY);
        }

        // Mold choices occupy a full row; their tooltips carry the long names.
        graphics.text(font, Component.translatable("screen.asterion.crucible.mold"),
                rightX + 8, panelY + 31, 0xFFAA967B);
        int moldStep = (panelW - 16) / MOLDS.length;
        for (int index = 0; index < MOLDS.length; index++) {
            int moldX = rightX + 8 + index * moldStep;
            int moldW = moldStep - 2;
            boolean hovered = inside(mouseX, mouseY, moldX, panelY + 43, moldW, 25);
            boolean available = mold == index || minecraft.player != null
                    && minecraft.player.getInventory().contains(new ItemStack(CrucibleBlockEntity.moldItem(index)));
            graphics.fill(moldX, panelY + 43, moldX + moldW, panelY + 68,
                    mold == index ? 0xFF594A34 : available ? 0xFF29231E : 0xFF151311);
            int selectedColor = moldPulseTicks > 0 ? pulseColor(0xFFFFD078, 0xFFFFFFFF, 0.35F) : 0xFFFFD078;
            graphics.outline(moldX, panelY + 43, moldW, 25,
                    hovered ? 0xFFE8C881 : mold == index ? selectedColor : 0xFF554A3D);
            ItemStack cast = CAST_ICONS[index];
            graphics.item(cast, moldX + Math.max(0, (moldW - 16) / 2), panelY + 47);
            if (!available) {
                graphics.fill(moldX + 1, panelY + 44, moldX + moldW - 1, panelY + 67, 0xC8000000);
                graphics.centeredText(font, Component.literal("×"), moldX + moldW / 2,
                        panelY + 51, 0xFF81756A);
            }
            if (hovered) graphics.setTooltipForNextFrame(font, available
                    ? Component.literal(MOLDS[index].label())
                    : Component.translatable("screen.asterion.crucible.mold_locked", MOLDS[index].label()),
                    mouseX, mouseY);
        }

        Component mixtureTitle = Component.translatable("screen.asterion.crucible.mixture", materialUnits, 4);
        graphics.text(font, mixtureTitle, rightX + 8, panelY + 76, 0xFFAA967B);
        if (!metalSequence.isEmpty()) {
            int swatchColor = (mixPulseTicks > 0 ? 0xFF : 0xE8) << 24 | mixColor;
            graphics.fill(rightX + panelW - 28, panelY + 78, rightX + panelW - 8, panelY + 84, swatchColor);
            graphics.outline(rightX + panelW - 28, panelY + 78, 20, 6, 0xFF8B765E);
        }
        for (int layer = 0; layer < metalSequence.length(); layer++) {
            int rowY = panelY + 88 + layer * 21;
            int rowX = rightX + 8, rowW = panelW - 16;
            boolean hovered = inside(mouseX, mouseY, rowX, rowY, rowW, 19);
            graphics.fill(rowX, rowY, rowX + rowW, rowY + 19, layer == 0 ? 0xFF41372C : 0xFF2B2520);
            graphics.outline(rowX, rowY, rowW, 19, hovered ? 0xFFE17060 : 0xFF655746);
            String material = (layer == 0 ? "BASE  " : "+50%  ")
                    + materialName(metalSequence.charAt(layer) - '0');
            material = font.plainSubstrByWidth(material, rowW - 29);
            graphics.text(font, material, rowX + 6, rowY + 5, layer == 0 ? 0xFFFFDA91 : 0xFFD6C5AD);
            graphics.text(font, "×", rowX + rowW - 13, rowY + 5, hovered ? 0xFFFF9A88 : 0xFFC96658);
        }
        if (metalSequence.isEmpty()) {
            graphics.text(font, Component.translatable("screen.asterion.crucible.empty_mixture"),
                    rightX + 14, panelY + 94, 0xFF887C6E);
        }

        graphics.fill(rightX + 8, panelY + 180, rightX + panelW - 8, panelY + 181, 0x665E5143);
        graphics.text(font, Component.translatable("screen.asterion.crucible.output"),
                rightX + 8, panelY + 188, 0xFFAA967B);
        int resultX = rightX + 8, resultY = panelY + 200, resultW = panelW - 16;
        graphics.fill(resultX, resultY, resultX + resultW, resultY + 29, 0xFF241F1A);
        graphics.outline(resultX, resultY, resultW, 29, calibrated && materialUnits > 0 ? 0xFF78B884 : 0xFF554A3D);
        ItemStack preview = mixturePreview();
        if (!preview.isEmpty()) {
            int bob = calibrated ? Math.round((float)Math.sin((screenTicks + delta) * 0.18F)) : 0;
            graphics.item(preview, resultX + 7, resultY + 7 + bob);
            graphics.itemDecorations(font, preview, resultX + 7, resultY + 7 + bob);
            var nameLines = font.split(preview.getHoverName(), resultW - 38);
            for (int line = 0; line < Math.min(2, nameLines.size()); line++)
                graphics.text(font, nameLines.get(line), resultX + 31, resultY + 5 + line * 10, 0xFFE5D4B9);
        } else {
            graphics.text(font, Component.translatable("screen.asterion.crucible.no_output"),
                    resultX + 8, resultY + 10, 0xFF81786C);
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
            case MINOTAUR_KEY -> Asterion.MINOTAUR_KEY;
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

    private void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int panelWidth, Component heading) {
        graphics.fill(x, y, x + panelWidth, y + PANEL_HEIGHT, 0xB3141110);
        graphics.outline(x, y, panelWidth, PANEL_HEIGHT, 0xD08B765E);
        graphics.fill(x + 1, y + 1, x + panelWidth - 1, y + 3, 0x806F553A);
        graphics.text(font, heading, x + 10, y + 10, 0xFFEAD6B7);
        graphics.fill(x + 9, y + 24, x + panelWidth - 9, y + 25, 0x665E5143);
    }

    private void drawHeatButton(GuiGraphicsExtractor graphics, Identifier texture, int x, int y,
                                int mouseX, int mouseY, boolean held) {
        boolean hovered = inside(mouseX, mouseY, x, y, 32, 32);
        int offset = held ? 1 : 0;
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y + offset,
                0, 0, 32, 32, 32, 32);
        if (hovered || held) graphics.outline(x, y + offset, 32, 32,
                held ? 0xFFFFE0A0 : 0xFFD3B878);
    }

    private void drawInventorySlot(GuiGraphicsExtractor graphics, net.minecraft.world.item.ItemStack stack,
                                   int x, int y, int size, int mouseX, int mouseY) {
        graphics.fill(x, y, x + size, y + size, 0xFF29231E);
        graphics.outline(x, y, size, size,
                inside(mouseX, mouseY, x, y, size, size) ? 0xFFE0BD72 : 0xFF554A3D);
        if (!stack.isEmpty()) {
            int inset = Math.max(0, (size - 16) / 2);
            graphics.item(stack, x + inset, y + inset);
            graphics.itemDecorations(font, stack, x + inset, y + inset);
            if (inside(mouseX, mouseY, x, y, size, size))
                graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
        }
    }

    private int pulseColor(int first, int second, float speed) {
        float amount = ((float)Math.sin(screenTicks * speed) + 1.0F) * 0.5F;
        int a = Math.round(Mth.lerp(amount, first >>> 24, second >>> 24));
        int r = Math.round(Mth.lerp(amount, first >> 16 & 255, second >> 16 & 255));
        int g = Math.round(Mth.lerp(amount, first >> 8 & 255, second >> 8 & 255));
        int b = Math.round(Mth.lerp(amount, first & 255, second & 255));
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static float smootherstep(float value) {
        return value * value * value * (value * (value * 6.0F - 15.0F) + 10.0F);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Keep the physical crucible crisp behind the two HUD rails.
    }
    @Override public void removed() {
        CrucibleCamera.end();
        super.removed();
    }
}
