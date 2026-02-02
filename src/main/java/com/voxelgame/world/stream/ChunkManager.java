package com.voxelgame.world.stream;

import com.voxelgame.render.TextureAtlas;
import com.voxelgame.save.SaveManager;
import com.voxelgame.sim.Player;
import com.voxelgame.world.*;
import com.voxelgame.world.gen.GenPipeline;
import com.voxelgame.world.gen.LODGenPipeline;
import com.voxelgame.world.lod.LODConfig;
import com.voxelgame.world.lod.LODLevel;
import com.voxelgame.world.lod.LODMesher;
import com.voxelgame.world.mesh.MeshResult;
import com.voxelgame.world.mesh.RawMeshResult;
import com.voxelgame.world.mesh.Mesher;
import com.voxelgame.world.mesh.NaiveMesher;

import java.util.*;
import java.util.concurrent.*;

/**
 * Manages chunk lifecycle: loading, generation, meshing, and unloading.
 * Supports multi-tier LOD with distance-based mesh quality selection.
 *
 * Optimizations:
 * - Thread pool (4 workers) for parallel chunk generation
 * - Background mesh building on worker threads (CPU-side only)
 * - Per-frame upload limit to prevent GPU stalls
 * - Priority-based loading (closest chunks first)
 * - LOD-aware: distant chunks get simplified meshes
 * - Aggressive unloading of chunks beyond max render distance
 */
public class ChunkManager {

    private final World world;
    private Mesher mesher;
    private LODMesher lodMesher;
    private TextureAtlas atlas;

    /** LOD configuration — controls distances and quality. */
    private final LODConfig lodConfig = new LODConfig();

    /** Thread pool for chunk generation. */
    private ExecutorService genPool;

    /** Futures for in-flight chunk generation tasks. */
    private final ConcurrentHashMap<ChunkPos, Future<Chunk>> pendingGen = new ConcurrentHashMap<>();

    /** Queue of completed mesh results ready for GPU upload (must happen on main thread). */
    private final ConcurrentLinkedQueue<MeshUpload> uploadQueue = new ConcurrentLinkedQueue<>();

    /** Queue of completed LOD mesh results ready for GPU upload. */
    private final ConcurrentLinkedQueue<LODMeshUpload> lodUploadQueue = new ConcurrentLinkedQueue<>();

    /** Thread pool for background meshing. */
    private ExecutorService meshPool;

    /** Set of chunks currently being meshed (to avoid double-meshing). */
    private final Set<ChunkPos> meshingInProgress = ConcurrentHashMap.newKeySet();

    /** Set of chunks currently being LOD meshed. */
    private final Set<ChunkPos> lodMeshingInProgress = ConcurrentHashMap.newKeySet();

    private int lastPlayerCX = Integer.MIN_VALUE;
    private int lastPlayerCZ = Integer.MIN_VALUE;

    /** Save manager for loading chunks from disk. May be null if save is disabled. */
    private SaveManager saveManager;

    /** Seed to use for world generation. */
    private long seed = ChunkGenerationWorker.DEFAULT_SEED;

    /** Shared generation pipeline (thread-safe for read, individual instances for gen). */
    private GenPipeline sharedPipeline;

    /** Frame counter for LOD update throttling. */
    private int frameCount = 0;

    // ---- Stats for debug overlay ----
    private volatile int lod0Count, lod1Count, lod2Count, lod3Count;
    private volatile int pendingUploads;

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

    /** Get the LOD configuration for settings changes. */
    public LODConfig getLodConfig() {
        return lodConfig;
    }

    public void init(TextureAtlas atlas) {
        this.atlas = atlas;
        this.mesher = new NaiveMesher(atlas);
        this.lodMesher = new LODMesher(atlas);
        this.sharedPipeline = GenPipeline.createDefault(seed);

        // Create thread pool for chunk generation
        genPool = Executors.newFixedThreadPool(LODConfig.GEN_THREAD_COUNT, r -> {
            Thread t = new Thread(r, "ChunkGen-Pool");
            t.setDaemon(true);
            return t;
        });

        // Create thread pool for background meshing (3 threads for LOD support)
        meshPool = Executors.newFixedThreadPool(LODConfig.MESH_THREAD_COUNT, r -> {
            Thread t = new Thread(r, "ChunkMesh-Pool");
            t.setDaemon(true);
            return t;
        });
    }

    public void update(Player player) {
        int pcx = (int) Math.floor(player.getPosition().x / WorldConstants.CHUNK_SIZE);
        int pcz = (int) Math.floor(player.getPosition().z / WorldConstants.CHUNK_SIZE);
        frameCount++;

        // 1. Process completed chunk generations
        processCompletedGenerations();

        // 2. Upload completed meshes to GPU (limited per frame)
        processMeshUploads();
        processLODMeshUploads();

        // 3. Rebuild dirty chunks (block changes) — only for close chunks
        for (Chunk chunk : world.getLoadedChunks()) {
            if (chunk.isDirty() && chunk.getMesh() != null &&
                chunk.getCurrentLOD() == LODLevel.LOD_0) {
                buildMesh(chunk);
            }
        }

        // 4. Update LOD levels for loaded chunks (every 10 frames to reduce overhead)
        if (frameCount % 10 == 0) {
            updateLODLevels(pcx, pcz);
        }

        // 5. Only update load/unload when player moves to new chunk
        if (pcx == lastPlayerCX && pcz == lastPlayerCZ) return;
        lastPlayerCX = pcx;
        lastPlayerCZ = pcz;

        // 6. Request chunks within full LOD render distance (sorted by distance, closest first)
        requestChunks(pcx, pcz);

        // 7. Unload far chunks
        unloadDistantChunks(pcx, pcz);
    }

    /**
     * Update LOD levels for all loaded chunks based on distance to player.
     * Triggers LOD mesh rebuilding when a chunk's LOD level changes.
     *
     * Uses a hysteresis buffer (2 chunks) to prevent LOD flickering
     * when the player is near a boundary.
     */
    private void updateLODLevels(int pcx, int pcz) {
        int l0 = 0, l1 = 0, l2 = 0, l3 = 0;
        int meshJobsSubmitted = 0;
        int maxMeshJobsPerUpdate = 8; // Don't overwhelm the mesh pool

        for (var entry : world.getChunkMap().entrySet()) {
            ChunkPos pos = entry.getKey();
            Chunk chunk = entry.getValue();

            int dx = pos.x() - pcx;
            int dz = pos.z() - pcz;
            int distSq = dx * dx + dz * dz;

            LODLevel newLOD = lodConfig.getLevelForDistance(distSq);
            LODLevel oldLOD = chunk.getCurrentLOD();

            // Hysteresis: only upgrade (increase detail) at boundary - 2 chunks,
            // only downgrade (decrease detail) at boundary + 2 chunks.
            // This prevents flickering when walking along a LOD boundary.
            if (newLOD.level() < oldLOD.level()) {
                // Upgrading — require 2 chunks past the boundary
                LODLevel checkLOD = lodConfig.getLevelForDistance(distSq + 4);
                if (checkLOD.level() >= oldLOD.level()) {
                    newLOD = oldLOD; // Not far enough past boundary yet
                }
            }

            // Count stats
            switch (newLOD) {
                case LOD_0 -> l0++;
                case LOD_1 -> l1++;
                case LOD_2 -> l2++;
                case LOD_3 -> l3++;
            }

            if (newLOD != oldLOD) {
                chunk.setCurrentLOD(newLOD);

                if (meshJobsSubmitted >= maxMeshJobsPerUpdate) continue;

                // If upgrading to LOD 0 and no full mesh, rebuild it
                if (newLOD == LODLevel.LOD_0 && (chunk.getMesh() == null || chunk.getMesh().isEmpty())) {
                    // Also compute lighting if missing
                    if (chunk.isLightDirty()) {
                        Lighting.computeInitialSkyLight(chunk, world);
                        Lighting.computeInitialBlockLight(chunk, world);
                    }
                    submitMeshJob(chunk, pos, false);
                    meshJobsSubmitted++;
                }
                // If downgrading, check if LOD mesh already cached
                else if (newLOD.level() > 0 && chunk.getLodMesh(newLOD.level()) == null) {
                    submitLODMeshJob(chunk, pos, newLOD);
                    meshJobsSubmitted++;
                } else {
                    chunk.setLodMeshReady(true);
                }
            }
        }

        this.lod0Count = l0;
        this.lod1Count = l1;
        this.lod2Count = l2;
        this.lod3Count = l3;
        this.pendingUploads = uploadQueue.size() + lodUploadQueue.size();
    }

    /**
     * Request chunks around player using spiral pattern for efficient loading.
     * Close chunks (LOD 0-1) are prioritized, then distant (LOD 2-3).
     *
     * Uses a spiral scan pattern limited by per-frame budgets to avoid
     * scanning all 65k+ positions in a 128-chunk radius each frame.
     */
    private void requestChunks(int pcx, int pcz) {
        int maxDist = lodConfig.getMaxRenderDistance();
        int closeMax = LODConfig.MAX_CLOSE_GEN_PER_FRAME;
        int farMax = LODConfig.MAX_FAR_GEN_PER_FRAME;
        int closeEnqueued = 0;
        int farEnqueued = 0;

        // Spiral scan: dx,dz from center outward
        // This naturally prioritizes close chunks without needing a full sort
        for (int ring = 0; ring <= maxDist; ring++) {
            if (closeEnqueued >= closeMax && farEnqueued >= farMax) break;

            // Scan the perimeter of the current ring
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    // Only process the ring perimeter (not interior — already done)
                    if (Math.abs(dx) != ring && Math.abs(dz) != ring) continue;

                    int distSq = dx * dx + dz * dz;
                    if (distSq > maxDist * maxDist) continue;

                    int cx = pcx + dx;
                    int cz = pcz + dz;
                    ChunkPos pos = new ChunkPos(cx, cz);
                    if (world.isLoaded(cx, cz) || pendingGen.containsKey(pos)) continue;

                    LODLevel level = lodConfig.getLevelForDistance(distSq);
                    boolean isClose = (level == LODLevel.LOD_0 || level == LODLevel.LOD_1);

                    if (isClose && closeEnqueued >= closeMax) continue;
                    if (!isClose && farEnqueued >= farMax) continue;

                    // Try loading from disk first
                    if (saveManager != null) {
                        Chunk loaded = saveManager.loadChunk(cx, cz);
                        if (loaded != null) {
                            loaded.setCurrentLOD(level);
                            world.addChunk(pos, loaded);
                            if (level == LODLevel.LOD_0) {
                                Lighting.computeInitialSkyLight(loaded, world);
                                Lighting.computeInitialBlockLight(loaded, world);
                                submitMeshJob(loaded, pos, true);
                            } else {
                                submitLODMeshJob(loaded, pos, level);
                            }
                            if (isClose) closeEnqueued++; else farEnqueued++;
                            continue;
                        }
                    }

                    // Submit async generation task
                    // Use simplified pipeline for distant chunks (LOD 2+)
                    final LODLevel genLevel = level;
                    Future<Chunk> future = genPool.submit(() -> {
                        Chunk chunk = new Chunk(pos);
                        if (genLevel.level() >= 2) {
                            // Simplified: terrain + surface only (no caves/ores/trees)
                            LODGenPipeline lodPipeline = new LODGenPipeline(seed);
                            lodPipeline.generateSimplified(chunk);
                        } else {
                            GenPipeline pipeline = GenPipeline.createDefault(seed);
                            pipeline.generate(chunk);
                        }
                        chunk.setCurrentLOD(genLevel);
                        return chunk;
                    });
                    pendingGen.put(pos, future);
                    if (isClose) closeEnqueued++; else farEnqueued++;
                }
            }
        }
    }

    /**
     * Check for completed generation futures and integrate results.
     */
    private void processCompletedGenerations() {
        Iterator<Map.Entry<ChunkPos, Future<Chunk>>> it = pendingGen.entrySet().iterator();
        int processed = 0;
        int maxProcess = LODConfig.MAX_CLOSE_GEN_PER_FRAME + LODConfig.MAX_FAR_GEN_PER_FRAME;

        while (it.hasNext() && processed < maxProcess) {
            Map.Entry<ChunkPos, Future<Chunk>> entry = it.next();
            Future<Chunk> future = entry.getValue();

            if (future.isDone()) {
                it.remove();
                try {
                    Chunk chunk = future.get();
                    if (chunk != null) {
                        ChunkPos pos = entry.getKey();
                        world.addChunk(pos, chunk);
                        LODLevel level = chunk.getCurrentLOD();

                        if (level == LODLevel.LOD_0) {
                            Lighting.computeInitialSkyLight(chunk, world);
                            Lighting.computeInitialBlockLight(chunk, world);
                            submitMeshJob(chunk, pos, true);
                        } else {
                            // For distant chunks, skip full lighting computation
                            submitLODMeshJob(chunk, pos, level);
                        }
                        processed++;
                    }
                } catch (Exception e) {
                    System.err.println("Chunk generation failed: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Submit a full mesh building job to the background mesh pool.
     * Uses meshAllRaw (CPU-only, no GL calls) on background threads.
     */
    private void submitMeshJob(Chunk chunk, ChunkPos pos, boolean rebuildNeighbors) {
        if (meshingInProgress.contains(pos)) return;
        meshingInProgress.add(pos);

        meshPool.submit(() -> {
            try {
                RawMeshResult raw = mesher.meshAllRaw(chunk, world);
                uploadQueue.add(new MeshUpload(chunk, pos, raw));
            } catch (Exception e) {
                System.err.println("Mesh building failed for " + pos + ": " + e.getMessage());
            } finally {
                meshingInProgress.remove(pos);
            }
        });

        // Also rebuild neighbor meshes (only for close chunks)
        if (rebuildNeighbors) {
            int[][] neighbors = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
            for (int[] n : neighbors) {
                ChunkPos nPos = new ChunkPos(pos.x() + n[0], pos.z() + n[1]);
                Chunk neighbor = world.getChunk(nPos.x(), nPos.z());
                if (neighbor != null && neighbor.getMesh() != null &&
                    neighbor.getCurrentLOD() == LODLevel.LOD_0 &&
                    !meshingInProgress.contains(nPos)) {
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
    }

    /**
     * Submit a LOD mesh building job.
     */
    private void submitLODMeshJob(Chunk chunk, ChunkPos pos, LODLevel level) {
        if (lodMeshingInProgress.contains(pos)) return;
        lodMeshingInProgress.add(pos);

        meshPool.submit(() -> {
            try {
                RawMeshResult raw = lodMesher.meshForLOD(chunk, world, level);
                if (raw != null) {
                    lodUploadQueue.add(new LODMeshUpload(chunk, pos, raw, level));
                }
            } catch (Exception e) {
                System.err.println("LOD mesh building failed for " + pos + " (LOD " + level + "): " + e.getMessage());
            } finally {
                lodMeshingInProgress.remove(pos);
            }
        });
    }

    /**
     * Process full mesh uploads on the main thread (GPU operations).
     */
    private void processMeshUploads() {
        int uploaded = 0;
        MeshUpload upload;
        while ((upload = uploadQueue.poll()) != null && uploaded < LODConfig.MAX_MESH_UPLOADS_PER_FRAME) {
            Chunk chunk = upload.chunk;
            RawMeshResult raw = upload.rawResult;

            MeshResult result = raw.upload();
            chunk.setMesh(result.opaqueMesh());
            chunk.setTransparentMesh(result.transparentMesh());
            chunk.setDirty(false);
            if (chunk.getCurrentLOD() == LODLevel.LOD_0) {
                chunk.setLodMeshReady(true);
            }
            uploaded++;
        }
    }

    /**
     * Process LOD mesh uploads on the main thread.
     */
    private void processLODMeshUploads() {
        int uploaded = 0;
        LODMeshUpload upload;
        while ((upload = lodUploadQueue.poll()) != null && uploaded < LODConfig.MAX_LOD_UPLOADS_PER_FRAME) {
            Chunk chunk = upload.chunk;
            RawMeshResult raw = upload.rawResult;
            int level = upload.level.level();

            // Upload only the opaque mesh for LOD levels
            var opaqueMesh = raw.opaqueData() != null ? raw.opaqueData().toChunkMesh() : null;
            chunk.setLodMesh(level, opaqueMesh);
            if (chunk.getCurrentLOD() == upload.level) {
                chunk.setLodMeshReady(true);
            }
            uploaded++;
        }
    }

    /**
     * Unload chunks beyond max render distance from player.
     */
    private void unloadDistantChunks(int pcx, int pcz) {
        int unloadDist = lodConfig.getUnloadDistance();
        int unloadDistSq = unloadDist * unloadDist;

        List<ChunkPos> toRemove = new ArrayList<>();
        for (var entry : world.getChunkMap().entrySet()) {
            ChunkPos pos = entry.getKey();
            int dx = pos.x() - pcx;
            int dz = pos.z() - pcz;
            if (dx * dx + dz * dz > unloadDistSq) {
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

    // ---- LOD stats for debug overlay ----
    public int getLod0Count() { return lod0Count; }
    public int getLod1Count() { return lod1Count; }
    public int getLod2Count() { return lod2Count; }
    public int getLod3Count() { return lod3Count; }
    public int getPendingUploads() { return pendingUploads; }
    public int getTotalChunks() { return world.getChunkMap().size(); }

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

    private static class LODMeshUpload {
        final Chunk chunk;
        final ChunkPos pos;
        final RawMeshResult rawResult;
        final LODLevel level;
        LODMeshUpload(Chunk chunk, ChunkPos pos, RawMeshResult rawResult, LODLevel level) {
            this.chunk = chunk;
            this.pos = pos;
            this.rawResult = rawResult;
            this.level = level;
        }
    }
}
