package com.voxelgame.sim;

import com.voxelgame.platform.Input;
import com.voxelgame.render.Camera;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Translates input events into player actions: movement, jumping,
 * camera rotation, sprinting, fly mode toggle.
 *
 * In fly mode:  direct position manipulation (free-cam).
 * In walk mode:  sets target velocity; applies acceleration/friction;
 *                Physics handles gravity & integration.
 *
 * Movement polish:
 * - Left Shift = sprint (1.5× speed)
 * - Smooth acceleration/deceleration via friction
 * - Reduced air control when airborne
 * - Auto step-up for ≤0.5 block ledges
 *
 * Automation: When AutomationController is active, Input.isKeyDown()
 * transparently returns true for automation-injected keys. No changes
 * needed in this class — automation hooks into Input.java directly.
 * @see com.voxelgame.input.AutomationController
 */
public class Controller {

    // ---- Speed constants ----
    private static final float FLY_SPEED        = 20.0f;   // blocks/s
    private static final float WALK_SPEED       = 4.3f;    // blocks/s (Minecraft-like)
    private static final float SPRINT_MULTIPLIER = 1.5f;   // sprint speed factor
    private static final float MOUSE_SENSITIVITY = 0.1f;

    // ---- Friction / Acceleration ----
    /** Ground friction: velocity is multiplied by (1 - GROUND_FRICTION * dt) each frame. */
    private static final float GROUND_FRICTION   = 18.0f;
    /** Ground acceleration: how fast player reaches target speed. */
    private static final float GROUND_ACCEL      = 60.0f;
    /** Air friction (much lower). */
    private static final float AIR_FRICTION      = 3.0f;
    /** Air acceleration (reduced control). */
    private static final float AIR_ACCEL         = 12.0f;

    private final Player player;
    private boolean sprinting = false;

    public Controller(Player player) {
        this.player = player;
    }

    public void update(float dt) {
        handleMouseLook();
        handleMovement(dt);
        handleModeToggles();
        handleHotbar();
    }

    public boolean isSprinting() {
        return sprinting;
    }

    // ---- Mouse look ----

    private void handleMouseLook() {
        if (!Input.isCursorLocked()) return;

        double dx = Input.getMouseDX();
        double dy = Input.getMouseDY();

        if (dx != 0 || dy != 0) {
            player.getCamera().rotate(
                (float) dx * MOUSE_SENSITIVITY,
                (float) -dy * MOUSE_SENSITIVITY
            );
        }
    }

    // ---- Movement ----

    private void handleMovement(float dt) {
        Camera camera = player.getCamera();
        Vector3f front = camera.getFront();
        Vector3f right = camera.getRight();

        if (player.isFlyMode()) {
            handleFlyMovement(dt, front, right);
        } else {
            handleWalkMovement(dt, front, right);
        }
    }

    /**
     * Fly mode: move directly along camera vectors. No physics.
     */
    private void handleFlyMovement(float dt, Vector3f front, Vector3f right) {
        Vector3f pos = player.getPosition();
        float speed = FLY_SPEED;
        if (Input.isKeyDown(GLFW_KEY_LEFT_SHIFT)) {
            speed *= 2.5f;
        }

        float moveX = 0, moveY = 0, moveZ = 0;

        if (Input.isKeyDown(GLFW_KEY_W)) { moveX += front.x; moveY += front.y; moveZ += front.z; }
        if (Input.isKeyDown(GLFW_KEY_S)) { moveX -= front.x; moveY -= front.y; moveZ -= front.z; }
        if (Input.isKeyDown(GLFW_KEY_A)) { moveX -= right.x; moveY -= right.y; moveZ -= right.z; }
        if (Input.isKeyDown(GLFW_KEY_D)) { moveX += right.x; moveY += right.y; moveZ += right.z; }
        if (Input.isKeyDown(GLFW_KEY_SPACE))        moveY += 1.0f;
        if (Input.isKeyDown(GLFW_KEY_LEFT_CONTROL)) moveY -= 1.0f;

        float len = (float) Math.sqrt(moveX * moveX + moveY * moveY + moveZ * moveZ);
        if (len > 0.001f) {
            float inv = 1.0f / len;
            pos.x += moveX * inv * speed * dt;
            pos.y += moveY * inv * speed * dt;
            pos.z += moveZ * inv * speed * dt;
        }

        // Fly mode doesn't use velocity
        player.getVelocity().set(0);
        sprinting = false;
    }

    /**
     * Walk mode: compute wish direction, apply acceleration/friction,
     * handle sprint and jump.
     */
    private void handleWalkMovement(float dt, Vector3f front, Vector3f right) {
        Vector3f vel = player.getVelocity();

        // Flat direction vectors (ignore camera pitch for horizontal movement)
        Vector3f flatFront = new Vector3f(front.x, 0, front.z);
        if (flatFront.lengthSquared() > 0.001f) flatFront.normalize();
        Vector3f flatRight = new Vector3f(right.x, 0, right.z);
        if (flatRight.lengthSquared() > 0.001f) flatRight.normalize();

        // Sprint
        sprinting = Input.isKeyDown(GLFW_KEY_LEFT_SHIFT) && Input.isKeyDown(GLFW_KEY_W);
        float speed = WALK_SPEED;
        if (sprinting) speed *= SPRINT_MULTIPLIER;

        // Compute wish direction
        float wishX = 0, wishZ = 0;
        if (Input.isKeyDown(GLFW_KEY_W)) { wishX += flatFront.x; wishZ += flatFront.z; }
        if (Input.isKeyDown(GLFW_KEY_S)) { wishX -= flatFront.x; wishZ -= flatFront.z; }
        if (Input.isKeyDown(GLFW_KEY_A)) { wishX -= flatRight.x; wishZ -= flatRight.z; }
        if (Input.isKeyDown(GLFW_KEY_D)) { wishX += flatRight.x; wishZ += flatRight.z; }

        float wishLen = (float) Math.sqrt(wishX * wishX + wishZ * wishZ);
        if (wishLen > 0.001f) {
            float inv = 1.0f / wishLen;
            wishX *= inv * speed;
            wishZ *= inv * speed;
        }

        // Apply acceleration/friction based on ground state
        boolean onGround = player.isOnGround();
        float accel   = onGround ? GROUND_ACCEL   : AIR_ACCEL;
        float friction = onGround ? GROUND_FRICTION : AIR_FRICTION;

        // Friction: decay current velocity toward zero
        float frictionFactor = Math.max(0.0f, 1.0f - friction * dt);
        vel.x *= frictionFactor;
        vel.z *= frictionFactor;

        // Acceleration: push toward wish velocity
        float dvx = wishX - vel.x;
        float dvz = wishZ - vel.z;
        float accelStep = accel * dt;

        float dvLen = (float) Math.sqrt(dvx * dvx + dvz * dvz);
        if (dvLen > 0.001f) {
            float maxAccel = Math.min(accelStep, dvLen);
            float ratio = maxAccel / dvLen;
            vel.x += dvx * ratio;
            vel.z += dvz * ratio;
        }

        // Clamp horizontal speed to prevent exceeding target
        float hSpeed = (float) Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        float maxSpeed = speed * 1.1f; // slight tolerance
        if (hSpeed > maxSpeed) {
            float clamp = maxSpeed / hSpeed;
            vel.x *= clamp;
            vel.z *= clamp;
        }

        // Jump
        if (Input.isKeyDown(GLFW_KEY_SPACE)) {
            player.jump();
        }
    }

    // ---- Hotbar selection ----

    private void handleHotbar() {
        // Number keys 1-9 select hotbar slots
        for (int i = 0; i < 9; i++) {
            if (Input.isKeyPressed(GLFW_KEY_1 + i)) {
                player.setSelectedSlot(i);
            }
        }

        // Scroll wheel cycles through slots
        double scrollY = Input.getScrollDY();
        if (scrollY != 0) {
            player.cycleSelectedSlot((int) Math.signum(scrollY));
        }
    }

    // ---- Mode toggles ----

    private void handleModeToggles() {
        // F = toggle fly mode (F3 = debug overlay, handled in GameLoop)
        if (Input.isKeyPressed(GLFW_KEY_F)) {
            player.toggleFlyMode();
            System.out.println("Fly mode: " + (player.isFlyMode() ? "ON" : "OFF"));
        }

        // ESC = toggle cursor lock
        if (Input.isKeyPressed(GLFW_KEY_ESCAPE)) {
            if (Input.isCursorLocked()) {
                Input.unlockCursor();
            } else {
                Input.lockCursor();
            }
        }
    }
}
