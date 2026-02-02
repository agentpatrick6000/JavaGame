package com.voxelgame.render;

import com.voxelgame.sim.Entity;
import com.voxelgame.sim.EntityType;
import com.voxelgame.sim.TNTEntity;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/**
 * Renders mob entities with multi-part box models (Infdev 611 style).
 *
 * Pig:    body, head, snout, 4 legs, eyes, nostrils
 * Zombie: head, body (shirt), body (pants), 2 arms, 2 legs, eyes
 *
 * Each body part is a colored box with optional rotation animation
 * (walk cycle for legs/arms). Models use simple box geometry matching
 * the period-accurate Infdev 611 aesthetic.
 */
public class EntityRenderer {

    // ==================================================================
    // Pig model colors (Infdev 611 palette)
    // ==================================================================
    private static final float[] PIG_BODY   = { 0.855f, 0.525f, 0.525f };  // pink
    private static final float[] PIG_LEG    = { 0.780f, 0.460f, 0.460f };  // darker pink
    private static final float[] PIG_SNOUT  = { 0.710f, 0.390f, 0.390f };  // dark pink snout
    private static final float[] PIG_EYE    = { 0.08f,  0.08f,  0.08f  };  // near-black
    private static final float[] PIG_NOSTRIL= { 0.50f,  0.28f,  0.28f  };  // dark hole

    // ==================================================================
    // Zombie model colors (Infdev 611 — green Steve skin)
    // ==================================================================
    private static final float[] ZOM_HEAD   = { 0.35f, 0.55f, 0.30f };  // green skin
    private static final float[] ZOM_SHIRT  = { 0.27f, 0.47f, 0.45f };  // teal shirt
    private static final float[] ZOM_ARM    = { 0.35f, 0.55f, 0.30f };  // green skin (arms)
    private static final float[] ZOM_PANTS  = { 0.22f, 0.22f, 0.40f };  // dark blue/purple pants
    private static final float[] ZOM_EYE    = { 0.90f, 0.10f, 0.10f };  // red eyes

    // ==================================================================
    // Vehicle/TNT colors (unchanged from before)
    // ==================================================================
    private static final float[] BOAT_COLOR = { 0.55f, 0.35f, 0.15f };
    private static final float[] CART_COLOR = { 0.50f, 0.50f, 0.55f };
    private static final float[] TNT_COLOR  = { 0.85f, 0.20f, 0.15f };
    private static final float[] TNT_FLASH  = { 1.00f, 1.00f, 1.00f };

    // ==================================================================
    // Pig model dimensions (in blocks) — Infdev 611 proportions
    // ==================================================================
    // Body: elongated (longer than wide, like a real pig)
    private static final float PIG_BODY_W = 0.5f;     // width (X)
    private static final float PIG_BODY_H = 0.5f;     // height (Y)
    private static final float PIG_BODY_D = 0.875f;   // depth/length (Z, front-to-back)
    // Legs: short and stubby
    private static final float PIG_LEG_W  = 0.25f;
    private static final float PIG_LEG_H  = 0.375f;
    private static final float PIG_LEG_D  = 0.25f;
    // Head: roughly cubic
    private static final float PIG_HEAD_S = 0.5f;     // head is a cube
    // Snout: protruding from face
    private static final float PIG_SNOUT_W = 0.25f;
    private static final float PIG_SNOUT_H = 0.1875f;
    private static final float PIG_SNOUT_D = 0.125f;

    // ==================================================================
    // Zombie model dimensions (humanoid, Infdev 611 proportions)
    // ==================================================================
    private static final float ZOM_HEAD_S  = 0.5f;    // head cube
    private static final float ZOM_BODY_W  = 0.5f;    // width
    private static final float ZOM_BODY_H  = 0.75f;   // height
    private static final float ZOM_BODY_D  = 0.25f;   // depth
    private static final float ZOM_ARM_W   = 0.25f;
    private static final float ZOM_ARM_H   = 0.75f;
    private static final float ZOM_ARM_D   = 0.25f;
    private static final float ZOM_LEG_W   = 0.25f;
    private static final float ZOM_LEG_H   = 0.75f;
    private static final float ZOM_LEG_D   = 0.25f;

    // ==================================================================
    // Hurt flash tint
    // ==================================================================
    private static final float[] HURT_TINT = { 1.0f, 0.3f, 0.3f };
    private static final float HURT_BLEND  = 0.6f;

    private Shader shader;
    private int vao, vbo;

    public void init() {
        shader = new Shader("shaders/line.vert", "shaders/line.frag");
        buildCube();
    }

    /**
     * Build a unit cube (0,0,0) → (1,1,1) as a triangle mesh.
     */
    private void buildCube() {
        float[] verts = {
                // Front face (z=1)
                0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 0, 1, 1, 1, 1, 0, 1, 1,
                // Back face (z=0)
                1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 1, 0, 1, 1, 0,
                // Top face (y=1)
                0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1, 1, 1, 0, 0, 1, 0,
                // Bottom face (y=0)
                0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 0, 1, 0, 1, 0, 0, 1,
                // Right face (x=1)
                1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 0, 1, 1, 1, 0, 1, 1, 1,
                // Left face (x=0)
                0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 0, 0, 0, 1, 1, 0, 1, 0,
        };

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);

        try (MemoryStack stk = MemoryStack.stackPush()) {
            FloatBuffer fb = stk.mallocFloat(verts.length);
            fb.put(verts).flip();
            glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        }

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);
    }

    // ==================================================================
    // Main render entry point
    // ==================================================================

    public void render(Camera camera, int windowW, int windowH, List<Entity> entities) {
        if (entities == null || entities.isEmpty()) return;

        Matrix4f projection = camera.getProjectionMatrix(windowW, windowH);
        Matrix4f cameraView = camera.getViewMatrix();

        shader.bind();
        shader.setMat4("uProjection", projection);

        glEnable(GL_DEPTH_TEST);
        glBindVertexArray(vao);

        for (Entity entity : entities) {
            if (entity.isDead()) continue;

            switch (entity.getType()) {
                case PIG    -> renderPig(cameraView, entity);
                case ZOMBIE -> renderZombie(cameraView, entity);
                case TNT    -> renderSimpleBox(cameraView, entity,
                        entity instanceof TNTEntity tnt && tnt.isBlinking() ? TNT_FLASH : TNT_COLOR);
                case BOAT   -> renderSimpleBox(cameraView, entity, BOAT_COLOR);
                case MINECART -> renderSimpleBox(cameraView, entity, CART_COLOR);
                default     -> renderSimpleBox(cameraView, entity, CART_COLOR);
            }
        }

        glBindVertexArray(0);
        shader.unbind();
    }

    // ==================================================================
    // Pig rendering — multi-part model with walk animation
    // ==================================================================

    private void renderPig(Matrix4f cameraView, Entity entity) {
        float ex = entity.getX(), ey = entity.getY(), ez = entity.getZ();
        float yawDeg = entity.getYaw();
        boolean hurt = entity.getHurtTimer() > 0;

        // Walk animation
        float limbSwing = entity.getLimbSwing();
        float limbAmount = entity.getLimbSwingAmount();
        float legAngle = (float) Math.sin(limbSwing * 0.6662f) * limbAmount * 40.0f;

        // ---- Body ----
        float bodyCY = PIG_LEG_H + PIG_BODY_H / 2;
        drawBox(cameraView, ex, ey, ez, yawDeg,
                0, bodyCY, 0,
                PIG_BODY_W, PIG_BODY_H, PIG_BODY_D,
                tint(PIG_BODY, hurt));

        // ---- Head ----
        float headCY = PIG_LEG_H + PIG_BODY_H - PIG_HEAD_S / 2;
        float headCZ = PIG_BODY_D / 2 + PIG_HEAD_S / 2 - 0.05f;
        drawBox(cameraView, ex, ey, ez, yawDeg,
                0, headCY, headCZ,
                PIG_HEAD_S, PIG_HEAD_S, PIG_HEAD_S,
                tint(PIG_BODY, hurt));

        // ---- Snout (protrudes from front face of head) ----
        float snoutCY = headCY - PIG_HEAD_S * 0.15f;
        float snoutCZ = headCZ + PIG_HEAD_S / 2 + PIG_SNOUT_D / 2;
        drawBox(cameraView, ex, ey, ez, yawDeg,
                0, snoutCY, snoutCZ,
                PIG_SNOUT_W, PIG_SNOUT_H, PIG_SNOUT_D,
                tint(PIG_SNOUT, hurt));

        // ---- Nostrils (two dark dots on snout face) ----
        float nostrilY = snoutCY - PIG_SNOUT_H * 0.1f;
        float nostrilZ = snoutCZ + PIG_SNOUT_D / 2 + 0.005f;
        float nostrilSize = 0.04f;
        float nostrilSpacing = PIG_SNOUT_W * 0.3f;
        drawBox(cameraView, ex, ey, ez, yawDeg,
                -nostrilSpacing, nostrilY, nostrilZ,
                nostrilSize, nostrilSize, nostrilSize,
                PIG_NOSTRIL);
        drawBox(cameraView, ex, ey, ez, yawDeg,
                nostrilSpacing, nostrilY, nostrilZ,
                nostrilSize, nostrilSize, nostrilSize,
                PIG_NOSTRIL);

        // ---- Eyes (on head face, above snout) ----
        float eyeY = headCY + PIG_HEAD_S * 0.1f;
        float eyeZ = headCZ + PIG_HEAD_S / 2 + 0.01f;
        float eyeSize = 0.06f;
        float eyeSpacing = PIG_HEAD_S * 0.22f;
        drawBox(cameraView, ex, ey, ez, yawDeg,
                -eyeSpacing, eyeY, eyeZ,
                eyeSize, eyeSize, eyeSize,
                PIG_EYE);
        drawBox(cameraView, ex, ey, ez, yawDeg,
                eyeSpacing, eyeY, eyeZ,
                eyeSize, eyeSize, eyeSize,
                PIG_EYE);

        // ---- Legs (4 legs with walk animation) ----
        // Leg positions: spread under the body
        float legXSpread = PIG_BODY_W / 2 - PIG_LEG_W / 2 - 0.01f;
        float legZFront  = PIG_BODY_D / 2 - PIG_LEG_D / 2 - 0.05f;
        float legZBack   = -(PIG_BODY_D / 2 - PIG_LEG_D / 2 - 0.05f);
        float legCY = PIG_LEG_H / 2;
        float legPivotY = PIG_LEG_H;  // pivot at hip (top of leg)

        float[] legCol = tint(PIG_LEG, hurt);

        // Front-left: +angle, Front-right: -angle, Back-left: -angle, Back-right: +angle
        drawAnimatedBox(cameraView, ex, ey, ez, yawDeg,
                -legXSpread, legCY, legZFront,
                PIG_LEG_W, PIG_LEG_H, PIG_LEG_D, legCol,
                -legXSpread, legPivotY, legZFront, legAngle, 0);
        drawAnimatedBox(cameraView, ex, ey, ez, yawDeg,
                legXSpread, legCY, legZFront,
                PIG_LEG_W, PIG_LEG_H, PIG_LEG_D, legCol,
                legXSpread, legPivotY, legZFront, -legAngle, 0);
        drawAnimatedBox(cameraView, ex, ey, ez, yawDeg,
                -legXSpread, legCY, legZBack,
                PIG_LEG_W, PIG_LEG_H, PIG_LEG_D, legCol,
                -legXSpread, legPivotY, legZBack, -legAngle, 0);
        drawAnimatedBox(cameraView, ex, ey, ez, yawDeg,
                legXSpread, legCY, legZBack,
                PIG_LEG_W, PIG_LEG_H, PIG_LEG_D, legCol,
                legXSpread, legPivotY, legZBack, legAngle, 0);
    }

    // ==================================================================
    // Zombie rendering — humanoid model with zombie arm pose
    // ==================================================================

    private void renderZombie(Matrix4f cameraView, Entity entity) {
        float ex = entity.getX(), ey = entity.getY(), ez = entity.getZ();
        float yawDeg = entity.getYaw();
        boolean hurt = entity.getHurtTimer() > 0;

        // Walk animation
        float limbSwing = entity.getLimbSwing();
        float limbAmount = entity.getLimbSwingAmount();
        float walkAngle = (float) Math.sin(limbSwing * 0.6662f) * limbAmount * 40.0f;

        // Y positions
        float legTop = ZOM_LEG_H;
        float bodyBot = legTop;
        float bodyTop = bodyBot + ZOM_BODY_H;
        float headBot = bodyTop;

        // ---- Legs (walk animation) ----
        float legCY = ZOM_LEG_H / 2;
        float legPivotY = ZOM_LEG_H;
        float legXSpread = ZOM_BODY_W / 2 - ZOM_LEG_W / 2;

        float[] pantsCol = tint(ZOM_PANTS, hurt);

        // Left leg: +walkAngle, Right leg: -walkAngle
        drawAnimatedBox(cameraView, ex, ey, ez, yawDeg,
                -legXSpread, legCY, 0,
                ZOM_LEG_W, ZOM_LEG_H, ZOM_LEG_D, pantsCol,
                -legXSpread, legPivotY, 0, walkAngle, 0);
        drawAnimatedBox(cameraView, ex, ey, ez, yawDeg,
                legXSpread, legCY, 0,
                ZOM_LEG_W, ZOM_LEG_H, ZOM_LEG_D, pantsCol,
                legXSpread, legPivotY, 0, -walkAngle, 0);

        // ---- Body (shirt area) ----
        float bodyCY = bodyBot + ZOM_BODY_H / 2;
        drawBox(cameraView, ex, ey, ez, yawDeg,
                0, bodyCY, 0,
                ZOM_BODY_W, ZOM_BODY_H, ZOM_BODY_D,
                tint(ZOM_SHIRT, hurt));

        // ---- Head ----
        float headCY = headBot + ZOM_HEAD_S / 2;
        drawBox(cameraView, ex, ey, ez, yawDeg,
                0, headCY, 0,
                ZOM_HEAD_S, ZOM_HEAD_S, ZOM_HEAD_S,
                tint(ZOM_HEAD, hurt));

        // ---- Eyes (red, on face) ----
        float eyeY = headCY + ZOM_HEAD_S * 0.08f;
        float eyeZ = ZOM_HEAD_S / 2 + 0.01f;
        float eyeSize = 0.07f;
        float eyeSpacing = ZOM_HEAD_S * 0.22f;
        drawBox(cameraView, ex, ey, ez, yawDeg,
                -eyeSpacing, eyeY, eyeZ,
                eyeSize, eyeSize, eyeSize,
                ZOM_EYE);
        drawBox(cameraView, ex, ey, ez, yawDeg,
                eyeSpacing, eyeY, eyeZ,
                eyeSize, eyeSize, eyeSize,
                ZOM_EYE);

        // ---- Arms (zombie pose: extended forward, -90° around X at shoulder) ----
        float shoulderY = bodyTop;  // top of body
        float armXOffset = ZOM_BODY_W / 2 + ZOM_ARM_W / 2;
        float armCY = shoulderY - ZOM_ARM_H / 2;  // arm center when hanging

        float[] armCol = tint(ZOM_ARM, hurt);

        // Base rotation: -90° forward + slight walk swing
        float armSwing = walkAngle * 0.25f;
        float leftArmRot  = -80.0f + armSwing;
        float rightArmRot = -80.0f - armSwing;

        drawAnimatedBox(cameraView, ex, ey, ez, yawDeg,
                -armXOffset, armCY, 0,
                ZOM_ARM_W, ZOM_ARM_H, ZOM_ARM_D, armCol,
                -armXOffset, shoulderY, 0, leftArmRot, 0);
        drawAnimatedBox(cameraView, ex, ey, ez, yawDeg,
                armXOffset, armCY, 0,
                ZOM_ARM_W, ZOM_ARM_H, ZOM_ARM_D, armCol,
                armXOffset, shoulderY, 0, rightArmRot, 0);
    }

    // ==================================================================
    // Simple box rendering (for TNT, boats, minecarts)
    // ==================================================================

    private void renderSimpleBox(Matrix4f cameraView, Entity entity, float[] color) {
        float hw = entity.getHalfWidth();
        float h = entity.getHeight();
        float w = hw * 2;

        boolean hurt = entity.getHurtTimer() > 0;
        float[] col = tint(color, hurt);

        drawBox(cameraView, entity.getX(), entity.getY(), entity.getZ(), entity.getYaw(),
                0, h / 2, 0, w, h, w, col);
    }

    // ==================================================================
    // Core box drawing methods
    // ==================================================================

    /**
     * Draw a non-animated (static) colored box in entity model space.
     *
     * @param viewBase   camera view matrix
     * @param ex,ey,ez   entity world position (feet)
     * @param yawDeg     entity yaw in degrees
     * @param cx,cy,cz   box CENTER in model space (feet = origin)
     * @param w,h,d      box dimensions
     * @param rgb        color array [r, g, b]
     */
    private void drawBox(Matrix4f viewBase,
                         float ex, float ey, float ez, float yawDeg,
                         float cx, float cy, float cz,
                         float w, float h, float d,
                         float[] rgb) {
        Matrix4f vm = new Matrix4f(viewBase);
        vm.translate(ex, ey, ez);
        vm.rotateY((float) Math.toRadians(yawDeg));
        vm.translate(cx - w / 2, cy - h / 2, cz - d / 2);
        vm.scale(w, h, d);

        shader.setMat4("uView", vm);
        shader.setVec4("uColor", rgb[0], rgb[1], rgb[2], 1.0f);
        glDrawArrays(GL_TRIANGLES, 0, 36);
    }

    /**
     * Draw an animated (rotating) colored box in entity model space.
     * The box rotates around a pivot point — used for legs and arms.
     *
     * @param viewBase       camera view matrix
     * @param ex,ey,ez       entity world position (feet)
     * @param yawDeg         entity yaw in degrees
     * @param cx,cy,cz       box CENTER in model space when NOT rotated
     * @param w,h,d          box dimensions
     * @param rgb            color array [r, g, b]
     * @param pivotX,pivotY,pivotZ  rotation pivot in model space
     * @param rotXDeg        rotation around X axis (degrees) — walk swing
     * @param rotZDeg        rotation around Z axis (degrees)
     */
    private void drawAnimatedBox(Matrix4f viewBase,
                                 float ex, float ey, float ez, float yawDeg,
                                 float cx, float cy, float cz,
                                 float w, float h, float d,
                                 float[] rgb,
                                 float pivotX, float pivotY, float pivotZ,
                                 float rotXDeg, float rotZDeg) {
        Matrix4f vm = new Matrix4f(viewBase);

        // 1. Position at entity feet in world space
        vm.translate(ex, ey, ez);

        // 2. Rotate entire model by entity yaw
        vm.rotateY((float) Math.toRadians(yawDeg));

        // 3. Translate to pivot point in model space
        vm.translate(pivotX, pivotY, pivotZ);

        // 4. Apply animation rotation at pivot
        if (rotXDeg != 0) vm.rotateX((float) Math.toRadians(rotXDeg));
        if (rotZDeg != 0) vm.rotateZ((float) Math.toRadians(rotZDeg));

        // 5. Translate from pivot to box corner
        //    (box center relative to pivot) then offset to corner
        vm.translate(cx - pivotX - w / 2,
                     cy - pivotY - h / 2,
                     cz - pivotZ - d / 2);

        // 6. Scale unit cube to box dimensions
        vm.scale(w, h, d);

        shader.setMat4("uView", vm);
        shader.setVec4("uColor", rgb[0], rgb[1], rgb[2], 1.0f);
        glDrawArrays(GL_TRIANGLES, 0, 36);
    }

    // ==================================================================
    // Color utilities
    // ==================================================================

    /**
     * Apply hurt flash tint if the entity is recently damaged.
     */
    private float[] tint(float[] baseColor, boolean hurt) {
        if (!hurt) return baseColor;
        return new float[] {
            baseColor[0] * (1 - HURT_BLEND) + HURT_TINT[0] * HURT_BLEND,
            baseColor[1] * (1 - HURT_BLEND) + HURT_TINT[1] * HURT_BLEND,
            baseColor[2] * (1 - HURT_BLEND) + HURT_TINT[2] * HURT_BLEND
        };
    }

    // ==================================================================
    // Cleanup
    // ==================================================================

    public void cleanup() {
        if (vbo != 0) glDeleteBuffers(vbo);
        if (vao != 0) glDeleteVertexArrays(vao);
        if (shader != null) shader.cleanup();
    }
}
