package com.voxelgame.world.stream;

import com.voxelgame.render.TextureAtlas;
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

    public ChunkManager(World world) {
        this.world = world;
    }

    public void init(TextureAtlas atlas) {
        this.mesher = new NaiveMesher(atlas);

        worker = new ChunkGenerationWorker(taskQueue, completedQueue);
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
                buildMesh(chunk);
                meshesBuilt++;
                pendingChunks.remove(pos);
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
            world.removeChunk(pos);
        }
    }

    private void buildMesh(Chunk chunk) {
        ChunkMesh mesh = mesher.mesh(chunk, world);
        chunk.setMesh(mesh);
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
