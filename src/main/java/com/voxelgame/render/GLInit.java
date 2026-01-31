package com.voxelgame.render;

import org.lwjgl.opengl.GL;

import static org.lwjgl.opengl.GL33.*;

/** OpenGL initialization and capabilities setup. */
public class GLInit {

    public static void init() {
        GL.createCapabilities();

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);

        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glClearColor(0.53f, 0.81f, 0.98f, 1.0f); // Sky blue
    }

    public static void setViewport(int width, int height) {
        glViewport(0, 0, width, height);
    }
}
