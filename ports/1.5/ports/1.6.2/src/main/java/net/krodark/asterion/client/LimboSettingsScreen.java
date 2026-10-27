package net.krodark.asterion.client;

import net.krodark.asterion.AsterionConfig;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.DoubleConsumer;

/** Live visual controls; world generation remains seed-driven. */
public final class LimboSettingsScreen extends Screen {
    private final Screen parent;
    public LimboSettingsScreen(Screen parent) {
        super(Component.literal("Limbo atmosphere"));
        this.parent = parent;
    }
    @Override protected void init() {
        var config = AsterionConfig.INSTANCE;
        int y = Math.max(20, height / 2 - 60);
        slider("Distance haze", y, config.limboFogStrength, 2, v -> config.limboFogStrength = (float)v);
        slider("Low mist", y + 24, config.limboMistStrength, 2, v -> config.limboMistStrength = (float)v);
        addRenderableWidget(Button.builder(Component.literal("Save and return"), b -> onClose())
                .bounds(width / 2 - 130, y + 56, 260, 20).build());
    }
    private void slider(String label, int y, double initial, double maximum, DoubleConsumer setter) {
        addRenderableWidget(new AbstractSliderButton(width / 2 - 130, y, 260, 20,
                Component.empty(), Math.clamp(initial / maximum, 0, 1)) {
            { updateMessage(); }
            @Override protected void updateMessage() {
                setMessage(Component.literal(label + ": " + Math.round(value * maximum * 100) + "%"));
            }
            @Override protected void applyValue() { setter.accept(value * maximum); }
        });
    }
    @Override public void onClose() {
        AsterionConfig.INSTANCE.save();
        minecraft.setScreen(parent);
    }
}
