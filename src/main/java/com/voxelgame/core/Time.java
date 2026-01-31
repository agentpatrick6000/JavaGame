package com.voxelgame.core;

import static org.lwjgl.glfw.GLFW.glfwGetTime;

/**
 * Tracks delta time, total elapsed time, and tick count.
 */
public class Time {

    private double lastTime;
    private float deltaTime;
    private float totalTime;
    private int frameCount;
    private float fpsTimer;
    private int fps;

    public void init() {
        lastTime = glfwGetTime();
    }

    public void update() {
        double now = glfwGetTime();
        deltaTime = (float)(now - lastTime);
        if (deltaTime > 0.1f) deltaTime = 0.1f; // Cap at 100ms
        lastTime = now;
        totalTime += deltaTime;
        frameCount++;
        fpsTimer += deltaTime;
        if (fpsTimer >= 1.0f) {
            fps = frameCount;
            frameCount = 0;
            fpsTimer -= 1.0f;
        }
    }

    public float getDeltaTime() { return deltaTime; }
    public float getTotalTime() { return totalTime; }
    public int getFps() { return fps; }
}
