package com.voxelgame.core;

import com.voxelgame.platform.Window;
import com.voxelgame.render.Renderer;
import com.voxelgame.sim.GameMode;
import com.voxelgame.sim.Player;
import com.voxelgame.world.WorldTime;
import com.voxelgame.world.stream.ChunkManager;

import java.io.*;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.LongBuffer;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

import static org.lwjgl.opengl.GL33.*;

/**
 * Performance truth capture mode.
 * 
 * Measures REAL frame time as experienced by the player:
 * - Wall-clock frame time (frame_begin to frame_end, INCLUDES swap)
 * - CPU update time
 * - CPU render submit time  
 * - Swap/present time (glfwSwapBuffers)
 * - GPU time via ARB_timer_query
 * - GC pauses
 * 
 * Outputs:
 *   perf_truth.json   - complete metrics with percentiles
 *   frame_times.csv   - per-frame breakdown
 *   SHA256SUMS.txt    - hashes of all outputs
 */
public class PerfSnapshot {

    public enum Scenario {
        GROUND,      // Ground-level walking
        HIGH_ALT     // High-altitude fast flight (Y=200, 50 blocks/sec)
    }

    private static final float HIGH_ALT_Y = 200.0f;
    private static final float FLIGHT_SPEED = 50.0f;
    
    private final int durationSeconds;
    private final Scenario scenario;
    private final String outputDir;
    
    private Player player;
    private WorldTime worldTime;
    private Renderer renderer;
    private ChunkManager chunkManager;
    private Window window;
    
    private boolean running = false;
    private boolean complete = false;
    private float timer = 0;
    private long captureStartMs;
    
    // Frame timing state
    private long frameBeginNs;
    private long cpuUpdateEndNs;
    private long cpuRenderEndNs;
    private long swapBeginNs;
    private long swapEndNs;
    
    // GPU timer query (double-buffered to avoid stalls)
    private int[] gpuQueryIds = new int[4]; // 2 pairs of start/end
    private int currentQueryPair = 0;
    private boolean gpuTimingAvailable = false;
    private long lastGpuTimeNs = 0;
    
    // Frame data
    private final List<FrameSample> samples = new ArrayList<>();
    
    // GC tracking
    private final List<GarbageCollectorMXBean> gcBeans;
    private long lastGcTimeMs = 0;
    
    // Flight state for HIGH_ALT scenario
    private float flightAngle = 0;
    private float flightDistance = 0;
    private float startX, startZ;
    
    // Metadata
    private String gitHash = "unknown";
    private long seed = 0;
    private boolean vsyncEnabled = true;
    private boolean shadowsEnabled = false;
    private boolean postfxEnabled = false;
    private String windowMode = "windowed";
    private int windowWidth, windowHeight;
    
    private static class FrameSample {
        long timestampMs;
        double frameWallMs;     // Total wall-clock time (includes swap)
        double cpuUpdateMs;     // CPU update phase
        double cpuRenderMs;     // CPU render submission
        double swapMs;          // glfwSwapBuffers time
        double gpuMs;           // GPU time from timer query
        double gcPauseMs;       // GC pause in this frame
        int chunksLoaded;
        int chunksVisible;
        int drawCalls;
        int triangles;
        float playerX, playerY, playerZ;
    }
    
    public PerfSnapshot(int durationSeconds, Scenario scenario, String outputDir) {
        this.durationSeconds = durationSeconds;
        this.scenario = scenario;
        this.outputDir = outputDir != null ? outputDir : "perf";
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        
        // Get git hash
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--short", "HEAD");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            if (line != null && line.length() >= 7) {
                gitHash = line.trim();
            }
            p.waitFor();
        } catch (Exception e) {
            // ignore
        }
    }
    
    public void setReferences(Player player, WorldTime worldTime, Renderer renderer, 
                              ChunkManager chunkManager, Window window) {
        this.player = player;
        this.worldTime = worldTime;
        this.renderer = renderer;
        this.chunkManager = chunkManager;
        this.window = window;
        
        // Capture metadata
        if (chunkManager != null) {
            this.seed = chunkManager.getSeed();
        }
        if (window != null) {
            this.windowWidth = window.getFramebufferWidth();
            this.windowHeight = window.getFramebufferHeight();
        }
        if (renderer != null) {
            this.shadowsEnabled = renderer.getShadowRenderer() != null && 
                                  renderer.getShadowRenderer().isShadowsEnabled();
        }
    }
    
    public boolean isComplete() {
        return complete;
    }
    
    public void start() {
        if (running) return;
        running = true;
        timer = 0;
        captureStartMs = System.currentTimeMillis();
        
        // Initialize GPU timer queries
        initGpuTimerQueries();
        
        // Setup scenario
        if (scenario == Scenario.HIGH_ALT) {
            player.setGameMode(GameMode.CREATIVE);
            if (!player.isFlyMode()) player.toggleFlyMode();
            player.getCamera().getPosition().y = HIGH_ALT_Y;
            player.getCamera().setPitch(-10);
            startX = player.getCamera().getPosition().x;
            startZ = player.getCamera().getPosition().z;
        }
        
        // Set time to noon
        if (worldTime != null) {
            worldTime.setWorldTick(6000);
        }
        
        // Record initial GC state
        updateGcBaseline();
        
        System.out.println("[PerfSnapshot] Starting " + scenario + " scenario for " + durationSeconds + "s");
        System.out.println("[PerfSnapshot] Output: " + outputDir);
        System.out.println("[PerfSnapshot] GPU timing: " + (gpuTimingAvailable ? "enabled" : "disabled"));
    }
    
    private void initGpuTimerQueries() {
        try {
            // Create timer query objects
            for (int i = 0; i < 4; i++) {
                gpuQueryIds[i] = glGenQueries();
            }
            gpuTimingAvailable = true;
            
            // Prime the queries with initial values
            glQueryCounter(gpuQueryIds[0], GL_TIMESTAMP);
            glQueryCounter(gpuQueryIds[1], GL_TIMESTAMP);
        } catch (Exception e) {
            System.out.println("[PerfSnapshot] GPU timer queries not available: " + e.getMessage());
            gpuTimingAvailable = false;
        }
    }
    
    private void updateGcBaseline() {
        long totalTime = 0;
        for (GarbageCollectorMXBean gc : gcBeans) {
            totalTime += gc.getCollectionTime();
        }
        lastGcTimeMs = totalTime;
    }
    
    private double getGcPauseSinceLastSample() {
        long totalTime = 0;
        for (GarbageCollectorMXBean gc : gcBeans) {
            totalTime += gc.getCollectionTime();
        }
        double pauseMs = totalTime - lastGcTimeMs;
        lastGcTimeMs = totalTime;
        return pauseMs;
    }
    
    /** Called at the very start of the frame, before any update logic. */
    public void beginFrame() {
        if (!running || complete) return;
        frameBeginNs = System.nanoTime();
        
        // Start GPU timestamp for this frame
        if (gpuTimingAvailable) {
            int startQuery = gpuQueryIds[currentQueryPair * 2];
            glQueryCounter(startQuery, GL_TIMESTAMP);
        }
    }
    
    /** Called after CPU update phase, before render. */
    public void endCpuUpdate() {
        if (!running || complete) return;
        cpuUpdateEndNs = System.nanoTime();
    }
    
    /** Called after render submission, before swap. */
    public void endCpuRender() {
        if (!running || complete) return;
        cpuRenderEndNs = System.nanoTime();
        
        // End GPU timestamp for this frame
        if (gpuTimingAvailable) {
            int endQuery = gpuQueryIds[currentQueryPair * 2 + 1];
            glQueryCounter(endQuery, GL_TIMESTAMP);
        }
    }
    
    /** Called just before swapBuffers. */
    public void beginSwap() {
        if (!running || complete) return;
        swapBeginNs = System.nanoTime();
    }
    
    /** Called just after swapBuffers. */
    public void endSwap() {
        if (!running || complete) return;
        swapEndNs = System.nanoTime();
    }
    
    /** Called at the very end of the frame. Records the sample. */
    public void endFrame(float dt) {
        if (!running || complete) return;
        
        timer += dt;
        
        // Update player for HIGH_ALT scenario
        if (scenario == Scenario.HIGH_ALT) {
            flightAngle += 0.3f * dt;
            flightDistance += FLIGHT_SPEED * dt;
            
            float targetX = startX + (float) Math.cos(flightAngle) * flightDistance * 0.3f + flightDistance * 0.7f;
            float targetZ = startZ + (float) Math.sin(flightAngle) * flightDistance * 0.3f;
            
            float dx = targetX - player.getCamera().getPosition().x;
            float dz = targetZ - player.getCamera().getPosition().z;
            float dist = (float) Math.sqrt(dx * dx + dz * dz);
            if (dist > 0.1f) {
                player.getCamera().getPosition().x += (dx / dist) * FLIGHT_SPEED * dt;
                player.getCamera().getPosition().z += (dz / dist) * FLIGHT_SPEED * dt;
            }
            player.getCamera().getPosition().y = HIGH_ALT_Y;
            
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            player.getCamera().setYaw(yaw);
        }
        
        // Read GPU time from previous frame's query (to avoid stall)
        long gpuTimeNs = 0;
        if (gpuTimingAvailable && samples.size() > 0) {
            int prevPair = 1 - currentQueryPair;
            int startQuery = gpuQueryIds[prevPair * 2];
            int endQuery = gpuQueryIds[prevPair * 2 + 1];
            
            // Check if results are available
            int[] available = new int[1];
            glGetQueryObjectiv(endQuery, GL_QUERY_RESULT_AVAILABLE, available);
            
            if (available[0] == GL_TRUE) {
                long[] startTime = new long[1];
                long[] endTime = new long[1];
                glGetQueryObjecti64v(startQuery, GL_QUERY_RESULT, startTime);
                glGetQueryObjecti64v(endQuery, GL_QUERY_RESULT, endTime);
                gpuTimeNs = endTime[0] - startTime[0];
                lastGpuTimeNs = gpuTimeNs;
            } else {
                gpuTimeNs = lastGpuTimeNs; // Use last known value
            }
        }
        
        // Swap query pair for next frame
        currentQueryPair = 1 - currentQueryPair;
        
        // Record sample
        takeSample(gpuTimeNs);
        
        // Check completion
        if (timer >= durationSeconds) {
            finish();
        }
    }
    
    private void takeSample(long gpuTimeNs) {
        FrameSample s = new FrameSample();
        s.timestampMs = System.currentTimeMillis() - captureStartMs;
        
        // Wall-clock frame time (the REAL frame time player experiences)
        s.frameWallMs = (swapEndNs - frameBeginNs) / 1_000_000.0;
        
        // CPU breakdown
        s.cpuUpdateMs = (cpuUpdateEndNs - frameBeginNs) / 1_000_000.0;
        s.cpuRenderMs = (cpuRenderEndNs - cpuUpdateEndNs) / 1_000_000.0;
        
        // Swap time (may be blocking due to vsync/GPU backlog)
        s.swapMs = (swapEndNs - swapBeginNs) / 1_000_000.0;
        
        // GPU time
        s.gpuMs = gpuTimeNs / 1_000_000.0;
        
        // GC pause
        s.gcPauseMs = getGcPauseSinceLastSample();
        
        // Chunk stats
        if (chunkManager != null) {
            s.chunksLoaded = chunkManager.getTotalChunks();
        }
        if (renderer != null) {
            s.chunksVisible = renderer.getRenderedChunks();
            s.drawCalls = renderer.getDrawCalls();
            s.triangles = renderer.getTriangleCount();
        }
        
        // Position
        s.playerX = player.getCamera().getPosition().x;
        s.playerY = player.getCamera().getPosition().y;
        s.playerZ = player.getCamera().getPosition().z;
        
        samples.add(s);
    }
    
    private void finish() {
        running = false;
        complete = true;
        
        System.out.println("[PerfSnapshot] Capture complete. " + samples.size() + " samples.");
        
        // Create output directory
        new File(outputDir).mkdirs();
        
        try {
            writeFrameTimes();
            writePerfTruth();
            writeSha256Sums();
            System.out.println("[PerfSnapshot] Results written to: " + outputDir);
        } catch (IOException e) {
            System.err.println("[PerfSnapshot] ERROR: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Cleanup GPU queries
        if (gpuTimingAvailable) {
            for (int id : gpuQueryIds) {
                glDeleteQueries(id);
            }
        }
    }
    
    private void writeFrameTimes() throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(new File(outputDir, "frame_times.csv")))) {
            pw.println("timestamp_ms,frame_wall_ms,cpu_update_ms,cpu_render_ms,swap_ms,gpu_ms,gc_pause_ms,chunks_loaded,chunks_visible,draw_calls,triangles,x,y,z");
            for (FrameSample s : samples) {
                pw.printf("%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%d,%d,%d,%d,%.1f,%.1f,%.1f%n",
                    s.timestampMs, s.frameWallMs, s.cpuUpdateMs, s.cpuRenderMs, 
                    s.swapMs, s.gpuMs, s.gcPauseMs,
                    s.chunksLoaded, s.chunksVisible, s.drawCalls, s.triangles,
                    s.playerX, s.playerY, s.playerZ);
            }
        }
    }
    
    private void writePerfTruth() throws IOException {
        if (samples.isEmpty()) return;
        
        // Collect all frame times for percentile calculation
        List<Double> frameWallTimes = new ArrayList<>();
        List<Double> swapTimes = new ArrayList<>();
        List<Double> gpuTimes = new ArrayList<>();
        List<Double> cpuUpdateTimes = new ArrayList<>();
        List<Double> cpuRenderTimes = new ArrayList<>();
        
        double totalWall = 0, totalSwap = 0, totalGpu = 0, totalGc = 0;
        double maxWall = 0, maxSwap = 0, maxGpu = 0;
        
        for (FrameSample s : samples) {
            frameWallTimes.add(s.frameWallMs);
            swapTimes.add(s.swapMs);
            gpuTimes.add(s.gpuMs);
            cpuUpdateTimes.add(s.cpuUpdateMs);
            cpuRenderTimes.add(s.cpuRenderMs);
            
            totalWall += s.frameWallMs;
            totalSwap += s.swapMs;
            totalGpu += s.gpuMs;
            totalGc += s.gcPauseMs;
            
            maxWall = Math.max(maxWall, s.frameWallMs);
            maxSwap = Math.max(maxSwap, s.swapMs);
            maxGpu = Math.max(maxGpu, s.gpuMs);
        }
        
        // Sort for percentiles
        Collections.sort(frameWallTimes);
        Collections.sort(swapTimes);
        Collections.sort(gpuTimes);
        Collections.sort(cpuUpdateTimes);
        Collections.sort(cpuRenderTimes);
        
        int n = samples.size();
        
        // Calculate percentiles
        double p50Wall = percentile(frameWallTimes, 0.50);
        double p95Wall = percentile(frameWallTimes, 0.95);
        double p99Wall = percentile(frameWallTimes, 0.99);
        double onePercentLowMs = percentile(frameWallTimes, 0.99); // worst 1%
        double onePercentLowFps = 1000.0 / onePercentLowMs;
        
        double p50Swap = percentile(swapTimes, 0.50);
        double p95Swap = percentile(swapTimes, 0.95);
        double p99Swap = percentile(swapTimes, 0.99);
        
        double p50Gpu = percentile(gpuTimes, 0.50);
        double p95Gpu = percentile(gpuTimes, 0.95);
        double p99Gpu = percentile(gpuTimes, 0.99);
        
        double avgWall = totalWall / n;
        double avgFps = 1000.0 / avgWall;
        
        // Determine bottleneck
        double avgSwap = totalSwap / n;
        double avgGpu = totalGpu / n;
        double avgCpuUpdate = cpuUpdateTimes.stream().mapToDouble(d -> d).average().orElse(0);
        double avgCpuRender = cpuRenderTimes.stream().mapToDouble(d -> d).average().orElse(0);
        
        String bottleneck;
        if (avgSwap > avgGpu && avgSwap > avgCpuUpdate + avgCpuRender) {
            bottleneck = "PRESENT/VSYNC";
        } else if (avgGpu > avgCpuUpdate + avgCpuRender) {
            bottleneck = "GPU";
        } else {
            bottleneck = "CPU";
        }
        
        try (PrintWriter pw = new PrintWriter(new FileWriter(new File(outputDir, "perf_truth.json")))) {
            pw.println("{");
            pw.println("  \"metadata\": {");
            pw.println("    \"git_hash\": \"" + gitHash + "\",");
            pw.println("    \"scenario\": \"" + scenario + "\",");
            pw.println("    \"duration_seconds\": " + durationSeconds + ",");
            pw.println("    \"sample_count\": " + n + ",");
            pw.println("    \"seed\": " + seed + ",");
            pw.println("    \"capture_timestamp\": \"" + Instant.now() + "\"");
            pw.println("  },");
            pw.println();
            pw.println("  \"environment\": {");
            pw.println("    \"vsync_enabled\": " + vsyncEnabled + ",");
            pw.println("    \"window_mode\": \"" + windowMode + "\",");
            pw.println("    \"resolution\": \"" + windowWidth + "x" + windowHeight + "\",");
            pw.println("    \"shadows_enabled\": " + shadowsEnabled + ",");
            pw.println("    \"postfx_enabled\": " + postfxEnabled + ",");
            pw.println("    \"gpu_timing_available\": " + gpuTimingAvailable);
            pw.println("  },");
            pw.println();
            pw.println("  \"frame_wall_ms\": {");
            pw.printf("    \"avg\": %.3f,%n", avgWall);
            pw.printf("    \"p50\": %.3f,%n", p50Wall);
            pw.printf("    \"p95\": %.3f,%n", p95Wall);
            pw.printf("    \"p99\": %.3f,%n", p99Wall);
            pw.printf("    \"max\": %.3f%n", maxWall);
            pw.println("  },");
            pw.println();
            pw.printf("  \"fps_avg\": %.2f,%n", avgFps);
            pw.printf("  \"fps_1pct_low\": %.2f,%n", onePercentLowFps);
            pw.println();
            pw.println("  \"swap_ms\": {");
            pw.printf("    \"avg\": %.3f,%n", avgSwap);
            pw.printf("    \"p50\": %.3f,%n", p50Swap);
            pw.printf("    \"p95\": %.3f,%n", p95Swap);
            pw.printf("    \"p99\": %.3f,%n", p99Swap);
            pw.printf("    \"max\": %.3f%n", maxSwap);
            pw.println("  },");
            pw.println();
            pw.println("  \"gpu_ms\": {");
            pw.printf("    \"avg\": %.3f,%n", avgGpu);
            pw.printf("    \"p50\": %.3f,%n", p50Gpu);
            pw.printf("    \"p95\": %.3f,%n", p95Gpu);
            pw.printf("    \"p99\": %.3f,%n", p99Gpu);
            pw.printf("    \"max\": %.3f%n", maxGpu);
            pw.println("  },");
            pw.println();
            pw.println("  \"cpu_ms\": {");
            pw.printf("    \"update_avg\": %.3f,%n", avgCpuUpdate);
            pw.printf("    \"render_avg\": %.3f%n", avgCpuRender);
            pw.println("  },");
            pw.println();
            pw.printf("  \"gc_total_ms\": %.3f,%n", totalGc);
            pw.println();
            pw.println("  \"verdict\": {");
            pw.println("    \"bottleneck\": \"" + bottleneck + "\",");
            pw.printf("    \"p99_dominated_by\": \"%s\",%n", 
                p99Swap > p99Gpu ? "swap" : (p99Gpu > p95Wall * 0.5 ? "gpu" : "cpu"));
            pw.printf("    \"recommendation\": \"%s\"%n", getRecommendation(bottleneck, p99Swap, p99Gpu));
            pw.println("  }");
            pw.println("}");
        }
    }
    
    private String getRecommendation(String bottleneck, double p99Swap, double p99Gpu) {
        if (bottleneck.equals("PRESENT/VSYNC")) {
            return "Frame rate capped by vsync or present blocking. Disable vsync to measure true performance.";
        } else if (bottleneck.equals("GPU")) {
            return "GPU bound. Reduce draw calls, triangle count, or shadow/postfx quality.";
        } else {
            return "CPU bound. Profile subsystems to find spike sources.";
        }
    }
    
    private void writeSha256Sums() throws IOException {
        File dir = new File(outputDir);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json") || name.endsWith(".csv"));
        if (files == null) return;
        
        try (PrintWriter pw = new PrintWriter(new FileWriter(new File(outputDir, "SHA256SUMS.txt")))) {
            for (File file : files) {
                String hash = sha256(file);
                pw.println(hash + "  " + file.getName());
            }
        }
    }
    
    private String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "error";
        }
    }
    
    private double percentile(List<Double> sorted, double p) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) (sorted.size() * p);
        return sorted.get(Math.min(idx, sorted.size() - 1));
    }
}
