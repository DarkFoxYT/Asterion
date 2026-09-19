package net.krodark.asterion.port.client;

import net.krodark.asterion.AsterionConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Complete 1.21.1 performance controls shared by Mod Menu and NeoForge. */
final class PortPerformancePresetsScreen extends Screen {
    private final Screen parent;

    PortPerformancePresetsScreen(Screen parent) {
        super(Component.literal("Performance presets"));
        this.parent = parent;
    }

    @Override protected void init() {
        int x = width / 2 - 155;
        int y = Math.max(24, (height - 204) / 2);
        addRenderableOnly(new StringWidget(x, y - 22, 310, 20, title, font));
        String[] names = {"Laptop", "Balanced", "High quality"};
        String[] descriptions = {
                "Low bloom, 24-block decorations, 16 lights, simplified sky and dust, and reduced particles.",
                "Medium effects and physics, 48 lights and sky shaders. Targets 60 FPS.",
                "High effects and physics, 96 lights and all visual effects. Targets 60 FPS."
        };
        for (int i = 0; i < names.length; i++) {
            int quality = i;
            addRenderableWidget(Button.builder(Component.literal(names[i]), button -> {
                applyPreset(quality);
                onClose();
            }).bounds(x, y + i * 24, 310, 20)
                    .tooltip(Tooltip.create(Component.literal(descriptions[i]))).build());
        }
        AsterionConfig config = AsterionConfig.INSTANCE;
        addRenderableWidget(CycleButton.<Integer>builder(value -> Component.literal(value.toString()))
                .withInitialValue(config.maxEmissiveParticles)
                .withValues(256, 512, 1024, 2048, 4096, 8192, 16384)
                .create(x, y + 78, 310, 20, Component.literal("Emissive particle limit"),
                        (button, value) -> config.maxEmissiveParticles = value));
        addRenderableWidget(CycleButton.<Integer>builder(value -> Component.literal(value + " blocks"))
                .withInitialValue(config.emissiveParticleDistance).withValues(32, 48, 64, 96, 128, 256)
                .create(x, y + 102, 310, 20, Component.literal("Emissive particle distance"),
                        (button, value) -> config.emissiveParticleDistance = value));
        addRenderableWidget(CycleButton.<Integer>builder(value -> Component.literal(switch (value) {
                    case -1 -> "Effects quality"; case 0 -> "Off"; case 1 -> "Low";
                    case 2 -> "Medium"; default -> "High";
                })).withInitialValue(config.bloomQuality).withValues(-1, 0, 1, 2, 3)
                .create(x, y + 126, 150, 20, Component.literal("Bloom"),
                        (button, value) -> config.bloomQuality = value));
        addRenderableWidget(CycleButton.<Integer>builder(value -> Component.literal(value + " blocks"))
                .withInitialValue(config.decorationRenderDistance).withValues(16, 24, 32, 48, 64, 96, 128)
                .create(x + 160, y + 126, 150, 20, Component.literal("Decorations"),
                        (button, value) -> config.decorationRenderDistance = value));
        var relighting = addRenderableWidget(CycleButton.onOffBuilder(config.blockLightUpdates)
                .create(x, y + 150, 310, 20, Component.literal("Held-item block relighting"),
                        (button, value) -> config.blockLightUpdates = value));
        relighting.setTooltip(Tooltip.create(Component.literal(
                "Updates world light near held and dropped emissive items; disable for slower systems.")));
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(x, y + 174, 310, 20).build());
    }

    private static void applyPreset(int quality) {
        AsterionConfig config = AsterionConfig.INSTANCE;
        config.cinematicQuality = quality;
        config.bloomQuality = quality + 1;
        config.decorationRenderDistance = quality == 0 ? 24 : quality == 1 ? 48 : 64;
        config.blockLightUpdates = quality > 0;
        config.ambientParticleQuality = quality;
        config.ragdollPhysicsQuality = quality;
        config.dynamicLightQuality = quality;
        config.dynamicLightsEnabled = true;
        config.droppedItemLights = quality > 0;
        config.maxDynamicLights = quality == 0 ? 16 : quality == 1 ? 48 : 96;
        config.dynamicLightRangePercent = quality == 0 ? 50 : quality == 1 ? 75 : 100;
        config.ragdollEquipment = quality > 0;
        config.enhancedLightning = quality > 0;
        config.deadSunEnabled = true;
        config.dustyAirEnabled = true;
        config.maxEmissiveParticles = quality == 0 ? 512 : quality == 1 ? 2048 : 8192;
        config.emissiveParticleDistance = quality == 0 ? 48 : quality == 1 ? 96 : 128;
        config.adaptivePerformance = true;
        config.performanceTargetFps = quality == 0 ? 30 : 60;
        config.potatoParticleCulling = false;
    }

    @Override public void onClose() {
        AsterionConfig.INSTANCE.save();
        PortEmissiveConfig.apply();
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
