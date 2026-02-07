# World Streaming Architecture Changelog

## [Audit Complete] - 2025-02-06

### Phase A: Architecture Audit

Created comprehensive WORLD_LOD_PIPELINE_REPORT.md documenting:

#### Chunk Lifecycle
- Block storage: 32KB per chunk (16×128×16 bytes)
- Light storage: 5 arrays totaling ~164KB per chunk
- Total RAM per chunk: ~160KB + mesh data
- ChunkPos record creates new object on every lookup (GC pressure)

#### LOD System (4 levels)
- LOD 0: Full detail with AO, RGB lighting (0-12 chunks)
- LOD 1: Simplified faces, no AO (12-14 chunks)
- LOD 2: Heightmap columns (14-17 chunks)
- LOD 3: Single flat quad (17-20 chunks)
- **Good**: No data duplication across LOD levels

#### Critical Issues Found
1. **Blocking disk I/O** in `ChunkManager.requestChunks()` - loads from disk on main thread
2. **Synchronous mesh rebuild** in `rebuildMeshAt()` - causes frame spikes on block changes
3. **ChunkPos object churn** - new ChunkPos on every `World.getChunk()` call
4. **ArrayList boxing** in `NaiveMesher.meshAllRaw()` - millions of Float/Integer allocations
5. **Region file full rewrite** on every chunk save

### Phase B: Instrumentation

Added `--bench-world [BEFORE|AFTER]` mode:

```bash
./gradlew run --args='--bench-world BEFORE'
```

Features:
- Fixed seed (42) for reproducibility
- Fixed camera path: spiral from origin to 400 blocks out
- 60-second duration
- 10 Hz sampling (600 data points)

Outputs to `artifacts/world_bench/BEFORE/` or `AFTER/`:
- `bench_config.json` - test parameters
- `perf_timeline.json` - time series metrics
- `summary.txt` - statistics summary
- `allocation_hotspots.txt` - known allocation points

Metrics collected:
- FPS and frame time (ms)
- Heap usage (MB)
- GC count and time
- Loaded chunks
- LOD distribution (LOD0/1/2/3)
- Pending mesh uploads
- Camera position

---

## [Implemented] - 2025-02-06 - Phase C: Async Fixes

### ✅ Implemented: Async Chunk Loading
**Location:** `ChunkManager.requestChunks()` lines 388-400

```java
// BEFORE: Blocking disk IO on main thread
if (saveManager != null) {
    Chunk loaded = saveManager.loadChunk(cx, cz);  // BLOCKS!
    ...
}

// AFTER: Async load in genPool worker
Future<Chunk> future = genPool.submit(() -> {
    if (sm != null) {
        Chunk loaded = sm.loadChunk(cx, cz);  // Non-blocking!
        if (loaded != null) return loaded;
    }
    // Generate if not on disk
    ...
});
```

### ✅ Implemented: Async Mesh Rebuilds  
**Location:** `ChunkManager.rebuildMeshAt()` lines 696-720

```java
// BEFORE: Sync buildMesh() call caused frame spikes
buildMesh(chunk);

// AFTER: Submit to mesh pool
submitMeshJob(chunk, pos, false);
```

Changes:
- `rebuildMeshAt()` now submits to meshPool instead of blocking
- `rebuildChunks()` uses async submit
- New `rebuildChunkAsync()` helper method
- Dirty chunk processing limited to 4/frame to avoid queue flood

### Deferred: Primitive Key Maps
Will require more invasive changes to World.java and callers.

### Deferred: Mesh Buffer Reuse
Requires refactoring NaiveMesher to accept pooled buffers.

### Expected Improvements
- No frame stutters when loading saved chunks from disk
- Smooth block place/break without mesh rebuild spikes
- Reduced main thread blocking during chunk streaming
