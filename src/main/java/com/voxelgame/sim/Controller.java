package com.voxelgame.sim;

import com.voxelgame.agent.ActionQueue;
import com.voxelgame.platform.Input;
import com.voxelgame.render.Camera;
import com.voxelgame.ui.InventoryScreen;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Translates input events into player actions: movement, jumping,
 * camera rotation, sprinting, fly mode toggle, inventory management,
 * and block-breaking progress tracking.
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
 * Block breaking:
 * - Creative mode: instant break on click
 * - Survival mode: hold left-click, progress builds over time based on block hardness
 * - Progress resets if you look away or release mouse
 *
 * Agent interface: When an ActionQueue is set, agent actions are drained
 * each tick and applied with the same priority as keyboard input.
 *
 * Automation: When AutomationController is active, Input.isKeyDown()
 * transparently returns true for automation-injected keys. No changes
 * needed in this class — automation hooks into Input.java directly.
 * @see com.voxelgame.input.AutomationController
 * @see com.voxelgame.agent.ActionQueue
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

    // Agent interface
    private ActionQueue agentActionQueue = null;

    // Agent movement state (active timed moves)
    private float agentMoveForward = 0;
    private float agentMoveStrafe = 0;
    private long agentMoveEndTime = 0;
    private boolean agentSprinting = false;
    private boolean agentCrouching = false;

    // Agent pending click actions (consumed next frame)
    private boolean agentAttackPending = false;
    private boolean agentUsePending = false;

    // ---- Block breaking state ----
    private float breakProgress = 0;      // 0..1
    private int breakingBlockX, breakingBlockY, breakingBlockZ; // currently breaking
    private boolean isBreaking = false;

    // ---- Inventory screen ----
    private InventoryScreen inventoryScreen = null;

    public Controller(Player player) {
        this.player = player;
    }

    /** Set the agent action queue. Null to disable. */
    public void setAgentActionQueue(ActionQueue queue) {
        this.agentActionQueue = queue;
    }

    /** Set the inventory screen reference (for E key toggle). */
    public void setInventoryScreen(InventoryScreen screen) {
        this.inventoryScreen = screen;
    }

    public void update(float dt) {
        // When dead, only allow ESC for cursor unlock — no movement, camera, or actions
        if (player.isDead()) {
            handleEscapeOnly();
            return;
        }

        // Drain agent actions first (they set state that movement reads)
        if (agentActionQueue != null) {
            drainAgentActions();
        }

        handleMouseLook();
        handleMovement(dt);
        handleModeToggles();
        handleHotbar();
        handleInventoryToggle();
    }

    // ---- Block breaking ----

    /**
     * Start or continue breaking a block at the given position.
     * Returns the new break progress (0..1). When it reaches 1.0, the block is broken.
     *
     * @param bx, by, bz block coordinates
     * @param breakTime total time to break this block (seconds)
     * @param dt delta time this frame
     * @return current break progress (0..1)
     */
    public float updateBreaking(int bx, int by, int bz, float breakTime, float dt) {
        if (bx != breakingBlockX || by != breakingBlockY || bz != breakingBlockZ) {
            // Changed target — reset progress
            breakProgress = 0;
            breakingBlockX = bx;
            breakingBlockY = by;
            breakingBlockZ = bz;
        }

        isBreaking = true;
        if (breakTime <= 0) {
            breakProgress = 1.0f; // instant break
        } else {
            breakProgress += dt / breakTime;
        }

        return Math.min(breakProgress, 1.0f);
    }

    /**
     * Reset breaking state (when player stops holding attack or looks away).
     */
    public void resetBreaking() {
        breakProgress = 0;
        isBreaking = false;
    }

    /** Get current break progress (0..1). */
    public float getBreakProgress() { return breakProgress; }

    /** Whether the player is currently breaking a block. */
    public boolean isBreaking() { return isBreaking; }

    // ---- Inventory toggle ----

    private void handleInventoryToggle() {
        if (Input.isKeyPressed(GLFW_KEY_E)) {
            if (inventoryScreen != null) {
                if (inventoryScreen.isOpen()) {
                    inventoryScreen.close(player.getInventory());
                    Input.lockCursor();
                } else {
                    inventoryScreen.setVisible(true);
                    Input.unlockCursor();
                }
            }
        }
    }

    /** Check if inventory screen is open (to suppress game interactions). */
    public boolean isInventoryOpen() {
        return inventoryScreen != null && inventoryScreen.isOpen();
    }

    // ---- Agent action processing ----

    /**
     * Drain all queued agent actions and apply them.
     * Actions are applied atomically — same priority as keyboard input.
     */
    private void drainAgentActions() {
        agentActionQueue.drain(action -> {
            switch (action.type()) {
                case "action_look" -> {
                    // Apply yaw/pitch delta directly to camera
                    player.getCamera().rotate(action.param1(), action.param2());
                }
                case "action_move" -> {
                    // Set timed movement (forward/strafe for duration ms)
                    agentMoveForward = action.param1();
                    agentMoveStrafe = action.param2();
                    agentMoveEndTime = System.currentTimeMillis() + action.durationMs();
                }
                case "action_jump" -> {
                    player.jump();
                }
                case "action_crouch" -> {
                    agentCrouching = action.param1() > 0.5f;
                }
                case "action_sprint" -> {
                    agentSprinting = action.param1() > 0.5f;
                }
                case "action_use" -> {
                    agentUsePending = true;
                }
                case "action_attack" -> {
                    agentAttackPending = true;
                }
                case "action_hotbar_select" -> {
                    player.setSelectedSlot(action.intParam());
                }
            }
        });

        // Expire timed movement
        if (System.currentTimeMillis() > agentMoveEndTime) {
            agentMoveForward = 0;
            agentMoveStrafe = 0;
        }
    }

    /** Check if agent has a pending attack (consumed by GameLoop for block breaking). */
    public boolean consumeAgentAttack() {
        if (agentAttackPending) {
            agentAttackPending = false;
            return true;
        }
        return false;
    }

    /** Check if agent has a pending use action (consumed by GameLoop for block placing). */
    public boolean consumeAgentUse() {
        if (agentUsePending) {
            agentUsePending = false;
            return true;
        }
        return false;
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
        if (Input.isKeyDown(GLFW_KEY_LEFT_SHIFT) || agentSprinting) {
            speed *= 2.5f;
        }

        float moveX = 0, moveY = 0, moveZ = 0;

        if (Input.isKeyDown(GLFW_KEY_W)) { moveX += front.x; moveY += front.y; moveZ += front.z; }
        if (Input.isKeyDown(GLFW_KEY_S)) { moveX -= front.x; moveY -= front.y; moveZ -= front.z; }
        if (Input.isKeyDown(GLFW_KEY_A)) { moveX -= right.x; moveY -= right.y; moveZ -= right.z; }
        if (Input.isKeyDown(GLFW_KEY_D)) { moveX += right.x; moveY += right.y; moveZ += right.z; }
        if (Input.isKeyDown(GLFW_KEY_SPACE))        moveY += 1.0f;
        if (Input.isKeyDown(GLFW_KEY_LEFT_CONTROL)) moveY -= 1.0f;

        // Add agent movement in fly mode
        if (agentMoveForward != 0) {
            moveX += front.x * agentMoveForward;
            moveY += front.y * agentMoveForward;
            moveZ += front.z * agentMoveForward;
        }
        if (agentMoveStrafe != 0) {
            moveX += right.x * agentMoveStrafe;
            moveY += right.y * agentMoveStrafe;
            moveZ += right.z * agentMoveStrafe;
        }

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

        // Sprint (keyboard OR agent)
        sprinting = (Input.isKeyDown(GLFW_KEY_LEFT_SHIFT) && Input.isKeyDown(GLFW_KEY_W))
                    || (agentSprinting && agentMoveForward > 0);
        float speed = WALK_SPEED;
        if (sprinting) speed *= SPRINT_MULTIPLIER;

        // Compute wish direction from keyboard
        float wishX = 0, wishZ = 0;
        if (Input.isKeyDown(GLFW_KEY_W)) { wishX += flatFront.x; wishZ += flatFront.z; }
        if (Input.isKeyDown(GLFW_KEY_S)) { wishX -= flatFront.x; wishZ -= flatFront.z; }
        if (Input.isKeyDown(GLFW_KEY_A)) { wishX -= flatRight.x; wishZ -= flatRight.z; }
        if (Input.isKeyDown(GLFW_KEY_D)) { wishX += flatRight.x; wishZ += flatRight.z; }

        // Add agent movement (additive with keyboard — both sources contribute)
        if (agentMoveForward != 0) {
            wishX += flatFront.x * agentMoveForward;
            wishZ += flatFront.z * agentMoveForward;
        }
        if (agentMoveStrafe != 0) {
            wishX += flatRight.x * agentMoveStrafe;
            wishZ += flatRight.z * agentMoveStrafe;
        }

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
        // F = toggle fly mode (gated by game mode — only creative allows flight)
        if (Input.isKeyPressed(GLFW_KEY_F)) {
            if (player.getGameMode().isFlightAllowed() || player.isFlyMode()) {
                player.toggleFlyMode();
                System.out.println("Fly mode: " + (player.isFlyMode() ? "ON" : "OFF"));
            } else {
                System.out.println("Fly mode not available in " + player.getGameMode() + " mode");
            }
        }

        // F4 = cycle game mode (Creative ↔ Survival)
        if (Input.isKeyPressed(GLFW_KEY_F4)) {
            GameMode next = player.getGameMode().next();
            player.setGameMode(next);
        }

        // F5 = cycle difficulty (Peaceful → Easy → Normal → Hard → loop)
        if (Input.isKeyPressed(GLFW_KEY_F5)) {
            Difficulty next = player.getDifficulty().next();
            player.setDifficulty(next);
        }

        // ESC handling moved to GameLoop (pause menu / screen system)
        // Inventory close on ESC is handled by GameLoop.updateAndRenderGame()
    }

    /** When dead — ESC is handled by GameLoop pause menu. */
    private void handleEscapeOnly() {
        // ESC handling moved to GameLoop
    }
}
