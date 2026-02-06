package com.voxelgame.world.lod;

import com.voxelgame.render.TextureAtlas;
import com.voxelgame.world.*;
import com.voxelgame.world.mesh.MeshData;
import com.voxelgame.world.mesh.RawMeshResult;

/**
 * Multi-tier LOD mesher. Generates progressively simplified meshes
 * based on the requested LOD level.
 *
 * LOD 0: Delegates to NaiveMesher (full detail with AO)
 * LOD 1: Simplified faces — no AO, flat per-face sky visibility, still per-block
 * LOD 2: Heightmap columns — one quad per surface column + side faces
 * LOD 3: Flat colored quad — single quad at average height with average color
 *
 * Phase 4 vertex format: [x, y, z, u, v, skyVisibility, blockLightR, blockLightG, blockLightB, horizonWeight, indirectR, indirectG, indirectB] (13 floats)
 * skyVisibility: 0-1 passed to shader for dynamic sky/sun RGB computation
 * blockLightR/G/B: RGB block light (set to 0 for LOD meshes - distant chunks don't need light precision)
 * horizonWeight: 0-1 zenith vs horizon blend (0.3 default for balanced look at distance)
 * indirectRGB: indirect bounce lighting (set to 0 for LOD meshes - distant chunks don't need GI precision)
 *
 * All methods are CPU-only (no GL calls). Safe for background threads.
 */
public class LODMesher {

    // Face light values (no AO at LOD 1+)
    private static final float[] FACE_LIGHT = {1.0f, 0.45f, 0.7f, 0.7f, 0.8f, 0.6f};

    private static final int[][] FACE_NORMALS = {
        { 0,  1,  0}, { 0, -1,  0}, { 0,  0, -1},
        { 0,  0,  1}, { 1,  0,  0}, {-1,  0,  0},
    };

    private final TextureAtlas atlas;

    public LODMesher(TextureAtlas atlas) {
        this.atlas = atlas;
    }

    /**
     * Build mesh data for the given LOD level.
     * Returns a RawMeshResult (CPU-side only, no GL calls).
     * Includes both opaque mesh and transparent water mesh.
     */
    public RawMeshResult meshForLOD(Chunk chunk, WorldAccess world, LODLevel level) {
        return switch (level) {
            case LOD_0 -> null; // Should use NaiveMesher for LOD 0
            case LOD_1 -> meshLOD1(chunk, world);
            case LOD_2 -> meshLOD2(chunk, world);
            case LOD_3 -> meshLOD3(chunk);
        };
    }

    // ========================================================================
    // LOD 1: Simplified per-block faces — no AO, flat lighting
    // ========================================================================

    private RawMeshResult meshLOD1(Chunk chunk, WorldAccess world) {
        // Pre-allocate arrays (avoid ArrayList boxing overhead)
        // Estimate: ~16*128*16 blocks * 6 faces * 4 verts max, but mostly air
        int estimatedVerts = 8192;
        float[] vertices = new float[estimatedVerts * 13]; // Phase 4: 13 floats per vertex
        int[] indices = new int[estimatedVerts * 6 / 4]; // 6 indices per quad (4 verts)
        int vertCount = 0;
        int idxCount = 0;

        ChunkPos pos = chunk.getPos();
        int cx = pos.worldX();
        int cz = pos.worldZ();

        for (int x = 0; x < WorldConstants.CHUNK_SIZE; x++) {
            for (int y = 0; y < WorldConstants.WORLD_HEIGHT; y++) {
                for (int z = 0; z < WorldConstants.CHUNK_SIZE; z++) {
                    int blockId = chunk.getBlock(x, y, z);
                    if (blockId == 0) continue;

                    Block block = Blocks.get(blockId);
                    if (!block.solid() && !block.transparent()) continue;
                    // Skip non-solid transparent at LOD 1 (flowers, torches)
                    if (block.transparent() && !block.solid()) continue;

                    float wx = cx + x;
                    float wy = y;
                    float wz = cz + z;

                    for (int face = 0; face < 6; face++) {
                        int nx = x + FACE_NORMALS[face][0];
                        int ny = y + FACE_NORMALS[face][1];
                        int nz = z + FACE_NORMALS[face][2];

                        int neighborId;
                        if (nx >= 0 && nx < WorldConstants.CHUNK_SIZE &&
                            ny >= 0 && ny < WorldConstants.WORLD_HEIGHT &&
                            nz >= 0 && nz < WorldConstants.CHUNK_SIZE) {
                            neighborId = chunk.getBlock(nx, ny, nz);
                        } else {
                            neighborId = world.getBlock(cx + nx, ny, cz + nz);
                        }

                        Block neighbor = Blocks.get(neighborId);
                        if (neighbor.solid() && !neighbor.transparent()) continue;
                        if (blockId == neighborId && neighbor.transparent()) continue;

                        // Get texture UV
                        int texIdx = block.getTextureIndex(face);
                        float[] uv = atlas.getUV(texIdx);
                        float light = FACE_LIGHT[face];

                        // Ensure array capacity (13 floats per vertex, 4 vertices = 52 floats)
                        if (vertCount + 52 >= vertices.length) {
                            vertices = grow(vertices, vertices.length * 2);
                        }
                        if (idxCount + 6 >= indices.length) {
                            indices = growInt(indices, indices.length * 2);
                        }

                        // Add 4 vertices (no AO, flat sky visibility for shader)
                        float[][] fv = FACE_VERTS[face];
                        int[][] fuv = FACE_UV;
                        int baseVert = vertCount / 13; // Phase 4: 13 floats per vertex

                        // LOD 1+ assumes full sky visibility for surface blocks
                        // (distant chunks don't need per-block lighting accuracy)
                        float skyVis = light; // Use directional light as proxy for visibility
                        float horizonWeight = 0.3f; // Balanced default for LOD meshes
                        for (int v = 0; v < 4; v++) {
                            float u = (fuv[v][0] == 0) ? uv[0] : uv[2];
                            float vCoord = (fuv[v][1] == 0) ? uv[1] : uv[3];
                            vertices[vertCount++] = wx + fv[v][0];
                            vertices[vertCount++] = wy + fv[v][1];
                            vertices[vertCount++] = wz + fv[v][2];
                            vertices[vertCount++] = u;
                            vertices[vertCount++] = vCoord;
                            vertices[vertCount++] = skyVis;       // skyVisibility
                            vertices[vertCount++] = 0.0f;         // blockLightR (unused for LOD)
                            vertices[vertCount++] = 0.0f;         // blockLightG
                            vertices[vertCount++] = 0.0f;         // blockLightB
                            vertices[vertCount++] = horizonWeight; // horizonWeight
                            vertices[vertCount++] = 0.0f;         // indirectR (unused for LOD)
                            vertices[vertCount++] = 0.0f;         // indirectG
                            vertices[vertCount++] = 0.0f;         // indirectB
                        }

                        // Standard quad indices
                        indices[idxCount++] = baseVert;
                        indices[idxCount++] = baseVert + 1;
                        indices[idxCount++] = baseVert + 2;
                        indices[idxCount++] = baseVert;
                        indices[idxCount++] = baseVert + 2;
                        indices[idxCount++] = baseVert + 3;
                    }
                }
            }
        }

        MeshData opaque = new MeshData(trim(vertices, vertCount), trimInt(indices, idxCount));
        return new RawMeshResult(opaque, MeshData.EMPTY);
    }

    // ========================================================================
    // LOD 2: Heightmap columns — top quad + side faces at height changes
    // ========================================================================

    private RawMeshResult meshLOD2(Chunk chunk, WorldAccess world) {
        // Compute heightmap and surface block for this chunk
        int[] heights = new int[WorldConstants.CHUNK_SIZE * WorldConstants.CHUNK_SIZE];
        int[] surfaceBlocks = new int[WorldConstants.CHUNK_SIZE * WorldConstants.CHUNK_SIZE];

        for (int x = 0; x < WorldConstants.CHUNK_SIZE; x++) {
            for (int z = 0; z < WorldConstants.CHUNK_SIZE; z++) {
                int idx = x * WorldConstants.CHUNK_SIZE + z;
                // Find top solid block
                for (int y = WorldConstants.WORLD_HEIGHT - 1; y >= 0; y--) {
                    int blockId = chunk.getBlock(x, y, z);
                    if (blockId != 0 && Blocks.get(blockId).solid()) {
                        heights[idx] = y;
                        surfaceBlocks[idx] = blockId;
                        break;
                    }
                }
            }
        }

        // Estimate output size (Phase 4: 13 floats per vertex)
        float[] vertices = new float[WorldConstants.CHUNK_SIZE * WorldConstants.CHUNK_SIZE * 13 * 4 * 3];
        int[] indices = new int[WorldConstants.CHUNK_SIZE * WorldConstants.CHUNK_SIZE * 6 * 3];
        int vertCount = 0;
        int idxCount = 0;

        ChunkPos pos = chunk.getPos();
        float cx = pos.worldX();
        float cz = pos.worldZ();

        for (int x = 0; x < WorldConstants.CHUNK_SIZE; x++) {
            for (int z = 0; z < WorldConstants.CHUNK_SIZE; z++) {
                int idx = x * WorldConstants.CHUNK_SIZE + z;
                int h = heights[idx];
                int blockId = surfaceBlocks[idx];
                if (blockId == 0) continue;

                Block block = Blocks.get(blockId);
                int texIdx = block.getTextureIndex(0); // top face texture
                float[] uv = atlas.getUV(texIdx);
                float wx = cx + x;
                float wz = cz + z;
                float wy = h + 1; // top of block

                // Ensure capacity (Phase 4: 13 floats per vertex)
                if (vertCount + 13 * 4 * 5 >= vertices.length) {
                    vertices = grow(vertices, vertices.length * 2);
                }
                if (idxCount + 6 * 5 >= indices.length) {
                    indices = growInt(indices, indices.length * 2);
                }

                // Top face quad
                int baseVert = vertCount / 13; // Phase 4: 13 floats per vertex
                float light = 1.0f; // full sky light for tops
                addVert(vertices, vertCount, wx, wy, wz, uv[0], uv[1], light); vertCount += 13;
                addVert(vertices, vertCount, wx, wy, wz + 1, uv[0], uv[3], light); vertCount += 13;
                addVert(vertices, vertCount, wx + 1, wy, wz + 1, uv[2], uv[3], light); vertCount += 13;
                addVert(vertices, vertCount, wx + 1, wy, wz, uv[2], uv[1], light); vertCount += 13;
                idxCount = addQuadIdx(indices, idxCount, baseVert);

                // Side faces where height changes (create "cliffs")
                // Check 4 neighbors
                int[][] neighbors = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                float[] sideLights = {0.8f, 0.6f, 0.7f, 0.7f};
                for (int n = 0; n < 4; n++) {
                    int nx = x + neighbors[n][0];
                    int nz = z + neighbors[n][1];
                    int nh;
                    if (nx >= 0 && nx < WorldConstants.CHUNK_SIZE &&
                        nz >= 0 && nz < WorldConstants.CHUNK_SIZE) {
                        nh = heights[nx * WorldConstants.CHUNK_SIZE + nz];
                    } else {
                        // Neighbor in different chunk — use world access
                        int wnx = pos.worldX() + nx;
                        int wnz = pos.worldZ() + nz;
                        nh = findSurfaceHeight(world, wnx, wnz);
                    }

                    if (h > nh) {
                        // Draw side face from nh+1 to h+1
                        int sideTexIdx = block.getTextureIndex(2); // side texture
                        float[] sideUv = atlas.getUV(sideTexIdx);
                        float sideLight = sideLights[n];

                        float y0 = nh + 1;
                        float y1 = h + 1;

                        // Ensure capacity (Phase 4: 13 floats per vertex)
                        if (vertCount + 52 >= vertices.length) {
                            vertices = grow(vertices, vertices.length * 2);
                        }
                        if (idxCount + 6 >= indices.length) {
                            indices = growInt(indices, indices.length * 2);
                        }

                        baseVert = vertCount / 13; // Phase 4: 13 floats per vertex

                        if (n == 0) { // +X
                            addVert(vertices, vertCount, wx+1, y1, wz+1, sideUv[0], sideUv[1], sideLight); vertCount += 13;
                            addVert(vertices, vertCount, wx+1, y0, wz+1, sideUv[0], sideUv[3], sideLight); vertCount += 13;
                            addVert(vertices, vertCount, wx+1, y0, wz, sideUv[2], sideUv[3], sideLight); vertCount += 13;
                            addVert(vertices, vertCount, wx+1, y1, wz, sideUv[2], sideUv[1], sideLight); vertCount += 13;
                        } else if (n == 1) { // -X
                            addVert(vertices, vertCount, wx, y1, wz, sideUv[0], sideUv[1], sideLight); vertCount += 13;
                            addVert(vertices, vertCount, wx, y0, wz, sideUv[0], sideUv[3], sideLight); vertCount += 13;
                            addVert(vertices, vertCount, wx, y0, wz+1, sideUv[2], sideUv[3], sideLight); vertCount += 13;
                            addVert(vertices, vertCount, wx, y1, wz+1, sideUv[2], sideUv[1], sideLight); vertCount += 13;
                        } else if (n == 2) { // +Z
                            addVert(vertices, vertCount, wx, y1, wz+1, sideUv[0], sideUv[1], sideLight); vertCount += 13;
                            addVert(vertices, vertCount, wx, y0, wz+1, sideUv[0], sideUv[3], sideLight); vertCount += 13;
                            addVert(vertices, vertCount, wx+1, y0, wz+1, sideUv[2], sideUv[3], sideLight); vertCount += 13;
                            addVert(vertices, vertCount, wx+1, y1, wz+1, sideUv[2], sideUv[1], sideLight); vertCount += 13;
                        } else { // -Z
                            addVert(vertices, vertCount, wx+1, y1, wz, sideUv[0], sideUv[1], sideLight); vertCount += 13;
                            addVert(vertices, vertCount, wx+1, y0, wz, sideUv[0], sideUv[3], sideLight); vertCount += 13;
                            addVert(vertices, vertCount, wx, y0, wz, sideUv[2], sideUv[3], sideLight); vertCount += 13;
                            addVert(vertices, vertCount, wx, y1, wz, sideUv[2], sideUv[1], sideLight); vertCount += 13;
                        }
                        idxCount = addQuadIdx(indices, idxCount, baseVert);
                    }
                }
            }
        }

        MeshData opaque = new MeshData(trim(vertices, vertCount), trimInt(indices, idxCount));
        return new RawMeshResult(opaque, MeshData.EMPTY);
    }

    // ========================================================================
    // LOD 3: Flat colored quad per chunk at average surface height
    // ========================================================================

    private RawMeshResult meshLOD3(Chunk chunk) {
        // Compute average height and dominant surface color
        long totalHeight = 0;
        int count = 0;
        int[] blockCounts = new int[256]; // count of each surface block type

        for (int x = 0; x < WorldConstants.CHUNK_SIZE; x++) {
            for (int z = 0; z < WorldConstants.CHUNK_SIZE; z++) {
                for (int y = WorldConstants.WORLD_HEIGHT - 1; y >= 0; y--) {
                    int blockId = chunk.getBlock(x, y, z);
                    if (blockId != 0 && Blocks.get(blockId).solid()) {
                        totalHeight += y;
                        count++;
                        blockCounts[blockId]++;
                        break;
                    }
                }
            }
        }

        if (count == 0) return new RawMeshResult(MeshData.EMPTY, MeshData.EMPTY);

        float avgHeight = (float) totalHeight / count + 1.0f;

        // Find dominant block type
        int dominantBlock = 4; // default to grass
        int maxCount = 0;
        for (int i = 1; i < blockCounts.length; i++) {
            if (blockCounts[i] > maxCount) {
                maxCount = blockCounts[i];
                dominantBlock = i;
            }
        }

        // Get texture UV for dominant block
        Block block = Blocks.get(dominantBlock);
        int texIdx = block.getTextureIndex(0); // top face
        float[] uv = atlas.getUV(texIdx);

        // Single quad for the entire chunk
        float wx = chunk.getPos().worldX();
        float wz = chunk.getPos().worldZ();
        float size = WorldConstants.CHUNK_SIZE;

        float[] vertices = new float[4 * 13]; // Phase 4: 13 floats per vertex
        int vc = 0;
        float light = 0.9f;

        // Four corners of chunk, at average height
        addVert(vertices, vc, wx, avgHeight, wz, uv[0], uv[1], light); vc += 13;
        addVert(vertices, vc, wx, avgHeight, wz + size, uv[0], uv[3], light); vc += 13;
        addVert(vertices, vc, wx + size, avgHeight, wz + size, uv[2], uv[3], light); vc += 13;
        addVert(vertices, vc, wx + size, avgHeight, wz, uv[2], uv[1], light); vc += 13;

        int[] indices = {0, 1, 2, 0, 2, 3};

        MeshData opaque = new MeshData(vertices, indices);
        return new RawMeshResult(opaque, MeshData.EMPTY);
    }

    // ========================================================================
    // Helper methods
    // ========================================================================

    private int findSurfaceHeight(WorldAccess world, int wx, int wz) {
        for (int y = WorldConstants.WORLD_HEIGHT - 1; y >= 0; y--) {
            int blockId = world.getBlock(wx, y, wz);
            if (blockId != 0 && Blocks.get(blockId).solid()) {
                return y;
            }
        }
        return 0;
    }

    /**
     * Phase 4: Write a 13-float vertex to the array.
     * [x, y, z, u, v, skyVis, blockR, blockG, blockB, horizonW, indR, indG, indB]
     */
    private void addVert(float[] arr, int offset, float x, float y, float z,
                         float u, float v, float skyVisibility) {
        arr[offset] = x;
        arr[offset + 1] = y;
        arr[offset + 2] = z;
        arr[offset + 3] = u;
        arr[offset + 4] = v;
        arr[offset + 5] = skyVisibility; // sky visibility for shader
        arr[offset + 6] = 0.0f;          // blockLightR (unused for LOD meshes)
        arr[offset + 7] = 0.0f;          // blockLightG
        arr[offset + 8] = 0.0f;          // blockLightB
        arr[offset + 9] = 0.3f;          // horizonWeight (balanced default for LOD)
        arr[offset + 10] = 0.0f;         // indirectR (distant chunks don't need GI)
        arr[offset + 11] = 0.0f;         // indirectG
        arr[offset + 12] = 0.0f;         // indirectB
    }

    private int addQuadIdx(int[] arr, int offset, int baseVert) {
        arr[offset] = baseVert;
        arr[offset + 1] = baseVert + 1;
        arr[offset + 2] = baseVert + 2;
        arr[offset + 3] = baseVert;
        arr[offset + 4] = baseVert + 2;
        arr[offset + 5] = baseVert + 3;
        return offset + 6;
    }

    private float[] grow(float[] arr, int newSize) {
        float[] bigger = new float[newSize];
        System.arraycopy(arr, 0, bigger, 0, arr.length);
        return bigger;
    }

    private int[] growInt(int[] arr, int newSize) {
        int[] bigger = new int[newSize];
        System.arraycopy(arr, 0, bigger, 0, arr.length);
        return bigger;
    }

    private float[] trim(float[] arr, int length) {
        if (length == arr.length) return arr;
        float[] trimmed = new float[length];
        System.arraycopy(arr, 0, trimmed, 0, length);
        return trimmed;
    }

    private int[] trimInt(int[] arr, int length) {
        if (length == arr.length) return arr;
        int[] trimmed = new int[length];
        System.arraycopy(arr, 0, trimmed, 0, length);
        return trimmed;
    }

    // Face vertex positions [face][vertex][x,y,z]
    private static final float[][][] FACE_VERTS = {
        // Top (+Y)
        {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
        // Bottom (-Y)
        {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}},
        // North (-Z)
        {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}},
        // South (+Z)
        {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}},
        // East (+X)
        {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}},
        // West (-X)
        {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}},
    };

    // UV indices: [vertex][u_idx, v_idx] where 0 = min, 1 = max
    private static final int[][] FACE_UV = {
        {0, 0}, {0, 1}, {1, 1}, {1, 0},
    };
}
