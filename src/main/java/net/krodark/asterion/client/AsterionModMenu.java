package net.krodark.asterion.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.client.light.AsterionEmissiveConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AsterionModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return AsterionSettingsScreen::new;
    }

    private static final class AsterionSettingsScreen extends Screen {
        private final Screen parent;

        private AsterionSettingsScreen(Screen parent) {
            super(Component.literal("Asterion Settings"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            AsterionConfig config = AsterionConfig.INSTANCE;
            int left = width / 2 - 155;
            int right = width / 2 + 5;
            int y = Math.max(20, (height - 211) / 2);
            addRenderableOnly(new StringWidget(width / 2 - 100, y - 20, 200, 20,
                    Component.literal("Cinematics and performance"), font));
            addRenderableWidget(CycleButton.onOffBuilder(config.cinematicsEnabled).create(
                    left, y, 150, 20, Component.literal("Cinematics"),
                    (button, value) -> config.cinematicsEnabled = value));
            addRenderableWidget(CycleButton.<Integer>builder(AsterionModMenu::qualityName,
                            config.cinematicQuality).withValues(0, 1, 2).create(
                    right, y, 150, 20, Component.literal("Effects quality"),
                    (button, value) -> config.cinematicQuality = value));
            y += 21;
            addRenderableWidget(CycleButton.onOffBuilder(config.dynamicLightsEnabled).create(
                    left, y, 150, 20, Component.literal("Dynamic lights"),
                    (button, value) -> config.dynamicLightsEnabled = value));
            addRenderableWidget(CycleButton.<Integer>builder(AsterionModMenu::qualityName,
                            config.dynamicLightQuality).withValues(0, 1, 2).create(
                    right, y, 150, 20, Component.literal("Light quality"),
                    (button, value) -> config.dynamicLightQuality = value));
            y += 21;
            addRenderableWidget(CycleButton.onOffBuilder(config.droppedItemLights).create(
                    left, y, 150, 20, Component.literal("Dropped-item lights"),
                    (button, value) -> config.droppedItemLights = value));
            addRenderableWidget(CycleButton.<Integer>builder(AsterionModMenu::lightLimitName,
                            config.maxDynamicLights).withValues(16, 24, 48, 96).create(
                    right, y, 150, 20, Component.literal("Light limit"),
                    (button, value) -> config.maxDynamicLights = value));
            y += 21;
            addRenderableWidget(CycleButton.<Integer>builder(AsterionModMenu::percentName,
                            config.dynamicLightRangePercent).withValues(25, 50, 75, 100).create(
                    left, y, 150, 20, Component.literal("Light range"),
                    (button, value) -> config.dynamicLightRangePercent = value));
            addRenderableWidget(CycleButton.<Integer>builder(AsterionModMenu::qualityName,
                            config.ambientParticleQuality).withValues(0, 1, 2).create(
                    right, y, 150, 20, Component.literal("Ambient particles"),
                    (button, value) -> config.ambientParticleQuality = value));
            y += 21;
            addRenderableWidget(CycleButton.onOffBuilder(config.enhancedLightning).create(
                    left, y, 150, 20, Component.literal("Enhanced lightning"),
                    (button, value) -> config.enhancedLightning = value));
            addRenderableWidget(CycleButton.<Integer>builder(AsterionModMenu::qualityName,
                            config.ragdollPhysicsQuality).withValues(0, 1, 2).create(
                    right, y, 150, 20, Component.literal("Ragdoll physics"),
                    (button, value) -> config.ragdollPhysicsQuality = value));
            y += 21;
            addRenderableWidget(CycleButton.onOffBuilder(config.ragdollEquipment).create(
                    left, y, 150, 20, Component.literal("Ragdoll equipment"),
                    (button, value) -> config.ragdollEquipment = value));
            addRenderableWidget(CycleButton.<Boolean>builder(value -> Component.literal(
                            value ? "Mash" : "Hold"), config.ragdollMashRecovery)
                    .withValues(false, true).create(right, y, 150, 20,
                            Component.literal("Ragdoll recovery"),
                            (button, value) -> config.ragdollMashRecovery = value));
            y += 21;
            addRenderableWidget(CycleButton.onOffBuilder(config.deadSunEnabled).create(
                    left, y, 150, 20, Component.literal("Dead Sun shader"),
                    (button, value) -> config.deadSunEnabled = value));
            addRenderableWidget(CycleButton.onOffBuilder(config.dustyAirEnabled).create(
                    right, y, 150, 20, Component.literal("Dusty-air shader"),
                    (button, value) -> config.dustyAirEnabled = value));
            y += 21;
            addRenderableWidget(CycleButton.onOffBuilder(config.potatoParticleCulling).create(
                    left, y, 310, 20, Component.literal("Potato particle culling"),
                    (button, value) -> config.potatoParticleCulling = value));
            y += 24;
            addRenderableWidget(Button.builder(Component.literal("Save and return"), button -> {
                config.save();
                AsterionEmissiveConfig.apply();
                minecraft.setScreen(parent);
            }).bounds(width / 2 - 100, y, 200, 20).build());
        }

        @Override
        public void onClose() {
            AsterionConfig.INSTANCE.save();
            AsterionEmissiveConfig.apply();
            minecraft.setScreen(parent);
        }
    }

    private static Component qualityName(Integer quality) {
        return Component.literal(switch (quality) {
            case 0 -> "Low";
            case 1 -> "Medium";
            default -> "High";
        });
    }

    private static Component percentName(Integer percent) {
        return Component.literal(percent + "%");
    }

    private static Component lightLimitName(Integer limit) {
        return Component.literal(Integer.toString(limit));
    }
}
