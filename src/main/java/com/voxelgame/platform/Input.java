package com.voxelgame.platform;

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

    public static boolean isKeyDown(int key) {
        return key >= 0 && key < keys.length && keys[key];
    }

    /** Returns true only on the frame the key was first pressed. */
    public static boolean isKeyPressed(int key) {
        return key >= 0 && key < keys.length && keysPressed[key];
    }

    public static double getMouseDX() { return mouseDX; }
    public static double getMouseDY() { return mouseDY; }

    public static boolean isLeftMouseClicked() { return leftMouseClicked; }
    public static boolean isRightMouseClicked() { return rightMouseClicked; }
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
    }
}
