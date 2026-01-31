package com.voxelgame.save;

import com.voxelgame.world.Chunk;
import com.voxelgame.world.ChunkPos;
import com.voxelgame.world.World;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Top-level save/load coordinator. Manages the save directory,
 * world metadata, and region-file based chunk persistence.
 *
 * Save structure:
 *   ~/.voxelgame/saves/<world-name>/
 *     world.dat          — metadata (seed, player pos, etc.)
 *     region/
 *       r.{rx}.{rz}.dat  — region files containing chunk data
 */
public class SaveManager {

    private static final Logger LOG = Logger.getLogger(SaveManager.class.getName());

    private final Path saveDir;
    private final Path regionDir;
    private final String worldName;

    /** Cached open region files. */
    private final Map<Long, RegionFile> regionCache = new ConcurrentHashMap<>();

    public SaveManager(String worldName) {
        this.worldName = worldName;
        String home = System.getProperty("user.home");
        this.saveDir = Paths.get(home, ".voxelgame", "saves", worldName);
        this.regionDir = saveDir.resolve("region");
    }

    /** Get the save directory path. */
    public Path getSaveDir() { return saveDir; }

    /** Check if a saved world exists. */
    public boolean worldExists() {
        return WorldMeta.exists(saveDir);
    }

    // --- World Metadata ---

    public WorldMeta loadMeta() throws IOException {
        return WorldMeta.load(saveDir);
    }

    public void saveMeta(WorldMeta meta) throws IOException {
        meta.save(saveDir);
    }

    // --- Chunk I/O ---

    /**
     * Save a single chunk to its region file.
     */
    public void saveChunk(Chunk chunk) throws IOException {
        ChunkPos pos = chunk.getPos();
        byte[] data = ChunkCodec.encode(chunk);

        RegionFile region = getOrCreateRegion(pos.x(), pos.z());
        region.writeChunkData(pos.x(), pos.z(), data);

        chunk.setModified(false);
    }

    /**
     * Save all modified chunks in the world.
     * Returns the number of chunks saved.
     */
    public int saveModifiedChunks(World world) {
        int saved = 0;

        // Group modified chunks by region for batch writes
        Map<Long, List<Chunk>> byRegion = new HashMap<>();
        for (Chunk chunk : world.getLoadedChunks()) {
            if (chunk.isModified()) {
                int rx = RegionFile.toRegion(chunk.getPos().x());
                int rz = RegionFile.toRegion(chunk.getPos().z());
                long key = regionKey(rx, rz);
                byRegion.computeIfAbsent(key, k -> new ArrayList<>()).add(chunk);
            }
        }

        for (var entry : byRegion.entrySet()) {
            List<Chunk> chunks = entry.getValue();
            for (Chunk chunk : chunks) {
                try {
                    saveChunk(chunk);
                    saved++;
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Failed to save chunk " + chunk.getPos(), e);
                }
            }
        }

        return saved;
    }

    /**
     * Save ALL loaded chunks (for shutdown).
     * Returns the number of chunks saved.
     */
    public int saveAllChunks(World world) {
        int saved = 0;
        for (Chunk chunk : world.getLoadedChunks()) {
            try {
                saveChunk(chunk);
                saved++;
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to save chunk " + chunk.getPos(), e);
            }
        }
        return saved;
    }

    /**
     * Try to load a chunk from disk.
     * Returns the loaded Chunk, or null if not saved.
     */
    public Chunk loadChunk(int chunkX, int chunkZ) {
        try {
            RegionFile region = getOrCreateRegion(chunkX, chunkZ);
            if (!region.hasChunk(chunkX, chunkZ)) return null;

            byte[] data = region.readChunkData(chunkX, chunkZ);
            if (data == null) return null;

            return ChunkCodec.decode(data);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to load chunk at " + chunkX + "," + chunkZ, e);
            return null;
        }
    }

    /**
     * Check if a chunk exists on disk.
     */
    public boolean hasChunk(int chunkX, int chunkZ) {
        try {
            RegionFile region = getOrCreateRegion(chunkX, chunkZ);
            return region.hasChunk(chunkX, chunkZ);
        } catch (IOException e) {
            return false;
        }
    }

    // --- Region file management ---

    private RegionFile getOrCreateRegion(int chunkX, int chunkZ) throws IOException {
        int rx = RegionFile.toRegion(chunkX);
        int rz = RegionFile.toRegion(chunkZ);
        long key = regionKey(rx, rz);

        return regionCache.computeIfAbsent(key, k -> {
            RegionFile rf = new RegionFile(regionDir, rx, rz);
            try {
                rf.loadHeader();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to load region header for " + rx + "," + rz, e);
            }
            return rf;
        });
    }

    private static long regionKey(int rx, int rz) {
        return ((long) rx << 32) | (rz & 0xFFFFFFFFL);
    }

    /**
     * Close all cached region files and clear the cache.
     */
    public void close() {
        regionCache.clear();
    }
}
