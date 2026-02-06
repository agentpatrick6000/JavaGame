package com.voxelgame.world.mesh;

import com.voxelgame.render.TextureAtlas;
import com.voxelgame.world.*;
import com.voxelgame.world.lighting.ProbeManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-face mesher with ambient occlusion, sky visibility, horizon weight, and indirect lighting.
 * Vertex format: [x, y, z, u, v, skyVisibility, blockLightR, blockLightG, blockLightB, horizonWeight, indirectR, indirectG, indirectB] per vertex, 4 vertices per face.
 *
 * Phase 4 Unified Lighting Model:
 * - skyVisibility (0-1): passed to shader for dynamic sky RGB computation
 * - blockLightR/G/B (0-1): RGB block light from colored light sources (torch=orange, lava=red, etc.)
 * - horizonWeight (0-1): 0 = zenith visible (open sky), 1 = only horizon visible (under overhang)
 * - indirectR/G/B: one-bounce GI from irradiance probes
 * - AO is baked into skyVisibility and blockLight as a multiplier
 *
 * The shader computes final lighting as:
 *   skyColor = mix(uSkyZenithColor, uSkyHorizonColor, vHorizonWeight)
 *   skyRGB = skyColor * vSkyVisibility * uSkyIntensity
 *   sunRGB = uSunColor * NdotL * uSunIntensity * vSkyVisibility
 *   blockRGB = vBlockLightRGB * flickerAmount  // Phase 4: direct RGB with flicker
 *   indirectRGB = vIndirectRGB * INDIRECT_STRENGTH
 *   totalRGB = skyRGB + sunRGB + blockRGB + indirectRGB
 *
 * Under overhangs: horizonWeight ≈ 1.0, so blocks get warm orange tint at sunset
 * Open sky: horizonWeight ≈ 0.3, so blocks get balanced zenith/horizon mix
 *
 * AO is computed by checking the 3 adjacent blocks at each vertex corner.
 * Quad diagonal is flipped when needed to avoid the "AO direction flip" artifact.
 */
public class NaiveMesher implements Mesher {

    // Face indices: 0=top(+Y), 1=bottom(-Y), 2=north(-Z), 3=south(+Z), 4=east(+X), 5=west(-X)
    // Wider spread between faces gives stronger directional depth cues
    private static final float[] FACE_LIGHT = {1.0f, 0.45f, 0.7f, 0.7f, 0.8f, 0.6f};

    // Direction offsets for neighbor checking [dx, dy, dz]
    private static final int[][] FACE_NORMALS = {
        { 0,  1,  0}, // top
        { 0, -1,  0}, // bottom
        { 0,  0, -1}, // north
        { 0,  0,  1}, // south
        { 1,  0,  0}, // east
        {-1,  0,  0}, // west
    };

    // AO levels: 0 occluders = 1.0, 1 = 0.55, 2 = 0.3, 3 = 0.15
    // Aggressive curve gives visible depth at block edges and corners
    private static final float[] AO_LEVELS = {1.0f, 0.55f, 0.3f, 0.15f};

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

    /** Probe manager for indirect lighting (may be null if not yet initialized) */
    private static ProbeManager probeManager;
    
    /** Set the probe manager for indirect lighting sampling. */
    public static void setProbeManager(ProbeManager pm) {
        probeManager = pm;
    }

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
    public RawMeshResult meshAllRaw(Chunk chunk, WorldAccess world) {
        // Separate lists for opaque and transparent geometry
        List<Float> opaqueVerts = new ArrayList<>();
        List<Integer> opaqueIndices = new ArrayList<>();
        int opaqueVertexCount = 0;

        List<Float> transVerts = new ArrayList<>();
        List<Integer> transIndices = new ArrayList<>();
        int transVertexCount = 0;

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

                    // Determine if this block is transparent (water, etc.)
                    boolean isTransparent = block.transparent() && !block.solid();

                    // Choose the target vertex/index lists
                    List<Float> verts = isTransparent ? transVerts : opaqueVerts;
                    List<Integer> indices = isTransparent ? transIndices : opaqueIndices;

                    float wx = cx + x;
                    float wy = y;
                    float wz = cz + z;

                    // Special rendering for torches: small centered cube
                    // Rendered in OPAQUE pass with alpha-discard (shader discards alpha < 0.1)
                    // This prevents the transparent pass's uAlpha/depth-write-off from making torches see-through
                    if (blockId == Blocks.TORCH.id() || blockId == Blocks.REDSTONE_TORCH.id()) {
                        opaqueVertexCount = meshTorch(opaqueVerts, opaqueIndices, opaqueVertexCount,
                                                       wx, wy, wz, block, atlas);
                        continue;
                    }

                    // Special rendering for flowers: cross-shaped billboard
                    // Rendered in OPAQUE pass with alpha-discard
                    if (Blocks.isFlower(blockId)) {
                        opaqueVertexCount = meshCross(opaqueVerts, opaqueIndices, opaqueVertexCount,
                                                       wx, wy, wz, block, atlas);
                        continue;
                    }

                    // Special rendering for wheat crops: cross-shaped billboard (like flowers)
                    if (Blocks.isWheatCrop(blockId)) {
                        opaqueVertexCount = meshCross(opaqueVerts, opaqueIndices, opaqueVertexCount,
                                                       wx, wy, wz, block, atlas);
                        continue;
                    }

                    // Special rendering for rails: flat quad on ground
                    // Rendered in OPAQUE pass with alpha-discard
                    if (Blocks.isRail(blockId)) {
                        opaqueVertexCount = meshFlatQuad(opaqueVerts, opaqueIndices, opaqueVertexCount,
                                                          wx, wy, wz, block, atlas);
                        continue;
                    }

                    // Special rendering for redstone wire: flat quad on ground
                    // Rendered in OPAQUE pass with alpha-discard
                    if (blockId == Blocks.REDSTONE_WIRE.id()) {
                        opaqueVertexCount = meshFlatQuad(opaqueVerts, opaqueIndices, opaqueVertexCount,
                                                          wx, wy, wz, block, atlas);
                        continue;
                    }

                    // Special rendering for redstone repeater: flat slab on ground
                    if (blockId == Blocks.REDSTONE_REPEATER.id()) {
                        transVertexCount = meshFlatSlab(transVerts, transIndices, transVertexCount,
                                                         wx, wy, wz, block, atlas);
                        continue;
                    }

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

                        // Compute per-vertex AO and separate sky/block light
                        int[][][] aoOffsets = AO_OFFSETS[face];
                        float[] vertSkyLight = new float[4];
                        float[][] vertBlockLightRGB = new float[4][3];  // Phase 4: RGB per vertex
                        int[] aoValues = new int[4];

                        // Compute per-vertex visibility using HeightfieldVisibility
                        float[] vertHorizonWeight = new float[4];
                        
                        for (int v = 0; v < 4; v++) {
                            boolean side1 = isOccluder(world, cx + x, y, cz + z, aoOffsets[v][0]);
                            boolean side2 = isOccluder(world, cx + x, y, cz + z, aoOffsets[v][1]);
                            boolean corner = isOccluder(world, cx + x, y, cz + z, aoOffsets[v][2]);

                            int ao;
                            if (side1 && side2) {
                                ao = 3;
                            } else {
                                ao = (side1 ? 1 : 0) + (side2 ? 1 : 0) + (corner ? 1 : 0);
                            }
                            aoValues[v] = ao;

                            float aoFactor = AO_LEVELS[ao];
                            
                            // Sample visibility using HeightfieldVisibility for zenith/horizon split
                            int sampleX = cx + x + FACE_NORMALS[face][0];
                            int sampleY = y + FACE_NORMALS[face][1];
                            int sampleZ = cz + z + FACE_NORMALS[face][2];
                            
                            float existingVis = getSkyVisibilitySafe(world, sampleX, sampleY, sampleZ);
                            HeightfieldVisibility.VisibilityResult visResult = 
                                HeightfieldVisibility.computeWithHint(world, sampleX, sampleY, sampleZ, existingVis);
                            
                            float skyVis = visResult.visibility;
                            vertHorizonWeight[v] = visResult.horizonWeight;
                            
                            // Phase 4: Sample RGB block light
                            float[] blockLightRGB = sampleVertexBlockLightRGB(world, cx + x, y, cz + z,
                                                                               face, aoOffsets[v]);

                            // Pass visibility and block light with AO and directional factors baked in
                            // Shader computes actual RGB from these + time-of-day uniforms
                            vertSkyLight[v] = dirLight * aoFactor * skyVis;
                            vertBlockLightRGB[v][0] = dirLight * aoFactor * blockLightRGB[0];
                            vertBlockLightRGB[v][1] = dirLight * aoFactor * blockLightRGB[1];
                            vertBlockLightRGB[v][2] = dirLight * aoFactor * blockLightRGB[2];
                        }

                        float[][] fv = FACE_VERTICES[face];
                        int[][] fuv = FACE_UV[face];

                        // Sample indirect lighting for this face (sample at face center)
                        float faceX = wx + 0.5f + FACE_NORMALS[face][0] * 0.5f;
                        float faceY = wy + 0.5f + FACE_NORMALS[face][1] * 0.5f;
                        float faceZ = wz + 0.5f + FACE_NORMALS[face][2] * 0.5f;
                        float[] indirect = sampleIndirect(faceX, faceY, faceZ);

                        int baseVertex = isTransparent ? transVertexCount : opaqueVertexCount;
                        for (int v = 0; v < 4; v++) {
                            float u = (fuv[v][0] == 0) ? uv[0] : uv[2];
                            float vCoord = (fuv[v][1] == 0) ? uv[1] : uv[3];
                            addVertex(verts, wx + fv[v][0], wy + fv[v][1], wz + fv[v][2],
                                     u, vCoord, vertSkyLight[v], 
                                     vertBlockLightRGB[v][0], vertBlockLightRGB[v][1], vertBlockLightRGB[v][2],
                                     vertHorizonWeight[v],
                                     indirect[0], indirect[1], indirect[2]);
                        }
                        if (isTransparent) {
                            transVertexCount += 4;
                        } else {
                            opaqueVertexCount += 4;
                        }

                        // AO-aware quad splitting
                        if (aoValues[0] + aoValues[2] > aoValues[1] + aoValues[3]) {
                            indices.add(baseVertex + 1);
                            indices.add(baseVertex + 2);
                            indices.add(baseVertex + 3);
                            indices.add(baseVertex + 1);
                            indices.add(baseVertex + 3);
                            indices.add(baseVertex);
                        } else {
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

        MeshData opaqueData = buildMeshData(opaqueVerts, opaqueIndices);
        MeshData transparentData = buildMeshData(transVerts, transIndices);
        return new RawMeshResult(opaqueData, transparentData);
    }

    @Override
    public MeshResult meshAll(Chunk chunk, WorldAccess world) {
        // meshAll delegates to raw + upload (for main-thread callers)
        RawMeshResult raw = meshAllRaw(chunk, world);
        return raw.upload();
    }

    private MeshData buildMeshData(List<Float> verts, List<Integer> indices) {
        float[] vertArray = new float[verts.size()];
        for (int i = 0; i < verts.size(); i++) {
            vertArray[i] = verts.get(i);
        }
        int[] idxArray = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            idxArray[i] = indices.get(i);
        }
        return new MeshData(vertArray, idxArray);
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
     * Sample block light for a vertex, similar to sky light sampling.
     * @deprecated Use sampleVertexBlockLightRGB for Phase 4 colored lighting.
     */
    @Deprecated
    private float sampleVertexBlockLight(WorldAccess world, int bx, int by, int bz,
                                          int face, int[][] aoNeighbors) {
        int[] normal = FACE_NORMALS[face];
        int fnx = bx + normal[0];
        int fny = by + normal[1];
        int fnz = bz + normal[2];

        float totalLight = getBlockLightSafe(world, fnx, fny, fnz);
        int count = 1;

        for (int[] ao : aoNeighbors) {
            int sx = bx + ao[0];
            int sy = by + ao[1];
            int sz = bz + ao[2];
            int blockId = world.getBlock(sx, sy, sz);
            Block block = Blocks.get(blockId);
            if (!block.solid() || block.transparent()) {
                totalLight += getBlockLightSafe(world, sx, sy, sz);
                count++;
            }
        }

        return totalLight / count;
    }

    /**
     * Phase 4: Sample RGB block light for a vertex.
     * Averages samples from face-normal position and AO neighbor positions.
     * @return RGB light values [R, G, B] each 0-1
     */
    private float[] sampleVertexBlockLightRGB(WorldAccess world, int bx, int by, int bz,
                                               int face, int[][] aoNeighbors) {
        int[] normal = FACE_NORMALS[face];
        int fnx = bx + normal[0];
        int fny = by + normal[1];
        int fnz = bz + normal[2];

        float[] totalLight = getBlockLightRGBSafe(world, fnx, fny, fnz);
        int count = 1;

        for (int[] ao : aoNeighbors) {
            int sx = bx + ao[0];
            int sy = by + ao[1];
            int sz = bz + ao[2];
            int blockId = world.getBlock(sx, sy, sz);
            Block block = Blocks.get(blockId);
            if (!block.solid() || block.transparent()) {
                float[] sample = getBlockLightRGBSafe(world, sx, sy, sz);
                totalLight[0] += sample[0];
                totalLight[1] += sample[1];
                totalLight[2] += sample[2];
                count++;
            }
        }

        return new float[] {
            totalLight[0] / count,
            totalLight[1] / count,
            totalLight[2] / count
        };
    }

    private float getBlockLightSafe(WorldAccess world, int x, int y, int z) {
        if (y >= WorldConstants.WORLD_HEIGHT) return 0.0f;
        if (y < 0) return 0.0f;
        return world.getBlockLight(x, y, z);
    }

    private float[] getBlockLightRGBSafe(WorldAccess world, int x, int y, int z) {
        if (y >= WorldConstants.WORLD_HEIGHT || y < 0) {
            return new float[] {0, 0, 0};
        }
        return world.getBlockLightRGB(x, y, z);
    }

    private float getSkyVisibilitySafe(WorldAccess world, int x, int y, int z) {
        if (y >= WorldConstants.WORLD_HEIGHT) return 1.0f;
        if (y < 0) return 0.0f;
        return world.getSkyVisibility(x, y, z);
    }

    /**
     * Phase 4: Add vertex with RGB block light (13 floats per vertex).
     * Format: [x, y, z, u, v, skyVis, blockR, blockG, blockB, horizonWeight, indirectR, indirectG, indirectB]
     */
    private void addVertex(List<Float> verts, float x, float y, float z,
                          float u, float v, float skyVisibility, 
                          float blockLightR, float blockLightG, float blockLightB,
                          float horizonWeight,
                          float indirectR, float indirectG, float indirectB) {
        verts.add(x);
        verts.add(y);
        verts.add(z);
        verts.add(u);
        verts.add(v);
        verts.add(skyVisibility);
        verts.add(blockLightR);
        verts.add(blockLightG);
        verts.add(blockLightB);
        verts.add(horizonWeight);
        verts.add(indirectR);
        verts.add(indirectG);
        verts.add(indirectB);
    }
    
    /**
     * Sample indirect lighting from probe manager at world position.
     * Returns [0,0,0] if probe manager is not available.
     */
    private float[] sampleIndirect(float worldX, float worldY, float worldZ) {
        if (probeManager != null) {
            return probeManager.sampleIndirect(worldX, worldY, worldZ);
        }
        return new float[] {0, 0, 0};
    }

    /**
     * Mesh a torch as a small centered cube (4/16 wide, 10/16 tall)
     * with bright light value for the glow effect.
     * Phase 4: Use RGB block light color for torch self-illumination.
     */
    private int meshTorch(List<Float> verts, List<Integer> indices, int vertexCount,
                           float wx, float wy, float wz, Block block, TextureAtlas atlas) {
        float[] uv = atlas.getUV(block.getTextureIndex(0));
        float skyL = 1.0f;  // fully bright sky component
        float hrzW = 0.3f;  // balanced horizon weight for torches
        
        // Phase 4: Get torch color from LightEmitters (self-illuminated at full intensity)
        float[] torchColor = LightEmitters.getLightColorRGB(block.id());
        float blkR = torchColor != null ? torchColor[0] : 1.0f;
        float blkG = torchColor != null ? torchColor[1] : 0.8f;
        float blkB = torchColor != null ? torchColor[2] : 0.5f;
        
        // Sample indirect lighting at torch position
        float[] ind = sampleIndirect(wx + 0.5f, wy + 0.5f, wz + 0.5f);

        // Inset: centered 4/16 wide, 10/16 tall
        float pad = 6.0f / 16.0f;  // 6/16 padding on each side
        float x0 = wx + pad, x1 = wx + 1.0f - pad;
        float z0 = wz + pad, z1 = wz + 1.0f - pad;
        float y0 = wy, y1 = wy + 10.0f / 16.0f;

        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];

        // 6 faces of the small cube
        int base = vertexCount;

        // Top (+Y)
        addVertex(verts, x0, y1, z0, u0, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x0, y1, z1, u0, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x1, y1, z1, u1, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x1, y1, z0, u1, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        // Bottom (-Y)
        addVertex(verts, x0, y0, z1, u0, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x0, y0, z0, u0, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x1, y0, z0, u1, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x1, y0, z1, u1, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        // North (-Z)
        addVertex(verts, x1, y1, z0, u0, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x1, y0, z0, u0, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x0, y0, z0, u1, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x0, y1, z0, u1, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        // South (+Z)
        addVertex(verts, x0, y1, z1, u0, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x0, y0, z1, u0, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x1, y0, z1, u1, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x1, y1, z1, u1, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        // East (+X)
        addVertex(verts, x1, y1, z1, u0, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x1, y0, z1, u0, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x1, y0, z0, u1, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x1, y1, z0, u1, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        // West (-X)
        addVertex(verts, x0, y1, z0, u0, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x0, y0, z0, u0, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x0, y0, z1, u1, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x0, y1, z1, u1, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        return base;
    }

    /**
     * Mesh a flower/cross-shaped block as two intersecting quads (X-pattern).
     * Classic Minecraft billboard rendering for plants.
     * Phase 4: No block light emission for flowers (blkR/G/B = 0).
     */
    private int meshCross(List<Float> verts, List<Integer> indices, int vertexCount,
                           float wx, float wy, float wz, Block block, TextureAtlas atlas) {
        float[] uv = atlas.getUV(block.getTextureIndex(0));
        float skyL = 0.85f;  // slightly dimmer sky light
        float blkR = 0.0f, blkG = 0.0f, blkB = 0.0f;  // no block light emission
        float hrzW = 0.3f;   // balanced horizon weight
        
        // Sample indirect lighting at flower center
        float[] ind = sampleIndirect(wx + 0.5f, wy + 0.5f, wz + 0.5f);

        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        int base = vertexCount;

        // Diagonal 1: corner (0,0)→(1,1) — front and back
        addVertex(verts, wx, wy + 1, wz, u0, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, wy, wz, u0, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, wy, wz + 1, u1, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, wy + 1, wz + 1, u1, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        // Back face of diagonal 1
        addVertex(verts, wx + 1, wy + 1, wz + 1, u0, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, wy, wz + 1, u0, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, wy, wz, u1, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, wy + 1, wz, u1, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        // Diagonal 2: corner (1,0)→(0,1) — front and back
        addVertex(verts, wx + 1, wy + 1, wz, u0, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, wy, wz, u0, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, wy, wz + 1, u1, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, wy + 1, wz + 1, u1, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        // Back face of diagonal 2
        addVertex(verts, wx, wy + 1, wz + 1, u0, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, wy, wz + 1, u0, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, wy, wz, u1, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, wy + 1, wz, u1, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        return base;
    }

    /**
     * Mesh a flat quad on the ground surface (for rails, redstone wire).
     * Renders as a single horizontal quad slightly above the block floor.
     * Phase 4: RGB block light = 0 for non-emissive blocks.
     */
    private int meshFlatQuad(List<Float> verts, List<Integer> indices, int vertexCount,
                              float wx, float wy, float wz, Block block, TextureAtlas atlas) {
        float[] uv = atlas.getUV(block.getTextureIndex(0));
        float skyL = 0.85f;
        float blkR = 0.0f, blkG = 0.0f, blkB = 0.0f;
        float hrzW = 0.3f;
        
        // Sample indirect lighting
        float[] ind = sampleIndirect(wx + 0.5f, wy + 0.1f, wz + 0.5f);

        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        float yOff = wy + 0.02f;  // slightly above ground to avoid z-fighting
        int base = vertexCount;

        // Top face
        addVertex(verts, wx, yOff, wz, u0, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, yOff, wz + 1, u0, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, yOff, wz + 1, u1, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, yOff, wz, u1, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        // Bottom face (visible from below)
        addVertex(verts, wx + 1, yOff, wz, u0, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, yOff, wz + 1, u0, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, yOff, wz + 1, u1, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, yOff, wz, u1, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        return base;
    }

    /**
     * Mesh a flat slab on the ground surface (for redstone repeater).
     * Renders as a thin box (2/16 tall) on the ground.
     * Phase 4: RGB block light = 0 for non-emissive blocks.
     */
    private int meshFlatSlab(List<Float> verts, List<Integer> indices, int vertexCount,
                              float wx, float wy, float wz, Block block, TextureAtlas atlas) {
        float[] uv = atlas.getUV(block.getTextureIndex(0));
        float skyL = 0.8f;
        float blkR = 0.0f, blkG = 0.0f, blkB = 0.0f;
        float hrzW = 0.3f;
        
        // Sample indirect lighting
        float[] ind = sampleIndirect(wx + 0.5f, wy + 0.1f, wz + 0.5f);

        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        float y0 = wy;
        float y1 = wy + 2.0f / 16.0f;
        int base = vertexCount;

        // Top (+Y)
        addVertex(verts, wx, y1, wz, u0, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, y1, wz + 1, u0, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, y1, wz + 1, u1, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, y1, wz, u1, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        // North (-Z)
        addVertex(verts, wx + 1, y1, wz, u0, v0, skyL * 0.7f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, y0, wz, u0, v1, skyL * 0.7f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, y0, wz, u1, v1, skyL * 0.7f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, y1, wz, u1, v0, skyL * 0.7f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        // South (+Z)
        addVertex(verts, wx, y1, wz + 1, u0, v0, skyL * 0.7f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, y0, wz + 1, u0, v1, skyL * 0.7f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, y0, wz + 1, u1, v1, skyL * 0.7f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, y1, wz + 1, u1, v0, skyL * 0.7f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        // East (+X)
        addVertex(verts, wx + 1, y1, wz + 1, u0, v0, skyL * 0.8f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, y0, wz + 1, u0, v1, skyL * 0.8f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, y0, wz, u1, v1, skyL * 0.8f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, y1, wz, u1, v0, skyL * 0.8f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        // West (-X)
        addVertex(verts, wx, y1, wz, u0, v0, skyL * 0.6f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, y0, wz, u0, v1, skyL * 0.6f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, y0, wz + 1, u1, v1, skyL * 0.6f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, y1, wz + 1, u1, v0, skyL * 0.6f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        return base;
    }

    /** Add standard quad indices (two triangles) for 4 vertices starting at base. */
    private void addQuadIndices(List<Integer> indices, int base) {
        indices.add(base);
        indices.add(base + 1);
        indices.add(base + 2);
        indices.add(base);
        indices.add(base + 2);
        indices.add(base + 3);
    }
}
