package net.krodark.asterion.client.render;

//? if >=26.3 {
/*import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

// Use Minecraft's SDL input API; a window handle is never passed to GLFW.
public final class NativeInput {
    public static final int GLFW_PRESS = InputConstants.PRESS;
    public static final int GLFW_KEY_SPACE = InputConstants.KEY_SPACE;
    public static final int GLFW_KEY_A = InputConstants.KEY_A;
    public static final int GLFW_KEY_D = InputConstants.KEY_D;
    public static final int GLFW_KEY_S = InputConstants.KEY_S;
    public static final int GLFW_KEY_W = InputConstants.KEY_W;
    public static final int GLFW_KEY_F3 = InputConstants.KEY_F3;
    public static final int GLFW_KEY_J = InputConstants.KEY_J;
    public static final int GLFW_MOUSE_BUTTON_RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;
    public static int glfwGetKey(long unusedWindow, int key) { return InputConstants.isKeyDown(key) ? GLFW_PRESS : 0; }
    public static int glfwGetMouseButton(long unusedWindow, int button) {
        var mouse = Minecraft.getInstance().mouseHandler;
        boolean pressed = button == InputConstants.MOUSE_BUTTON_RIGHT ? mouse.isRightPressed()
            : button == InputConstants.MOUSE_BUTTON_LEFT ? mouse.isLeftPressed() : mouse.isMiddlePressed();
        return pressed ? GLFW_PRESS : 0;
    }
}
*///?}
