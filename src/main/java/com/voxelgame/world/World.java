package com.voxelgame.world;

import com.voxelgame.bench.BenchFixes;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The game world. Owns all loaded chunks, provides block and light access across chunk boundaries.
 */
public class World implements WorldAccess {

    private final ConcurrentHashMap<ChunkPos, Chunk> chunks = new ConcurrentHashMap<>();
    
    // Fix B2: Primitive-keyed chunk map (no boxing on lookup)
    // Uses ReadWriteLock: reads are concurrent, writes are exclusive
    private final Long2ObjectOpenHashMap<Chunk> primitiveChunks = new Long2ObjectOpenHashMap<>();
    private final ReadWriteLock primitiveChunksLock = new ReentrantReadWriteLock();
    
    /** Pack chunk coordinates into a long key (pure math, no allocation). */
    public static long key(int cx, int cz) {
        return (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
    }
    
    /** Get chunk by packed key using primitive map (no boxing). */
    private Chunk getChunkPrimitive(long k) {
        primitiveChunksLock.readLock().lock();
        try {
            return primitiveChunks.get(k);
        } finally {
            primitiveChunksLock.readLock().unlock();
        }
    }
    
    /** Get chunk by chunk coords - dispatches based on toggle. */
    private Chunk getChunkInternal(int cx, int cz) {
        if (BenchFixes.FIX_B2_PRIMITIVE_MAP) {
            return getChunkPrimitive(key(cx, cz));
        } else {
            return chunks.get(new ChunkPos(cx, cz));
        }
    }

    @Override
    public int getBlock(int x, int y, int z) {
        if (y < 0 || y >= WorldConstants.WORLD_HEIGHT) return 0;
        int cx = Math.floorDiv(x, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(z, WorldConstants.CHUNK_SIZE);
        Chunk chunk = getChunkInternal(cx, cz);
        if (chunk == null) return 0;
        int lx = Math.floorMod(x, WorldConstants.CHUNK_SIZE);
        int lz = Math.floorMod(z, WorldConstants.CHUNK_SIZE);
        return chunk.getBlock(lx, y, lz);
    }

    @Override
    public float getSkyVisibility(int x, int y, int z) {
        if (y >= WorldConstants.WORLD_HEIGHT) return 1.0f;
        if (y < 0) return 0.0f;
        int cx = Math.floorDiv(x, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(z, WorldConstants.CHUNK_SIZE);
        Chunk chunk = getChunkInternal(cx, cz);
        if (chunk == null) return 1.0f; // assume full sky visibility for unloaded chunks
        int lx = Math.floorMod(x, WorldConstants.CHUNK_SIZE);
        int lz = Math.floorMod(z, WorldConstants.CHUNK_SIZE);
        return chunk.getSkyVisibility(lx, y, lz);
    }

    public void setSkyVisibility(int x, int y, int z, float visibility) {
        if (y < 0 || y >= WorldConstants.WORLD_HEIGHT) return;
        int cx = Math.floorDiv(x, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(z, WorldConstants.CHUNK_SIZE);
        Chunk chunk = getChunkInternal(cx, cz);
        if (chunk == null) return;
        int lx = Math.floorMod(x, WorldConstants.CHUNK_SIZE);
        int lz = Math.floorMod(z, WorldConstants.CHUNK_SIZE);
        chunk.setSkyVisibility(lx, y, lz, visibility);
    }

    @Override
    @Deprecated
    public int getSkyLight(int x, int y, int z) {
        if (y >= WorldConstants.WORLD_HEIGHT) return 15;
        if (y < 0) return 0;
        int cx = Math.floorDiv(x, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(z, WorldConstants.CHUNK_SIZE);
        Chunk chunk = getChunkInternal(cx, cz);
        if (chunk == null) return 15; // assume full sun for unloaded chunks
        int lx = Math.floorMod(x, WorldConstants.CHUNK_SIZE);
        int lz = Math.floorMod(z, WorldConstants.CHUNK_SIZE);
        return chunk.getSkyLight(lx, y, lz);
    }

    @Override
    public int getBlockLight(int x, int y, int z) {
        if (y < 0 || y >= WorldConstants.WORLD_HEIGHT) return 0;
        int cx = Math.floorDiv(x, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(z, WorldConstants.CHUNK_SIZE);
        Chunk chunk = getChunkInternal(cx, cz);
        if (chunk == null) return 0;
        int lx = Math.floorMod(x, WorldConstants.CHUNK_SIZE);
        int lz = Math.floorMod(z, WorldConstants.CHUNK_SIZE);
        return chunk.getBlockLight(lx, y, lz);
    }

    public void setBlock(int x, int y, int z, int blockId) {
        if (y < 0 || y >= WorldConstants.WORLD_HEIGHT) return;
        int cx = Math.floorDiv(x, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(z, WorldConstants.CHUNK_SIZE);
        Chunk chunk = getChunkInternal(cx, cz);
        if (chunk == null) return;
        int lx = Math.floorMod(x, WorldConstants.CHUNK_SIZE);
        int lz = Math.floorMod(z, WorldConstants.CHUNK_SIZE);
        chunk.setBlock(lx, y, lz, blockId);
    }

    public void setSkyLight(int x, int y, int z, int level) {
        if (y < 0 || y >= WorldConstants.WORLD_HEIGHT) return;
        int cx = Math.floorDiv(x, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(z, WorldConstants.CHUNK_SIZE);
        Chunk chunk = getChunkInternal(cx, cz);
        if (chunk == null) return;
        int lx = Math.floorMod(x, WorldConstants.CHUNK_SIZE);
        int lz = Math.floorMod(z, WorldConstants.CHUNK_SIZE);
        chunk.setSkyLight(lx, y, lz, level);
    }

    public void setBlockLight(int x, int y, int z, int level) {
        if (y < 0 || y >= WorldConstants.WORLD_HEIGHT) return;
        int cx = Math.floorDiv(x, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(z, WorldConstants.CHUNK_SIZE);
        Chunk chunk = getChunkInternal(cx, cz);
        if (chunk == null) return;
        int lx = Math.floorMod(x, WorldConstants.CHUNK_SIZE);
        int lz = Math.floorMod(z, WorldConstants.CHUNK_SIZE);
        chunk.setBlockLight(lx, y, lz, level);
    }

    // ========================================================================
    // Phase 4: RGB Block Light Methods
    // ========================================================================

    @Override
    public float[] getBlockLightRGB(int x, int y, int z) {
        if (y < 0 || y >= WorldConstants.WORLD_HEIGHT) return new float[] {0, 0, 0};
        int cx = Math.floorDiv(x, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(z, WorldConstants.CHUNK_SIZE);
        Chunk chunk = getChunkInternal(cx, cz);
        if (chunk == null) return new float[] {0, 0, 0};
        int lx = Math.floorMod(x, WorldConstants.CHUNK_SIZE);
        int lz = Math.floorMod(z, WorldConstants.CHUNK_SIZE);
        return chunk.getBlockLightRGB(lx, y, lz);
    }

    @Override
    public float getBlockLightR(int x, int y, int z) {
        if (y < 0 || y >= WorldConstants.WORLD_HEIGHT) return 0;
        int cx = Math.floorDiv(x, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(z, WorldConstants.CHUNK_SIZE);
        Chunk chunk = getChunkInternal(cx, cz);
        if (chunk == null) return 0;
        int lx = Math.floorMod(x, WorldConstants.CHUNK_SIZE);
        int lz = Math.floorMod(z, WorldConstants.CHUNK_SIZE);
        return chunk.getBlockLightR(lx, y, lz);
    }

    @Override
    public float getBlockLightG(int x, int y, int z) {
        if (y < 0 || y >= WorldConstants.WORLD_HEIGHT) return 0;
        int cx = Math.floorDiv(x, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(z, WorldConstants.CHUNK_SIZE);
        Chunk chunk = getChunkInternal(cx, cz);
        if (chunk == null) return 0;
        int lx = Math.floorMod(x, WorldConstants.CHUNK_SIZE);
        int lz = Math.floorMod(z, WorldConstants.CHUNK_SIZE);
        return chunk.getBlockLightG(lx, y, lz);
    }

    @Override
    public float getBlockLightB(int x, int y, int z) {
        if (y < 0 || y >= WorldConstants.WORLD_HEIGHT) return 0;
        int cx = Math.floorDiv(x, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(z, WorldConstants.CHUNK_SIZE);
        Chunk chunk = getChunkInternal(cx, cz);
        if (chunk == null) return 0;
        int lx = Math.floorMod(x, WorldConstants.CHUNK_SIZE);
        int lz = Math.floorMod(z, WorldConstants.CHUNK_SIZE);
        return chunk.getBlockLightB(lx, y, lz);
    }

    public void setBlockLightRGB(int x, int y, int z, float r, float g, float b) {
        if (y < 0 || y >= WorldConstants.WORLD_HEIGHT) return;
        int cx = Math.floorDiv(x, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(z, WorldConstants.CHUNK_SIZE);
        Chunk chunk = getChunkInternal(cx, cz);
        if (chunk == null) return;
        int lx = Math.floorMod(x, WorldConstants.CHUNK_SIZE);
        int lz = Math.floorMod(z, WorldConstants.CHUNK_SIZE);
        chunk.setBlockLightRGB(lx, y, lz, r, g, b);
    }

    @Override
    public Chunk getChunk(int cx, int cz) {
        return getChunkInternal(cx, cz);
    }

    @Override
    public boolean isLoaded(int cx, int cz) {
        if (BenchFixes.FIX_B2_PRIMITIVE_MAP) {
            primitiveChunksLock.readLock().lock();
            try {
                return primitiveChunks.containsKey(key(cx, cz));
            } finally {
                primitiveChunksLock.readLock().unlock();
            }
        } else {
            return chunks.containsKey(new ChunkPos(cx, cz));
        }
    }

    public void addChunk(ChunkPos pos, Chunk chunk) {
        chunks.put(pos, chunk);
        // Also add to primitive map for FIX_B2
        primitiveChunksLock.writeLock().lock();
        try {
            primitiveChunks.put(key(pos.x(), pos.z()), chunk);
        } finally {
            primitiveChunksLock.writeLock().unlock();
        }
    }

    public void removeChunk(ChunkPos pos) {
        Chunk chunk = chunks.remove(pos);
        // Also remove from primitive map
        primitiveChunksLock.writeLock().lock();
        try {
            primitiveChunks.remove(key(pos.x(), pos.z()));
        } finally {
            primitiveChunksLock.writeLock().unlock();
        }
        if (chunk != null) {
            chunk.dispose();
        }
    }

    public Collection<Chunk> getLoadedChunks() {
        return chunks.values();
    }

    public ConcurrentHashMap<ChunkPos, Chunk> getChunkMap() {
        return chunks;
    }
}
