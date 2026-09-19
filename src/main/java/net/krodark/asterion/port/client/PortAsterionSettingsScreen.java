package net.krodark.asterion.port.client;

import net.krodark.asterion.AsterionConfig;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Loader-neutral settings screen used by Mod Menu and NeoForge's Mods screen. */
public final class PortAsterionSettingsScreen extends Screen {
    private final Screen parent;

    public PortAsterionSettingsScreen(Screen parent) {
        super(Component.literal("Asterion Settings"));
        this.parent = parent;
    }

    @Override protected void init() {
        AsterionConfig config = AsterionConfig.INSTANCE;
        int left = width / 2 - 155, right = width / 2 + 5;
        int y = Math.max(12, (height - 270) / 2);
        addRenderableOnly(new StringWidget(width / 2 - 100, y - 13, 200, 12,
                Component.literal("Cinematics, atmosphere, audio and performance"), font));
        addToggle(left, y, "Cinematics", config.cinematicsEnabled, value -> config.cinematicsEnabled = value);
        addQuality(right, y, "Effects quality", config.cinematicQuality, value -> config.cinematicQuality = value);
        y += 21;
        addToggle(left, y, "Dead Sun shader", config.deadSunEnabled, value -> config.deadSunEnabled = value);
        addToggle(right, y, "Volumetric dust", config.dustyAirEnabled, value -> config.dustyAirEnabled = value);
        y += 21;
        addToggle(left, y, "Enhanced lightning", config.enhancedLightning, value -> config.enhancedLightning = value);
        addQuality(right, y, "Ambient particles", config.ambientParticleQuality, value -> config.ambientParticleQuality = value);
        y += 21;
        addToggle(left, y, "Adaptive performance", config.adaptivePerformance, value -> config.adaptivePerformance = value);
        addRenderableWidget(CycleButton.<Integer>builder(value -> Component.literal(value + " FPS"))
                .withInitialValue(config.performanceTargetFps).withValues(30, 45, 60, 90, 120, 165, 240, 360)
                .create(right, y, 150, 20, Component.literal("Performance target"),
                        (button, value) -> config.performanceTargetFps = value));
        y += 21;
        addToggle(left, y, "Dynamic lights", config.dynamicLightsEnabled, value -> config.dynamicLightsEnabled = value);
        addQuality(right, y, "Light quality", config.dynamicLightQuality, value -> config.dynamicLightQuality = value);
        y += 21;
        addToggle(left, y, "Dropped-item lights", config.droppedItemLights, value -> config.droppedItemLights = value);
        addRenderableWidget(CycleButton.<Integer>builder(value -> Component.literal(Integer.toString(value)))
                .withInitialValue(config.maxDynamicLights).withValues(16, 24, 48, 96)
                .create(right, y, 150, 20, Component.literal("Light limit"),
                        (button, value) -> config.maxDynamicLights = value));
        y += 21;
        addRenderableWidget(CycleButton.<Integer>builder(value -> Component.literal(value + "%"))
                .withInitialValue(config.dynamicLightRangePercent).withValues(25, 50, 75, 100)
                .create(left, y, 150, 20, Component.literal("Light range"),
                        (button, value) -> config.dynamicLightRangePercent = value));
        addRenderableWidget(CycleButton.<Integer>builder(value -> Component.literal(value == 0 ? "Always" : value + "s"))
                .withInitialValue(config.objectiveHudSeconds).withValues(0, 5, 12, 25, 60, 120)
                .create(right, y, 150, 20, Component.literal("Objective display"),
                        (button, value) -> config.objectiveHudSeconds = value));
        y += 21;
        addQuality(left, y, "Ragdoll physics", config.ragdollPhysicsQuality, value -> config.ragdollPhysicsQuality = value);
        addToggle(right, y, "Ragdoll equipment", config.ragdollEquipment, value -> config.ragdollEquipment = value);
        y += 21;
        addRenderableWidget(CycleButton.<Boolean>builder(value -> Component.literal(value ? "Mash" : "Hold"))
                .withInitialValue(config.ragdollMashRecovery).withValues(false, true)
                .create(left, y, 150, 20, Component.literal("Ragdoll recovery"),
                        (button, value) -> config.ragdollMashRecovery = value));
        addToggle(right, y, "Objectives", config.objectiveHudEnabled, value -> config.objectiveHudEnabled = value);
        y += 21;
        addToggle(left, y, "Particle culling", config.potatoParticleCulling, value -> config.potatoParticleCulling = value);
        addRenderableWidget(CycleButton.<Integer>builder(value -> Component.literal(
                        value < 0 ? "Vanilla" : value == 0 ? "Moody" : value + "%"))
                .withInitialValue(config.brightnessPercent)
                .withValues(-1, 0, 25, 50, 75, 100)
                .create(right, y, 150, 20, Component.literal("Brightness"),
                        (button, value) -> config.brightnessPercent = value));
        y += 21;
        addRenderableWidget(new AbstractSliderButton(left, y, 310, 20, Component.empty(),
                config.musicVolumePercent / 100.0D) {
            { updateMessage(); }
            @Override protected void updateMessage() {
                setMessage(Component.literal("Dimension and arena music: " + Math.round(value * 100) + "%"));
            }
            @Override protected void applyValue() { config.musicVolumePercent = (int)Math.round(value * 100); }
        });
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Performance presets"), button -> {
            AsterionConfig.INSTANCE.save();
        PortEmissiveConfig.apply();
            if (minecraft != null) minecraft.setScreen(new PortPerformancePresetsScreen(this));
        }).bounds(left, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save and return"), button -> closeAndSave())
                .bounds(right, y, 150, 20).build());
    }

    private void addToggle(int x, int y, String label, boolean initial, java.util.function.Consumer<Boolean> setter) {
        addRenderableWidget(CycleButton.onOffBuilder(initial).create(x, y, 150, 20, Component.literal(label),
                (button, value) -> setter.accept(value)));
    }

    private void addQuality(int x, int y, String label, int initial, java.util.function.IntConsumer setter) {
        addRenderableWidget(CycleButton.<Integer>builder(value -> Component.literal(switch (value) {
                    case 0 -> "Low"; case 1 -> "Medium"; default -> "High";
                })).withInitialValue(initial).withValues(0, 1, 2).create(x, y, 150, 20, Component.literal(label),
                (button, value) -> setter.accept(value)));
    }

    private void closeAndSave() {
        AsterionConfig.INSTANCE.save();
        PortEmissiveConfig.apply();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override public void onClose() { closeAndSave(); }
}
