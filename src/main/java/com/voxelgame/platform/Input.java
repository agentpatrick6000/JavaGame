package com.voxelgame.platform;

import com.voxelgame.input.AutomationController;

import static org.lwjgl.glfw.GLFW.*;

/** Keyboard and mouse input handling. */
public class Input {

    private static final boolean[] keys = new boolean[512];
    private static final boolean[] keysPressed = new boolean[512]; // single-frame press
    private static double mouseX, mouseY;
    private static double mouseDX, mouseDY;
    private static boolean firstMouse = true;
    private static boolean cursorLocked = false;
    private static long windowHandle;

    // Mouse buttons
    private static boolean leftMouseDown = false;
    private static boolean rightMouseDown = false;
    private static boolean leftMouseClicked = false;
    private static boolean rightMouseClicked = false;

    // Scroll wheel
    private static double scrollDX = 0;
    private static double scrollDY = 0;

    // Automation overlay
    private static AutomationController automationController = null;

    public static void init(long handle) {
        windowHandle = handle;

        glfwSetKeyCallback(handle, (window, key, scancode, action, mods) -> {
            if (key >= 0 && key < keys.length) {
                if (action == GLFW_PRESS) {
                    keys[key] = true;
                    keysPressed[key] = true;
                } else if (action == GLFW_RELEASE) {
                    keys[key] = false;
                }
            }
        });

        glfwSetCursorPosCallback(handle, (window, xpos, ypos) -> {
            if (firstMouse) {
                mouseX = xpos;
                mouseY = ypos;
                firstMouse = false;
            }
            mouseDX += xpos - mouseX;
            mouseDY += ypos - mouseY;
            mouseX = xpos;
            mouseY = ypos;
        });

        glfwSetScrollCallback(handle, (window, xoffset, yoffset) -> {
            scrollDX += xoffset;
            scrollDY += yoffset;
        });

        glfwSetMouseButtonCallback(handle, (window, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW_PRESS) {
                    leftMouseDown = true;
                    leftMouseClicked = true;
                } else if (action == GLFW_RELEASE) {
                    leftMouseDown = false;
                }
            } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                if (action == GLFW_PRESS) {
                    rightMouseDown = true;
                    rightMouseClicked = true;
                } else if (action == GLFW_RELEASE) {
                    rightMouseDown = false;
                }
            }
        });
    }

    public static void lockCursor() {
        glfwSetInputMode(windowHandle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        cursorLocked = true;
        firstMouse = true;
    }

    public static void unlockCursor() {
        glfwSetInputMode(windowHandle, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        cursorLocked = false;
    }

    public static boolean isCursorLocked() { return cursorLocked; }

    // ---- Automation integration ----

    /** Set the automation controller (null to disable). */
    public static void setAutomationController(AutomationController controller) {
        automationController = controller;
    }

    /** Get the automation controller (may be null). */
    public static AutomationController getAutomationController() {
        return automationController;
    }

    public static boolean isKeyDown(int key) {
        boolean real = key >= 0 && key < keys.length && keys[key];
        if (real) return true;
        // Check automation overlay
        return automationController != null && automationController.isKeyDown(key);
    }

    /** Returns true only on the frame the key was first pressed. */
    public static boolean isKeyPressed(int key) {
        boolean real = key >= 0 && key < keys.length && keysPressed[key];
        if (real) return true;
        // Check automation overlay
        return automationController != null && automationController.isKeyPressed(key);
    }

    public static double getMouseDX() {
        double dx = mouseDX;
        if (automationController != null) dx += automationController.consumeMouseDX();
        return dx;
    }

    public static double getMouseDY() {
        double dy = mouseDY;
        if (automationController != null) dy += automationController.consumeMouseDY();
        return dy;
    }

    public static boolean isLeftMouseClicked() {
        boolean real = leftMouseClicked;
        if (real) return true;
        return automationController != null && automationController.consumeLeftMouseClick();
    }

    public static boolean isRightMouseClicked() {
        boolean real = rightMouseClicked;
        if (real) return true;
        return automationController != null && automationController.consumeRightMouseClick();
    }

    public static boolean isLeftMouseDown() { return leftMouseDown; }
    public static boolean isRightMouseDown() { return rightMouseDown; }

    public static double getScrollDX() { return scrollDX; }
    public static double getScrollDY() { return scrollDY; }

    public static void endFrame() {
        mouseDX = 0;
        mouseDY = 0;
        scrollDX = 0;
        scrollDY = 0;
        leftMouseClicked = false;
        rightMouseClicked = false;
        java.util.Arrays.fill(keysPressed, false);
        // Clear automation single-frame states
        if (automationController != null) {
            automationController.endFrame();
        }
    }
}
