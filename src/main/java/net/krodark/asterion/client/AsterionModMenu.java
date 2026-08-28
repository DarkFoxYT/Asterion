package net.krodark.asterion.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.krodark.asterion.AsterionConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Small dependency-light Mod Menu surface for the settings that materially affect frame cost. */
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
            int y = Math.max(42, height / 2 - 108);
            addRenderableOnly(new StringWidget(width / 2 - 100, y - 26, 200, 20,
                    Component.literal("Cinematics and performance"), font));
            addRenderableWidget(CycleButton.onOffBuilder(config.cinematicsEnabled).create(
                    left, y, 150, 20, Component.literal("Cinematics"),
                    (button, value) -> config.cinematicsEnabled = value));
            addRenderableWidget(CycleButton.<Integer>builder(AsterionModMenu::qualityName,
                            config.cinematicQuality).withValues(0, 1, 2).create(
                    right, y, 150, 20, Component.literal("Effects quality"),
                    (button, value) -> config.cinematicQuality = value));
            y += 24;
            addRenderableWidget(CycleButton.<Integer>builder(AsterionModMenu::qualityName,
                            config.dynamicLightQuality).withValues(0, 1, 2).create(
                    left, y, 150, 20, Component.literal("Dynamic lights"),
                    (button, value) -> config.dynamicLightQuality = value));
            addRenderableWidget(CycleButton.onOffBuilder(config.enhancedLightning).create(
                    right, y, 150, 20, Component.literal("Enhanced lightning"),
                    (button, value) -> config.enhancedLightning = value));
            y += 24;
            addRenderableWidget(CycleButton.onOffBuilder(config.ragdollEquipment).create(
                    left, y, 150, 20, Component.literal("Ragdoll equipment"),
                    (button, value) -> config.ragdollEquipment = value));
            addRenderableWidget(CycleButton.onOffBuilder(config.deadSunEnabled).create(
                    right, y, 150, 20, Component.literal("Dead Sun shader"),
                    (button, value) -> config.deadSunEnabled = value));
            y += 38;
            addRenderableWidget(Button.builder(Component.literal("Save and return"), button -> {
                config.save();
                minecraft.setScreen(parent);
            }).bounds(width / 2 - 100, y, 200, 20).build());
        }

        @Override
        public void onClose() {
            AsterionConfig.INSTANCE.save();
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
}
