package com.voxelgame.sim;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.World;
import com.voxelgame.world.WorldConstants;
import com.voxelgame.world.WorldTime;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Handles mob spawning and despawning logic.
 *
 * Passive spawning (pigs): On grass blocks in light (skyLight >= 7), max 15 per world.
 * Hostile spawning (zombies): In dark areas (light < 7), max 20 per world.
 * Despawn: Entities farther than 128 blocks from player are removed.
 *
 * Max 50 entities total (EntityManager cap).
 */
public class MobSpawner {

    // ---- Spawn intervals ----
    private static final float PASSIVE_SPAWN_INTERVAL = 5.0f;
    private static final float HOSTILE_SPAWN_INTERVAL = 3.0f;
    private static final float DESPAWN_CHECK_INTERVAL = 10.0f;

    // ---- Spawn distances ----
    private static final float MIN_SPAWN_DIST = 24.0f;
    private static final float MAX_SPAWN_DIST = 64.0f;
    private static final float DESPAWN_DIST = 64.0f;

    // ---- Max per type ----
    private static final int MAX_PIGS    = 15;
    private static final int MAX_ZOMBIES = 20;

    // ---- Light thresholds ----
    private static final int HOSTILE_MAX_LIGHT = 7; // zombies spawn in light < 7

    // ---- Timers ----
    private float passiveTimer = 0;
    private float hostileTimer = 0;
    private float despawnTimer = 0;

    private final Random random = new Random();

    /**
     * Update spawning and despawning logic.
     */
    public void update(float dt, World world, Player player,
                       EntityManager entityManager, WorldTime worldTime) {
        if (entityManager.getEntityCount() >= EntityManager.MAX_ENTITIES) return;
        if (player.isDead()) return;

        // ---- Despawn check ----
        despawnTimer += dt;
        if (despawnTimer >= DESPAWN_CHECK_INTERVAL) {
            despawnTimer = 0;
            despawnFarEntities(player, entityManager);
        }

        // ---- Passive mob spawning (pigs) ----
        passiveTimer += dt;
        if (passiveTimer >= PASSIVE_SPAWN_INTERVAL) {
            passiveTimer = 0;

            if (worldTime.isDay() && entityManager.countType(EntityType.PIG) < MAX_PIGS) {
                trySpawnPig(world, player, entityManager);
            }
        }

        // ---- Hostile mob spawning (zombies) — night only ----
        hostileTimer += dt;
        if (hostileTimer >= HOSTILE_SPAWN_INTERVAL) {
            hostileTimer = 0;

            if (worldTime.isNight() && entityManager.countType(EntityType.ZOMBIE) < MAX_ZOMBIES) {
                trySpawnZombie(world, player, entityManager);
            }
        }
    }

    /**
     * Remove entities too far from the player.
     */
    private void despawnFarEntities(Player player, EntityManager entityManager) {
        Vector3f pPos = player.getPosition();

        entityManager.getEntities().removeIf(entity -> {
            float dx = entity.getX() - pPos.x;
            float dz = entity.getZ() - pPos.z;
            float distSq = dx * dx + dz * dz;
            if (distSq > DESPAWN_DIST * DESPAWN_DIST) {
                System.out.printf("[Despawn] %s despawned at (%.1f, %.1f, %.1f) — too far%n",
                    entity.getType(), entity.getX(), entity.getY(), entity.getZ());
                return true;
            }
            return false;
        });
    }

    /**
     * Attempt to spawn a pig near the player.
     * Requires grass surface block with air above AND sufficient light.
     */
    private void trySpawnPig(World world, Player player, EntityManager entityManager) {
        Vector3f pPos = player.getPosition();

        for (int attempt = 0; attempt < 5; attempt++) {
            float angle = random.nextFloat() * (float) (Math.PI * 2);
            float dist = MIN_SPAWN_DIST + random.nextFloat() * (MAX_SPAWN_DIST - MIN_SPAWN_DIST);
            float sx = pPos.x + (float) Math.cos(angle) * dist;
            float sz = pPos.z + (float) Math.sin(angle) * dist;

            int surfaceY = findSurfaceY(world, (int) Math.floor(sx), (int) Math.floor(sz));
            if (surfaceY < 0) continue;

            int surfaceBlock = world.getBlock((int) Math.floor(sx), surfaceY, (int) Math.floor(sz));
            if (surfaceBlock != Blocks.GRASS.id()) continue;

            int spawnY = surfaceY + 1;
            if (world.getBlock((int) Math.floor(sx), spawnY, (int) Math.floor(sz)) != 0) continue;
            if (world.getBlock((int) Math.floor(sx), spawnY + 1, (int) Math.floor(sz)) != 0) continue;

            // Check light level — pigs need light >= 7
            int skyLight = world.getSkyLight((int) Math.floor(sx), spawnY, (int) Math.floor(sz));
            int blockLight = world.getBlockLight((int) Math.floor(sx), spawnY, (int) Math.floor(sz));
            int maxLight = Math.max(skyLight, blockLight);
            if (maxLight < HOSTILE_MAX_LIGHT) continue; // too dark for passive mobs

            // Spawn a small group (1-3 pigs) — Infdev 611 behavior
            int groupSize = 1 + random.nextInt(3);
            for (int g = 0; g < groupSize; g++) {
                float gx = sx + (random.nextFloat() - 0.5f) * 4.0f;
                float gz = sz + (random.nextFloat() - 0.5f) * 4.0f;
                int gy = findSurfaceY(world, (int) Math.floor(gx), (int) Math.floor(gz));
                if (gy < 0) { gx = sx; gz = sz; gy = surfaceY; }
                int gSpawnY = gy + 1;
                if (entityManager.countType(EntityType.PIG) >= MAX_PIGS) break;
                Pig pig = new Pig(gx, gSpawnY, gz);
                entityManager.addEntity(pig);
                System.out.printf("[Spawn] Pig spawned at (%.1f, %d, %.1f) light=%d (group %d/%d)%n",
                    gx, gSpawnY, gz, maxLight, g + 1, groupSize);
            }
            return;
        }
    }

    /**
     * Attempt to spawn a zombie near the player.
     * Requires solid surface with air above, in dark areas (light < 7).
     */
    private void trySpawnZombie(World world, Player player, EntityManager entityManager) {
        Vector3f pPos = player.getPosition();

        for (int attempt = 0; attempt < 8; attempt++) {
            float angle = random.nextFloat() * (float) (Math.PI * 2);
            float dist = MIN_SPAWN_DIST + random.nextFloat() * (MAX_SPAWN_DIST - MIN_SPAWN_DIST);
            float sx = pPos.x + (float) Math.cos(angle) * dist;
            float sz = pPos.z + (float) Math.sin(angle) * dist;

            int surfaceY = findSurfaceY(world, (int) Math.floor(sx), (int) Math.floor(sz));
            if (surfaceY < 0) continue;

            int spawnY = surfaceY + 1;
            if (world.getBlock((int) Math.floor(sx), spawnY, (int) Math.floor(sz)) != 0) continue;
            if (world.getBlock((int) Math.floor(sx), spawnY + 1, (int) Math.floor(sz)) != 0) continue;

            // Check light level — zombies spawn in dark (light < 7)
            int skyLight = world.getSkyLight((int) Math.floor(sx), spawnY, (int) Math.floor(sz));
            int blockLight = world.getBlockLight((int) Math.floor(sx), spawnY, (int) Math.floor(sz));
            int maxLight = Math.max(skyLight, blockLight);
            if (maxLight >= HOSTILE_MAX_LIGHT) continue; // too bright for hostile mobs

            Zombie zombie = new Zombie(sx, spawnY, sz);
            entityManager.addEntity(zombie);
            System.out.printf("[Spawn] Zombie spawned at (%.1f, %d, %.1f) light=%d%n", sx, spawnY, sz, maxLight);
            return;
        }
    }

    /**
     * Find the highest solid block Y at the given XZ position.
     */
    private int findSurfaceY(World world, int x, int z) {
        for (int y = WorldConstants.WORLD_HEIGHT - 1; y >= 0; y--) {
            if (Blocks.get(world.getBlock(x, y, z)).solid()) {
                return y;
            }
        }
        return -1;
    }
}
