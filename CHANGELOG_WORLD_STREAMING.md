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

## [Planned] - Phase C: Fixes

### Priority 1: Async Chunk Loading
- Move `saveManager.loadChunk()` call to worker thread
- Add IO worker pool for disk operations
- Queue loaded chunks for main thread integration

### Priority 2: Budgeted Mesh Rebuilds
- Move `buildMesh()` from main thread to mesh pool
- Add per-frame mesh budget (e.g., 2ms max)
- Queue dirty chunks instead of immediate rebuild

### Priority 3: Primitive Key Maps
- Replace `ConcurrentHashMap<ChunkPos, Chunk>` with long-key primitive map
- Pack chunk coords: `(long)x << 32 | (z & 0xFFFFFFFFL)`
- Eliminate ChunkPos allocation on lookups

### Priority 4: Mesh Buffer Reuse
- Pre-allocate float[] and int[] buffers for meshing
- Pool and reuse instead of new ArrayList per mesh
- Estimate: ~80% reduction in mesh build allocations

### Expected Results
- **Before**: Heap growth, GC spikes, frame stutters on load
- **After**: Stable heap, smooth loading, no blocking IO
