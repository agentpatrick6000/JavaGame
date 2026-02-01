package com.voxelgame.platform;

import org.lwjgl.glfw.*;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/** GLFW window creation and management. */
public class Window {

    private long handle;
    private int width;              // Window size in logical (screen) pixels
    private int height;
    private int framebufferWidth;   // Framebuffer size in physical pixels (may differ on HiDPI)
    private int framebufferHeight;
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

        // Track framebuffer size (for glViewport — physical pixels)
        glfwSetFramebufferSizeCallback(handle, (window, w, h) -> {
            this.framebufferWidth = w;
            this.framebufferHeight = h;
            this.resized = true;
        });

        // Track window size (for UI/mouse coordinates — logical pixels)
        glfwSetWindowSizeCallback(handle, (window, w, h) -> {
            this.width = w;
            this.height = h;
        });

        // Initialize framebuffer size (may differ from window size on HiDPI/Retina)
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pW = stack.mallocInt(1);
            IntBuffer pH = stack.mallocInt(1);
            glfwGetFramebufferSize(handle, pW, pH);
            this.framebufferWidth = pW.get(0);
            this.framebufferHeight = pH.get(0);
        }

        // Center window (monitor can be null in some launch contexts)
        long monitor = glfwGetPrimaryMonitor();
        if (monitor != NULL) {
            GLFWVidMode vidMode = glfwGetVideoMode(monitor);
            if (vidMode != null) {
                glfwSetWindowPos(handle,
                    (vidMode.width() - width) / 2,
                    (vidMode.height() - height) / 2);
            }
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

    public void requestClose() {
        glfwSetWindowShouldClose(handle, true);
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
    /** Window width in logical (screen) pixels — matches GLFW mouse coordinates. */
    public int getWidth() { return width; }
    /** Window height in logical (screen) pixels — matches GLFW mouse coordinates. */
    public int getHeight() { return height; }
    /** Framebuffer width in physical pixels — use for glViewport. */
    public int getFramebufferWidth() { return framebufferWidth; }
    /** Framebuffer height in physical pixels — use for glViewport. */
    public int getFramebufferHeight() { return framebufferHeight; }
}
