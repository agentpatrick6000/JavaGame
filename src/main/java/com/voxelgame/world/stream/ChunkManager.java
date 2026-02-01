package com.voxelgame.world.stream;

import com.voxelgame.render.TextureAtlas;
import com.voxelgame.save.SaveManager;
import com.voxelgame.sim.Player;
import com.voxelgame.world.*;
import com.voxelgame.world.gen.GenPipeline;
import com.voxelgame.world.mesh.MeshResult;
import com.voxelgame.world.mesh.RawMeshResult;
import com.voxelgame.world.mesh.Mesher;
import com.voxelgame.world.mesh.NaiveMesher;

import java.util.*;
import java.util.concurrent.*;

/**
 * Manages chunk lifecycle: loading, generation, meshing, and unloading.
 * Uses a thread pool for chunk generation and background meshing.
 *
 * Optimizations:
 * - Thread pool (4 workers) for parallel chunk generation
 * - Background mesh building on worker threads (CPU-side only)
 * - Per-frame upload limit to prevent GPU stalls
 * - Priority-based loading (closest chunks first)
 * - Aggressive unloading of distant chunks
 */
public class ChunkManager {

    private static final int RENDER_DISTANCE = 8; // chunks
    private static final int UNLOAD_DISTANCE = RENDER_DISTANCE + 2;
    private static final int GEN_THREAD_COUNT = 4;   // generation threads
    private static final int MAX_MESH_UPLOADS_PER_FRAME = 6; // limit GPU uploads
    private static final int MAX_GEN_PER_FRAME = 8;  // max generation tasks to enqueue per frame

    private final World world;
    private Mesher mesher;
    private TextureAtlas atlas;

    /** Thread pool for chunk generation. */
    private ExecutorService genPool;

    /** Futures for in-flight chunk generation tasks. */
    private final ConcurrentHashMap<ChunkPos, Future<Chunk>> pendingGen = new ConcurrentHashMap<>();

    /** Queue of chunks that need meshing (generated but not yet meshed). */
    private final ConcurrentLinkedQueue<MeshJob> meshQueue = new ConcurrentLinkedQueue<>();

    /** Queue of completed mesh results ready for GPU upload (must happen on main thread). */
    private final ConcurrentLinkedQueue<MeshUpload> uploadQueue = new ConcurrentLinkedQueue<>();

    /** Thread pool for background meshing. */
    private ExecutorService meshPool;

    /** Set of chunks currently being meshed (to avoid double-meshing). */
    private final Set<ChunkPos> meshingInProgress = ConcurrentHashMap.newKeySet();

    private int lastPlayerCX = Integer.MIN_VALUE;
    private int lastPlayerCZ = Integer.MIN_VALUE;

    /** Save manager for loading chunks from disk. May be null if save is disabled. */
    private SaveManager saveManager;

    /** Seed to use for world generation. */
    private long seed = ChunkGenerationWorker.DEFAULT_SEED;

    /** Shared generation pipeline (thread-safe for read, individual instances for gen). */
    private GenPipeline sharedPipeline;

    public ChunkManager(World world) {
        this.world = world;
    }

    /** Set the save manager (call before init). */
    public void setSaveManager(SaveManager saveManager) {
        this.saveManager = saveManager;
    }

    /** Set the world seed (call before init). */
    public void setSeed(long seed) {
        this.seed = seed;
    }

    public void init(TextureAtlas atlas) {
        this.atlas = atlas;
        this.mesher = new NaiveMesher(atlas);
        this.sharedPipeline = GenPipeline.createDefault(seed);

        // Create thread pool for chunk generation
        genPool = Executors.newFixedThreadPool(GEN_THREAD_COUNT, r -> {
            Thread t = new Thread(r, "ChunkGen-Pool");
            t.setDaemon(true);
            return t;
        });

        // Create thread pool for background meshing (2 threads)
        meshPool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "ChunkMesh-Pool");
            t.setDaemon(true);
            return t;
        });
    }

    public void update(Player player) {
        int pcx = (int) Math.floor(player.getPosition().x / WorldConstants.CHUNK_SIZE);
        int pcz = (int) Math.floor(player.getPosition().z / WorldConstants.CHUNK_SIZE);

        // 1. Process completed chunk generations
        processCompletedGenerations();

        // 2. Upload completed meshes to GPU (limited per frame)
        processMeshUploads();

        // 3. Rebuild dirty chunks (block changes)
        for (Chunk chunk : world.getLoadedChunks()) {
            if (chunk.isDirty() && chunk.getMesh() != null) {
                buildMesh(chunk);
            }
        }

        // 4. Only update load/unload when player moves to new chunk
        if (pcx == lastPlayerCX && pcz == lastPlayerCZ) return;
        lastPlayerCX = pcx;
        lastPlayerCZ = pcz;

        // 5. Request chunks within render distance (sorted by distance, closest first)
        requestChunks(pcx, pcz);

        // 6. Unload far chunks
        unloadDistantChunks(pcx, pcz);
    }

    /**
     * Request chunks around player, sorted by distance (closest first).
     */
    private void requestChunks(int pcx, int pcz) {
        // Build list of needed chunks with distance
        List<int[]> needed = new ArrayList<>();
        for (int dx = -RENDER_DISTANCE; dx <= RENDER_DISTANCE; dx++) {
            for (int dz = -RENDER_DISTANCE; dz <= RENDER_DISTANCE; dz++) {
                if (dx * dx + dz * dz > RENDER_DISTANCE * RENDER_DISTANCE) continue;
                int cx = pcx + dx;
                int cz = pcz + dz;
                ChunkPos pos = new ChunkPos(cx, cz);
                if (!world.isLoaded(cx, cz) && !pendingGen.containsKey(pos)) {
                    needed.add(new int[]{cx, cz, dx * dx + dz * dz});
                }
            }
        }

        // Sort by distance (closest first)
        needed.sort(Comparator.comparingInt(a -> a[2]));

        // Enqueue up to MAX_GEN_PER_FRAME new generation tasks
        int enqueued = 0;
        for (int[] entry : needed) {
            if (enqueued >= MAX_GEN_PER_FRAME) break;

            int cx = entry[0], cz = entry[1];
            ChunkPos pos = new ChunkPos(cx, cz);

            // Try loading from disk first (synchronous but fast)
            if (saveManager != null) {
                Chunk loaded = saveManager.loadChunk(cx, cz);
                if (loaded != null) {
                    world.addChunk(pos, loaded);
                    Lighting.computeInitialSkyLight(loaded, world);
                    Lighting.computeInitialBlockLight(loaded, world);
                    submitMeshJob(loaded, pos);
                    continue;
                }
            }

            // Submit async generation task
            Future<Chunk> future = genPool.submit(() -> {
                // Each task creates its own pipeline to avoid thread contention
                GenPipeline pipeline = GenPipeline.createDefault(seed);
                Chunk chunk = new Chunk(pos);
                pipeline.generate(chunk);
                return chunk;
            });
            pendingGen.put(pos, future);
            enqueued++;
        }
    }

    /**
     * Check for completed generation futures and integrate results.
     */
    private void processCompletedGenerations() {
        Iterator<Map.Entry<ChunkPos, Future<Chunk>>> it = pendingGen.entrySet().iterator();
        int processed = 0;

        while (it.hasNext() && processed < MAX_GEN_PER_FRAME) {
            Map.Entry<ChunkPos, Future<Chunk>> entry = it.next();
            Future<Chunk> future = entry.getValue();

            if (future.isDone()) {
                it.remove();
                try {
                    Chunk chunk = future.get();
                    if (chunk != null) {
                        ChunkPos pos = entry.getKey();
                        world.addChunk(pos, chunk);
                        // Compute lighting on main thread (fast, and needs world access)
                        Lighting.computeInitialSkyLight(chunk, world);
                        Lighting.computeInitialBlockLight(chunk, world);
                        submitMeshJob(chunk, pos);
                        processed++;
                    }
                } catch (Exception e) {
                    System.err.println("Chunk generation failed: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Submit a mesh building job to the background mesh pool.
     * Uses meshAllRaw (CPU-only, no GL calls) on background threads.
     */
    private void submitMeshJob(Chunk chunk, ChunkPos pos) {
        if (meshingInProgress.contains(pos)) return;
        meshingInProgress.add(pos);

        meshPool.submit(() -> {
            try {
                // Build mesh data on background thread (CPU work only — no GL calls)
                RawMeshResult raw = mesher.meshAllRaw(chunk, world);
                // Queue the raw data for GPU upload on main thread
                uploadQueue.add(new MeshUpload(chunk, pos, raw));
            } catch (Exception e) {
                System.err.println("Mesh building failed for " + pos + ": " + e.getMessage());
            } finally {
                meshingInProgress.remove(pos);
            }
        });

        // Also rebuild neighbor meshes
        int[][] neighbors = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] n : neighbors) {
            ChunkPos nPos = new ChunkPos(pos.x() + n[0], pos.z() + n[1]);
            Chunk neighbor = world.getChunk(nPos.x(), nPos.z());
            if (neighbor != null && neighbor.getMesh() != null && !meshingInProgress.contains(nPos)) {
                meshingInProgress.add(nPos);
                meshPool.submit(() -> {
                    try {
                        RawMeshResult raw = mesher.meshAllRaw(neighbor, world);
                        uploadQueue.add(new MeshUpload(neighbor, nPos, raw));
                    } catch (Exception e) {
                        // Neighbor mesh rebuild failure is non-critical
                    } finally {
                        meshingInProgress.remove(nPos);
                    }
                });
            }
        }
    }

    /**
     * Process mesh uploads on the main thread (GPU operations).
     * Limited per frame to prevent stalls.
     */
    private void processMeshUploads() {
        int uploaded = 0;
        MeshUpload upload;
        while ((upload = uploadQueue.poll()) != null && uploaded < MAX_MESH_UPLOADS_PER_FRAME) {
            Chunk chunk = upload.chunk;
            RawMeshResult raw = upload.rawResult;

            // Upload to GPU on the GL thread
            MeshResult result = raw.upload();
            chunk.setMesh(result.opaqueMesh());
            chunk.setTransparentMesh(result.transparentMesh());
            chunk.setDirty(false);
            uploaded++;
        }
    }

    /**
     * Unload chunks beyond UNLOAD_DISTANCE from player.
     */
    private void unloadDistantChunks(int pcx, int pcz) {
        List<ChunkPos> toRemove = new ArrayList<>();
        for (var entry : world.getChunkMap().entrySet()) {
            ChunkPos pos = entry.getKey();
            int dx = pos.x() - pcx;
            int dz = pos.z() - pcz;
            if (dx * dx + dz * dz > UNLOAD_DISTANCE * UNLOAD_DISTANCE) {
                toRemove.add(pos);
            }
        }
        for (ChunkPos pos : toRemove) {
            // Save modified chunks before unloading
            if (saveManager != null) {
                Chunk chunk = world.getChunk(pos.x(), pos.z());
                if (chunk != null && chunk.isModified()) {
                    try {
                        saveManager.saveChunk(chunk);
                    } catch (java.io.IOException e) {
                        System.err.println("Failed to save chunk on unload: " + pos);
                    }
                }
            }
            // Cancel any pending generation
            Future<Chunk> pending = pendingGen.remove(pos);
            if (pending != null) pending.cancel(false);

            world.removeChunk(pos);
        }
    }

    /**
     * Synchronously build mesh for a chunk (used for block change rebuilds).
     * This is called from the main thread for immediate dirty chunks.
     */
    private void buildMesh(Chunk chunk) {
        MeshResult result = mesher.meshAll(chunk, world);
        chunk.setMesh(result.opaqueMesh());
        chunk.setTransparentMesh(result.transparentMesh());
        chunk.setDirty(false);
    }

    /**
     * Rebuild mesh for the chunk containing the given world coordinates.
     * Also rebuilds neighbor chunks if the block is on a chunk boundary.
     */
    public void rebuildMeshAt(int wx, int wy, int wz) {
        int cx = Math.floorDiv(wx, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(wz, WorldConstants.CHUNK_SIZE);
        int lx = Math.floorMod(wx, WorldConstants.CHUNK_SIZE);
        int lz = Math.floorMod(wz, WorldConstants.CHUNK_SIZE);

        Chunk chunk = world.getChunk(cx, cz);
        if (chunk != null) {
            buildMesh(chunk);
        }

        // Rebuild neighbors if on boundary
        if (lx == 0) rebuildChunk(cx - 1, cz);
        if (lx == WorldConstants.CHUNK_SIZE - 1) rebuildChunk(cx + 1, cz);
        if (lz == 0) rebuildChunk(cx, cz - 1);
        if (lz == WorldConstants.CHUNK_SIZE - 1) rebuildChunk(cx, cz + 1);
    }

    /**
     * Rebuild meshes for all chunks in the given set.
     */
    public void rebuildChunks(Set<ChunkPos> positions) {
        for (ChunkPos pos : positions) {
            Chunk chunk = world.getChunk(pos.x(), pos.z());
            if (chunk != null) {
                buildMesh(chunk);
            }
        }
    }

    private void rebuildChunk(int cx, int cz) {
        Chunk chunk = world.getChunk(cx, cz);
        if (chunk != null) {
            buildMesh(chunk);
        }
    }

    /** Get the generation pipeline (for spawn point finding, etc.) */
    public GenPipeline getPipeline() {
        return sharedPipeline;
    }

    public World getWorld() {
        return world;
    }

    public void shutdown() {
        if (genPool != null) {
            genPool.shutdownNow();
            try { genPool.awaitTermination(2, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        if (meshPool != null) {
            meshPool.shutdownNow();
            try { meshPool.awaitTermination(2, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    // ---- Internal data classes ----

    private static class MeshJob {
        final Chunk chunk;
        final ChunkPos pos;
        MeshJob(Chunk chunk, ChunkPos pos) {
            this.chunk = chunk;
            this.pos = pos;
        }
    }

    private static class MeshUpload {
        final Chunk chunk;
        final ChunkPos pos;
        final RawMeshResult rawResult;
        MeshUpload(Chunk chunk, ChunkPos pos, RawMeshResult rawResult) {
            this.chunk = chunk;
            this.pos = pos;
            this.rawResult = rawResult;
        }
    }
}
