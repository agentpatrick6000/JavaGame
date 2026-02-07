package com.voxelgame.bench;

import com.voxelgame.world.World;
import com.voxelgame.world.stream.ChunkManager;
import com.voxelgame.sim.Player;

import java.io.*;
import java.lang.management.*;
import java.time.Instant;
import java.util.*;

/**
 * World streaming benchmark for measuring chunk loading, meshing, and memory performance.
 * 
 * Usage: --bench-world [BEFORE|AFTER]
 * 
 * Runs a 60-second automated flight path over terrain and logs:
 * - FPS and frame time (avg, p95, p99)
 * - GC events and pause times
 * - Heap usage
 * - Chunk counts (by state and LOD level)
 * - Queue depths
 */
public class WorldBenchmark {
    
    private static final int DURATION_SECONDS = 60;
    private static final int SAMPLE_RATE_HZ = 10;
    private static final int SAMPLE_INTERVAL_MS = 1000 / SAMPLE_RATE_HZ;
    
    // Flight path: spiral outward from spawn
    private static final float FLIGHT_SPEED = 20.0f;  // blocks per second
    private static final float TURN_RATE = 0.5f;      // radians per second
    
    private final ChunkManager chunkManager;
    private final World world;
    private final Player player;
    private final String outputDir;
    
    // Timing
    private long startTimeMs;
    private long lastSampleMs;
    private int frameCount;
    private List<Float> frameTimes = new ArrayList<>();
    
    // Samples
    private List<Map<String, Object>> samples = new ArrayList<>();
    
    // GC tracking
    private List<GarbageCollectorMXBean> gcBeans;
    private long lastGcCount;
    private long lastGcTimeMs;
    
    // Memory
    private MemoryMXBean memoryBean;
    
    // Flight state
    private float flightAngle = 0;
    private float flightRadius = 0;
    
    public WorldBenchmark(ChunkManager chunkManager, World world, Player player, String outputDir) {
        this.chunkManager = chunkManager;
        this.world = world;
        this.player = player;
        this.outputDir = outputDir;
        
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        this.memoryBean = ManagementFactory.getMemoryMXBean();
    }
    
    public void start() {
        startTimeMs = System.currentTimeMillis();
        lastSampleMs = startTimeMs;
        frameCount = 0;
        
        // Initial GC state
        lastGcCount = getTotalGcCount();
        lastGcTimeMs = getTotalGcTimeMs();
        
        // Create output directory
        new File(outputDir).mkdirs();
        
        System.out.println("[Benchmark] Started. Duration: " + DURATION_SECONDS + "s");
        System.out.println("[Benchmark] Output: " + outputDir);
    }
    
    public void update(float dt, float fps) {
        long now = System.currentTimeMillis();
        float elapsedSec = (now - startTimeMs) / 1000.0f;
        
        frameCount++;
        frameTimes.add(dt * 1000);  // Convert to ms
        
        // Update flight path (spiral outward)
        flightAngle += TURN_RATE * dt;
        flightRadius += FLIGHT_SPEED * dt * 0.1f;  // Slowly expand radius
        
        float targetX = (float) Math.cos(flightAngle) * flightRadius;
        float targetZ = (float) Math.sin(flightAngle) * flightRadius;
        
        // Move player toward target
        float dx = targetX - player.getPosition().x;
        float dz = targetZ - player.getPosition().z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        if (dist > 0.1f) {
            player.getCamera().getPosition().x += (dx / dist) * FLIGHT_SPEED * dt;
            player.getCamera().getPosition().z += (dz / dist) * FLIGHT_SPEED * dt;
        }
        
        // Maintain flight height
        player.getCamera().getPosition().y = 100.0f;
        
        // Sample at fixed rate
        if (now - lastSampleMs >= SAMPLE_INTERVAL_MS) {
            takeSample(elapsedSec, fps);
            lastSampleMs = now;
        }
    }
    
    public boolean isComplete() {
        return (System.currentTimeMillis() - startTimeMs) >= DURATION_SECONDS * 1000;
    }
    
    public void finish() {
        System.out.println("[Benchmark] Complete. Writing results...");
        
        try {
            writeConfig();
            writeTimeline();
            writeSummary();
        } catch (IOException e) {
            System.err.println("[Benchmark] Failed to write results: " + e.getMessage());
        }
        
        System.out.println("[Benchmark] Results written to: " + outputDir);
    }
    
    private void takeSample(float elapsedSec, float fps) {
        Map<String, Object> sample = new LinkedHashMap<>();
        
        sample.put("time_sec", elapsedSec);
        sample.put("fps", fps);
        
        // Frame time stats (last N frames)
        int windowSize = Math.min(frameTimes.size(), SAMPLE_RATE_HZ * 2);
        if (windowSize > 0) {
            List<Float> window = new ArrayList<>(frameTimes.subList(frameTimes.size() - windowSize, frameTimes.size()));
            Collections.sort(window);
            sample.put("frame_time_avg_ms", average(window));
            sample.put("frame_time_p95_ms", percentile(window, 0.95f));
            sample.put("frame_time_p99_ms", percentile(window, 0.99f));
        }
        
        // GC
        long gcCount = getTotalGcCount();
        long gcTimeMs = getTotalGcTimeMs();
        sample.put("gc_count_delta", gcCount - lastGcCount);
        sample.put("gc_time_ms_delta", gcTimeMs - lastGcTimeMs);
        lastGcCount = gcCount;
        lastGcTimeMs = gcTimeMs;
        
        // Heap
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        sample.put("heap_used_mb", heap.getUsed() / (1024 * 1024));
        sample.put("heap_committed_mb", heap.getCommitted() / (1024 * 1024));
        sample.put("heap_max_mb", heap.getMax() / (1024 * 1024));
        
        // Chunk counts
        sample.put("chunks_loaded", chunkManager.getTotalChunks());
        sample.put("chunks_lod0", chunkManager.getLod0Count());
        sample.put("chunks_lod1", chunkManager.getLod1Count());
        sample.put("chunks_lod2", chunkManager.getLod2Count());
        sample.put("chunks_lod3", chunkManager.getLod3Count());
        sample.put("pending_uploads", chunkManager.getPendingUploads());
        
        // Player position
        sample.put("player_x", player.getPosition().x);
        sample.put("player_z", player.getPosition().z);
        
        samples.add(sample);
    }
    
    private void writeConfig() throws IOException {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("timestamp", Instant.now().toString());
        config.put("duration_seconds", DURATION_SECONDS);
        config.put("sample_rate_hz", SAMPLE_RATE_HZ);
        config.put("seed", chunkManager.getSeed());
        config.put("render_distance", chunkManager.getLodConfig().getMaxRenderDistance());
        config.put("lod_threshold", chunkManager.getLodConfig().getLodThreshold());
        config.put("max_chunks", chunkManager.getLodConfig().getMaxLoadedChunks());
        config.put("gen_threads", 4);
        config.put("mesh_threads", 3);
        config.put("flight_speed", FLIGHT_SPEED);
        
        writeJson(new File(outputDir, "bench_config.json"), config);
    }
    
    private void writeTimeline() throws IOException {
        writeJsonArray(new File(outputDir, "perf_timeline.json"), samples);
    }
    
    private void writeSummary() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# World Benchmark Summary\n\n");
        sb.append("**Duration:** ").append(DURATION_SECONDS).append(" seconds\n");
        sb.append("**Total frames:** ").append(frameCount).append("\n\n");
        
        // Overall frame time stats
        List<Float> sortedTimes = new ArrayList<>(frameTimes);
        Collections.sort(sortedTimes);
        sb.append("## Frame Time\n\n");
        sb.append("- Average: ").append(String.format("%.2f", average(sortedTimes))).append(" ms\n");
        sb.append("- P95: ").append(String.format("%.2f", percentile(sortedTimes, 0.95f))).append(" ms\n");
        sb.append("- P99: ").append(String.format("%.2f", percentile(sortedTimes, 0.99f))).append(" ms\n");
        if (!sortedTimes.isEmpty()) {
            sb.append("- Max: ").append(String.format("%.2f", sortedTimes.get(sortedTimes.size() - 1))).append(" ms\n\n");
        }
        
        // Chunk stats
        sb.append("## Chunks\n\n");
        if (!samples.isEmpty()) {
            Map<String, Object> last = samples.get(samples.size() - 1);
            sb.append("- Final loaded: ").append(last.get("chunks_loaded")).append("\n");
            sb.append("- LOD distribution: ");
            sb.append("LOD0=").append(last.get("chunks_lod0")).append(", ");
            sb.append("LOD1=").append(last.get("chunks_lod1")).append(", ");
            sb.append("LOD2=").append(last.get("chunks_lod2")).append(", ");
            sb.append("LOD3=").append(last.get("chunks_lod3")).append("\n\n");
        }
        
        // GC stats
        sb.append("## GC\n\n");
        long totalGcEvents = 0;
        long totalGcTime = 0;
        for (Map<String, Object> s : samples) {
            totalGcEvents += ((Number) s.get("gc_count_delta")).longValue();
            totalGcTime += ((Number) s.get("gc_time_ms_delta")).longValue();
        }
        sb.append("- Total GC events: ").append(totalGcEvents).append("\n");
        sb.append("- Total GC time: ").append(totalGcTime).append(" ms\n");
        sb.append("- GC per second: ").append(String.format("%.1f", totalGcEvents / (float) DURATION_SECONDS)).append("\n\n");
        
        // Heap stats
        sb.append("## Heap\n\n");
        if (!samples.isEmpty()) {
            Map<String, Object> last = samples.get(samples.size() - 1);
            sb.append("- Final used: ").append(last.get("heap_used_mb")).append(" MB\n");
            sb.append("- Committed: ").append(last.get("heap_committed_mb")).append(" MB\n");
            sb.append("- Max: ").append(last.get("heap_max_mb")).append(" MB\n");
        }
        
        try (FileWriter fw = new FileWriter(new File(outputDir, "summary.txt"))) {
            fw.write(sb.toString());
        }
    }
    
    private long getTotalGcCount() {
        long total = 0;
        for (GarbageCollectorMXBean gc : gcBeans) {
            if (gc.getCollectionCount() >= 0) {
                total += gc.getCollectionCount();
            }
        }
        return total;
    }
    
    private long getTotalGcTimeMs() {
        long total = 0;
        for (GarbageCollectorMXBean gc : gcBeans) {
            if (gc.getCollectionTime() >= 0) {
                total += gc.getCollectionTime();
            }
        }
        return total;
    }
    
    private static float average(List<Float> values) {
        if (values.isEmpty()) return 0;
        float sum = 0;
        for (float v : values) sum += v;
        return sum / values.size();
    }
    
    private static float percentile(List<Float> sortedValues, float p) {
        if (sortedValues.isEmpty()) return 0;
        int idx = (int) (sortedValues.size() * p);
        return sortedValues.get(Math.min(idx, sortedValues.size() - 1));
    }
    
    private static void writeJson(File file, Map<String, Object> data) throws IOException {
        try (FileWriter fw = new FileWriter(file)) {
            fw.write("{\n");
            int i = 0;
            for (var entry : data.entrySet()) {
                fw.write("  \"" + entry.getKey() + "\": ");
                writeValue(fw, entry.getValue());
                if (++i < data.size()) fw.write(",");
                fw.write("\n");
            }
            fw.write("}\n");
        }
    }
    
    private static void writeJsonArray(File file, List<Map<String, Object>> data) throws IOException {
        try (FileWriter fw = new FileWriter(file)) {
            fw.write("[\n");
            for (int i = 0; i < data.size(); i++) {
                fw.write("  {");
                Map<String, Object> entry = data.get(i);
                int j = 0;
                for (var kv : entry.entrySet()) {
                    fw.write("\"" + kv.getKey() + "\":");
                    writeValue(fw, kv.getValue());
                    if (++j < entry.size()) fw.write(",");
                }
                fw.write("}");
                if (i < data.size() - 1) fw.write(",");
                fw.write("\n");
            }
            fw.write("]\n");
        }
    }
    
    private static void writeValue(FileWriter fw, Object value) throws IOException {
        if (value instanceof String) {
            fw.write("\"" + value + "\"");
        } else if (value instanceof Float) {
            fw.write(String.format("%.3f", value));
        } else if (value instanceof Double) {
            fw.write(String.format("%.3f", value));
        } else {
            fw.write(String.valueOf(value));
        }
    }
}
