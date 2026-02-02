package com.voxelgame.sim;

import com.voxelgame.render.Camera;
import com.voxelgame.world.Blocks;
import org.joml.Vector3f;

/**
 * Player entity. Holds position (at eye level via Camera), velocity,
 * on-ground state, fly mode, inventory with block selection,
 * health/damage system, and game mode.
 *
 * Position convention: getPosition() returns the eye-level position.
 * The player hitbox extends from (pos.y - EYE_HEIGHT) to (pos.y - EYE_HEIGHT + HEIGHT).
 */
public class Player {

    /** Player hitbox dimensions. */
    public static final float WIDTH      = 0.6f;
    public static final float HEIGHT     = 1.8f;
    public static final float EYE_HEIGHT = 1.62f;
    public static final float HALF_WIDTH = WIDTH / 2.0f;  // 0.3

    /** Number of hotbar slots. */
    public static final int HOTBAR_SIZE = 9;

    /** Health constants. */
    public static final float MAX_HEALTH = 20.0f;

    private final Camera camera;
    private final Vector3f velocity = new Vector3f();
    private boolean flyMode = false;  // start in walk mode with physics/collision
    private boolean onGround = false;

    /** Player inventory (9 hotbar + 27 storage). */
    private final Inventory inventory = new Inventory();

    /** Currently selected hotbar slot (0-based). */
    private int selectedSlot = 0;

    // ---- Health system ----
    private float health = MAX_HEALTH;
    private boolean dead = false;

    // ---- Game mode + difficulty ----
    private GameMode gameMode = GameMode.SURVIVAL;
    private Difficulty difficulty = Difficulty.NORMAL;

    // ---- Fall tracking ----
    /** Highest Y (feet) while airborne. Reset on landing or entering fly mode. */
    private float fallHighestY = Float.MIN_VALUE;
    /** Whether we are currently tracking a fall (went airborne). */
    private boolean trackingFall = false;

    // ---- Spawn point ----
    private float spawnX, spawnY, spawnZ;

    // ---- Damage flash (for HUD effects) ----
    private float damageFlashTimer = 0.0f;
    private static final float DAMAGE_FLASH_DURATION = 0.3f;

    // ---- Attack cooldown (for entity combat) ----
    private float attackCooldown = 0.0f;
    private static final float ATTACK_COOLDOWN_TIME = 0.4f;

    // ---- Swimming / Water state ----
    private boolean inWater = false;
    private boolean headUnderwater = false;
    private float oxygen = Physics.MAX_OXYGEN;

    // ---- Mounted entity (boat / minecart) ----
    private Entity mountedEntity = null;

    public Player() {
        this.camera = new Camera();
        camera.updateVectors();
    }

    /**
     * Initialize the hotbar with default creative-mode block palette.
     * Only called for creative mode — survival starts with empty inventory.
     */
    public void initCreativeInventory() {
        // Creative mode palette (hotbar shows block types for quick placement)
        inventory.setSlot(0, new Inventory.ItemStack(Blocks.STONE.id(), 64));
        inventory.setSlot(1, new Inventory.ItemStack(Blocks.COBBLESTONE.id(), 64));
        inventory.setSlot(2, new Inventory.ItemStack(Blocks.DIRT.id(), 64));
        inventory.setSlot(3, new Inventory.ItemStack(Blocks.GRASS.id(), 64));
        inventory.setSlot(4, new Inventory.ItemStack(Blocks.SAND.id(), 64));
        inventory.setSlot(5, new Inventory.ItemStack(Blocks.LOG.id(), 64));
        inventory.setSlot(6, new Inventory.ItemStack(Blocks.LEAVES.id(), 64));
        inventory.setSlot(7, new Inventory.ItemStack(Blocks.GRAVEL.id(), 64));
        inventory.setSlot(8, new Inventory.ItemStack(Blocks.WATER.id(), 64));
    }

    // --- Camera / Position ---

    public Camera getCamera() { return camera; }

    /** Eye-level position. Feet are at y - EYE_HEIGHT. */
    public Vector3f getPosition() { return camera.getPosition(); }

    // --- Velocity ---

    public Vector3f getVelocity() { return velocity; }

    // --- Inventory ---

    public Inventory getInventory() { return inventory; }

    // --- Fly mode ---

    public boolean isFlyMode() { return flyMode; }

    public void setFlyMode(boolean fly) {
        this.flyMode = fly;
        if (fly) resetFallTracking();
    }

    public void toggleFlyMode() {
        // Only allow flight in game modes that permit it
        if (!flyMode && !gameMode.isFlightAllowed()) {
            return; // Can't enable flight in this mode
        }
        this.flyMode = !this.flyMode;
        // Zero velocity on mode switch to prevent jarring movement
        velocity.set(0);
        if (flyMode) resetFallTracking();
    }

    // --- Ground state ---

    public boolean isOnGround() { return onGround; }
    public void setOnGround(boolean og) { this.onGround = og; }

    // --- Jump ---

    /**
     * Attempt a jump. Only succeeds if on ground and not in fly mode.
     */
    public void jump() {
        if (onGround && !flyMode) {
            velocity.y = Physics.JUMP_VELOCITY;
            onGround = false;
        }
    }

    // --- Hotbar / Block selection (uses Inventory) ---

    /** Get the block ID in the currently selected hotbar slot. */
    public int getSelectedBlock() {
        return inventory.getHotbarBlockId(selectedSlot);
    }

    /** Get the currently selected hotbar slot index (0-based). */
    public int getSelectedSlot() { return selectedSlot; }

    /** Set the selected hotbar slot index (0-based, clamped to valid range). */
    public void setSelectedSlot(int slot) {
        this.selectedSlot = Math.max(0, Math.min(HOTBAR_SIZE - 1, slot));
    }

    /** Cycle the selected slot by delta (positive = right, negative = left). */
    public void cycleSelectedSlot(int delta) {
        selectedSlot = ((selectedSlot - delta) % HOTBAR_SIZE + HOTBAR_SIZE) % HOTBAR_SIZE;
    }

    /** Get the block ID at a specific hotbar slot. */
    public int getHotbarBlock(int slot) {
        return inventory.getHotbarBlockId(slot);
    }

    /** Set the block ID at a specific hotbar slot. */
    public void setHotbarBlock(int slot, int blockId) {
        if (slot >= 0 && slot < HOTBAR_SIZE) {
            inventory.setSlot(slot, new Inventory.ItemStack(blockId, 64));
        }
    }

    /** For backward compatibility. */
    public void setSelectedBlock(int block) {
        inventory.setSlot(selectedSlot, new Inventory.ItemStack(block, 64));
    }

    /**
     * Try to eat the currently held item. Returns true if food was consumed.
     * Cooked porkchop restores 8 HP, raw porkchop restores 3 HP.
     */
    public boolean tryEatHeldItem() {
        Inventory.ItemStack stack = inventory.getSlot(selectedSlot);
        if (stack == null || stack.isEmpty()) return false;

        int blockId = stack.getBlockId();
        float healAmount = 0;

        if (blockId == Blocks.COOKED_PORKCHOP.id()) {
            healAmount = 8.0f;
        } else if (blockId == Blocks.RAW_PORKCHOP.id()) {
            healAmount = 3.0f;
        }

        if (healAmount <= 0) return false;
        if (health >= MAX_HEALTH) return false; // already full health

        heal(healAmount);
        stack.remove(1);
        if (stack.isEmpty()) {
            inventory.setSlot(selectedSlot, null);
        }

        System.out.printf("[Player] Ate %s, healed %.1f HP — HP: %.1f/%.1f%n",
            Blocks.get(blockId).name(), healAmount, health, MAX_HEALTH);
        return true;
    }

    /**
     * Consume one item from the selected hotbar slot (for block placement in survival).
     * Returns true if an item was consumed, false if slot was empty.
     */
    public boolean consumeSelectedBlock() {
        Inventory.ItemStack stack = inventory.getSlot(selectedSlot);
        if (stack == null || stack.isEmpty()) return false;
        stack.remove(1);
        if (stack.isEmpty()) {
            inventory.setSlot(selectedSlot, null);
        }
        return true;
    }

    // ================================================================
    // Health system
    // ================================================================

    public float getHealth() { return health; }
    public float getMaxHealth() { return MAX_HEALTH; }
    public boolean isDead() { return dead; }

    /**
     * Apply damage to the player, scaled by difficulty multiplier.
     * Creative mode is always invulnerable regardless of difficulty.
     *
     * @param amount      raw damage amount (before scaling)
     * @param source      the cause of damage
     */
    public void damage(float amount, DamageSource source) {
        if (dead) return;
        if (gameMode.isInvulnerable()) return;

        float scaled = amount * difficulty.getDamageMultiplier(source);
        if (scaled <= 0) return;

        // Armor damage reduction
        float totalDefense = inventory.getTotalArmorDefense();
        if (totalDefense > 0) {
            float reduction = ArmorItem.calculateDamageReduction(totalDefense);
            scaled -= scaled * reduction;
            inventory.damageArmor();
        }
        if (scaled <= 0) return;

        health -= scaled;
        damageFlashTimer = DAMAGE_FLASH_DURATION;

        System.out.printf("[Health] Took %.1f damage (%s, %s mode, %s difficulty, %.1fx) — HP: %.1f/%.1f%n",
            scaled, source, gameMode, difficulty, difficulty.getDamageMultiplier(source), health, MAX_HEALTH);

        if (health <= 0) {
            health = 0;
            die();
        }
    }

    /**
     * Heal the player.
     *
     * @param amount  amount to heal
     */
    public void heal(float amount) {
        if (dead) return;
        health = Math.min(MAX_HEALTH, health + amount);
    }

    /**
     * Restore health to a specific value (for save/load only — bypasses damage logic).
     *
     * @param value  health value to restore (clamped to 0..MAX_HEALTH)
     */
    public void restoreHealth(float value) {
        this.health = Math.max(0, Math.min(MAX_HEALTH, value));
        this.dead = (health <= 0);
    }

    /**
     * Kill the player.
     */
    private void die() {
        dead = true;
        velocity.set(0);
        System.out.println("[Health] Player died!");
    }

    /**
     * Respawn the player: reset health, teleport to spawn, clear dead state.
     */
    public void respawn() {
        health = MAX_HEALTH;
        dead = false;
        velocity.set(0);
        camera.getPosition().set(spawnX, spawnY, spawnZ);
        resetFallTracking();
        System.out.printf("[Health] Respawned at (%.1f, %.1f, %.1f)%n", spawnX, spawnY, spawnZ);
    }

    // ---- Damage flash ----

    /** Update damage flash timer. Call each frame with dt. */
    public void updateDamageFlash(float dt) {
        if (damageFlashTimer > 0) {
            damageFlashTimer -= dt;
            if (damageFlashTimer < 0) damageFlashTimer = 0;
        }
    }

    // ---- Attack cooldown ----

    /** Whether the player can attack an entity (cooldown expired). */
    public boolean canAttack() {
        return attackCooldown <= 0;
    }

    /** Reset attack cooldown after attacking. */
    public void resetAttackCooldown() {
        attackCooldown = ATTACK_COOLDOWN_TIME;
    }

    /** Update attack cooldown timer. Call each frame with dt. */
    public void updateAttackCooldown(float dt) {
        if (attackCooldown > 0) {
            attackCooldown -= dt;
        }
    }

    /** Returns 0..1 intensity for damage flash effect. */
    public float getDamageFlashIntensity() {
        return damageFlashTimer / DAMAGE_FLASH_DURATION;
    }

    // ================================================================
    // Game mode
    // ================================================================

    public GameMode getGameMode() { return gameMode; }

    public void setGameMode(GameMode mode) {
        GameMode prev = this.gameMode;
        this.gameMode = mode;

        // If switching away from creative and currently flying, disable fly
        if (!mode.isFlightAllowed() && flyMode) {
            flyMode = false;
            velocity.set(0);
            System.out.println("[GameMode] Flight disabled in " + mode + " mode");
        }

        // Reset health when switching to creative
        if (mode.isInvulnerable()) {
            health = MAX_HEALTH;
            dead = false;
        }

        System.out.println("[GameMode] " + prev + " -> " + mode +
            " (invuln=" + mode.isInvulnerable() +
            ", flight=" + mode.isFlightAllowed() + ")");
    }

    // ================================================================
    // Difficulty
    // ================================================================

    public Difficulty getDifficulty() { return difficulty; }

    public void setDifficulty(Difficulty diff) {
        Difficulty prev = this.difficulty;
        this.difficulty = diff;
        System.out.println("[Difficulty] " + prev + " -> " + diff +
            " (env=" + diff.getEnvDamageMultiplier() + "x, mob=" + diff.getMobDamageMultiplier() + "x)");
    }

    // ================================================================
    // Fall tracking
    // ================================================================

    /**
     * Called by Physics each frame while the player is airborne.
     * Tracks the highest feet-Y to compute fall distance on landing.
     *
     * @param feetY  current feet Y position (pos.y - EYE_HEIGHT)
     */
    public void updateFallTracking(float feetY) {
        if (!trackingFall) {
            trackingFall = true;
            fallHighestY = feetY;
        } else {
            fallHighestY = Math.max(fallHighestY, feetY);
        }
    }

    /**
     * Called by Physics when the player lands.
     * Returns the fall distance (highest point minus landing point).
     * Resets tracking state.
     *
     * @param feetY  landing feet Y position
     * @return fall distance in blocks (always >= 0)
     */
    public float landAndGetFallDistance(float feetY) {
        if (!trackingFall) return 0;
        float dist = fallHighestY - feetY;
        resetFallTracking();
        return Math.max(0, dist);
    }

    /** Reset fall tracking (e.g., on entering fly mode). */
    public void resetFallTracking() {
        trackingFall = false;
        fallHighestY = Float.MIN_VALUE;
    }

    public boolean isTrackingFall() { return trackingFall; }

    // ================================================================
    // Swimming / Water
    // ================================================================

    public boolean isInWater() { return inWater; }
    public void setInWater(boolean inWater) { this.inWater = inWater; }

    public boolean isHeadUnderwater() { return headUnderwater; }
    public void setHeadUnderwater(boolean underwater) { this.headUnderwater = underwater; }

    public float getOxygen() { return oxygen; }
    public float getMaxOxygen() { return Physics.MAX_OXYGEN; }

    /** Drain oxygen by dt seconds. */
    public void drainOxygen(float dt) {
        oxygen = Math.max(0, oxygen - dt);
    }

    /** Refill oxygen instantly (head above water). */
    public void refillOxygen() {
        oxygen = Physics.MAX_OXYGEN;
    }

    // ================================================================
    // Mounted entity (boat / minecart)
    // ================================================================

    public Entity getMountedEntity() { return mountedEntity; }
    public boolean isMounted() { return mountedEntity != null; }

    public void mount(Entity entity) {
        this.mountedEntity = entity;
    }

    public void dismount() {
        this.mountedEntity = null;
    }

    // ================================================================
    // Spawn point
    // ================================================================

    public float getSpawnX() { return spawnX; }
    public float getSpawnY() { return spawnY; }
    public float getSpawnZ() { return spawnZ; }

    public void setSpawnPoint(float x, float y, float z) {
        this.spawnX = x;
        this.spawnY = y;
        this.spawnZ = z;
    }
}
