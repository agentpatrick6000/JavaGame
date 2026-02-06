package com.voxelgame.sim;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.LightingUtil;
import com.voxelgame.world.World;
import com.voxelgame.world.WorldConstants;
import com.voxelgame.world.WorldTime;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Handles mob spawning and despawning logic.
 *
 * Passive spawning (pigs, cows, etc.): On grass blocks in light (effective level >= 7).
 * Hostile spawning (zombies): In dark areas (effective level < 7).
 * Despawn: Entities farther than 64 blocks from player are removed.
 *
 * Light level is computed using the unified lighting model:
 * effective_level = max(sky_contribution, block_light)
 * where sky_contribution = skyVisibility * sunBrightness * 15 / L_REF
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
    private static final int MAX_PIGS     = 15;
    private static final int MAX_COWS     = 12;
    private static final int MAX_SHEEP    = 12;
    private static final int MAX_CHICKENS = 12;
    private static final int MAX_ZOMBIES  = 20;

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

        // ---- Passive mob spawning (pigs + cows + sheep + chickens) ----
        passiveTimer += dt;
        if (passiveTimer >= PASSIVE_SPAWN_INTERVAL) {
            passiveTimer = 0;

            if (worldTime.isDay()) {
                // Random passive mob spawn
                int choice = random.nextInt(4); // 0=pig, 1=cow, 2=sheep, 3=chicken
                if (choice == 0 && entityManager.countType(EntityType.PIG) < MAX_PIGS) {
                    trySpawnPig(world, player, entityManager, worldTime);
                } else if (choice == 1 && entityManager.countType(EntityType.COW) < MAX_COWS) {
                    trySpawnCow(world, player, entityManager, worldTime);
                } else if (choice == 2 && entityManager.countType(EntityType.SHEEP) < MAX_SHEEP) {
                    trySpawnSheep(world, player, entityManager, worldTime);
                } else if (choice == 3 && entityManager.countType(EntityType.CHICKEN) < MAX_CHICKENS) {
                    trySpawnChicken(world, player, entityManager, worldTime);
                }
            }
        }

        // ---- Hostile mob spawning (zombies) — night only ----
        hostileTimer += dt;
        if (hostileTimer >= HOSTILE_SPAWN_INTERVAL) {
            hostileTimer = 0;

            if (worldTime.isNight() && entityManager.countType(EntityType.ZOMBIE) < MAX_ZOMBIES) {
                trySpawnZombie(world, player, entityManager, worldTime);
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
    private void trySpawnPig(World world, Player player, EntityManager entityManager, WorldTime worldTime) {
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

            // Check light level using unified lighting model
            float skyVis = world.getSkyVisibility((int) Math.floor(sx), spawnY, (int) Math.floor(sz));
            int blockLight = world.getBlockLight((int) Math.floor(sx), spawnY, (int) Math.floor(sz));
            float sunBrightness = worldTime.getSunBrightness();
            if (!LightingUtil.isBrightForPassives(skyVis, blockLight, sunBrightness, HOSTILE_MAX_LIGHT)) {
                continue; // too dark for passive mobs
            }

            Pig pig = new Pig(sx, spawnY, sz);
            entityManager.addEntity(pig);
            int effectiveLight = LightingUtil.computeSpawnLightLevel(skyVis, blockLight, sunBrightness);
            System.out.printf("[Spawn] Pig spawned at (%.1f, %d, %.1f) light=%d%n", sx, spawnY, sz, effectiveLight);
            return;
        }
    }

    /**
     * Attempt to spawn a cow near the player.
     * Requires grass block + air above, in bright areas (light >= 7).
     */
    private void trySpawnCow(World world, Player player, EntityManager entityManager, WorldTime worldTime) {
        Vector3f pPos = player.getPosition();

        for (int attempt = 0; attempt < 8; attempt++) {
            float angle = random.nextFloat() * (float) (Math.PI * 2);
            float dist = MIN_SPAWN_DIST + random.nextFloat() * (MAX_SPAWN_DIST - MIN_SPAWN_DIST);
            float sx = pPos.x + (float) Math.cos(angle) * dist;
            float sz = pPos.z + (float) Math.sin(angle) * dist;

            int surfaceY = WorldConstants.WORLD_HEIGHT - 1;
            for (int y = WorldConstants.WORLD_HEIGHT - 1; y >= 0; y--) {
                if (world.getBlock((int) Math.floor(sx), y, (int) Math.floor(sz)) != 0) {
                    surfaceY = y;
                    break;
                }
            }

            // Must spawn on grass
            if (world.getBlock((int) Math.floor(sx), surfaceY, (int) Math.floor(sz)) != Blocks.GRASS.id())
                continue;

            int spawnY = surfaceY + 1;
            if (world.getBlock((int) Math.floor(sx), spawnY, (int) Math.floor(sz)) != 0) continue;
            if (world.getBlock((int) Math.floor(sx), spawnY + 1, (int) Math.floor(sz)) != 0) continue;

            // Check light level using unified lighting model
            float skyVis = world.getSkyVisibility((int) Math.floor(sx), spawnY, (int) Math.floor(sz));
            int blockLight = world.getBlockLight((int) Math.floor(sx), spawnY, (int) Math.floor(sz));
            float sunBrightness = worldTime.getSunBrightness();
            if (!LightingUtil.isBrightForPassives(skyVis, blockLight, sunBrightness, HOSTILE_MAX_LIGHT)) {
                continue; // too dark for passive mobs
            }

            Cow cow = new Cow(sx, spawnY, sz);
            entityManager.addEntity(cow);
            int effectiveLight = LightingUtil.computeSpawnLightLevel(skyVis, blockLight, sunBrightness);
            System.out.printf("[Spawn] Cow spawned at (%.1f, %d, %.1f) light=%d%n", sx, spawnY, sz, effectiveLight);
            return;
        }
    }

    /**
     * Attempt to spawn a sheep near the player.
     * Requires grass block + air above, in bright areas (light >= 7).
     */
    private void trySpawnSheep(World world, Player player, EntityManager entityManager, WorldTime worldTime) {
        Vector3f pPos = player.getPosition();

        for (int attempt = 0; attempt < 8; attempt++) {
            float angle = random.nextFloat() * (float) (Math.PI * 2);
            float dist = MIN_SPAWN_DIST + random.nextFloat() * (MAX_SPAWN_DIST - MIN_SPAWN_DIST);
            float sx = pPos.x + (float) Math.cos(angle) * dist;
            float sz = pPos.z + (float) Math.sin(angle) * dist;

            int surfaceY = WorldConstants.WORLD_HEIGHT - 1;
            for (int y = WorldConstants.WORLD_HEIGHT - 1; y >= 0; y--) {
                if (world.getBlock((int) Math.floor(sx), y, (int) Math.floor(sz)) != 0) {
                    surfaceY = y;
                    break;
                }
            }

            // Must spawn on grass
            if (world.getBlock((int) Math.floor(sx), surfaceY, (int) Math.floor(sz)) != Blocks.GRASS.id())
                continue;

            int spawnY = surfaceY + 1;
            if (world.getBlock((int) Math.floor(sx), spawnY, (int) Math.floor(sz)) != 0) continue;
            if (world.getBlock((int) Math.floor(sx), spawnY + 1, (int) Math.floor(sz)) != 0) continue;

            // Check light level using unified lighting model
            float skyVis = world.getSkyVisibility((int) Math.floor(sx), spawnY, (int) Math.floor(sz));
            int blockLight = world.getBlockLight((int) Math.floor(sx), spawnY, (int) Math.floor(sz));
            float sunBrightness = worldTime.getSunBrightness();
            if (!LightingUtil.isBrightForPassives(skyVis, blockLight, sunBrightness, HOSTILE_MAX_LIGHT)) {
                continue; // too dark for passive mobs
            }

            Sheep sheep = new Sheep(sx, spawnY, sz);
            entityManager.addEntity(sheep);
            int effectiveLight = LightingUtil.computeSpawnLightLevel(skyVis, blockLight, sunBrightness);
            System.out.printf("[Spawn] Sheep spawned at (%.1f, %d, %.1f) light=%d%n", sx, spawnY, sz, effectiveLight);
            return;
        }
    }

    /**
     * Attempt to spawn a chicken near the player.
     * Requires grass block + air above, in bright areas (light >= 7).
     */
    private void trySpawnChicken(World world, Player player, EntityManager entityManager, WorldTime worldTime) {
        Vector3f pPos = player.getPosition();

        for (int attempt = 0; attempt < 8; attempt++) {
            float angle = random.nextFloat() * (float) (Math.PI * 2);
            float dist = MIN_SPAWN_DIST + random.nextFloat() * (MAX_SPAWN_DIST - MIN_SPAWN_DIST);
            float sx = pPos.x + (float) Math.cos(angle) * dist;
            float sz = pPos.z + (float) Math.sin(angle) * dist;

            int surfaceY = WorldConstants.WORLD_HEIGHT - 1;
            for (int y = WorldConstants.WORLD_HEIGHT - 1; y >= 0; y--) {
                if (world.getBlock((int) Math.floor(sx), y, (int) Math.floor(sz)) != 0) {
                    surfaceY = y;
                    break;
                }
            }

            // Must spawn on grass
            if (world.getBlock((int) Math.floor(sx), surfaceY, (int) Math.floor(sz)) != Blocks.GRASS.id())
                continue;

            int spawnY = surfaceY + 1;
            if (world.getBlock((int) Math.floor(sx), spawnY, (int) Math.floor(sz)) != 0) continue;
            if (world.getBlock((int) Math.floor(sx), spawnY + 1, (int) Math.floor(sz)) != 0) continue;

            // Check light level using unified lighting model
            float skyVis = world.getSkyVisibility((int) Math.floor(sx), spawnY, (int) Math.floor(sz));
            int blockLight = world.getBlockLight((int) Math.floor(sx), spawnY, (int) Math.floor(sz));
            float sunBrightness = worldTime.getSunBrightness();
            if (!LightingUtil.isBrightForPassives(skyVis, blockLight, sunBrightness, HOSTILE_MAX_LIGHT)) {
                continue; // too dark for passive mobs
            }

            Chicken chicken = new Chicken(sx, spawnY, sz);
            entityManager.addEntity(chicken);
            int effectiveLight = LightingUtil.computeSpawnLightLevel(skyVis, blockLight, sunBrightness);
            System.out.printf("[Spawn] Chicken spawned at (%.1f, %d, %.1f) light=%d%n", sx, spawnY, sz, effectiveLight);
            return;
        }
    }

    /**
     * Attempt to spawn a zombie near the player.
     * Requires solid surface with air above, in dark areas (light < 7).
     */
    private void trySpawnZombie(World world, Player player, EntityManager entityManager, WorldTime worldTime) {
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

            // Check light level using unified lighting model — zombies spawn in dark (light < 7)
            float skyVis = world.getSkyVisibility((int) Math.floor(sx), spawnY, (int) Math.floor(sz));
            int blockLight = world.getBlockLight((int) Math.floor(sx), spawnY, (int) Math.floor(sz));
            float sunBrightness = worldTime.getSunBrightness();
            if (!LightingUtil.isDarkForHostiles(skyVis, blockLight, sunBrightness, HOSTILE_MAX_LIGHT)) {
                continue; // too bright for hostile mobs
            }

            Zombie zombie = new Zombie(sx, spawnY, sz);
            entityManager.addEntity(zombie);
            int effectiveLight = LightingUtil.computeSpawnLightLevel(skyVis, blockLight, sunBrightness);
            System.out.printf("[Spawn] Zombie spawned at (%.1f, %d, %.1f) light=%d%n", sx, spawnY, sz, effectiveLight);
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
