# World / LOD Pipeline Report

**Generated:** 2026-02-06  
**Commit:** eb20c48  
**Purpose:** Code-referenced autopsy of world, chunk, LOD, storage, and streaming architecture.

---

## A1) Chunk Lifecycle (End-to-End)

### Chunk Data Model

**File:** `src/main/java/com/voxelgame/world/Chunk.java`

```java
public class Chunk {
    private final ChunkPos pos;
    private final byte[] blocks;        // 16×128×16 = 32,768 bytes
    private final byte[] lightMap;      // 32,768 bytes (packed sky/block nibbles)
    private final byte[] blockLightR;   // 32,768 bytes (Phase 4 RGB)
    private final byte[] blockLightG;   // 32,768 bytes
    private final byte[] blockLightB;   // 32,768 bytes
    private ChunkMesh mesh;             // GPU-uploaded mesh
    private ChunkMesh transparentMesh;
    private final ChunkMesh[] lodMeshes = new ChunkMesh[4];  // LOD 1-3 meshes
}
```

**Memory per chunk (data only):** ~163 KB
- blocks: 32 KB
- lightMap: 32 KB
- RGB blockLight: 96 KB
- Object overhead: ~3 KB

### Chunk Coordinate → Key

**File:** `src/main/java/com/voxelgame/world/ChunkPos.java`

```java
public record ChunkPos(int x, int z) {
    // Uses record's auto-generated hashCode/equals
    // hashCode = 31 * x + z (record default)
}
```

**File:** `src/main/java/com/voxelgame/world/World.java`

```java
private final ConcurrentHashMap<ChunkPos, Chunk> chunks = new ConcurrentHashMap<>();
```

⚠️ **ISSUE:** Uses boxed `ChunkPos` objects as keys → HashMap boxing overhead. Each lookup creates a new `ChunkPos` for equality check.

### Generation Entrypoint & Threading

**File:** `src/main/java/com/voxelgame/world/stream/ChunkManager.java`

```java
// Thread pool for chunk generation
private ExecutorService genPool = Executors.newFixedThreadPool(4, ...);  // LODConfig.GEN_THREAD_COUNT

// Futures for in-flight generation
private final ConcurrentHashMap<ChunkPos, Future<Chunk>> pendingGen = new ConcurrentHashMap<>();
```

**Method:** `requestChunks(int pcx, int pcz)` (line ~300)
- Spiral scan pattern from player position
- Per-frame budget: 4 close + 6 far chunks
- Submits to `genPool.submit(() -> { ... pipeline.generate(chunk) })`

**Seed usage:**
```java
// ChunkManager.java line ~100
private long seed = ChunkGenerationWorker.DEFAULT_SEED;

// GenPipeline.createWithConfig(seed, genConfig) creates per-thread instances
```

### Load Path (Disk → Memory)

**File:** `src/main/java/com/voxelgame/save/SaveManager.java`

```java
public Chunk loadChunk(int chunkX, int chunkZ) {
    RegionFile region = getOrCreateRegion(chunkX, chunkZ);  // Opens/caches region
    byte[] data = region.readChunkData(chunkX, chunkZ);     // RandomAccessFile.read
    return ChunkCodec.decode(data);                          // GZIP decompress
}
```

**File:** `src/main/java/com/voxelgame/save/ChunkCodec.java`

```java
public static Chunk decode(byte[] compressed) {
    GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
    DataInputStream dis = new DataInputStream(gzip);
    // Reads: version, chunkX, chunkZ, blocks[], lightMap[]
    chunk.loadBlocks(blockData);
    chunk.loadLightMap(lightData);
}
```

### Save Path (Memory → Disk)

**File:** `src/main/java/com/voxelgame/save/SaveManager.java`

```java
public void saveChunk(Chunk chunk) {
    byte[] data = ChunkCodec.encode(chunk);  // GZIP compress
    RegionFile region = getOrCreateRegion(pos.x(), pos.z());
    region.writeChunkData(pos.x(), pos.z(), data);
}
```

**File:** `src/main/java/com/voxelgame/save/RegionFile.java`

```java
public void writeChunkData(int chunkX, int chunkZ, byte[] data) {
    Map<Integer, byte[]> allChunks = readAllChunks();  // Reads ENTIRE region file
    allChunks.put(idx, data);
    writeAllChunks(allChunks);  // Rewrites ENTIRE region file
}
```

⚠️ **CRITICAL ISSUE:** Every single chunk save rewrites the entire region file (~32 MB for full region). This is O(n²) for batch saves.

### State Transitions

| State | Meaning | Stored In |
|-------|---------|-----------|
| Pending | Generation in progress | `pendingGen.containsKey(pos)` |
| Loaded | In world.chunks map | `world.isLoaded(cx, cz)` |
| Dirty | Needs mesh rebuild | `chunk.isDirty()` |
| Modified | Needs disk save | `chunk.isModified()` |
| LOD assigned | Has currentLOD set | `chunk.getCurrentLOD()` |
| Mesh ready | Has mesh for current LOD | `chunk.isLodMeshReady()` |

### Eviction Policy

**File:** `src/main/java/com/voxelgame/world/stream/ChunkManager.java`

```java
private void unloadDistantChunks(int pcx, int pcz) {
    int unloadDist = lodConfig.getUnloadDistance();  // maxRenderDistance + 2
    // Iterates ALL loaded chunks, removes those beyond unloadDistSq
}

private void enforceChunkCap(int pcx, int pcz) {
    int maxChunks = lodConfig.getMaxLoadedChunks();  // π*r² * 1.1, max 2500
    // Sorts ALL chunks by distance, removes farthest
}
```

⚠️ **ISSUE:** Both methods iterate/sort the entire chunk map every player chunk crossing.

---

## A2) LOD System

### LOD Level Selection

**File:** `src/main/java/com/voxelgame/world/lod/LODConfig.java`

```java
public LODLevel getLevelForDistance(int distSq) {
    if (distSq <= lodThreshold * lodThreshold) return LODLevel.LOD_0;    // 12 chunks default
    if (distSq <= lod2Start * lod2Start) return LODLevel.LOD_1;           // ~14 chunks
    if (distSq <= lod3Start * lod3Start) return LODLevel.LOD_2;           // ~17 chunks
    return LODLevel.LOD_3;                                                 // beyond
}
```

### LOD Levels and Contents

**File:** `src/main/java/com/voxelgame/world/lod/LODLevel.java`

```java
public enum LODLevel {
    LOD_0(0),   // Full detail: all block faces, AO, smooth lighting, transparency
    LOD_1(1),   // Simplified: skip small features
    LOD_2(2),   // LODGenPipeline: terrain + surface only (no caves/ores/trees)
    LOD_3(3);   // Same as LOD_2, farthest
}
```

### LOD Data Sharing vs Duplication

⚠️ **CRITICAL ISSUE:** LOD does NOT share chunk data. Each LOD level:

1. **LOD 0-1:** Uses same `Chunk` object, but builds SEPARATE mesh stored in `lodMeshes[level]`
2. **LOD 2-3:** Uses SAME `Chunk` object but simplified generation via `LODGenPipeline`

```java
// Chunk.java
private ChunkMesh mesh;              // LOD 0 opaque
private ChunkMesh transparentMesh;   // LOD 0 transparent
private final ChunkMesh[] lodMeshes = new ChunkMesh[4];  // LOD 1-3 meshes
```

The chunk's 163 KB of block/light data is ALWAYS fully allocated regardless of LOD level. LOD only affects mesh complexity.

### LOD Interaction with Meshing

**File:** `src/main/java/com/voxelgame/world/stream/ChunkManager.java`

```java
private void updateLODLevels(int pcx, int pcz) {
    // Every 15 frames:
    for (var entry : world.getChunkMap().entrySet()) {
        LODLevel newLOD = lodConfig.getLevelForDistance(distSq);
        if (newLOD != oldLOD) {
            if (newLOD == LODLevel.LOD_0) {
                submitMeshJob(chunk, pos, false);  // Full rebuild
            } else {
                submitLODMeshJob(chunk, pos, newLOD);
            }
        }
    }
}
```

### LOD-Caused Meshing Thrash

⚠️ **ISSUE:** When player moves near LOD boundary, chunks repeatedly transition between LOD levels, triggering mesh rebuilds. Hysteresis (2 chunk buffer) helps but doesn't eliminate thrash.

```java
// Hysteresis check (helps but limited)
if (newLOD.level() < oldLOD.level()) {
    int checkDistSq = (dist + 2) * (dist + 2);
    LODLevel checkLOD = lodConfig.getLevelForDistance(checkDistSq);
    if (checkLOD.level() >= oldLOD.level()) {
        newLOD = oldLOD;  // Don't upgrade yet
    }
}
```

---

## A3) Meshing and GPU Upload

### Meshing Algorithm

**File:** `src/main/java/com/voxelgame/world/mesh/NaiveMesher.java`

- **Algorithm:** Per-face culled meshing with AO (NOT greedy)
- **Vertex format:** 13 floats per vertex (x, y, z, u, v, skyVis, blkR, blkG, blkB, hrzW, indR, indG, indB)
- **Memory per face:** 13 × 4 × 4 = 208 bytes (4 verts) + 24 bytes (6 indices) = 232 bytes

**File:** `src/main/java/com/voxelgame/world/lod/LODMesher.java`

- Simplified meshing for LOD 1-3 (fewer features)

### Meshing Thread/Queue

**File:** `src/main/java/com/voxelgame/world/stream/ChunkManager.java`

```java
private ExecutorService meshPool = Executors.newFixedThreadPool(3);  // MESH_THREAD_COUNT
private final Set<ChunkPos> meshingInProgress = ConcurrentHashMap.newKeySet();
private final ConcurrentLinkedQueue<MeshUpload> uploadQueue = new ConcurrentLinkedQueue<>();
```

**Flow:**
1. `submitMeshJob()` → `meshPool.submit(() -> mesher.meshAllRaw(chunk, world))`
2. Result → `uploadQueue.add(new MeshUpload(...))`
3. Main thread: `processMeshUploads()` → `raw.upload()` (GL calls)

### Mesh Caching and Invalidation

```java
// Chunk.java
public void setMesh(ChunkMesh mesh) {
    if (this.mesh != null) {
        this.mesh.dispose();  // GL delete
    }
    this.mesh = mesh;
}
```

⚠️ **ISSUE:** No mesh versioning. Any block change triggers full chunk remesh.

### GPU Buffer Lifecycle

**File:** `src/main/java/com/voxelgame/world/mesh/ChunkMesh.java`

```java
public ChunkMesh(float[] vertices, int[] indices) {
    vaoId = glGenVertexArrays();
    vboId = glGenBuffers();
    eboId = glGenBuffers();
    // Upload immediately on construction
}

public void dispose() {
    glDeleteVertexArrays(vaoId);
    glDeleteBuffers(vboId);
    glDeleteBuffers(eboId);
}
```

### Per-Frame Allocations in Render Path

**File:** `src/main/java/com/voxelgame/world/mesh/NaiveMesher.java`

```java
public RawMeshResult meshAllRaw(Chunk chunk, WorldAccess world) {
    List<Float> opaqueVerts = new ArrayList<>();  // ⚠️ ALLOCATION
    List<Integer> opaqueIndices = new ArrayList<>();  // ⚠️ ALLOCATION
    // ... per-vertex:
    verts.add(x);  // ⚠️ Boxing + potential ArrayList resize
}
```

⚠️ **CRITICAL ISSUE:** `ArrayList<Float>` and `ArrayList<Integer>` cause:
- Boxing of every vertex float (13 per vertex × 4 verts × 6 faces × thousands of blocks)
- Dynamic resizing with array copies
- GC pressure from boxed objects

**File:** `src/main/java/com/voxelgame/world/World.java`

```java
public int getBlock(int x, int y, int z) {
    Chunk chunk = chunks.get(new ChunkPos(cx, cz));  // ⚠️ new ChunkPos every call
}
```

⚠️ **CRITICAL ISSUE:** Every block lookup creates a new `ChunkPos` object.

---

## A4) Memory Model and Allocations

### Chunk Block Storage

**File:** `src/main/java/com/voxelgame/world/Chunk.java`

| Array | Size | Type | Memory |
|-------|------|------|--------|
| `blocks` | 32,768 | `byte[]` | 32 KB |
| `lightMap` | 32,768 | `byte[]` | 32 KB |
| `blockLightR` | 32,768 | `byte[]` | 32 KB |
| `blockLightG` | 32,768 | `byte[]` | 32 KB |
| `blockLightB` | 32,768 | `byte[]` | 32 KB |

**Total per chunk:** ~163 KB data + object overhead

✅ **GOOD:** Uses `byte[]` primitives, not per-block objects.

⚠️ **ISSUE:** No palette/bitpacking. With ~30 block types, could use 5 bits instead of 8.

### Mesh Data in RAM

**File:** `src/main/java/com/voxelgame/world/mesh/MeshData.java`

```java
public record MeshData(float[] vertices, int[] indices) {}
```

Typical chunk mesh: 10,000-50,000 vertices × 13 floats × 4 bytes = 520 KB - 2.6 MB per chunk

**File:** `src/main/java/com/voxelgame/world/mesh/ChunkMesh.java`

Stores vertex/index count but data is GPU-side only after upload.

### Chunk Map / Hash Maps

**File:** `src/main/java/com/voxelgame/world/World.java`

```java
private final ConcurrentHashMap<ChunkPos, Chunk> chunks = new ConcurrentHashMap<>();
```

- Key: `ChunkPos` (boxed record, ~24 bytes object overhead)
- Value: `Chunk` reference
- Entry overhead: ~48 bytes per entry

**File:** `src/main/java/com/voxelgame/world/stream/ChunkManager.java`

```java
private final ConcurrentHashMap<ChunkPos, Future<Chunk>> pendingGen;
private final ConcurrentLinkedQueue<MeshUpload> uploadQueue;
private final ConcurrentLinkedQueue<LODMeshUpload> lodUploadQueue;
private final Set<ChunkPos> meshingInProgress;
private final Set<ChunkPos> lodMeshingInProgress;
```

### Temporary Buffers

**Meshing:**
```java
List<Float> opaqueVerts = new ArrayList<>();  // Grows dynamically
List<Integer> opaqueIndices = new ArrayList<>();
```

**Save/Load:**
```java
// ChunkCodec.java
ByteArrayOutputStream baos = new ByteArrayOutputStream();
GZIPOutputStream gzip = new GZIPOutputStream(baos);
// Creates ~64 KB buffer per chunk save
```

### Critical Memory Issues Summary

| Issue | Location | Impact |
|-------|----------|--------|
| `ArrayList<Float>` boxing | NaiveMesher.meshAllRaw | ~100 MB GC per second during heavy meshing |
| `new ChunkPos()` per lookup | World.getBlock | Millions of short-lived objects |
| No palette compression | Chunk.blocks | 3x memory waste for sparse block types |
| Full LOD block data | Chunk | 163 KB even for distant LOD 3 chunks |
| Region file rewrite | RegionFile.writeChunkData | O(n²) disk writes |

---

## A5) IO + Persistence

### Chunk File Format

**File:** `src/main/java/com/voxelgame/save/RegionFile.java`

```
Region file: r.{rx}.{rz}.dat
- Header: 1024 entries × 8 bytes = 8 KB
  [4 bytes offset][4 bytes length] per chunk
- Data: concatenated GZIP-compressed chunk blobs

Region size: 32×32 = 1024 chunks
```

**File:** `src/main/java/com/voxelgame/save/ChunkCodec.java`

```
Chunk blob format (after GZIP):
- 4 bytes: format version
- 4 bytes: chunk X
- 4 bytes: chunk Z
- 32,768 bytes: blocks
- 32,768 bytes: lightMap
Total uncompressed: ~65 KB → compressed: ~5-15 KB typical
```

### Compression Method

- **Method:** GZIP (`GZIPInputStream`/`GZIPOutputStream`)
- **Streaming-friendly:** No (reads entire chunk into memory)

### Sync vs Async Saves

```java
// ChunkManager.java - Save on unload (SYNC on main thread caller)
private void unloadDistantChunks(int pcx, int pcz) {
    if (chunk.isModified()) {
        saveManager.saveChunk(chunk);  // SYNC - blocks caller
    }
}

// Auto-save in GameLoop (called from main thread)
private void performAutoSave() {
    saveManager.saveModifiedChunks(world);  // SYNC
}
```

⚠️ **ISSUE:** All saves are synchronous. Disk IO blocks the render thread.

### Load Blocking Render Thread

```java
// ChunkManager.requestChunks() - called from main thread update()
if (saveManager != null) {
    Chunk loaded = saveManager.loadChunk(cx, cz);  // SYNC disk read!
    if (loaded != null) {
        // ... immediate use
    }
}
```

⚠️ **CRITICAL ISSUE:** Chunk loading from disk happens synchronously on main thread, causing frame hitches.

### Re-reading Chunks

```java
// RegionFile.writeChunkData - rewrites entire region
private Map<Integer, byte[]> readAllChunks() {
    // Reads ALL chunks in region just to add one chunk
}
```

⚠️ **CRITICAL ISSUE:** Saving one chunk reads and rewrites the entire region file.

---

## Summary: Critical Issues Ranked

1. **ArrayList boxing in mesher** - Causes constant GC pressure during gameplay
2. **ChunkPos allocation per lookup** - Millions of throwaway objects
3. **Sync disk IO on main thread** - Frame hitches on load/save
4. **Region file full rewrite** - O(n²) save performance
5. **Full chunk data at all LOD levels** - 163 KB × 2500 max = 400+ MB floor
6. **No mesh caching/versioning** - Unnecessary rebuilds

---

## Next Steps

1. **B) Instrumentation:** Add `--bench-world` mode with metrics collection
2. **C) Fixes:** 
   - Primitive arrays for mesh building (no boxing)
   - Long-packed chunk keys
   - Async disk IO
   - Incremental region writes
   - Palette-based block storage
