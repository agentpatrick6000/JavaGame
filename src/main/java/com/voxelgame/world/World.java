package com.voxelgame.world;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The game world. Owns all loaded chunks, provides block and light access across chunk boundaries.
 */
public class World implements WorldAccess {

    private final ConcurrentHashMap<ChunkPos, Chunk> chunks = new ConcurrentHashMap<>();

    @Override
    public int getBlock(int x, int y, int z) {
        if (y < 0 || y >= WorldConstants.WORLD_HEIGHT) return 0;
        int cx = Math.floorDiv(x, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(z, WorldConstants.CHUNK_SIZE);
        Chunk chunk = chunks.get(new ChunkPos(cx, cz));
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
        Chunk chunk = chunks.get(new ChunkPos(cx, cz));
        if (chunk == null) return 1.0f; // assume full sky visibility for unloaded chunks
        int lx = Math.floorMod(x, WorldConstants.CHUNK_SIZE);
        int lz = Math.floorMod(z, WorldConstants.CHUNK_SIZE);
        return chunk.getSkyVisibility(lx, y, lz);
    }

    public void setSkyVisibility(int x, int y, int z, float visibility) {
        if (y < 0 || y >= WorldConstants.WORLD_HEIGHT) return;
        int cx = Math.floorDiv(x, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(z, WorldConstants.CHUNK_SIZE);
        Chunk chunk = chunks.get(new ChunkPos(cx, cz));
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
        Chunk chunk = chunks.get(new ChunkPos(cx, cz));
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
        Chunk chunk = chunks.get(new ChunkPos(cx, cz));
        if (chunk == null) return 0;
        int lx = Math.floorMod(x, WorldConstants.CHUNK_SIZE);
        int lz = Math.floorMod(z, WorldConstants.CHUNK_SIZE);
        return chunk.getBlockLight(lx, y, lz);
    }

    public void setBlock(int x, int y, int z, int blockId) {
        if (y < 0 || y >= WorldConstants.WORLD_HEIGHT) return;
        int cx = Math.floorDiv(x, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(z, WorldConstants.CHUNK_SIZE);
        Chunk chunk = chunks.get(new ChunkPos(cx, cz));
        if (chunk == null) return;
        int lx = Math.floorMod(x, WorldConstants.CHUNK_SIZE);
        int lz = Math.floorMod(z, WorldConstants.CHUNK_SIZE);
        chunk.setBlock(lx, y, lz, blockId);
    }

    public void setSkyLight(int x, int y, int z, int level) {
        if (y < 0 || y >= WorldConstants.WORLD_HEIGHT) return;
        int cx = Math.floorDiv(x, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(z, WorldConstants.CHUNK_SIZE);
        Chunk chunk = chunks.get(new ChunkPos(cx, cz));
        if (chunk == null) return;
        int lx = Math.floorMod(x, WorldConstants.CHUNK_SIZE);
        int lz = Math.floorMod(z, WorldConstants.CHUNK_SIZE);
        chunk.setSkyLight(lx, y, lz, level);
    }

    public void setBlockLight(int x, int y, int z, int level) {
        if (y < 0 || y >= WorldConstants.WORLD_HEIGHT) return;
        int cx = Math.floorDiv(x, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(z, WorldConstants.CHUNK_SIZE);
        Chunk chunk = chunks.get(new ChunkPos(cx, cz));
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
        Chunk chunk = chunks.get(new ChunkPos(cx, cz));
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
        Chunk chunk = chunks.get(new ChunkPos(cx, cz));
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
        Chunk chunk = chunks.get(new ChunkPos(cx, cz));
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
        Chunk chunk = chunks.get(new ChunkPos(cx, cz));
        if (chunk == null) return 0;
        int lx = Math.floorMod(x, WorldConstants.CHUNK_SIZE);
        int lz = Math.floorMod(z, WorldConstants.CHUNK_SIZE);
        return chunk.getBlockLightB(lx, y, lz);
    }

    public void setBlockLightRGB(int x, int y, int z, float r, float g, float b) {
        if (y < 0 || y >= WorldConstants.WORLD_HEIGHT) return;
        int cx = Math.floorDiv(x, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(z, WorldConstants.CHUNK_SIZE);
        Chunk chunk = chunks.get(new ChunkPos(cx, cz));
        if (chunk == null) return;
        int lx = Math.floorMod(x, WorldConstants.CHUNK_SIZE);
        int lz = Math.floorMod(z, WorldConstants.CHUNK_SIZE);
        chunk.setBlockLightRGB(lx, y, lz, r, g, b);
    }

    @Override
    public Chunk getChunk(int cx, int cz) {
        return chunks.get(new ChunkPos(cx, cz));
    }

    @Override
    public boolean isLoaded(int cx, int cz) {
        return chunks.containsKey(new ChunkPos(cx, cz));
    }

    public void addChunk(ChunkPos pos, Chunk chunk) {
        chunks.put(pos, chunk);
    }

    public void removeChunk(ChunkPos pos) {
        Chunk chunk = chunks.remove(pos);
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
