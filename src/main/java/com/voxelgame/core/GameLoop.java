package com.voxelgame.core;

import com.voxelgame.platform.Window;
import com.voxelgame.render.GLInit;

import static org.lwjgl.opengl.GL33.*;

/**
 * Fixed-timestep game loop. Drives update ticks and render frames.
 */
public class GameLoop {

    private Window window;
    private Time time;

    public void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        window = new Window(1280, 720, "VoxelGame");
        GLInit.init();
        GLInit.setViewport(window.getWidth(), window.getHeight());
        time = new Time();
        time.init();
        System.out.println("VoxelGame initialized successfully!");
    }

    private void loop() {
        while (!window.shouldClose()) {
            time.update();
            window.pollEvents();

            if (window.wasResized()) {
                GLInit.setViewport(window.getWidth(), window.getHeight());
            }

            // Clear screen
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            window.swapBuffers();
        }
    }

    private void cleanup() {
        window.destroy();
    }

    public Window getWindow() { return window; }
    public Time getTime() { return time; }
}
