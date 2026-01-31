package com.voxelgame.sim;

import com.voxelgame.platform.Input;
import com.voxelgame.render.Camera;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Translates input events into player actions: movement, jumping,
 * camera rotation, fly mode toggle.
 *
 * In fly mode:  direct position manipulation (free-cam).
 * In walk mode:  sets velocity; Physics integrates position.
 */
public class Controller {

    private static final float FLY_SPEED        = 20.0f;  // blocks/s
    private static final float WALK_SPEED       = 4.3f;   // blocks/s (Minecraft-like)
    private static final float MOUSE_SENSITIVITY = 0.1f;

    private final Player player;

    public Controller(Player player) {
        this.player = player;
    }

    public void update(float dt) {
        handleMouseLook();
        handleMovement(dt);
        handleModeToggles();
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
    }

    /**
     * Walk mode: set horizontal velocity from input; Physics handles gravity & integration.
     */
    private void handleWalkMovement(float dt, Vector3f front, Vector3f right) {
        Vector3f vel = player.getVelocity();

        // Flat direction vectors (ignore camera pitch for horizontal movement)
        Vector3f flatFront = new Vector3f(front.x, 0, front.z);
        if (flatFront.lengthSquared() > 0.001f) flatFront.normalize();
        Vector3f flatRight = new Vector3f(right.x, 0, right.z);
        if (flatRight.lengthSquared() > 0.001f) flatRight.normalize();

        float speed = WALK_SPEED;

        float moveX = 0, moveZ = 0;
        if (Input.isKeyDown(GLFW_KEY_W)) { moveX += flatFront.x; moveZ += flatFront.z; }
        if (Input.isKeyDown(GLFW_KEY_S)) { moveX -= flatFront.x; moveZ -= flatFront.z; }
        if (Input.isKeyDown(GLFW_KEY_A)) { moveX -= flatRight.x; moveZ -= flatRight.z; }
        if (Input.isKeyDown(GLFW_KEY_D)) { moveX += flatRight.x; moveZ += flatRight.z; }

        float len = (float) Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (len > 0.001f) {
            float inv = 1.0f / len;
            vel.x = moveX * inv * speed;
            vel.z = moveZ * inv * speed;
        } else {
            vel.x = 0;
            vel.z = 0;
        }

        // Jump
        if (Input.isKeyDown(GLFW_KEY_SPACE)) {
            player.jump();
        }
    }

    // ---- Mode toggles ----

    private void handleModeToggles() {
        // F4 = toggle fly mode (F3 = debug overlay, handled in GameLoop)
        if (Input.isKeyPressed(GLFW_KEY_F4)) {
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
