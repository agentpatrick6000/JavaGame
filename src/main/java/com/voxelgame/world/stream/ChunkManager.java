package com.voxelgame.world.stream;

import com.voxelgame.render.SkySystem;
import com.voxelgame.render.TextureAtlas;
import com.voxelgame.save.SaveManager;
import com.voxelgame.sim.Player;
import com.voxelgame.world.*;
import com.voxelgame.world.gen.GenConfig;
import com.voxelgame.world.gen.GenPipeline;
import com.voxelgame.world.gen.LODGenPipeline;
import com.voxelgame.world.lighting.ProbeManager;
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

    /** World generation config (preset + advanced settings). */
    private GenConfig genConfig = GenConfig.defaultConfig();

    /** Shared generation pipeline (thread-safe for read, individual instances for gen). */
    private GenPipeline sharedPipeline;

    /** Frame counter for LOD update throttling. */
    private int frameCount = 0;

    /** Probe manager for indirect lighting (Phase 3). */
    private ProbeManager probeManager;
    
    /** Sky system for probe lighting calculations. */
    private SkySystem skySystem;
    
    /** Current time of day for probe updates (0-1). */
    private float currentTimeOfDay = 0.5f;

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
    
    /** Get the world seed. */
    public long getSeed() {
        return seed;
    }

    /** Set the world generation config (call before init). */
    public void setGenConfig(GenConfig config) {
        this.genConfig = config != null ? config : GenConfig.defaultConfig();
    }

    /** Get the current generation config. */
    public GenConfig getGenConfig() {
        return genConfig;
    }

    /** Get the LOD configuration for settings changes. */
    public LODConfig getLodConfig() {
        return lodConfig;
    }

    public void init(TextureAtlas atlas) {
        this.atlas = atlas;
        this.mesher = new NaiveMesher(atlas);
        this.lodMesher = new LODMesher(atlas);
        this.sharedPipeline = GenPipeline.createWithConfig(seed, genConfig);
        
        // Initialize probe manager for indirect lighting (Phase 3)
        this.probeManager = new ProbeManager();
        this.skySystem = new SkySystem();
        this.probeManager.init(skySystem);
        
        // Wire probe manager to mesher for sampling
        NaiveMesher.setProbeManager(probeManager);

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

        // Periodic debug logging for chunk performance monitoring
        if (frameCount % 300 == 0) { // ~every 5-10 seconds
            System.out.println("[ChunkPerf] loaded=" + world.getChunkMap().size()
                + " pending=" + pendingGen.size()
                + " cap=" + lodConfig.getMaxLoadedChunks()
                + " maxDist=" + lodConfig.getMaxRenderDistance()
                + " LOD[0/1/2/3]=" + lod0Count + "/" + lod1Count + "/" + lod2Count + "/" + lod3Count
                + " uploads=" + (uploadQueue.size() + lodUploadQueue.size()));
        }

        // 1. Process completed chunk generations
        processCompletedGenerations();

        // 2. Upload completed meshes to GPU (limited per frame)
        processMeshUploads();
        processLODMeshUploads();

        // 3. Rebuild dirty chunks (block changes) — only for close chunks
        // ASYNC FIX: Submit to mesh pool instead of blocking main thread
        int dirtyRebuilds = 0;
        for (Chunk chunk : world.getLoadedChunks()) {
            if (chunk.isDirty() && chunk.getMesh() != null &&
                chunk.getCurrentLOD() == LODLevel.LOD_0 &&
                dirtyRebuilds < 4) {  // Limit per-frame to avoid queue flood
                ChunkPos pos = chunk.getPos();
                if (!meshingInProgress.contains(pos)) {
                    submitMeshJob(chunk, pos, false);
                    chunk.setDirty(false);  // Clear dirty flag after submit
                    dirtyRebuilds++;
                }
            }
        }

        // 4. Update LOD levels for loaded chunks (every 15 frames for responsive transitions)
        if (frameCount % 15 == 0) {
            updateLODLevels(pcx, pcz);
        }

        // 5. Update probe manager player position and dirty probes
        if (probeManager != null) {
            probeManager.updatePlayerPosition(player.getPosition().x, player.getPosition().z);
            probeManager.updateDirtyProbes(world, currentTimeOfDay);
        }

        boolean playerMovedChunk = (pcx != lastPlayerCX || pcz != lastPlayerCZ);
        if (playerMovedChunk) {
            lastPlayerCX = pcx;
            lastPlayerCZ = pcz;

            // 5. Unload far chunks FIRST to free space before loading new ones
            unloadDistantChunks(pcx, pcz);

            // 6. Enforce hard chunk cap — aggressively unload farthest if over limit
            enforceChunkCap(pcx, pcz);
        }

        // 7. Request chunks every frame (budget-limited) until world is filled.
        // This ensures continuous loading rather than only on chunk crossings.
        if (world.getChunkMap().size() + pendingGen.size() < lodConfig.getMaxLoadedChunks()) {
            requestChunks(pcx, pcz);
        }
    }

    /**
     * Update LOD levels for all loaded chunks based on distance to player.
     * Triggers LOD mesh rebuilding when a chunk's LOD level changes.
     *
     * Uses a hysteresis buffer (2 chunks) to prevent LOD flickering
     * when the player is near a boundary.
     *
     * CRITICAL: LOD level is only changed when we can also submit the
     * corresponding mesh job. This prevents chunks from being stuck at
     * a LOD level with no mesh for that level.
     */
    private void updateLODLevels(int pcx, int pcz) {
        int l0 = 0, l1 = 0, l2 = 0, l3 = 0;
        int meshJobsSubmitted = 0;
        int maxMeshJobsPerUpdate = 16; // Increased from 8 to handle more transitions per update

        for (var entry : world.getChunkMap().entrySet()) {
            ChunkPos pos = entry.getKey();
            Chunk chunk = entry.getValue();

            int dx = pos.x() - pcx;
            int dz = pos.z() - pcz;
            int distSq = dx * dx + dz * dz;

            LODLevel newLOD = lodConfig.getLevelForDistance(distSq);
            LODLevel oldLOD = chunk.getCurrentLOD();

            // Hysteresis: only upgrade (increase detail) when firmly past the boundary.
            // Uses actual chunk distance + 2 buffer, not just distSq + 4.
            if (newLOD.level() < oldLOD.level()) {
                // Check what LOD we'd be at 2 chunks farther out
                int dist = (int) Math.sqrt(distSq);
                int checkDistSq = (dist + 2) * (dist + 2);
                LODLevel checkLOD = lodConfig.getLevelForDistance(checkDistSq);
                if (checkLOD.level() >= oldLOD.level()) {
                    newLOD = oldLOD; // Not far enough past boundary yet
                }
            }

            if (newLOD != oldLOD) {
                // CRITICAL: Only change LOD if we can submit the mesh job.
                // Otherwise the chunk gets stuck at the new LOD with no mesh.
                if (meshJobsSubmitted >= maxMeshJobsPerUpdate) {
                    // Can't submit mesh job this frame — keep old LOD
                    newLOD = oldLOD;
                } else {
                    // If upgrading to LOD 0 and no full mesh, rebuild it
                    if (newLOD == LODLevel.LOD_0 && (chunk.getMesh() == null || chunk.getMesh().isEmpty())) {
                        chunk.setCurrentLOD(newLOD);
                        if (chunk.isLightDirty()) {
                            Lighting.computeInitialSkyVisibility(chunk);
                            Lighting.computeInitialBlockLight(chunk, world);
                        }
                        submitMeshJob(chunk, pos, false);
                        meshJobsSubmitted++;
                    }
                    // If downgrading, check if LOD mesh already cached
                    else if (newLOD.level() > 0 && chunk.getLodMesh(newLOD.level()) == null) {
                        chunk.setCurrentLOD(newLOD);
                        submitLODMeshJob(chunk, pos, newLOD);
                        meshJobsSubmitted++;
                    } else {
                        // Mesh already cached for this LOD level
                        chunk.setCurrentLOD(newLOD);
                        chunk.setLodMeshReady(true);
                    }
                }
            } else {
                // LOD didn't change — but check if mesh is missing (retry failed builds)
                if (meshJobsSubmitted < maxMeshJobsPerUpdate) {
                    if (newLOD == LODLevel.LOD_0 && (chunk.getMesh() == null || chunk.getMesh().isEmpty())) {
                        if (chunk.isLightDirty()) {
                            Lighting.computeInitialSkyVisibility(chunk);
                            Lighting.computeInitialBlockLight(chunk, world);
                        }
                        submitMeshJob(chunk, pos, false);
                        meshJobsSubmitted++;
                    } else if (newLOD.level() > 0) {
                        // Check if this chunk has ANY renderable mesh
                        boolean hasAnyMesh = false;
                        for (int i = newLOD.level(); i <= 3; i++) {
                            if (chunk.getLodMesh(i) != null && !chunk.getLodMesh(i).isEmpty()) {
                                hasAnyMesh = true;
                                break;
                            }
                        }
                        if (!hasAnyMesh && chunk.getMesh() == null) {
                            submitLODMeshJob(chunk, pos, newLOD);
                            meshJobsSubmitted++;
                        }
                    }
                }
            }

            // Count stats based on ACTUAL current LOD (after potential revert)
            switch (chunk.getCurrentLOD()) {
                case LOD_0 -> l0++;
                case LOD_1 -> l1++;
                case LOD_2 -> l2++;
                case LOD_3 -> l3++;
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
     * scanning all positions in a large radius each frame.
     *
     * Enforces the chunk cap: stops requesting new chunks when the total
     * loaded count + pending generation count approaches the cap.
     */
    private void requestChunks(int pcx, int pcz) {
        int maxDist = lodConfig.getMaxRenderDistance();
        int closeMax = LODConfig.MAX_CLOSE_GEN_PER_FRAME;
        int farMax = LODConfig.MAX_FAR_GEN_PER_FRAME;
        int closeEnqueued = 0;
        int farEnqueued = 0;

        // Hard stop: don't queue more if we're near the chunk cap
        int headroom = lodConfig.getMaxLoadedChunks() - world.getChunkMap().size() - pendingGen.size();
        if (headroom <= 0) return;

        // Spiral scan: dx,dz from center outward
        // This naturally prioritizes close chunks without needing a full sort
        boolean budgetFull = false;
        for (int ring = 0; ring <= maxDist && !budgetFull; ring++) {
            if (closeEnqueued >= closeMax && farEnqueued >= farMax) break;
            if (closeEnqueued + farEnqueued >= headroom) break; // respect chunk cap

            // Scan the perimeter of the current ring
            for (int dx = -ring; dx <= ring && !budgetFull; dx++) {
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
                    if (closeEnqueued + farEnqueued >= headroom) { budgetFull = true; break; }

                    // Submit async load/generation task
                    // ASYNC FIX: Now loads from disk on worker thread instead of blocking main thread
                    // Use simplified pipeline for distant chunks (LOD 2+)
                    final LODLevel genLevel = level;
                    final GenConfig cfg = genConfig;  // capture for lambda
                    final SaveManager sm = saveManager;  // capture for lambda
                    Future<Chunk> future = genPool.submit(() -> {
                        // ASYNC: Try loading from disk first (no longer blocks main thread!)
                        if (sm != null) {
                            Chunk loaded = sm.loadChunk(cx, cz);
                            if (loaded != null) {
                                loaded.setCurrentLOD(genLevel);
                                return loaded;
                            }
                        }
                        // Not on disk - generate new chunk
                        Chunk chunk = new Chunk(pos);
                        if (genLevel.level() >= 2) {
                            // Simplified: terrain + surface only (no caves/ores/trees)
                            LODGenPipeline lodPipeline = new LODGenPipeline(seed, cfg);
                            lodPipeline.generateSimplified(chunk);
                        } else {
                            GenPipeline pipeline = GenPipeline.createWithConfig(seed, cfg);
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
        // Limit how many completed chunks we integrate per frame to avoid spikes.
        // Also respect the chunk cap — don't add more if we're at the limit.
        int maxProcess = Math.min(
            LODConfig.MAX_CLOSE_GEN_PER_FRAME + LODConfig.MAX_FAR_GEN_PER_FRAME,
            lodConfig.getMaxLoadedChunks() - world.getChunkMap().size()
        );
        if (maxProcess <= 0) {
            // At chunk cap — cancel only NOT-YET-DONE futures to free thread pool.
            // Preserve completed futures so their results aren't lost.
            while (it.hasNext()) {
                Map.Entry<ChunkPos, Future<Chunk>> entry = it.next();
                Future<Chunk> f = entry.getValue();
                if (!f.isDone()) {
                    f.cancel(false);
                    it.remove();
                }
                // Completed futures are kept for processing next frame when there's room
            }
            return;
        }

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
                            Lighting.computeInitialSkyVisibility(chunk);
                            Lighting.computeInitialBlockLight(chunk, world);
                            // Create probe grid for close chunks
                            if (probeManager != null) {
                                probeManager.onChunkLoaded(chunk, world, currentTimeOfDay);
                            }
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

            // Notify probe manager of unload
            if (probeManager != null) {
                probeManager.onChunkUnloaded(pos);
            }

            world.removeChunk(pos);
        }
    }

    /**
     * Enforce hard chunk cap by unloading the farthest chunks when over limit.
     * This prevents unbounded memory growth regardless of render distance settings.
     */
    private void enforceChunkCap(int pcx, int pcz) {
        int maxChunks = lodConfig.getMaxLoadedChunks();
        int currentCount = world.getChunkMap().size();

        if (currentCount <= maxChunks) return;

        // Collect all chunks with their distances
        List<Map.Entry<ChunkPos, Integer>> byDistance = new ArrayList<>();
        for (ChunkPos pos : world.getChunkMap().keySet()) {
            int dx = pos.x() - pcx;
            int dz = pos.z() - pcz;
            byDistance.add(Map.entry(pos, dx * dx + dz * dz));
        }

        // Sort by distance descending (farthest first)
        byDistance.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        // Remove chunks until we're under the cap
        int toRemove = currentCount - maxChunks;
        for (int i = 0; i < toRemove && i < byDistance.size(); i++) {
            ChunkPos pos = byDistance.get(i).getKey();

            // Save modified chunks before unloading
            if (saveManager != null) {
                Chunk chunk = world.getChunk(pos.x(), pos.z());
                if (chunk != null && chunk.isModified()) {
                    try {
                        saveManager.saveChunk(chunk);
                    } catch (java.io.IOException e) {
                        System.err.println("Failed to save chunk on cap enforce: " + pos);
                    }
                }
            }
            Future<Chunk> pending = pendingGen.remove(pos);
            if (pending != null) pending.cancel(false);

            // Notify probe manager of unload
            if (probeManager != null) {
                probeManager.onChunkUnloaded(pos);
            }

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
     * Notifies probe manager of the block change for indirect lighting updates.
     * 
     * ASYNC FIX: Now submits async mesh job instead of blocking main thread.
     */
    public void rebuildMeshAt(int wx, int wy, int wz) {
        int cx = Math.floorDiv(wx, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(wz, WorldConstants.CHUNK_SIZE);
        int lx = Math.floorMod(wx, WorldConstants.CHUNK_SIZE);
        int lz = Math.floorMod(wz, WorldConstants.CHUNK_SIZE);

        // Update probes near the changed block
        if (probeManager != null) {
            probeManager.onBlockChanged(wx, wy, wz, world, currentTimeOfDay);
        }

        Chunk chunk = world.getChunk(cx, cz);
        if (chunk != null) {
            // ASYNC: Submit to mesh pool instead of sync rebuild
            ChunkPos pos = new ChunkPos(cx, cz);
            submitMeshJob(chunk, pos, false);
        }

        // Rebuild neighbors if on boundary (async)
        if (lx == 0) rebuildChunkAsync(cx - 1, cz);
        if (lx == WorldConstants.CHUNK_SIZE - 1) rebuildChunkAsync(cx + 1, cz);
        if (lz == 0) rebuildChunkAsync(cx, cz - 1);
        if (lz == WorldConstants.CHUNK_SIZE - 1) rebuildChunkAsync(cx, cz + 1);
    }
    
    /**
     * Update time of day for probe calculations.
     * Call when time changes (e.g., from WorldTime).
     * 
     * @param timeOfDay Normalized time 0-1 where 0 = midnight, 0.5 = noon
     */
    public void setTimeOfDay(float timeOfDay) {
        this.currentTimeOfDay = timeOfDay;
        if (probeManager != null) {
            probeManager.onTimeOfDayChanged(timeOfDay, world);
        }
    }

    /**
     * Rebuild meshes for all chunks in the given set.
     * ASYNC FIX: Now submits async mesh jobs instead of blocking.
     */
    public void rebuildChunks(Set<ChunkPos> positions) {
        for (ChunkPos pos : positions) {
            Chunk chunk = world.getChunk(pos.x(), pos.z());
            if (chunk != null) {
                submitMeshJob(chunk, pos, false);
            }
        }
    }

    /**
     * Async version of rebuildChunk - submits to mesh pool.
     */
    private void rebuildChunkAsync(int cx, int cz) {
        Chunk chunk = world.getChunk(cx, cz);
        if (chunk != null) {
            ChunkPos pos = new ChunkPos(cx, cz);
            submitMeshJob(chunk, pos, false);
        }
    }

    /**
     * @deprecated Use rebuildChunkAsync instead to avoid blocking main thread.
     */
    @Deprecated
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
        if (probeManager != null) {
            probeManager.clear();
        }
    }
    
    /** Get the probe manager (for debugging/stats). */
    public ProbeManager getProbeManager() {
        return probeManager;
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
