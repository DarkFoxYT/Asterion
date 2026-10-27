package net.krodark.asterion.client;

import net.krodark.asterion.AsterionConfig;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;

/** Live controls for the Labyrinth's fog volume and ambient haze. */
public final class LabyrinthAtmosphereScreen extends Screen {
    private final Screen parent;

    public LabyrinthAtmosphereScreen(Screen parent) {
        super(Component.literal("Labyrinth atmosphere"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        AsterionConfig config = AsterionConfig.INSTANCE;
        int y = Math.max(20, height / 2 - 85);
        slider("Fog strength", y, config.fogStrength, 2.5,
                value -> config.fogStrength = (float)value);
        slider("Dust density", y + 24, config.dustDensity, 2.5,
                value -> config.dustDensity = (float)value);
        slider("Ambient haze", y + 48, config.labyrinthHazeStrength, 2,
                value -> config.labyrinthHazeStrength = (float)value);
        addRenderableWidget(Button.builder(Component.literal("Save and return"), button -> onClose())
                .bounds(width / 2 - 130, y + 80, 260, 20).build());
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

    @Override
    public void onClose() {
        AsterionConfig.INSTANCE.save();
        minecraft.setScreen(parent);
    }
}
