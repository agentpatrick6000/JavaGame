package com.voxelgame.render;

import com.voxelgame.world.WorldTime;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * Renders an atmospheric sky gradient behind the world.
 * 
 * Features:
 * - Vertical gradient from zenith (darker) to horizon (lighter/hazier)
 * - Color changes with time of day (blue day, orange sunset, dark night)
 * - Renders as fullscreen quad at maximum depth
 */
public class SkyRenderer {

    private Shader skyShader;
    private int vao, vbo;
    private boolean initialized = false;

    // Sky colors (updated each frame from world time)
    private final Vector3f zenithColor = new Vector3f(0.3f, 0.5f, 0.9f);   // Top of sky
    private final Vector3f horizonColor = new Vector3f(0.7f, 0.85f, 1.0f); // Horizon

    public void init() {
        // Create sky shader
        skyShader = new Shader("shaders/sky.vert", "shaders/sky.frag");
        
        // Create fullscreen quad (using clip space coordinates)
        // Vertices: position (x, y) + texcoord (u, v) where v = vertical position
        float[] vertices = {
            // Triangle 1
            -1.0f,  1.0f,  0.0f, 1.0f,  // top-left (v=1 = top of sky)
            -1.0f, -1.0f,  0.0f, 0.0f,  // bottom-left (v=0 = horizon)
             1.0f, -1.0f,  1.0f, 0.0f,  // bottom-right
            // Triangle 2
            -1.0f,  1.0f,  0.0f, 1.0f,  // top-left
             1.0f, -1.0f,  1.0f, 0.0f,  // bottom-right
             1.0f,  1.0f,  1.0f, 1.0f   // top-right
        };

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        
        FloatBuffer buffer = MemoryUtil.memAllocFloat(vertices.length);
        buffer.put(vertices).flip();
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
        MemoryUtil.memFree(buffer);

        // Position attribute (location 0)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        // Texcoord attribute (location 1) - using v component for vertical position
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2L * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindVertexArray(0);
        initialized = true;
    }

    /**
     * Update sky colors based on world time.
     */
    public void updateColors(WorldTime worldTime) {
        if (worldTime == null) return;
        
        float sunBrightness = worldTime.getSunBrightness();
        float[] skyColor = worldTime.getSkyColor();
        
        // Zenith color (top of sky) - slightly darker than base sky color
        zenithColor.set(
            skyColor[0] * 0.6f,
            skyColor[1] * 0.6f,
            skyColor[2] * 0.85f  // Keep blue tint stronger at zenith
        );
        
        // Horizon color - brighter, more washed out (atmospheric scattering)
        horizonColor.set(
            Math.min(1.0f, skyColor[0] * 1.3f + 0.1f),
            Math.min(1.0f, skyColor[1] * 1.2f + 0.1f),
            Math.min(1.0f, skyColor[2] * 1.1f + 0.1f)
        );
        
        // Special case: sunset/sunrise (warm horizon)
        if (sunBrightness > 0.1f && sunBrightness < 0.4f) {
            // Transitioning between day/night - add warm tones to horizon
            float warmth = 1.0f - Math.abs(sunBrightness - 0.25f) * 4.0f;
            warmth = Math.max(0, warmth);
            horizonColor.set(
                Math.min(1.0f, horizonColor.x + warmth * 0.3f),
                horizonColor.y * (1.0f - warmth * 0.2f),
                horizonColor.z * (1.0f - warmth * 0.4f)
            );
        }
        
        // Night: darker zenith, subtle blue-gray horizon
        if (sunBrightness < 0.1f) {
            zenithColor.set(0.01f, 0.01f, 0.03f);
            horizonColor.set(0.04f, 0.05f, 0.08f);
        }
    }

    /**
     * Render the sky background.
     * Must be called BEFORE rendering the world (renders at far depth).
     */
    public void render() {
        if (!initialized) return;
        
        // Disable depth write but keep test (sky should be at infinity)
        glDepthMask(false);
        glDepthFunc(GL_LEQUAL); // Pass if depth <= 1.0 (maximum depth)
        
        skyShader.bind();
        skyShader.setVec3("uZenithColor", zenithColor);
        skyShader.setVec3("uHorizonColor", horizonColor);
        
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);
        
        skyShader.unbind();
        
        // Restore state
        glDepthMask(true);
        glDepthFunc(GL_LESS);
    }

    /**
     * Get the current horizon color (used for fog color matching).
     */
    public Vector3f getHorizonColor() {
        return horizonColor;
    }

    public void cleanup() {
        if (skyShader != null) skyShader.cleanup();
        if (vao != 0) glDeleteVertexArrays(vao);
        if (vbo != 0) glDeleteBuffers(vbo);
    }

    public boolean isInitialized() {
        return initialized;
    }
}
