package com.voxelgame.core;

import com.voxelgame.render.Renderer;
import com.voxelgame.sim.GameMode;
import com.voxelgame.sim.Player;
import com.voxelgame.world.WorldTime;
import com.voxelgame.world.stream.ChunkManager;

import java.io.*;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.*;

/**
 * Minimal performance snapshot mode.
 * 
 * Usage: --perf-snapshot <seconds>
 * 
 * Captures per-frame timing data for the specified duration and writes:
 *   perf/frame_times.csv  - per-frame ms
 *   perf/timers.json      - aggregated CPU time per subsystem
 * 
 * Uses the existing Profiler instrumentation plus GC tracking.
 */
public class PerfSnapshot {

    public enum Scenario {
        GROUND,      // Ground-level walking
        HIGH_ALT     // High-altitude fast flight
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
    
    private boolean running = false;
    private boolean complete = false;
    private float timer = 0;
    private long captureStartMs;
    
    // Frame data
    private final List<FrameSample> samples = new ArrayList<>();
    
    // GC tracking
    private final List<GarbageCollectorMXBean> gcBeans;
    private long lastGcTimeMs = 0;
    private int lastGcCount = 0;
    
    // Flight state for HIGH_ALT scenario
    private float flightAngle = 0;
    private float flightDistance = 0;
    private float startX, startZ;
    
    private String gitHash = "unknown";
    
    private static class FrameSample {
        long timestampMs;
        double frameMs;
        double gcPauseMs;
        Map<String, Double> subsystemMs = new LinkedHashMap<>();
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
    
    public void setReferences(Player player, WorldTime worldTime, Renderer renderer, ChunkManager chunkManager) {
        this.player = player;
        this.worldTime = worldTime;
        this.renderer = renderer;
        this.chunkManager = chunkManager;
    }
    
    public boolean isComplete() {
        return complete;
    }
    
    public void start() {
        if (running) return;
        running = true;
        timer = 0;
        captureStartMs = System.currentTimeMillis();
        
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
    }
    
    private void updateGcBaseline() {
        long totalTime = 0;
        int totalCount = 0;
        for (GarbageCollectorMXBean gc : gcBeans) {
            totalTime += gc.getCollectionTime();
            totalCount += gc.getCollectionCount();
        }
        lastGcTimeMs = totalTime;
        lastGcCount = totalCount;
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
    
    public void update(float dt) {
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
        
        // Sample this frame
        takeSample();
        
        // Check completion
        if (timer >= durationSeconds) {
            finish();
        }
    }
    
    private void takeSample() {
        Profiler profiler = Profiler.getInstance();
        
        FrameSample s = new FrameSample();
        s.timestampMs = System.currentTimeMillis() - captureStartMs;
        
        // Get frame time from profiler
        s.frameMs = profiler.getLastFrameMs("Frame");
        
        // Get GC pause since last sample
        s.gcPauseMs = getGcPauseSinceLastSample();
        
        // Get all subsystem timings
        for (String section : profiler.getSections()) {
            if (!section.equals("Frame")) {
                s.subsystemMs.put(section, profiler.getLastFrameMs(section));
            }
        }
        
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
            writeTimersJson();
            System.out.println("[PerfSnapshot] Results written to: " + outputDir);
        } catch (IOException e) {
            System.err.println("[PerfSnapshot] ERROR: " + e.getMessage());
        }
    }
    
    private void writeFrameTimes() throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(new File(outputDir, "frame_times.csv")))) {
            pw.println("timestamp_ms,frame_ms,gc_pause_ms,chunks_loaded,chunks_visible,draw_calls,triangles,x,y,z");
            for (FrameSample s : samples) {
                pw.printf("%d,%.3f,%.3f,%d,%d,%d,%d,%.1f,%.1f,%.1f%n",
                    s.timestampMs, s.frameMs, s.gcPauseMs,
                    s.chunksLoaded, s.chunksVisible, s.drawCalls, s.triangles,
                    s.playerX, s.playerY, s.playerZ);
            }
        }
        System.out.println("[PerfSnapshot] Wrote: " + outputDir + "/frame_times.csv");
    }
    
    private void writeTimersJson() throws IOException {
        // Aggregate subsystem timings
        Map<String, double[]> subsystemStats = new LinkedHashMap<>();
        double totalFrameMs = 0;
        double totalGcMs = 0;
        double maxFrameMs = 0;
        
        for (FrameSample s : samples) {
            totalFrameMs += s.frameMs;
            totalGcMs += s.gcPauseMs;
            maxFrameMs = Math.max(maxFrameMs, s.frameMs);
            
            for (Map.Entry<String, Double> e : s.subsystemMs.entrySet()) {
                subsystemStats.computeIfAbsent(e.getKey(), k -> new double[3]); // [sum, max, count]
                double[] stats = subsystemStats.get(e.getKey());
                stats[0] += e.getValue();
                stats[1] = Math.max(stats[1], e.getValue());
                stats[2]++;
            }
        }
        
        int n = samples.size();
        double avgFrameMs = n > 0 ? totalFrameMs / n : 0;
        double avgFps = avgFrameMs > 0 ? 1000.0 / avgFrameMs : 0;
        
        // Sort subsystems by total time
        List<Map.Entry<String, double[]>> sorted = new ArrayList<>(subsystemStats.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]));
        
        // Calculate percentages
        double accountedMs = 0;
        for (double[] stats : subsystemStats.values()) {
            accountedMs += stats[0];
        }
        
        try (PrintWriter pw = new PrintWriter(new FileWriter(new File(outputDir, "timers.json")))) {
            pw.println("{");
            pw.println("  \"git_hash\": \"" + gitHash + "\",");
            pw.println("  \"scenario\": \"" + scenario + "\",");
            pw.println("  \"duration_seconds\": " + durationSeconds + ",");
            pw.println("  \"sample_count\": " + n + ",");
            pw.println("  \"capture_timestamp\": \"" + Instant.now() + "\",");
            pw.println();
            pw.println("  \"frame_time_ms\": {");
            pw.printf("    \"avg\": %.3f,%n", avgFrameMs);
            pw.printf("    \"max\": %.3f%n", maxFrameMs);
            pw.println("  },");
            pw.printf("  \"fps_avg\": %.2f,%n", avgFps);
            pw.printf("  \"gc_total_ms\": %.3f,%n", totalGcMs);
            pw.println();
            pw.println("  \"subsystems\": {");
            
            int i = 0;
            for (Map.Entry<String, double[]> e : sorted) {
                String name = e.getKey();
                double[] stats = e.getValue();
                double avgMs = stats[2] > 0 ? stats[0] / stats[2] : 0;
                double pct = totalFrameMs > 0 ? (stats[0] / totalFrameMs) * 100 : 0;
                
                pw.printf("    \"%s\": { \"total_ms\": %.3f, \"avg_ms\": %.3f, \"max_ms\": %.3f, \"pct\": %.1f }%s%n",
                    name, stats[0], avgMs, stats[1], pct,
                    i < sorted.size() - 1 ? "," : "");
                i++;
            }
            
            pw.println("  },");
            pw.println();
            
            // Top 3 time consumers
            pw.println("  \"top_3\": [");
            for (int j = 0; j < Math.min(3, sorted.size()); j++) {
                Map.Entry<String, double[]> e = sorted.get(j);
                double pct = totalFrameMs > 0 ? (e.getValue()[0] / totalFrameMs) * 100 : 0;
                pw.printf("    { \"name\": \"%s\", \"pct\": %.1f }%s%n",
                    e.getKey(), pct, j < 2 && j < sorted.size() - 1 ? "," : "");
            }
            pw.println("  ]");
            pw.println("}");
        }
        System.out.println("[PerfSnapshot] Wrote: " + outputDir + "/timers.json");
    }
}
