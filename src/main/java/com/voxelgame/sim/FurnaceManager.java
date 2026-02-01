package com.voxelgame.sim;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages all furnace tile entities in the world.
 * Provides lookup by position, creation, removal, and tick updating.
 */
public class FurnaceManager {

    private final List<Furnace> furnaces = new ArrayList<>();

    /**
     * Create a new furnace at the given position.
     * If one already exists there, returns the existing one.
     */
    public Furnace createFurnace(int x, int y, int z) {
        Furnace existing = getFurnaceAt(x, y, z);
        if (existing != null) return existing;

        Furnace furnace = new Furnace(x, y, z);
        furnaces.add(furnace);
        System.out.printf("[Furnace] Created furnace at (%d, %d, %d)%n", x, y, z);
        return furnace;
    }

    /**
     * Get the furnace at the given position, or null if none.
     */
    public Furnace getFurnaceAt(int x, int y, int z) {
        for (Furnace f : furnaces) {
            if (f.isAt(x, y, z)) return f;
        }
        return null;
    }

    /**
     * Remove the furnace at the given position and return it (for dropping items).
     */
    public Furnace removeFurnace(int x, int y, int z) {
        Iterator<Furnace> iter = furnaces.iterator();
        while (iter.hasNext()) {
            Furnace f = iter.next();
            if (f.isAt(x, y, z)) {
                iter.remove();
                System.out.printf("[Furnace] Removed furnace at (%d, %d, %d)%n", x, y, z);
                return f;
            }
        }
        return null;
    }

    /**
     * Tick all furnaces. Called every game tick from the main loop.
     */
    public void tickAll() {
        for (Furnace f : furnaces) {
            f.tick();
        }
    }

    public List<Furnace> getAllFurnaces() {
        return furnaces;
    }

    public void clear() {
        furnaces.clear();
    }

    public int getFurnaceCount() {
        return furnaces.size();
    }
}
