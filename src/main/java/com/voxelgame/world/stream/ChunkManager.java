package com.voxelgame.world.stream;

import com.voxelgame.render.TextureAtlas;
import com.voxelgame.save.SaveManager;
import com.voxelgame.sim.Player;
import com.voxelgame.world.*;
import com.voxelgame.world.gen.GenPipeline;
import com.voxelgame.world.mesh.ChunkMesh;
import com.voxelgame.world.mesh.Mesher;
import com.voxelgame.world.mesh.NaiveMesher;

import java.util.*;
import java.util.concurrent.*;

/**
 * Manages chunk lifecycle: loading, generation, meshing, and unloading.
 * Coordinates background worker and maintains load radius around the player.
 * Integrates with SaveManager to load saved chunks from disk before generating.
 */
public class ChunkManager {

    private static final int RENDER_DISTANCE = 8; // chunks
    private static final int UNLOAD_DISTANCE = RENDER_DISTANCE + 2;

    private final World world;
    private Mesher mesher;

    private final BlockingQueue<ChunkTask> taskQueue = new LinkedBlockingQueue<>();
    private final ConcurrentLinkedQueue<ChunkTask> completedQueue = new ConcurrentLinkedQueue<>();
    private final Set<ChunkPos> pendingChunks = ConcurrentHashMap.newKeySet();

    private ChunkGenerationWorker worker;
    private Thread workerThread;

    private int lastPlayerCX = Integer.MIN_VALUE;
    private int lastPlayerCZ = Integer.MIN_VALUE;

    /** Save manager for loading chunks from disk. May be null if save is disabled. */
    private SaveManager saveManager;

    /** Seed to use for world generation. */
    private long seed = ChunkGenerationWorker.DEFAULT_SEED;

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
        this.mesher = new NaiveMesher(atlas);

        worker = new ChunkGenerationWorker(taskQueue, completedQueue, seed);
        workerThread = new Thread(worker, "ChunkGen-Worker");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    public void update(Player player) {
        int pcx = (int) Math.floor(player.getPosition().x / WorldConstants.CHUNK_SIZE);
        int pcz = (int) Math.floor(player.getPosition().z / WorldConstants.CHUNK_SIZE);

        // Process completed chunks
        ChunkTask completed;
        int meshesBuilt = 0;
        while ((completed = completedQueue.poll()) != null) {
            ChunkPos pos = completed.getPos();
            Chunk chunk = completed.getResult();
            if (chunk != null) {
                world.addChunk(pos, chunk);
                // Compute initial sky light for the new chunk
                Lighting.computeInitialSkyLight(chunk, world);
                buildMesh(chunk);
                meshesBuilt++;
                pendingChunks.remove(pos);

                // Rebuild neighbor meshes so they pick up cross-boundary lighting
                rebuildNeighborMeshes(pos);
            }
        }

        // Rebuild dirty chunks' meshes
        for (Chunk chunk : world.getLoadedChunks()) {
            if (chunk.isDirty() && chunk.getMesh() != null) {
                buildMesh(chunk);
            }
        }

        // Only update load/unload when player moves to new chunk
        if (pcx == lastPlayerCX && pcz == lastPlayerCZ) return;
        lastPlayerCX = pcx;
        lastPlayerCZ = pcz;

        // Request chunks within render distance
        for (int dx = -RENDER_DISTANCE; dx <= RENDER_DISTANCE; dx++) {
            for (int dz = -RENDER_DISTANCE; dz <= RENDER_DISTANCE; dz++) {
                if (dx * dx + dz * dz > RENDER_DISTANCE * RENDER_DISTANCE) continue;
                ChunkPos pos = new ChunkPos(pcx + dx, pcz + dz);
                if (!world.isLoaded(pos.x(), pos.z()) && !pendingChunks.contains(pos)) {
                    // Try loading from disk first
                    if (saveManager != null) {
                        Chunk loaded = saveManager.loadChunk(pos.x(), pos.z());
                        if (loaded != null) {
                            // Loaded from disk — add directly (skip generation)
                            world.addChunk(pos, loaded);
                            Lighting.computeInitialSkyLight(loaded, world);
                            buildMesh(loaded);
                            rebuildNeighborMeshes(pos);
                            continue;
                        }
                    }
                    // Not on disk — queue for generation
                    pendingChunks.add(pos);
                    taskQueue.add(new ChunkTask(pos));
                }
            }
        }

        // Unload far chunks
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
            world.removeChunk(pos);
        }
    }

    private void buildMesh(Chunk chunk) {
        ChunkMesh mesh = mesher.mesh(chunk, world);
        chunk.setMesh(mesh);
        chunk.setDirty(false);
    }

    private void rebuildNeighborMeshes(ChunkPos pos) {
        int[][] neighbors = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] n : neighbors) {
            Chunk neighbor = world.getChunk(pos.x() + n[0], pos.z() + n[1]);
            if (neighbor != null && neighbor.getMesh() != null) {
                buildMesh(neighbor);
            }
        }
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
        return worker != null ? worker.getPipeline() : null;
    }

    public World getWorld() {
        return world;
    }

    public void shutdown() {
        if (worker != null) {
            worker.stop();
        }
        if (workerThread != null) {
            workerThread.interrupt();
            try {
                workerThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
