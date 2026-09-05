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

/** Texture-backed Forge controls around the world-space camera view. */
public final class CrucibleScreen extends Screen {
    public static final Identifier GAUGE_TEXTURE = Asterion.id("textures/gui/forge/left/temp_gauge.png");
    public static final Identifier BUTTON_UP_TEXTURE = Asterion.id("textures/gui/forge/left/button_up.png");
    public static final Identifier BUTTON_DOWN_TEXTURE = Asterion.id("textures/gui/forge/left/button_down.png");
    private final BlockPos pos;
    private final boolean previousHideGui;
    private final boolean[] flowRows = new boolean[208];
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
    private float displayedPourProgress;
    private int heldControl;
    private int heldTicks, noticeTicks;
    private String controlNotice = "";
    private static final CrucibleBlockEntity.Mold[] MOLDS = CrucibleBlockEntity.Mold.values();
    private static final int[] VISIBLE_MOLDS = {0, 1, 2, 3, 4, 5};
    private static final String[] MATERIAL_NAMES = {
            "Iron", "Copper", "Tarnished Gold", "Netherite", "Celestial Bronze",
            "Bonesteel", "Celestial Steel", "Celestial Gold", "Gold", "Mazesteel", "Ancient Bone"
    };
    private static final ItemStack[] METAL_ICONS = java.util.stream.IntStream.range(0, 11)
            .mapToObj(CrucibleBlockEntity::returnedMetal).toArray(ItemStack[]::new);
    private ItemStack cachedPreview = ItemStack.EMPTY;
    private String cachedPreviewSequence = "";
    private int cachedPreviewMold = Integer.MIN_VALUE;

    public CrucibleScreen(CrucibleScreenPayload state) {
        super(Component.translatable("screen.asterion.crucible"));
        pos = state.pos();
        previousHideGui = net.minecraft.client.Minecraft.getInstance().options.hideGui;
        net.minecraft.client.Minecraft.getInstance().options.hideGui = true;
        update(state);
        prepareFlowTexture();
        CrucibleCamera.begin(pos);
    }

    public boolean matches(BlockPos candidate) { return pos.equals(candidate); }
    public void update(CrucibleScreenPayload state) {
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
    }

    @Override public void tick() {
        minecraft.options.hideGui = true;
        if (noticeTicks > 0) noticeTicks--;
        heatPanel.tick(); forgePanel.tick(); moldPanel.tick();
        previousInventoryReveal = inventoryReveal;
        inventoryReveal += ((inventoryOpen ? 1F : 0F) - inventoryReveal) * .35F;
        if (closing && Math.max(heatPanel.amount, Math.max(forgePanel.amount, moldPanel.amount)) < .01F) {
            super.onClose();
            return;
        }
        if (minecraft.level != null
                && minecraft.level.getBlockEntity(pos) instanceof CrucibleBlockEntity crucible) {
            temperature = crucible.temperature();
            targetTemperature = crucible.targetTemperature();
            heatControl = crucible.heatControl();
            fuelTicks = crucible.fuelTicks();
            mold = crucible.selectedMoldIndex();
            mixColor = crucible.mixColor();
            materialUnits = crucible.materialUnits();
            metalSequence = crucible.metalSequence();
            autoPourProgress = crucible.autoPourProgress();
        }
        if (heldControl != 0 && ++heldTicks % 2 == 0) send(heldControl);
        displayedTemperature += (temperature - displayedTemperature) * 0.16F;
        displayedPourProgress += (autoPourProgress - displayedPourProgress) * 0.13F;
    }

    private boolean inventoryOpen, closing;
    private float inventoryReveal, previousInventoryReveal, framePartial = 1;
    private final Panel heatPanel = new Panel(), forgePanel = new Panel(), moldPanel = new Panel();
    private static final class Panel {
        boolean open = true;
        float amount, previous;
        float value(float partial) { return Mth.lerp(partial, previous, amount); }
        void tick() { previous = amount; amount += ((open ? 1F : 0F) - amount) * .35F; }
    }
    private int leftOffset() { return Math.round(-128 * (1 - heatPanel.value(framePartial))); }
    @Override public void onClose() {
        closing = true;
        inventoryOpen = false;
        heatPanel.open = forgePanel.open = moldPanel.open = false;
        heldControl = 0;
    }
    private static final ItemStack INGOT_ICON = new ItemStack(net.minecraft.world.item.Items.IRON_INGOT);
    private static final Identifier LEFT = texture("left/border");
    private static final Identifier RIGHT = texture("right/border");
    private static final Identifier CENTER = texture("right/center");
    private static final Identifier CENTER_FILL = Asterion.id("dynamic/forge_center_fill");
    private static final Identifier BOTTOM = texture("bottom_center/border");
    private static final Identifier SMELT = texture("right/smelt_button");
    private static final Identifier POUR = texture("right/pour_button");
    private static final Identifier INPUT = texture("right/material_input");
    private static final Identifier[] MOLD_TEXTURES = {
            texture("bottom_center/ingot_mold"), texture("bottom_center/guard_mold"),
            texture("bottom_center/pomel_mold"), texture("bottom_center/blade_mold"),
            texture("bottom_center/blade_mold"), texture("bottom_center/minotaur_key_mold")
    };
    private static final Identifier GAUGE_FILL = texture("left/temp_gauge_fill");
    private static final Identifier STATUS = texture("left/too_hot");
    private static final ItemStack[] MOLD_OUTPUT_ICONS = createMoldOutputIcons();
    private static Identifier texture(String name) { return Asterion.id("textures/gui/forge/" + name + ".png"); }
    // One coordinate system drives drawing and hit testing, including small windows.
    private float scale() { return Math.min(1.5F, Math.min(width / 544F, height / 224F)); }
    private int rightX() { return Math.round(width / scale()) - 132 + Math.round(128 * (1 - forgePanel.value(framePartial))); }
    private int bottomX() { return Math.round(width / scale()) / 2 - 128; }
    private int bottomY() { return Math.round(height / scale()) - 68 + Math.round(64 * (1 - moldPanel.value(framePartial))); }
    private int ingredientX(int index) { return rightX() + (index == 0 ? 56 : 16 + (index - 1) * 40); }
    private int ingredientY(int index) { return index == 0 ? 24 : 64; }
    private int inventoryX() { return Math.round(width / scale()) / 2 - 85; }
    private int inventoryY() { return Math.max(8, Math.round(height / scale()) - 162) + Math.round((1 - Mth.lerp(framePartial, previousInventoryReveal, inventoryReveal)) * 100); }
    private int inventoryTabX() { return rightX() + 42; }
    private int inventoryTabY() { return 198; }
    private static boolean inside(double x, double y, int left, int top, int w, int h) {
        return x >= left && x < left + w && y >= top && y < top + h;
    }
    private int inventorySlotAt(double x, double y) {
        if (!inventoryOpen) return -1;
        for (int row = 0; row < 4; row++) for (int col = 0; col < 9; col++) {
            int sy = inventoryY() + 16 + row * 18 + (row == 3 ? 4 : 0);
            if (inside(x, y, inventoryX() + col * 19, sy, 18, 18))
                return row == 3 ? col : 9 + row * 9 + col;
        }
        return -1;
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (closing) return true;
        double x = event.x() / scale(), y = event.y() / scale();
        if (!inventoryOpen && (event.button() == 0 || event.button() == 1)) {
            for (int i = metalSequence.length() - 1; i >= 0; i--) {
                if (inside(x, y, ingredientX(i), ingredientY(i), 16, 16)) {
                    send(CrucibleControlPayload.removeMaterial(i)); return true;
                }
            }
        }
        if (event.button() != 0) return super.mouseClicked(event, doubled);
        if (inside(x, y, 132 + leftOffset(), 90, 18, 34)) { heatPanel.open = !heatPanel.open; heldControl = 0; return true; }
        if (inside(x, y, rightX() - 18, 90, 18, 34)) { forgePanel.open = !forgePanel.open; return true; }
        if (inside(x, y, bottomX() + 95, bottomY() - 14, 66, 14)) { moldPanel.open = !moldPanel.open; return true; }
        if (inside(x, y, inventoryTabX(), inventoryTabY(), 44, 14)) { inventoryOpen = !inventoryOpen; return true; }
        double heatX = x - leftOffset();
        int slot = inventorySlotAt(x, y);
        if (slot >= 0) { send(CrucibleControlPayload.insertSlot(slot)); inventoryOpen = false; return true; }
        if (inventoryOpen && inside(x, y, inventoryX() - 5, inventoryY() - 4, 181, 98)) return true;
        if (inside(heatX, y, 84, 36, 32, 32)) heldControl = CrucibleControlPayload.HEAT;
        else if (inside(heatX, y, 84, 132, 32, 32)) heldControl = CrucibleControlPayload.COOL;
        else if (inside(heatX, y, 84, 84, 32, 32)) { send(CrucibleControlPayload.NEXT_MOLD); return true; }
        else if (inside(x, y, rightX() + 24, 164, 80, 32)) { send(CrucibleControlPayload.POUR); return true; }
        else if (inside(x, y, rightX() + 16, 128, 96, 32)) {
            controlNotice = materialUnits == 0 ? "ADD METAL" : fuelTicks <= 0 ? "HEAT BELOW"
                    : hasUnsmeltedIngredients() ? "HEAT TO 350°" : "METAL READY";
            noticeTicks = 60;
            send(CrucibleControlPayload.SMELT); return true;
        } else if (inside(x, y, rightX() + 48, 16, 32, 32)
                || inside(x, y, rightX() + 8, 56, 112, 32)) {
            inventoryOpen = !inventoryOpen; return true;
        } else {
            for (int i = 0; i < VISIBLE_MOLDS.length; i++) if (inside(x, y, bottomX() + 8 + i * 40, bottomY() + 16, 32, 32)) {
                send(CrucibleControlPayload.selectMold(VISIBLE_MOLDS[i])); return true;
            }
            return super.mouseClicked(event, doubled);
        }
        heldTicks = 0;
        send(heldControl);
        return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        heldControl = heldTicks = 0;
        return super.mouseReleased(event);
    }
    private void send(int action) {
        if (ClientPlayNetworking.canSend(CrucibleControlPayload.TYPE))
            ClientPlayNetworking.send(new CrucibleControlPayload(pos, action));
    }
    private static void image(GuiGraphicsExtractor g, Identifier texture, int x, int y, int w, int h) {
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, w, h, w, h);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        framePartial = Mth.clamp(delta, 0, 1);
        int mx = (int)(mouseX / scale()), my = (int)(mouseY / scale());
        g.pose().pushMatrix();
        g.pose().scale(scale(), scale());
        g.pose().pushMatrix();
        g.pose().translate(leftOffset(), 0);
        g.fill(4, 4, 132, 212, 0xE8100E0D);
        image(g, LEFT, 4, 4, 128, 208);
        g.pose().pushMatrix();
        g.pose().translate(12, 8);
        g.pose().scale(.82F, .78F);
        var fill = GAUGE_FILL;
        g.blit(RenderPipelines.GUI_TEXTURED, fill, 0, 0, 0, 0, 64, 208, 64, 208, 0xFF211B18);
        int first = Math.round(192 - Mth.clamp(displayedTemperature / CrucibleBlockEntity.MAX_TEMPERATURE, 0, 1) * 180);
        for (int row = first; row < 192; row += 2) {
            float heat = (192 - row) / 180F;
            int color = heat < .55F ? net.minecraft.util.ARGB.linearLerp(heat / .55F, 0xFF9D3B20, 0xFFF59C36)
                    : net.minecraft.util.ARGB.linearLerp((heat - .55F) / .45F, 0xFFF59C36, 0xFFFFF0BC);
            g.blit(RenderPipelines.GUI_TEXTURED, fill, 0, row, 0, row, 64, Math.min(2, 192 - row), 64, 208, color);
        }
        image(g, GAUGE_TEXTURE, 0, 0, 64, 208);
        g.pose().popMatrix();
        shadowCentered(g, Math.round(displayedTemperature) + "°", 43, 172, 0xFFD4BE9E);
        image(g, BUTTON_UP_TEXTURE, 84, 36, 32, 32);
        image(g, INPUT, 84, 84, 32, 32);
        image(g, BUTTON_DOWN_TEXTURE, 84, 132, 32, 32);
        for (int i = 0; i < 3; i++) {
            int buttonY = 36 + i * 48;
            if (inside(mx - leftOffset(), my, 84, buttonY, 32, 32))
                g.outline(84, buttonY, 32, 32, 0xFFD0B68C);
        }
        shadowCentered(g, Integer.toString(heatControl), 100, 96, 0xFFBDA88A);
        var selected = mold < 0 ? null : MOLDS[mold];
        boolean ready = selected != null && Math.abs(temperature - selected.target()) <= CrucibleBlockEntity.TOLERANCE;
        String status = selected == null ? "NO MOLD" : ready ? "READY" : temperature > selected.target() ? "TOO HOT" : "TOO COLD";
        g.blit(RenderPipelines.GUI_TEXTURED, STATUS, 32, 187, 0, 0, 72, 18, 80, 32, 80, 32);
        shadowCentered(g, status, 68, 192,
                ready ? 0xFF8EBB79 : temperature > targetTemperature ? 0xFFFF4840 : 0xFFBDA88A);
        if (inside(mx - leftOffset(), my, 12, 8, 53, 162))
            g.setTooltipForNextFrame(font, Component.literal(temperature + "° / " + targetTemperature + "°"), mouseX, mouseY);
        g.pose().popMatrix();
        int rx = rightX();
        g.fill(rx, 4, rx + 128, 212, 0xE8100E0D);
        image(g, RIGHT, rx, 4, 128, 208);
        image(g, CENTER, rx, 4, 128, 208);
        drawMetalFlow(g, rx);
        image(g, INPUT, rx + 48, 16, 32, 32);
        for (int i = 0; i < 3; i++) image(g, INPUT, rx + 8 + i * 40, 56, 32, 32);
        image(g, SMELT, rx + 16, 128, 96, 32);
        image(g, POUR, rx + 24, 164, 80, 32);
        shadowCentered(g, "SMELT", rx + 64, 140, 0xFFC5AE8E);
        shadowCentered(g, "POUR", rx + 64, 176, 0xFFC5AE8E);
        if (inside(mx, my, rx + 16, 128, 96, 32)) g.outline(rx + 16, 128, 96, 32, 0xFFD0B68C);
        if (inside(mx, my, rx + 24, 164, 80, 32)) g.outline(rx + 24, 164, 80, 32, 0xFFD0B68C);
        if (noticeTicks > 0) shadowCentered(g, controlNotice, rx + 64, 116, 0xFFE5B77B);
        if (mold >= 0 && mold != 4) {
            g.pose().pushMatrix();
            image(g, MOLD_TEXTURES[mold], rx + 48, 91, 32, 32);
            g.pose().translate(rx + 52, 95);
            g.pose().scale(1.5F, 1.5F);
            g.item(MOLD_OUTPUT_ICONS[mold], 0, 0);
            g.pose().popMatrix();
        }
        ItemStack preview = mixturePreview();
        if (!preview.isEmpty()) {
            drawItemGlow(g, rx + 56, 99, mixColor);
            g.item(preview, rx + 56, 99);
        }
        if (autoPourProgress > 0 && noticeTicks == 0) shadowCentered(g,
                Math.round(displayedPourProgress * 100F / CrucibleBlockEntity.AUTO_POUR_TICKS) + "%",
                rx + 64, 116, 0xFFBDA88A);
        if (inside(mx, my, rx + 48, 16, 32, 32) || inside(mx, my, rx + 8, 56, 112, 32))
            g.setTooltipForNextFrame(font, Component.literal("Open inventory — add ingots or molds"), mouseX, mouseY);
        for (int i = 0; i < metalSequence.length(); i++) {
            int x = ingredientX(i);
            int y = ingredientY(i);
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.item(METAL_ICONS[metalSequence.charAt(i) - '0'], 0, 0);
            g.pose().popMatrix();
            if (inside(mx, my, x, y, 16, 16)) {
                g.outline(x, y, 16, 16, 0xFFD0B68C);
                g.setTooltipForNextFrame(font, Component.literal(materialName(metalSequence.charAt(i) - '0')
                        + " — click to return to inventory"), mouseX, mouseY);
            }
        }
        g.fill(bottomX(), bottomY(), bottomX() + 256, bottomY() + 64, 0xE8100E0D);
        image(g, BOTTOM, bottomX(), bottomY(), 256, 64);
        for (int slotIndex = 0; slotIndex < VISIBLE_MOLDS.length; slotIndex++) {
            int i = VISIBLE_MOLDS[slotIndex];
            int x = bottomX() + 8 + slotIndex * 40, y = bottomY() + 16;
            image(g, MOLD_TEXTURES[i], x, y, 32, 32);
            boolean owned = mold == i || hasMoldInInventory(i);
            if (owned) {
                g.pose().pushMatrix();
                float itemScale = mold == i ? 1.25F : 1F;
                g.pose().translate(x + (mold == i ? 6 : 8), y + (mold == i ? 6 : 8));
                g.pose().scale(itemScale, itemScale);
                g.item(MOLD_OUTPUT_ICONS[i], 0, 0);
                g.pose().popMatrix();
            } else g.fill(x + 4, y + 4, x + 28, y + 28, 0xA0100E0D);
            if (mold == i || inside(mx, my, x, y, 32, 32)) g.outline(x, y, 32, 32, 0xFFD0B68C);
            if (inside(mx, my, x, y, 32, 32)) g.setTooltipForNextFrame(font,
                    Component.literal(MOLDS[i].label() + (owned ? "" : " — not in inventory")), mouseX, mouseY);
        }
        tab(g, 132 + leftOffset(), 90, 18, 34, heatPanel.open ? "‹" : "›");
        tab(g, rx - 18, 90, 18, 34, forgePanel.open ? "›" : "‹");
        tab(g, bottomX() + 95, bottomY() - 14, 66, 14, moldPanel.open ? "MOLDS ▾" : "MOLDS ▴");
        tab(g, inventoryTabX(), inventoryTabY(), 44, 14, inventoryOpen ? "INV ▾" : "INV ▴");
        if (inventoryReveal > .01F && minecraft.player != null) {
            int ix = inventoryX(), iy = inventoryY();
            g.fill(ix - 5, iy - 4, ix + 176, iy + 94, 0xF0181513);
            g.outline(ix - 5, iy - 4, 181, 98, 0xFF817361);
            g.text(font, "Add ingots or a mold", ix, iy + 2, 0xFFBDA88A, true);
            for (int row = 0; row < 4; row++) for (int col = 0; col < 9; col++) {
                int slot = row == 3 ? col : 9 + row * 9 + col;
                drawInventorySlot(g, minecraft.player.getInventory().getItem(slot), ix + col * 19,
                        iy + 16 + row * 18 + (row == 3 ? 4 : 0), 18, mx, my);
            }
        }
        g.pose().popMatrix();
    }

    private void tab(GuiGraphicsExtractor g, int x, int y, int w, int h, String label) {
        g.fill(x, y, x + w, y + h, 0xF025211D);
        g.outline(x, y, w, h, 0xFF817361);
        shadowCentered(g, label, x + w / 2, y + (h - 8) / 2, 0xFFCFB993);
    }

    private void shadowCentered(GuiGraphicsExtractor g, String text, int centerX, int y, int color) {
        g.text(font, text, centerX - font.width(text) / 2, y, color, true);
    }

    private void drawItemGlow(GuiGraphicsExtractor g, int x, int y, int color) {
        float time = minecraft.level == null ? 0 : minecraft.level.getGameTime() + framePartial;
        int alpha = 38 + Math.round((.5F + .5F * Mth.sin(time * .18F)) * 42F);
        int glow = alpha << 24 | color;
        g.fill(x - 3, y - 3, x + 19, y + 19, glow);
        g.outline(x - 2, y - 2, 20, 20, 0xA0FFE0A0);
    }

    private void prepareFlowTexture() {
        // The authored texture defines coverage; its dark paint is neutralized for heat tinting.
        var client = net.minecraft.client.Minecraft.getInstance();
        try (var stream = client.getResourceManager().open(texture("right/center_fill"))) {
            var pixels = com.mojang.blaze3d.platform.NativeImage.read(stream);
            for (int y = 0; y < pixels.getHeight(); y++) for (int x = 0; x < pixels.getWidth(); x++)
                {
                    int alpha = pixels.getPixel(x, y) & 0xFF000000;
                    if (alpha != 0) flowRows[y] = true;
                    pixels.setPixel(x, y, alpha | 0xFFFFFF);
                }
            client.getTextureManager().register(CENTER_FILL,
                    new net.minecraft.client.renderer.texture.DynamicTexture(() -> "Forge flow mask", pixels));
        } catch (java.io.IOException error) {
            Asterion.LOGGER.warn("Could not load Forge flow texture", error);
        }
    }

    private void drawMetalFlow(GuiGraphicsExtractor g, int x) {
        if (materialUnits == 0) return;
        float heat = Mth.clamp(displayedTemperature / 900F, 0F, 1F);
        float time = minecraft.level == null ? 0 : minecraft.level.getGameTime() + framePartial;
        float progress = displayedPourProgress / CrucibleBlockEntity.AUTO_POUR_TICKS;
        int baseColor = net.minecraft.util.ARGB.linearLerp(heat, 0xFF000000 | mixColor, 0xFFFF792A);
        for (int y = 16; y < 192; y += 2) {
            if (!flowRows[y] && !flowRows[y + 1]) continue;
            if (y >= 28 && y < 60 || y >= 116 && y < 148) continue;
            // The lower channel fills toward the output mold as pouring progresses.
            if (y > 110 && y > 110 + progress * 82) continue;
            float pulse = (.5F + .5F * Mth.sin(y * .12F - time * .16F)) * heat;
            int color = net.minecraft.util.ARGB.linearLerp(pulse * .65F, baseColor, 0xFFFFE8A0);
            g.blit(RenderPipelines.GUI_TEXTURED, CENTER_FILL, x, 4 + y, 0, y, 128, 2, 128, 208, color);
        }
    }

    private ItemStack mixturePreview() {
        if (metalSequence.isEmpty() || mold < 0 || hasUnsmeltedIngredients()) return ItemStack.EMPTY;
        if (metalSequence.equals(cachedPreviewSequence) && mold == cachedPreviewMold) return cachedPreview;
        if (MOLDS[mold] == CrucibleBlockEntity.Mold.INGOT && metalSequence.chars().allMatch(value -> value == '5')) {
            cachedPreviewSequence = metalSequence;
            cachedPreviewMold = mold;
            return cachedPreview = new ItemStack(Asterion.BONESTEEL_INGOT, metalSequence.length());
        }
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

    private boolean hasUnsmeltedIngredients() { return metalSequence.indexOf('9') >= 0 || metalSequence.indexOf(':') >= 0; }

    private static ItemStack[] createMoldOutputIcons() {
        ItemStack[] icons = new ItemStack[MOLDS.length];
        for (int index = 0; index < icons.length; index++) {
            net.minecraft.world.item.Item item = switch (MOLDS[index]) {
                case INGOT -> Asterion.FORGED_INGOT;
                case SWORD_GUARD -> Asterion.FORGED_SWORD_GUARD;
                case SWORD_POMMEL -> Asterion.FORGED_SWORD_POMMEL;
                case SWORD_BLADE -> Asterion.FORGED_SWORD_BLADE;
                case AXE_HEAD -> Asterion.FORGED_AXE_HEAD;
                case MINOTAUR_KEY -> Asterion.MINOTAUR_KEY;
            };
            ItemStack icon = new ItemStack(item);
            icon.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(),
                    List.of("iron", "none", "none", "none"),
                    List.of(0xFFFFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x00FFFFFF)));
            icons[index] = icon;
        }
        return icons;
    }

    private boolean hasMoldInInventory(int index) {
        if (minecraft.player == null) return false;
        net.minecraft.world.item.Item wanted = CrucibleBlockEntity.moldItem(index);
        for (int slot = 0; slot < 36; slot++)
            if (minecraft.player.getInventory().getItem(slot).is(wanted)) return true;
        return false;
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
                graphics.setTooltipForNextFrame(font, stack, Math.round(mouseX * scale()), Math.round(mouseY * scale()));
        }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Keep the physical Forge visible behind the panels.
    }
    @Override public void removed() {
        net.minecraft.client.Minecraft.getInstance().options.hideGui = previousHideGui;
        net.minecraft.client.Minecraft.getInstance().getTextureManager().release(CENTER_FILL);
        CrucibleCamera.end();
        super.removed();
    }
}
