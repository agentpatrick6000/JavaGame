package com.voxelgame.bench;

import com.voxelgame.world.World;
import com.voxelgame.world.stream.ChunkManager;
import com.voxelgame.world.lod.LODConfig;
import com.voxelgame.sim.Player;

import java.io.*;
import java.lang.management.*;
import java.time.Instant;
import java.util.*;

/**
 * World streaming benchmark for measuring chunk loading, meshing, and memory performance.
 * 
 * Usage: --bench-world BEFORE --seed 42 --bench-out artifacts/world_bench/PROFILE_BEFORE
 * 
 * Outputs:
 * - bench_config.json: run configuration
 * - bench_samples.csv: per-sample metrics at 10Hz
 * - bench_summary.json: aggregated statistics
 * - run.log: stdout/stderr capture
 */
public class WorldBenchmark {
    
    private static final int DURATION_SECONDS = 60;
    private static final int SAMPLE_RATE_HZ = 10;
    private static final int SAMPLE_INTERVAL_MS = 1000 / SAMPLE_RATE_HZ;
    
    // Flight path: spiral outward from spawn
    private static final float FLIGHT_SPEED = 20.0f;
    private static final float TURN_RATE = 0.5f;
    private static final float START_X = 0.0f;
    private static final float START_Z = 0.0f;
    private static final float FLIGHT_Y = 100.0f;
    
    private final ChunkManager chunkManager;
    private final World world;
    private final Player player;
    private final String outputDir;
    private final String profileName;
    private final long seed;
    private final String gitHash;
    
    // Timing
    private long startTimeMs;
    private long lastSampleMs;
    private int frameCount;
    
    // Samples storage
    private List<Sample> samples = new ArrayList<>();
    
    // GC tracking
    private List<GarbageCollectorMXBean> gcBeans;
    private long lastGcCount;
    private long lastGcTimeMs;
    
    // Memory
    private MemoryMXBean memoryBean;
    
    // Flight state
    private float flightAngle = 0;
    private float flightRadius = 0;
    
    // Log capture
    private StringBuilder runLog = new StringBuilder();
    
    private static class Sample {
        long timestampMs;
        float fps;
        float frameMs;
        long gcPauseMs;
        long heapUsedMb;
        int loadedChunks;
        int meshedChunks;
        int pendingMeshJobs;
        int pendingIoJobs;
        int meshQuads;
        long mainThreadBlockedMs;
    }
    
    public WorldBenchmark(ChunkManager chunkManager, World world, Player player, 
                          String outputDir, String profileName, long seed, String gitHash) {
        this.chunkManager = chunkManager;
        this.world = world;
        this.player = player;
        this.outputDir = outputDir;
        this.profileName = profileName;
        this.seed = seed;
        this.gitHash = gitHash;
        
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        this.memoryBean = ManagementFactory.getMemoryMXBean();
    }
    
    public void start() {
        startTimeMs = System.currentTimeMillis();
        lastSampleMs = startTimeMs;
        frameCount = 0;
        
        lastGcCount = getTotalGcCount();
        lastGcTimeMs = getTotalGcTimeMs();
        
        new File(outputDir).mkdirs();
        
        log("[Benchmark] Started");
        log("[Benchmark] Profile: " + profileName);
        log("[Benchmark] Seed: " + seed);
        log("[Benchmark] Duration: " + DURATION_SECONDS + "s");
        log("[Benchmark] Output: " + outputDir);
    }
    
    public void update(float dt, float fps) {
        long now = System.currentTimeMillis();
        
        frameCount++;
        
        // Update flight path (spiral outward)
        flightAngle += TURN_RATE * dt;
        flightRadius += FLIGHT_SPEED * dt * 0.1f;
        
        float targetX = START_X + (float) Math.cos(flightAngle) * flightRadius;
        float targetZ = START_Z + (float) Math.sin(flightAngle) * flightRadius;
        
        float dx = targetX - player.getPosition().x;
        float dz = targetZ - player.getPosition().z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        if (dist > 0.1f) {
            player.getCamera().getPosition().x += (dx / dist) * FLIGHT_SPEED * dt;
            player.getCamera().getPosition().z += (dz / dist) * FLIGHT_SPEED * dt;
        }
        player.getCamera().getPosition().y = FLIGHT_Y;
        
        // Sample at fixed rate
        if (now - lastSampleMs >= SAMPLE_INTERVAL_MS) {
            takeSample(now, fps, dt);
            lastSampleMs = now;
        }
    }
    
    public boolean isComplete() {
        return (System.currentTimeMillis() - startTimeMs) >= DURATION_SECONDS * 1000;
    }
    
    public void finish() {
        log("[Benchmark] Complete. Writing results...");
        
        try {
            writeConfig();
            writeSamples();
            writeSummary();
            writeRunLog();
        } catch (IOException e) {
            log("[Benchmark] ERROR: Failed to write results: " + e.getMessage());
        }
        
        log("[Benchmark] Results written to: " + outputDir);
    }
    
    private void takeSample(long now, float fps, float dt) {
        Sample s = new Sample();
        s.timestampMs = now - startTimeMs;
        s.fps = fps;
        s.frameMs = dt * 1000.0f;
        
        // GC delta
        long gcCount = getTotalGcCount();
        long gcTimeMs = getTotalGcTimeMs();
        s.gcPauseMs = gcTimeMs - lastGcTimeMs;
        lastGcCount = gcCount;
        lastGcTimeMs = gcTimeMs;
        
        // Heap
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        s.heapUsedMb = heap.getUsed() / (1024 * 1024);
        
        // Chunk counts
        s.loadedChunks = chunkManager.getTotalChunks();
        s.meshedChunks = chunkManager.getMeshedChunks();
        s.pendingMeshJobs = chunkManager.getPendingMeshJobs();
        s.pendingIoJobs = chunkManager.getPendingIoJobs();
        s.meshQuads = chunkManager.getTotalMeshQuads();
        s.mainThreadBlockedMs = 0; // Not easily measurable without instrumentation
        
        samples.add(s);
    }
    
    private void writeConfig() throws IOException {
        LODConfig lod = chunkManager.getLodConfig();
        
        try (PrintWriter pw = new PrintWriter(new FileWriter(new File(outputDir, "bench_config.json")))) {
            pw.println("{");
            pw.println("  \"git_head_hash\": \"" + gitHash + "\",");
            pw.println("  \"profile_name\": \"" + profileName + "\",");
            pw.println("  \"seed\": " + seed + ",");
            pw.println("  \"bench_fixes\": {");
            pw.println("    \"FIX_MESH_PRIMITIVE_BUFFERS\": " + BenchFixes.FIX_MESH_PRIMITIVE_BUFFERS + ",");
            pw.println("    \"FIX_CHUNKPOS_PACKED\": " + BenchFixes.FIX_CHUNKPOS_PACKED + ",");
            pw.println("    \"FIX_ASYNC_REGION_IO\": " + BenchFixes.FIX_ASYNC_REGION_IO);
            pw.println("  },");
            pw.println("  \"camera_path\": {");
            pw.println("    \"type\": \"spiral\",");
            pw.println("    \"start_pos\": [" + START_X + ", " + FLIGHT_Y + ", " + START_Z + "],");
            pw.println("    \"flight_speed\": " + FLIGHT_SPEED + ",");
            pw.println("    \"turn_rate\": " + TURN_RATE);
            pw.println("  },");
            pw.println("  \"duration_s\": " + DURATION_SECONDS + ",");
            pw.println("  \"sample_hz\": " + SAMPLE_RATE_HZ + ",");
            pw.println("  \"render_distance_chunks\": " + lod.getMaxRenderDistance() + ",");
            pw.println("  \"lod_settings\": {");
            pw.println("    \"lod_threshold\": " + lod.getLodThreshold() + ",");
            pw.println("    \"lod2_start\": " + lod.getLod2Start() + ",");
            pw.println("    \"lod3_start\": " + lod.getLod3Start() + ",");
            pw.println("    \"max_loaded_chunks\": " + lod.getMaxLoadedChunks());
            pw.println("  },");
            pw.println("  \"io_settings\": {");
            pw.println("    \"sync_mode\": \"" + (chunkManager.isAsyncIoEnabled() ? "async" : "sync") + "\",");
            pw.println("    \"region_format\": \"gzip\",");
            pw.println("    \"cache_size\": 0");
            pw.println("  }");
            pw.println("}");
        }
    }
    
    private void writeSamples() throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(new File(outputDir, "bench_samples.csv")))) {
            pw.println("timestamp_ms,fps,frame_ms,gc_pause_ms,heap_used_mb,loaded_chunks,meshed_chunks,pending_mesh_jobs,pending_io_jobs,mesh_quads,main_thread_blocked_ms");
            for (Sample s : samples) {
                pw.printf("%d,%.2f,%.3f,%d,%d,%d,%d,%d,%d,%d,%d%n",
                    s.timestampMs, s.fps, s.frameMs, s.gcPauseMs, s.heapUsedMb,
                    s.loadedChunks, s.meshedChunks, s.pendingMeshJobs, s.pendingIoJobs,
                    s.meshQuads, s.mainThreadBlockedMs);
            }
        }
    }
    
    private void writeSummary() throws IOException {
        if (samples.isEmpty()) return;
        
        // Calculate frame time percentiles
        List<Float> frameTimes = new ArrayList<>();
        for (Sample s : samples) frameTimes.add(s.frameMs);
        Collections.sort(frameTimes);
        
        float p50 = percentile(frameTimes, 0.50f);
        float p95 = percentile(frameTimes, 0.95f);
        float p99 = percentile(frameTimes, 0.99f);
        
        // FPS stats
        float fpsSum = 0, fpsMin = Float.MAX_VALUE, fpsMax = 0;
        for (Sample s : samples) {
            fpsSum += s.fps;
            if (s.fps < fpsMin) fpsMin = s.fps;
            if (s.fps > fpsMax) fpsMax = s.fps;
        }
        float fpsAvg = fpsSum / samples.size();
        
        // GC total
        long totalGcPause = 0;
        for (Sample s : samples) totalGcPause += s.gcPauseMs;
        
        // Max chunks and queues
        int maxLoadedChunks = 0, maxPendingMesh = 0, maxPendingIo = 0;
        for (Sample s : samples) {
            if (s.loadedChunks > maxLoadedChunks) maxLoadedChunks = s.loadedChunks;
            if (s.pendingMeshJobs > maxPendingMesh) maxPendingMesh = s.pendingMeshJobs;
            if (s.pendingIoJobs > maxPendingIo) maxPendingIo = s.pendingIoJobs;
        }
        
        try (PrintWriter pw = new PrintWriter(new FileWriter(new File(outputDir, "bench_summary.json")))) {
            pw.println("{");
            pw.printf("  \"frame_ms_p50\": %.3f,%n", p50);
            pw.printf("  \"frame_ms_p95\": %.3f,%n", p95);
            pw.printf("  \"frame_ms_p99\": %.3f,%n", p99);
            pw.printf("  \"fps_avg\": %.2f,%n", fpsAvg);
            pw.printf("  \"fps_min\": %.2f,%n", fpsMin);
            pw.printf("  \"fps_max\": %.2f,%n", fpsMax);
            pw.printf("  \"total_gc_pause_ms\": %d,%n", totalGcPause);
            pw.printf("  \"max_loaded_chunks\": %d,%n", maxLoadedChunks);
            pw.printf("  \"max_pending_mesh_jobs\": %d,%n", maxPendingMesh);
            pw.printf("  \"max_pending_io_jobs\": %d,%n", maxPendingIo);
            pw.println("  \"total_bytes_written\": 0,");
            pw.println("  \"total_chunks_saved\": 0");
            pw.println("}");
        }
    }
    
    private void writeRunLog() throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(new File(outputDir, "run.log")))) {
            pw.print(runLog.toString());
        }
    }
    
    private void log(String msg) {
        String line = "[" + Instant.now() + "] " + msg;
        System.out.println(line);
        runLog.append(line).append("\n");
    }
    
    private long getTotalGcCount() {
        long total = 0;
        for (GarbageCollectorMXBean gc : gcBeans) {
            if (gc.getCollectionCount() >= 0) total += gc.getCollectionCount();
        }
        return total;
    }
    
    private long getTotalGcTimeMs() {
        long total = 0;
        for (GarbageCollectorMXBean gc : gcBeans) {
            if (gc.getCollectionTime() >= 0) total += gc.getCollectionTime();
        }
        return total;
    }
    
    private static float percentile(List<Float> sorted, float p) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) (sorted.size() * p);
        return sorted.get(Math.min(idx, sorted.size() - 1));
    }
}
