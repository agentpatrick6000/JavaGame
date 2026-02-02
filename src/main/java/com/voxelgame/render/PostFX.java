package com.voxelgame.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * Post-processing pipeline for advanced rendering effects.
 * Renders the scene to an FBO, then applies screen-space effects:
 *   1. SSAO (screen-space ambient occlusion)
 *   2. SSAO blur
 *   3. Final composite with tone mapping
 *
 * This is what puts us BEYOND Minecraft — MC has none of these.
 *
 * Pipeline:
 *   Scene → sceneFBO (color + depth + normal/position)
 *   sceneFBO.depth → SSAO pass → ssaoFBO (single-channel occlusion)
 *   ssaoFBO → Blur pass → ssaoBlurFBO (blurred occlusion)
 *   sceneFBO.color + ssaoBlurFBO → Composite + tone mapping → screen
 */
public class PostFX {

    // Scene FBO (renders world into this instead of default framebuffer)
    private int sceneFBO;
    private int sceneColorTex;   // RGB16F color attachment
    private int sceneDepthTex;   // depth texture (for SSAO)
    private int sceneNormalTex;  // RGB16F view-space normals (for SSAO)
    private int sceneDepthRBO;   // depth renderbuffer

    // SSAO FBO
    private int ssaoFBO;
    private int ssaoColorTex;    // single-channel occlusion factor

    // SSAO Blur FBO
    private int ssaoBlurFBO;
    private int ssaoBlurTex;     // blurred occlusion

    // Shaders
    private Shader ssaoShader;
    private Shader ssaoBlurShader;
    private Shader compositeShader;

    // SSAO kernel
    private static final int SSAO_KERNEL_SIZE = 32;
    private Vector3f[] ssaoKernel;
    private int noiseTexture;

    // Fullscreen quad VAO
    private int quadVAO;
    private int quadVBO;

    // Current dimensions
    private int width, height;
    private boolean initialized = false;

    // SSAO enabled toggle
    private boolean ssaoEnabled = true;

    public void init(int width, int height) {
        this.width = width;
        this.height = height;

        // Create fullscreen quad
        createQuad();

        // Create FBOs
        createSceneFBO(width, height);
        createSSAOFBOs(width, height);

        // Load shaders
        ssaoShader = new Shader("shaders/ssao.vert", "shaders/ssao.frag");
        ssaoBlurShader = new Shader("shaders/ssao_blur.vert", "shaders/ssao_blur.frag");
        compositeShader = new Shader("shaders/composite.vert", "shaders/composite.frag");

        // Generate SSAO kernel (hemisphere samples)
        generateSSAOKernel();

        // Generate noise texture (random rotation vectors)
        generateNoiseTexture();

        initialized = true;
    }

    /**
     * Bind the scene FBO so the world renders into it.
     * Call this BEFORE rendering the world.
     */
    public void beginSceneCapture() {
        if (!initialized) return;
        glBindFramebuffer(GL_FRAMEBUFFER, sceneFBO);
        glViewport(0, 0, width, height);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    /**
     * Stop capturing to scene FBO and run the post-processing pipeline.
     * Call this AFTER rendering the world but BEFORE rendering UI.
     */
    public void endSceneAndApplyEffects(Matrix4f projection, Matrix4f view) {
        if (!initialized) {
            return;
        }

        // Unbind scene FBO
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        // --- SSAO Pass ---
        if (ssaoEnabled) {
            // Pass 1: Compute raw SSAO
            glBindFramebuffer(GL_FRAMEBUFFER, ssaoFBO);
            glViewport(0, 0, width, height);
            glClear(GL_COLOR_BUFFER_BIT);
            glDisable(GL_DEPTH_TEST);

            ssaoShader.bind();
            ssaoShader.setInt("uDepthTex", 0);
            ssaoShader.setInt("uNormalTex", 1);
            ssaoShader.setInt("uNoiseTex", 2);
            ssaoShader.setMat4("uProjection", projection);
            ssaoShader.setMat4("uView", view);
            ssaoShader.setVec2("uNoiseScale", width / 4.0f, height / 4.0f);
            ssaoShader.setFloat("uRadius", 0.8f);
            ssaoShader.setFloat("uBias", 0.025f);

            // Set kernel samples
            for (int i = 0; i < SSAO_KERNEL_SIZE; i++) {
                ssaoShader.setVec3("uSamples[" + i + "]",
                    ssaoKernel[i].x, ssaoKernel[i].y, ssaoKernel[i].z);
            }

            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, sceneDepthTex);
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, sceneNormalTex);
            glActiveTexture(GL_TEXTURE2);
            glBindTexture(GL_TEXTURE_2D, noiseTexture);

            drawQuad();
            ssaoShader.unbind();

            // Pass 2: Blur SSAO
            glBindFramebuffer(GL_FRAMEBUFFER, ssaoBlurFBO);
            glClear(GL_COLOR_BUFFER_BIT);

            ssaoBlurShader.bind();
            ssaoBlurShader.setInt("uSSAOInput", 0);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, ssaoColorTex);

            drawQuad();
            ssaoBlurShader.unbind();
        }

        // --- Composite Pass (combine scene + SSAO + tone mapping) ---
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, width, height);
        glClear(GL_COLOR_BUFFER_BIT);
        glDisable(GL_DEPTH_TEST);

        compositeShader.bind();
        compositeShader.setInt("uSceneColor", 0);
        compositeShader.setInt("uSSAO", 1);
        compositeShader.setInt("uSSAOEnabled", ssaoEnabled ? 1 : 0);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sceneColorTex);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, ssaoEnabled ? ssaoBlurTex : 0);

        drawQuad();
        compositeShader.unbind();

        // Re-enable depth test for subsequent UI rendering
        glEnable(GL_DEPTH_TEST);
    }

    /**
     * Resize all FBOs when window size changes.
     */
    public void resize(int newWidth, int newHeight) {
        if (!initialized) return;
        if (newWidth == this.width && newHeight == this.height) return;

        this.width = newWidth;
        this.height = newHeight;

        // Recreate all FBOs at new size
        deleteFBOs();
        createSceneFBO(newWidth, newHeight);
        createSSAOFBOs(newWidth, newHeight);
    }

    // ---- FBO Creation ----

    private void createSceneFBO(int w, int h) {
        sceneFBO = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, sceneFBO);

        // Color attachment (RGB16F for HDR range)
        sceneColorTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, sceneColorTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, w, h, 0, GL_RGB, GL_FLOAT, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, sceneColorTex, 0);

        // Normal attachment (view-space normals for SSAO)
        sceneNormalTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, sceneNormalTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, w, h, 0, GL_RGB, GL_FLOAT, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, sceneNormalTex, 0);

        // Depth texture (readable for SSAO)
        sceneDepthTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, sceneDepthTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, w, h, 0, GL_DEPTH_COMPONENT, GL_FLOAT, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, sceneDepthTex, 0);

        // Tell OpenGL to draw to both color attachments
        int[] drawBuffers = {GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1};
        glDrawBuffers(drawBuffers);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            System.err.println("[PostFX] Scene FBO not complete!");
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    private void createSSAOFBOs(int w, int h) {
        // SSAO FBO (raw occlusion)
        ssaoFBO = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, ssaoFBO);

        ssaoColorTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, ssaoColorTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RED, w, h, 0, GL_RED, GL_FLOAT, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, ssaoColorTex, 0);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            System.err.println("[PostFX] SSAO FBO not complete!");
        }

        // SSAO Blur FBO
        ssaoBlurFBO = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, ssaoBlurFBO);

        ssaoBlurTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, ssaoBlurTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RED, w, h, 0, GL_RED, GL_FLOAT, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, ssaoBlurTex, 0);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            System.err.println("[PostFX] SSAO Blur FBO not complete!");
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    // ---- SSAO Kernel/Noise Generation ----

    private void generateSSAOKernel() {
        ssaoKernel = new Vector3f[SSAO_KERNEL_SIZE];
        java.util.Random rng = new java.util.Random(42);

        for (int i = 0; i < SSAO_KERNEL_SIZE; i++) {
            // Random point in hemisphere (tangent space, Z up)
            float x = rng.nextFloat() * 2.0f - 1.0f;
            float y = rng.nextFloat() * 2.0f - 1.0f;
            float z = rng.nextFloat(); // hemisphere: z >= 0

            Vector3f sample = new Vector3f(x, y, z).normalize();
            sample.mul(rng.nextFloat()); // random length

            // Accelerating interpolation: bias samples closer to origin
            float scale = (float) i / SSAO_KERNEL_SIZE;
            scale = lerp(0.1f, 1.0f, scale * scale);
            sample.mul(scale);

            ssaoKernel[i] = sample;
        }
    }

    private void generateNoiseTexture() {
        // 4x4 rotation noise texture
        java.util.Random rng = new java.util.Random(123);
        float[] noise = new float[16 * 3]; // 4x4, RGB
        for (int i = 0; i < 16; i++) {
            noise[i * 3] = rng.nextFloat() * 2.0f - 1.0f;
            noise[i * 3 + 1] = rng.nextFloat() * 2.0f - 1.0f;
            noise[i * 3 + 2] = 0.0f; // rotate around Z
        }

        noiseTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, noiseTexture);
        FloatBuffer buf = MemoryUtil.memAllocFloat(noise.length);
        buf.put(noise).flip();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, 4, 4, 0, GL_RGB, GL_FLOAT, buf);
        MemoryUtil.memFree(buf);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
    }

    // ---- Fullscreen Quad ----

    private void createQuad() {
        float[] quadVertices = {
            // pos (x,y)   texcoord (u,v)
            -1.0f,  1.0f,  0.0f, 1.0f,
            -1.0f, -1.0f,  0.0f, 0.0f,
             1.0f, -1.0f,  1.0f, 0.0f,

            -1.0f,  1.0f,  0.0f, 1.0f,
             1.0f, -1.0f,  1.0f, 0.0f,
             1.0f,  1.0f,  1.0f, 1.0f,
        };

        quadVAO = glGenVertexArrays();
        quadVBO = glGenBuffers();
        glBindVertexArray(quadVAO);
        glBindBuffer(GL_ARRAY_BUFFER, quadVBO);

        FloatBuffer buf = MemoryUtil.memAllocFloat(quadVertices.length);
        buf.put(quadVertices).flip();
        glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW);
        MemoryUtil.memFree(buf);

        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2L * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindVertexArray(0);
    }

    private void drawQuad() {
        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);
    }

    // ---- Cleanup ----

    private void deleteFBOs() {
        if (sceneFBO != 0) glDeleteFramebuffers(sceneFBO);
        if (sceneColorTex != 0) glDeleteTextures(sceneColorTex);
        if (sceneNormalTex != 0) glDeleteTextures(sceneNormalTex);
        if (sceneDepthTex != 0) glDeleteTextures(sceneDepthTex);
        if (ssaoFBO != 0) glDeleteFramebuffers(ssaoFBO);
        if (ssaoColorTex != 0) glDeleteTextures(ssaoColorTex);
        if (ssaoBlurFBO != 0) glDeleteFramebuffers(ssaoBlurFBO);
        if (ssaoBlurTex != 0) glDeleteTextures(ssaoBlurTex);
    }

    public void cleanup() {
        deleteFBOs();
        if (noiseTexture != 0) glDeleteTextures(noiseTexture);
        if (quadVAO != 0) glDeleteVertexArrays(quadVAO);
        if (quadVBO != 0) glDeleteBuffers(quadVBO);
        if (ssaoShader != null) ssaoShader.cleanup();
        if (ssaoBlurShader != null) ssaoBlurShader.cleanup();
        if (compositeShader != null) compositeShader.cleanup();
    }

    public boolean isInitialized() { return initialized; }
    public boolean isSSAOEnabled() { return ssaoEnabled; }
    public void setSSAOEnabled(boolean enabled) { this.ssaoEnabled = enabled; }
    public int getSceneFBO() { return sceneFBO; }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
