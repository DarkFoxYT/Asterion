package net.krodark.asterion.test;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.krodark.asterion.update.UpdateMenuButton;
import net.krodark.asterion.update.UpdateNotifications;
import net.krodark.asterion.update.UpdateScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import java.nio.file.Path;

final class UpdateMenuRegression {
    private int stage, ticks;
    private final long deadline = System.nanoTime() + 180_000_000_000L;

    static void register() { ClientTickEvents.END_CLIENT_TICK.register(new UpdateMenuRegression()::tick); }

    private static Button button(Minecraft client, String label) {
        return client.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }

    private static void press(Button button) { button.onPress(new KeyEvent(257, 0, 0)); }

    private static void screenshot(Minecraft client, String name) {
        Path directory = Path.of(System.getProperty("asterion.updateProofDir"));
        try { java.nio.file.Files.createDirectories(directory.resolve("screenshots")); }
        catch (java.io.IOException error) { throw new AssertionError(error); }
        Screenshot.grab(directory.toFile(), name,
                client.getMainRenderTarget(), 1, message -> {});
    }

    private void tick(Minecraft client) {
        if (System.nanoTime() > deadline) throw new AssertionError("Update menu test timed out");
        if (client.getOverlay() != null || client.screen == null) return;
        if (++ticks < (stage == 0 ? 100 : 20)) return;
        ticks = 0;
        if (stage == 0) {
            if (!(client.screen instanceof TitleScreen)) return;
            try {
                var available = UpdateNotifications.class.getDeclaredField("available");
                available.setAccessible(true);
                available.set(null, new UpdateNotifications.Notice("1.6.999", "1.6.8", "26.1.2",
                        System.getProperty("asterion.updateSmokeLoader", "fabric"), UpdateNotifications.DOWNLOADS));
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        } else if (stage == 1) {
            if (!button(client, "View Asterion update").visible) throw new AssertionError("Banner hidden");
            screenshot(client, "asterion-update-main-menu.png");
            press(button(client, "View Asterion update"));
        } else if (stage == 2) {
            if (!(client.screen instanceof UpdateScreen)) throw new AssertionError("Update screen did not open");
            if (!button(client, "Open CurseForge").active) throw new AssertionError("Download button disabled");
            screenshot(client, "asterion-update-details.png");
            press(button(client, "Later"));
        } else if (stage == 3) {
            if (!(client.screen instanceof TitleScreen)) throw new AssertionError("Later did not return to menu");
            client.screen.resize(320, 240);
            for (var child : client.screen.children()) if (child instanceof UpdateMenuButton b) {
                if (b.getX() < 0 || b.getX() + b.getWidth() > 320 || b.getY() + b.getHeight() > 30)
                    throw new AssertionError("Update menu overlaps the logo or leaves a small screen");
            }
            client.screen.resize(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
            press(button(client, "View Asterion update"));
        } else if (stage == 4) {
            press(button(client, "Skip this version"));
        } else if (stage == 5) {
            if (UpdateNotifications.available() != null || button(client, "View Asterion update").visible)
                throw new AssertionError("Dismissed update remains visible");
            net.krodark.asterion.Asterion.LOGGER.info("ASTERION_UPDATE_MENU PASS: banner, details, download button, later, resize and dismissal");
            client.stop();
        }
        stage++;
    }
}
