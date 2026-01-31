package com.voxelgame.world.mesh;

import com.voxelgame.render.TextureAtlas;
import com.voxelgame.world.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-face mesher with ambient occlusion and sky light.
 * Vertex format: [x, y, z, u, v, light] per vertex, 4 vertices per face.
 *
 * For each face, computes per-vertex light as:
 *   light = directionalLight * aoFactor * (skyLight / 15.0)
 *
 * AO is computed by checking the 3 adjacent blocks at each vertex corner.
 * Quad diagonal is flipped when needed to avoid the "AO direction flip" artifact.
 */
public class NaiveMesher implements Mesher {

    // Face indices: 0=top(+Y), 1=bottom(-Y), 2=north(-Z), 3=south(+Z), 4=east(+X), 5=west(-X)
    private static final float[] FACE_LIGHT = {1.0f, 0.6f, 0.8f, 0.8f, 0.85f, 0.75f};

    // Direction offsets for neighbor checking [dx, dy, dz]
    private static final int[][] FACE_NORMALS = {
        { 0,  1,  0}, // top
        { 0, -1,  0}, // bottom
        { 0,  0, -1}, // north
        { 0,  0,  1}, // south
        { 1,  0,  0}, // east
        {-1,  0,  0}, // west
    };

    // AO levels: 0 occluders = 1.0, 1 = 0.75, 2 = 0.5, 3 = 0.35
    private static final float[] AO_LEVELS = {1.0f, 0.75f, 0.5f, 0.35f};

    /**
     * For each face, the 4 vertices and their 3 AO neighbor offsets.
     * Offsets are in LOCAL face space relative to the block position.
     * [vertex][neighbor_index][dx, dy, dz]
     *
     * The 3 neighbors for AO at each vertex are:
     *   side1, side2, and the corner (diagonally opposite along the face plane).
     */

    // Top face (+Y): y+1 plane, vertices at corners of (x, x+1) × (z, z+1)
    // Vertex 0: (x, y+1, z)      — AO checks: (-1,1,0), (0,1,-1), (-1,1,-1)
    // Vertex 1: (x, y+1, z+1)    — AO checks: (-1,1,0), (0,1,+1), (-1,1,+1)
    // Vertex 2: (x+1, y+1, z+1)  — AO checks: (+1,1,0), (0,1,+1), (+1,1,+1)
    // Vertex 3: (x+1, y+1, z)    — AO checks: (+1,1,0), (0,1,-1), (+1,1,-1)
    private static final int[][][] AO_TOP = {
        {{ -1, 1, 0 }, { 0, 1, -1 }, { -1, 1, -1 }},
        {{ -1, 1, 0 }, { 0, 1,  1 }, { -1, 1,  1 }},
        {{  1, 1, 0 }, { 0, 1,  1 }, {  1, 1,  1 }},
        {{  1, 1, 0 }, { 0, 1, -1 }, {  1, 1, -1 }},
    };

    // Bottom face (-Y): y plane
    // Vertex 0: (x, y, z+1)
    // Vertex 1: (x, y, z)
    // Vertex 2: (x+1, y, z)
    // Vertex 3: (x+1, y, z+1)
    private static final int[][][] AO_BOTTOM = {
        {{ -1, -1, 0 }, { 0, -1,  1 }, { -1, -1,  1 }},
        {{ -1, -1, 0 }, { 0, -1, -1 }, { -1, -1, -1 }},
        {{  1, -1, 0 }, { 0, -1, -1 }, {  1, -1, -1 }},
        {{  1, -1, 0 }, { 0, -1,  1 }, {  1, -1,  1 }},
    };

    // North face (-Z): z plane
    // Vertex 0: (x+1, y+1, z)
    // Vertex 1: (x+1, y, z)
    // Vertex 2: (x, y, z)
    // Vertex 3: (x, y+1, z)
    private static final int[][][] AO_NORTH = {
        {{  1, 0, -1 }, { 0, 1, -1 }, {  1, 1, -1 }},
        {{  1, 0, -1 }, { 0, -1, -1 }, {  1, -1, -1 }},
        {{ -1, 0, -1 }, { 0, -1, -1 }, { -1, -1, -1 }},
        {{ -1, 0, -1 }, { 0, 1, -1 }, { -1, 1, -1 }},
    };

    // South face (+Z): z+1 plane
    // Vertex 0: (x, y+1, z+1)
    // Vertex 1: (x, y, z+1)
    // Vertex 2: (x+1, y, z+1)
    // Vertex 3: (x+1, y+1, z+1)
    private static final int[][][] AO_SOUTH = {
        {{ -1, 0, 1 }, { 0, 1, 1 }, { -1, 1, 1 }},
        {{ -1, 0, 1 }, { 0, -1, 1 }, { -1, -1, 1 }},
        {{  1, 0, 1 }, { 0, -1, 1 }, {  1, -1, 1 }},
        {{  1, 0, 1 }, { 0, 1, 1 }, {  1, 1, 1 }},
    };

    // East face (+X): x+1 plane
    // Vertex 0: (x+1, y+1, z+1)
    // Vertex 1: (x+1, y, z+1)
    // Vertex 2: (x+1, y, z)
    // Vertex 3: (x+1, y+1, z)
    private static final int[][][] AO_EAST = {
        {{ 1, 0,  1 }, { 1, 1, 0 }, { 1, 1,  1 }},
        {{ 1, 0,  1 }, { 1, -1, 0 }, { 1, -1,  1 }},
        {{ 1, 0, -1 }, { 1, -1, 0 }, { 1, -1, -1 }},
        {{ 1, 0, -1 }, { 1, 1, 0 }, { 1, 1, -1 }},
    };

    // West face (-X): x plane
    // Vertex 0: (x, y+1, z)
    // Vertex 1: (x, y, z)
    // Vertex 2: (x, y, z+1)
    // Vertex 3: (x, y+1, z+1)
    private static final int[][][] AO_WEST = {
        {{ -1, 0, -1 }, { -1, 1, 0 }, { -1, 1, -1 }},
        {{ -1, 0, -1 }, { -1, -1, 0 }, { -1, -1, -1 }},
        {{ -1, 0,  1 }, { -1, -1, 0 }, { -1, -1,  1 }},
        {{ -1, 0,  1 }, { -1, 1, 0 }, { -1, 1,  1 }},
    };

    private static final int[][][][] AO_OFFSETS = { AO_TOP, AO_BOTTOM, AO_NORTH, AO_SOUTH, AO_EAST, AO_WEST };

    /**
     * For each face, the 4 vertex positions relative to block origin.
     * Format: [face][vertex][x_off, y_off, z_off]
     */
    private static final float[][][] FACE_VERTICES = {
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

    /**
     * UV coordinates for each vertex of each face.
     * Format: [face][vertex][u_idx, v_idx] where 0 = min, 1 = max
     */
    private static final int[][][] FACE_UV = {
        // Top
        {{0, 0}, {0, 1}, {1, 1}, {1, 0}},
        // Bottom
        {{0, 0}, {0, 1}, {1, 1}, {1, 0}},
        // North
        {{0, 0}, {0, 1}, {1, 1}, {1, 0}},
        // South
        {{0, 0}, {0, 1}, {1, 1}, {1, 0}},
        // East
        {{0, 0}, {0, 1}, {1, 1}, {1, 0}},
        // West
        {{0, 0}, {0, 1}, {1, 1}, {1, 0}},
    };

    private TextureAtlas atlas;

    public NaiveMesher(TextureAtlas atlas) {
        this.atlas = atlas;
    }

    @Override
    public ChunkMesh mesh(Chunk chunk, WorldAccess world) {
        List<Float> verts = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        int vertexCount = 0;
        ChunkPos pos = chunk.getPos();
        int cx = pos.x() * WorldConstants.CHUNK_SIZE;
        int cz = pos.z() * WorldConstants.CHUNK_SIZE;

        for (int x = 0; x < WorldConstants.CHUNK_SIZE; x++) {
            for (int y = 0; y < WorldConstants.WORLD_HEIGHT; y++) {
                for (int z = 0; z < WorldConstants.CHUNK_SIZE; z++) {
                    int blockId = chunk.getBlock(x, y, z);
                    if (blockId == 0) continue; // Skip air

                    Block block = Blocks.get(blockId);
                    if (!block.solid() && !block.transparent()) continue;

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

                        int texIdx = block.getTextureIndex(face);
                        float[] uv = atlas.getUV(texIdx);
                        float dirLight = FACE_LIGHT[face];

                        // Compute per-vertex AO and light
                        int[][][] aoOffsets = AO_OFFSETS[face];
                        float[] vertLight = new float[4];
                        int[] aoValues = new int[4];

                        for (int v = 0; v < 4; v++) {
                            // AO: check 3 adjacent blocks for occlusion
                            boolean side1 = isOccluder(world, cx + x, y, cz + z, aoOffsets[v][0]);
                            boolean side2 = isOccluder(world, cx + x, y, cz + z, aoOffsets[v][1]);
                            boolean corner = isOccluder(world, cx + x, y, cz + z, aoOffsets[v][2]);

                            int ao;
                            if (side1 && side2) {
                                ao = 3; // Both sides occlude → corner is also occluded
                            } else {
                                ao = (side1 ? 1 : 0) + (side2 ? 1 : 0) + (corner ? 1 : 0);
                            }
                            aoValues[v] = ao;

                            float aoFactor = AO_LEVELS[ao];

                            // Sample sky light at the face neighbor position (where the air is)
                            // For smooth vertex lighting, average the light from the 4 blocks
                            // adjacent to this vertex along the face normal direction
                            float skyLight = sampleVertexSkyLight(world, cx + x, y, cz + z,
                                                                   face, aoOffsets[v]);
                            float lightFactor = skyLight / 15.0f;

                            vertLight[v] = dirLight * aoFactor * lightFactor;
                        }

                        // Get vertex positions and UVs for this face
                        float[][] fv = FACE_VERTICES[face];
                        int[][] fuv = FACE_UV[face];

                        // Add 4 vertices
                        int baseVertex = vertexCount;
                        for (int v = 0; v < 4; v++) {
                            float u = (fuv[v][0] == 0) ? uv[0] : uv[2];
                            float vCoord = (fuv[v][1] == 0) ? uv[1] : uv[3];
                            addVertex(verts, wx + fv[v][0], wy + fv[v][1], wz + fv[v][2],
                                     u, vCoord, vertLight[v]);
                        }
                        vertexCount += 4;

                        // AO-aware quad splitting: flip diagonal if needed to avoid artifact
                        // If ao[0]+ao[2] > ao[1]+ao[3], flip the quad split diagonal
                        if (aoValues[0] + aoValues[2] > aoValues[1] + aoValues[3]) {
                            // Flipped triangles: (1,2,3) and (1,3,0)
                            indices.add(baseVertex + 1);
                            indices.add(baseVertex + 2);
                            indices.add(baseVertex + 3);
                            indices.add(baseVertex + 1);
                            indices.add(baseVertex + 3);
                            indices.add(baseVertex);
                        } else {
                            // Normal triangles: (0,1,2) and (0,2,3)
                            indices.add(baseVertex);
                            indices.add(baseVertex + 1);
                            indices.add(baseVertex + 2);
                            indices.add(baseVertex);
                            indices.add(baseVertex + 2);
                            indices.add(baseVertex + 3);
                        }
                    }
                }
            }
        }

        float[] vertArray = new float[verts.size()];
        for (int i = 0; i < verts.size(); i++) {
            vertArray[i] = verts.get(i);
        }

        int[] idxArray = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            idxArray[i] = indices.get(i);
        }

        ChunkMesh mesh = new ChunkMesh();
        mesh.upload(vertArray, idxArray);
        return mesh;
    }

    /**
     * Check if a block at (bx + offset) is an occluder for AO purposes.
     * An occluder is a solid, opaque block.
     */
    private boolean isOccluder(WorldAccess world, int bx, int by, int bz, int[] offset) {
        int wx = bx + offset[0];
        int wy = by + offset[1];
        int wz = bz + offset[2];
        if (wy < 0 || wy >= WorldConstants.WORLD_HEIGHT) return false;
        int blockId = world.getBlock(wx, wy, wz);
        Block block = Blocks.get(blockId);
        return block.solid() && !block.transparent();
    }

    /**
     * Sample sky light for a vertex by averaging the light from the face's
     * normal direction neighbor and the 3 AO neighbor positions.
     * This gives smooth per-vertex lighting.
     */
    private float sampleVertexSkyLight(WorldAccess world, int bx, int by, int bz,
                                        int face, int[][] aoNeighbors) {
        int[] normal = FACE_NORMALS[face];
        // The face neighbor (where the air is)
        int fnx = bx + normal[0];
        int fny = by + normal[1];
        int fnz = bz + normal[2];

        float totalLight = getSkyLightSafe(world, fnx, fny, fnz);
        int count = 1;

        // Also sample from the AO neighbor directions (shifted by face normal)
        // These give us smooth gradients across the face
        for (int[] ao : aoNeighbors) {
            int sx = bx + ao[0];
            int sy = by + ao[1];
            int sz = bz + ao[2];
            // Only sample light from non-opaque positions
            int blockId = world.getBlock(sx, sy, sz);
            Block block = Blocks.get(blockId);
            if (!block.solid() || block.transparent()) {
                totalLight += getSkyLightSafe(world, sx, sy, sz);
                count++;
            }
        }

        return totalLight / count;
    }

    private float getSkyLightSafe(WorldAccess world, int x, int y, int z) {
        if (y >= WorldConstants.WORLD_HEIGHT) return 15.0f;
        if (y < 0) return 0.0f;
        return world.getSkyLight(x, y, z);
    }

    private void addVertex(List<Float> verts, float x, float y, float z,
                          float u, float v, float light) {
        verts.add(x);
        verts.add(y);
        verts.add(z);
        verts.add(u);
        verts.add(v);
        verts.add(light);
    }
}
