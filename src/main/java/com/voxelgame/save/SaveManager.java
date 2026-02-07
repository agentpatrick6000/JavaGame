package com.voxelgame.save;

import com.voxelgame.bench.BenchFixes;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.ChunkPos;
import com.voxelgame.world.World;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.List;
import java.util.ArrayList;

/**
 * Top-level save/load coordinator. Manages the save directory,
 * world metadata, and region-file based chunk persistence.
 *
 * Save structure:
 *   ~/.voxelgame/saves/<world-name>/
 *     world.dat          — metadata (seed, player pos, etc.)
 *     region/
 *       r.{rx}.{rz}.dat  — region files containing chunk data
 *
 * When FIX_ASYNC_REGION_IO is enabled, saves are queued to a background
 * thread and the main thread never blocks on disk writes.
 */
public class SaveManager {

    private static final Logger LOG = Logger.getLogger(SaveManager.class.getName());

    private final Path saveDir;
    private final Path regionDir;
    private final String worldName;

    /** Cached open region files (used for sync mode). */
    private final Map<Long, RegionFile> regionCache = new ConcurrentHashMap<>();

    /** Async region writer (used when FIX_ASYNC_REGION_IO is enabled). */
    private AsyncRegionWriter asyncWriter;

    // ---- Sync mode stats ----
    private final AtomicLong syncBytesWritten = new AtomicLong(0);
    private final AtomicLong syncChunksSaved = new AtomicLong(0);
    private final AtomicLong syncIoTimeNs = new AtomicLong(0);

    public SaveManager(String worldName) {
        this.worldName = worldName;
        String home = System.getProperty("user.home");
        this.saveDir = Paths.get(home, ".voxelgame", "saves", worldName);
        this.regionDir = saveDir.resolve("region");
        
        // Initialize async writer if enabled
        if (BenchFixes.FIX_ASYNC_REGION_IO) {
            this.asyncWriter = new AsyncRegionWriter(regionDir);
        }
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
     * When FIX_ASYNC_REGION_IO is enabled, this enqueues to background thread
     * and returns immediately (never blocks main thread).
     */
    public void saveChunk(Chunk chunk) throws IOException {
        ChunkPos pos = chunk.getPos();
        byte[] data = ChunkCodec.encode(chunk);

        if (BenchFixes.FIX_ASYNC_REGION_IO && asyncWriter != null) {
            // Async mode: enqueue and return immediately
            asyncWriter.enqueue(pos.x(), pos.z(), data);
        } else {
            // Sync mode: write directly (blocks main thread)
            long startNs = System.nanoTime();
            RegionFile region = getOrCreateRegion(pos.x(), pos.z());
            region.writeChunkData(pos.x(), pos.z(), data);
            long elapsedNs = System.nanoTime() - startNs;
            
            syncBytesWritten.addAndGet(data.length);
            syncChunksSaved.incrementAndGet();
            syncIoTimeNs.addAndGet(elapsedNs);
        }

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
     * Flushes and shuts down async writer if enabled.
     */
    public void close() {
        if (asyncWriter != null) {
            asyncWriter.flushSync(); // Ensure all pending writes complete
            asyncWriter.shutdown();
            asyncWriter = null;
        }
        regionCache.clear();
    }

    // ---- IO Stats for benchmarking ----

    /** Number of pending async IO jobs. */
    public int getPendingIoJobs() {
        if (asyncWriter != null) {
            return asyncWriter.getPendingJobCount();
        }
        return 0;
    }

    /** Total bytes written to disk. */
    public long getBytesWrittenTotal() {
        if (asyncWriter != null) {
            return asyncWriter.getBytesWrittenTotal();
        }
        return syncBytesWritten.get();
    }

    /** Total chunks saved to disk. */
    public long getChunksSavedTotal() {
        if (asyncWriter != null) {
            return asyncWriter.getChunksWrittenTotal();
        }
        return syncChunksSaved.get();
    }

    /** Time spent in IO flush (ms). For async, this is on IO thread. */
    public long getIoFlushMs() {
        if (asyncWriter != null) {
            return asyncWriter.getIoFlushMs();
        }
        return syncIoTimeNs.get() / 1_000_000;
    }

    /** Time main thread was blocked on IO (ms). Should be ~0 with async. */
    public long getMainThreadBlockedMs() {
        if (asyncWriter != null) {
            return asyncWriter.getMainThreadBlockedMs();
        }
        // In sync mode, all IO time is main thread blocked time
        return syncIoTimeNs.get() / 1_000_000;
    }

    /** Check if async IO is currently enabled. */
    public boolean isAsyncIoEnabled() {
        return asyncWriter != null;
    }

    // ---- Static utilities for world management ----

    /**
     * Get the root saves directory (~/.voxelgame/saves/).
     */
    public static Path getSavesRoot() {
        String home = System.getProperty("user.home");
        return Paths.get(home, ".voxelgame", "saves");
    }

    /**
     * List all saved world folder names (directories containing world.dat).
     */
    public static List<String> listWorlds() {
        List<String> result = new ArrayList<>();
        Path savesRoot = getSavesRoot();
        if (!savesRoot.toFile().exists()) return result;

        java.io.File[] dirs = savesRoot.toFile().listFiles(java.io.File::isDirectory);
        if (dirs == null) return result;

        for (java.io.File dir : dirs) {
            if (new java.io.File(dir, "world.dat").exists()) {
                result.add(dir.getName());
            }
        }
        return result;
    }

    /**
     * Delete a world by folder name. Recursively removes the entire directory.
     * @return true if successfully deleted
     */
    public static boolean deleteWorld(String folderName) {
        Path worldDir = getSavesRoot().resolve(folderName);
        if (!worldDir.toFile().exists()) return false;

        try {
            java.nio.file.Files.walk(worldDir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try { java.nio.file.Files.delete(p); }
                    catch (IOException e) { /* best effort */ }
                });
            return !worldDir.toFile().exists();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to delete world: " + folderName, e);
            return false;
        }
    }

    /**
     * Generate a sanitized folder name from a display name.
     * Replaces spaces/special chars with underscores, ensures uniqueness.
     */
    public static String toFolderName(String displayName) {
        String base = displayName.trim()
            .replaceAll("[^a-zA-Z0-9_\\-]", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "");
        if (base.isEmpty()) base = "world";

        // Ensure uniqueness
        Path savesRoot = getSavesRoot();
        String candidate = base;
        int counter = 1;
        while (savesRoot.resolve(candidate).toFile().exists()) {
            candidate = base + "_" + counter;
            counter++;
        }
        return candidate;
    }
}
