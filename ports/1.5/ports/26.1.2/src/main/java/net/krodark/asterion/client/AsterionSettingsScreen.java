package net.krodark.asterion.client;

import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.client.light.AsterionEmissiveConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Compact settings: the controls players use, with tuning details kept internal. */
public final class AsterionSettingsScreen extends Screen {
    private final Screen parent;
    private int tab, left, top, panelWidth, panelHeight;
    public AsterionSettingsScreen(Screen parent) {
        super(Component.literal("Asterion"));
        this.parent = parent;
    }
    @Override protected void init() {
        panelWidth = Math.min(356, width - 20);
        panelHeight = Math.min(230, height - 12);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        var config = AsterionConfig.INSTANCE;
        String[] tabs = {"Visuals", "Audio", "Interface"};
        int tabWidth = (panelWidth - 24) / 3;
        for (int i = 0; i < tabs.length; i++) {
            final int selected = i;
            Button button = Button.builder(Component.literal(tabs[i]), clicked -> {
                tab = selected; rebuildWidgets();
            }).bounds(left + 12 + i * tabWidth, top + 45, tabWidth - 3, 20).build();
            button.active = tab != i;
            addRenderableWidget(button);
        }
        int x = left + 16, y = top + 72, rowWidth = panelWidth - 32;
        if (tab == 0) {
            addRenderableWidget(Button.builder(qualityLabel(), button -> {
                int quality = (config.cinematicQuality + 1) % 3;
                config.cinematicQuality = config.ambientParticleQuality = config.dynamicLightQuality = config.ragdollPhysicsQuality = quality;
                button.setMessage(qualityLabel());
            }).bounds(x, y, rowWidth, 20).build());
            int column = (rowWidth - 6) / 2;
            toggle(x, y + 22, column, "Cinematics", () -> config.cinematicsEnabled, value -> config.cinematicsEnabled = value);
            toggle(x + column + 6, y + 22, column, "Dead Sun", () -> config.deadSunEnabled, value -> config.deadSunEnabled = value);
            toggle(x, y + 44, column, "Atmosphere", () -> config.dustyAirEnabled, value -> config.dustyAirEnabled = value);
            toggle(x + column + 6, y + 44, column, "Lighting", () -> config.dynamicLightsEnabled, value -> config.dynamicLightsEnabled = value);
        } else if (tab == 1) {
            addRenderableWidget(new AbstractSliderButton(x, y, rowWidth, 20, Component.empty(), config.musicVolumePercent / 100.0) {
                { updateMessage(); }
                @Override protected void updateMessage() { setMessage(Component.literal("Music volume: " + Math.round(value * 100) + "%")); }
                @Override protected void applyValue() { config.musicVolumePercent = (int)Math.round(value * 100); }
            });
        } else {
            toggle(x, y, rowWidth, "Objectives", () -> config.objectiveHudEnabled, value -> config.objectiveHudEnabled = value);
            addRenderableWidget(Button.builder(brightnessLabel(), button -> {
                int[] choices = {-1, 0, 25, 50, 75, 100}; int next = 0;
                for (int i = 0; i < choices.length; i++) if (choices[i] == config.brightnessPercent) next = (i + 1) % choices.length;
                config.brightnessPercent = choices[next]; button.setMessage(brightnessLabel());
            }).bounds(x, y + 22, rowWidth, 20).build());
            addRenderableWidget(Button.builder(recoveryLabel(), button -> {
                config.ragdollMashRecovery = !config.ragdollMashRecovery; button.setMessage(recoveryLabel());
            }).bounds(x, y + 44, rowWidth, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + panelWidth - 106, top + panelHeight - 29, 90, 20).build());
    }
    private Component qualityLabel() {
        return Component.literal("Quality: " + switch (AsterionConfig.INSTANCE.cinematicQuality) {
            case 0 -> "Low"; case 1 -> "Balanced"; default -> "High";
        });
    }
    private Component brightnessLabel() {
        int value = AsterionConfig.INSTANCE.brightnessPercent;
        return Component.literal("Brightness: " + (value < 0 ? "Minecraft setting" : value + "%"));
    }
    private Component recoveryLabel() {
        return Component.literal("Get up: " + (AsterionConfig.INSTANCE.ragdollMashRecovery ? "Tap repeatedly" : "Hold"));
    }
    private void toggle(int x, int y, int rowWidth, String label, BooleanSupplier value, Consumer<Boolean> setter) {
        addRenderableWidget(Button.builder(Component.literal(label + ": " + (value.getAsBoolean() ? "On" : "Off")), button -> {
            setter.accept(!value.getAsBoolean());
            button.setMessage(Component.literal(label + ": " + (value.getAsBoolean() ? "On" : "Off")));
        }).bounds(x, y, rowWidth, 20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        graphics.fill(0, 0, width, height, 0xB5090C10);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF0151C21);
        graphics.fill(left, top, left + panelWidth, top + 2, 0xFFD2B779);
        graphics.fill(left + 12, top + 69, left + panelWidth - 12, top + 70, 0xFF344149);
        graphics.text(font, "ASTERION", left + 16, top + 14, 0xFFEAD9AA, false);
        graphics.text(font, "Make the Labyrinth your own.", left + 16, top + 28, 0xFF9BAFB8, false);
        if (tab == 1) {
            graphics.text(font, "Biome and arena music", left + 16, top + 108, 0xFFC7D2D6, false);
            graphics.text(font, "Track titles and artists appear in game.", left + 16, top + 124, 0xFF9BAFB8, false);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partial);
    }
    @Override public void onClose() {
        AsterionConfig.INSTANCE.save(); AsterionEmissiveConfig.apply();
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
