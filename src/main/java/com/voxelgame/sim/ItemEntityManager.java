package com.voxelgame.sim;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages all item entities in the world.
 * Handles spawning, updating, collection, and despawning.
 */
public class ItemEntityManager {

    /** Maximum number of item entities in the world (performance limit). */
    private static final int MAX_ITEMS = 256;

    private final List<ItemEntity> items = new ArrayList<>();

    /**
     * Spawn a dropped item at the given position.
     * The item will be given a small random velocity.
     *
     * @param blockId the block type
     * @param count   number of items
     * @param x       block X position
     * @param y       block Y position
     * @param z       block Z position
     */
    public void spawnDrop(int blockId, int count, float x, float y, float z) {
        if (blockId <= 0 || count <= 0) return;

        // Enforce max items limit
        if (items.size() >= MAX_ITEMS) {
            items.remove(0);
        }

        ItemEntity item = new ItemEntity(blockId, count, x + 0.5f, y + 0.5f, z + 0.5f);
        items.add(item);

        System.out.printf("[ItemDrop] Spawned %s x%d at (%.1f, %.1f, %.1f)%n",
            Blocks.get(blockId).name(), count, x + 0.5f, y + 0.5f, z + 0.5f);
    }

    /**
     * Update all item entities and handle collection.
     *
     * @param dt        delta time
     * @param world     world for physics
     * @param player    player for pickup
     * @param inventory player inventory to add items to
     */
    public void update(float dt, World world, Player player, Inventory inventory) {
        if (items.isEmpty()) return;

        float px = player.getPosition().x;
        float py = player.getPosition().y;
        float pz = player.getPosition().z;

        Iterator<ItemEntity> iter = items.iterator();
        while (iter.hasNext()) {
            ItemEntity item = iter.next();

            // Update physics and animation
            item.update(dt, world);

            // Check for player pickup
            if (item.canPickUp() && !player.isDead()) {
                if (item.isInPickupRange(px, py, pz, Player.EYE_HEIGHT)) {
                    // Try to add to inventory
                    int leftover = inventory.addItem(item.getBlockId(), item.getCount());
                    if (leftover < item.getCount()) {
                        int picked = item.getCount() - leftover;
                        item.kill();
                        System.out.printf("[ItemPickup] Collected %s x%d%n",
                            Blocks.get(item.getBlockId()).name(), picked);
                    }
                }
            }

            // Remove dead items
            if (item.isDead()) {
                iter.remove();
            }
        }
    }

    /** Get all active item entities (for rendering). */
    public List<ItemEntity> getItems() {
        return items;
    }

    /** Get number of active items. */
    public int getItemCount() {
        return items.size();
    }

    /** Clear all items (e.g., on world unload). */
    public void clear() {
        items.clear();
    }
}
