package com.voxelgame.platform;

import org.lwjgl.glfw.*;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/** GLFW window creation and management. */
public class Window {

    private long handle;
    private int width;
    private int height;
    private boolean resized;

    public Window(int width, int height, String title) {
        this.width = width;
        this.height = height;

        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        handle = glfwCreateWindow(width, height, title, NULL, NULL);
        if (handle == NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }

        glfwSetFramebufferSizeCallback(handle, (window, w, h) -> {
            this.width = w;
            this.height = h;
            this.resized = true;
        });

        // Center window
        GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vidMode != null) {
            glfwSetWindowPos(handle,
                (vidMode.width() - width) / 2,
                (vidMode.height() - height) / 2);
        }

        glfwMakeContextCurrent(handle);
        glfwSwapInterval(1); // VSync
        glfwShowWindow(handle);
    }

    public void pollEvents() {
        glfwPollEvents();
    }

    public void swapBuffers() {
        glfwSwapBuffers(handle);
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(handle);
    }

    public boolean wasResized() {
        if (resized) {
            resized = false;
            return true;
        }
        return false;
    }

    public void destroy() {
        glfwDestroyWindow(handle);
        glfwTerminate();
        GLFWErrorCallback cb = glfwSetErrorCallback(null);
        if (cb != null) cb.free();
    }

    public long getHandle() { return handle; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
