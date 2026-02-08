package com.voxelgame.core;

import com.voxelgame.render.Renderer;
import com.voxelgame.sim.GameMode;
import com.voxelgame.sim.Player;
import com.voxelgame.world.WorldTime;
import com.voxelgame.world.stream.ChunkManager;

import java.io.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Performance capture mode for profiling world streaming + render pipeline.
 * 
 * Captures per-frame metrics during a high-altitude fast-flight scenario.
 * Outputs: perf_samples.csv, perf_summary.json, run.log, SHA256SUMS.txt
 */
public class PerfCapture {

    // Scenario: high altitude fast flight
    private static final float START_X = 0.0f;
    private static final float START_Y = 200.0f;  // High altitude
    private static final float START_Z = 0.0f;
    private static final float FLIGHT_SPEED = 50.0f;  // ~3 chunks/sec at 16 blocks/chunk
    private static final float FLIGHT_Y = 200.0f;
    private static final int DURATION_SECONDS = 30;
    private static final int SAMPLE_RATE_HZ = 60;  // Per-frame at 60fps target

    // Phases
    private static final int PHASE_INIT = 0;
    private static final int PHASE_WARMUP = 1;
    private static final int PHASE_CAPTURE = 2;
    private static final int PHASE_COMPLETE = 3;

    private int phase = PHASE_INIT;
    private float timer = 0;
    private int warmupFrames = 0;
    private static final int WARMUP_FRAME_COUNT = 120;

    private final String outputDir;
    private final String scenarioName;
    private String gitHeadHash = "unknown";

    // References
    private Player player;
    private WorldTime worldTime;
    private Renderer renderer;
    private ChunkManager chunkManager;

    // Flight state
    private float flightAngle = 0;
    private float flightDistance = 0;

    // Timing (nanoseconds)
    private long frameStartNs;
    private long lastFrameNs = 0;  // Duration of the last completed frame
    private long captureStartMs;

    // Per-frame metrics
    private List<Sample> samples = new ArrayList<>();
    private StringBuilder runLog = new StringBuilder();

    // Current frame breakdown (set by external profiler hooks)
    private long cpuWorldNs = 0;
    private long cpuGenNs = 0;
    private long cpuMeshNs = 0;
    private long cpuUploadNs = 0;
    private long cpuRenderSubmitNs = 0;
    private long cpuWaitNs = 0;

    private static class Sample {
        long timestampMs;
        float cpuFrameMs;
        float cpuWorldMs;
        float cpuGenMs;
        float cpuMeshMs;
        float cpuUploadMs;
        float cpuRenderSubmitMs;
        float cpuWaitMs;
        int chunksLoaded;
        int chunksVisible;
        int chunksMeshed;
        int meshJobsPending;
        int genJobsPending;
        int ioJobsPending;
        int ioJobsDropped;
        long bytesUploaded;
        int drawCalls;
        int triangles;
        float playerX, playerY, playerZ;
    }

    public PerfCapture(String outputDir, String scenarioName) {
        this.outputDir = outputDir != null ? outputDir : "artifacts/perf_live";
        this.scenarioName = scenarioName != null ? scenarioName : "HIGH_ALT_FAST_FLIGHT";

        // Get git hash
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "HEAD");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            if (line != null && line.length() >= 7) {
                gitHeadHash = line.trim();
            }
            p.waitFor();
        } catch (Exception e) {
            log("[PerfCapture] Failed to get git hash: " + e.getMessage());
        }
    }

    public void setReferences(Player player, WorldTime worldTime, Renderer renderer, ChunkManager chunkManager) {
        this.player = player;
        this.worldTime = worldTime;
        this.renderer = renderer;
        this.chunkManager = chunkManager;
    }

    public boolean isComplete() {
        return phase == PHASE_COMPLETE;
    }

    public void beginFrame() {
        frameStartNs = System.nanoTime();
    }

    public void endFrame() {
        lastFrameNs = System.nanoTime() - frameStartNs;
    }

    // Profiler hooks - call these from the appropriate places
    public void recordWorldTime(long ns) { cpuWorldNs = ns; }
    public void recordGenTime(long ns) { cpuGenNs = ns; }
    public void recordMeshTime(long ns) { cpuMeshNs = ns; }
    public void recordUploadTime(long ns) { cpuUploadNs = ns; }
    public void recordRenderSubmitTime(long ns) { cpuRenderSubmitNs = ns; }
    public void recordWaitTime(long ns) { cpuWaitNs = ns; }

    public void update(float dt) {
        timer += dt;

        switch (phase) {
            case PHASE_INIT -> handleInit();
            case PHASE_WARMUP -> handleWarmup();
            case PHASE_CAPTURE -> handleCapture(dt);
        }
    }

    private void handleInit() {
        if (timer < 0.5f) {
            player.setGameMode(GameMode.CREATIVE);
            if (!player.isFlyMode()) player.toggleFlyMode();
            return;
        }

        log("[PerfCapture] Starting scenario: " + scenarioName);
        log("[PerfCapture] Output: " + outputDir + "/" + scenarioName);
        log("[PerfCapture] Duration: " + DURATION_SECONDS + "s at " + FLIGHT_SPEED + " blocks/sec");

        // Create output directory
        new File(outputDir + "/" + scenarioName).mkdirs();

        // Set starting position
        player.getCamera().getPosition().set(START_X, START_Y, START_Z);
        player.getCamera().setYaw(0);
        player.getCamera().setPitch(-10);  // Slightly down to see terrain

        // Set time to noon for consistent lighting
        if (worldTime != null) {
            worldTime.setWorldTick(6000);
        }

        phase = PHASE_WARMUP;
        timer = 0;
        warmupFrames = 0;
    }

    private void handleWarmup() {
        warmupFrames++;

        // Keep position fixed during warmup
        player.getCamera().getPosition().set(START_X, START_Y, START_Z);

        if (warmupFrames >= WARMUP_FRAME_COUNT) {
            log("[PerfCapture] Warmup complete (" + warmupFrames + " frames)");
            phase = PHASE_CAPTURE;
            timer = 0;
            captureStartMs = System.currentTimeMillis();
            flightDistance = 0;
            flightAngle = 0;
        }
    }

    private void handleCapture(float dt) {
        // Flight pattern: spiral outward at high altitude
        flightAngle += 0.3f * dt;  // Slow turn
        flightDistance += FLIGHT_SPEED * dt;

        float targetX = START_X + (float) Math.cos(flightAngle) * flightDistance * 0.3f + flightDistance * 0.7f;
        float targetZ = START_Z + (float) Math.sin(flightAngle) * flightDistance * 0.3f;

        // Move toward target
        float dx = targetX - player.getCamera().getPosition().x;
        float dz = targetZ - player.getCamera().getPosition().z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        if (dist > 0.1f) {
            player.getCamera().getPosition().x += (dx / dist) * FLIGHT_SPEED * dt;
            player.getCamera().getPosition().z += (dz / dist) * FLIGHT_SPEED * dt;
        }
        player.getCamera().getPosition().y = FLIGHT_Y;

        // Look in movement direction
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        player.getCamera().setYaw(yaw);

        // Record sample
        takeSample();

        // Check completion
        long elapsedMs = System.currentTimeMillis() - captureStartMs;
        if (elapsedMs >= DURATION_SECONDS * 1000L) {
            finish();
            phase = PHASE_COMPLETE;
        }
    }

    private void takeSample() {
        Sample s = new Sample();
        s.timestampMs = System.currentTimeMillis() - captureStartMs;

        // Frame time (from previous frame - current frame is still in progress)
        s.cpuFrameMs = lastFrameNs / 1_000_000.0f;

        // Breakdown times
        s.cpuWorldMs = cpuWorldNs / 1_000_000.0f;
        s.cpuGenMs = cpuGenNs / 1_000_000.0f;
        s.cpuMeshMs = cpuMeshNs / 1_000_000.0f;
        s.cpuUploadMs = cpuUploadNs / 1_000_000.0f;
        s.cpuRenderSubmitMs = cpuRenderSubmitNs / 1_000_000.0f;
        s.cpuWaitMs = cpuWaitNs / 1_000_000.0f;

        // Chunk stats
        if (chunkManager != null) {
            s.chunksLoaded = chunkManager.getTotalChunks();
            s.chunksVisible = renderer != null ? renderer.getRenderedChunks() : 0;
            s.chunksMeshed = chunkManager.getMeshedChunks();
            s.meshJobsPending = chunkManager.getPendingMeshJobs();
            s.genJobsPending = chunkManager.getPendingGenJobs();
            s.ioJobsPending = chunkManager.getPendingIoJobs();
            s.ioJobsDropped = (int) chunkManager.getIoJobsDropped();
        }

        // Render stats
        if (renderer != null) {
            s.drawCalls = renderer.getDrawCalls();
            s.triangles = renderer.getTriangleCount();
            s.bytesUploaded = renderer.getBytesUploaded();
        }

        // Player position
        s.playerX = player.getCamera().getPosition().x;
        s.playerY = player.getCamera().getPosition().y;
        s.playerZ = player.getCamera().getPosition().z;

        samples.add(s);

        // Reset per-frame counters
        cpuWorldNs = 0;
        cpuGenNs = 0;
        cpuMeshNs = 0;
        cpuUploadNs = 0;
        cpuRenderSubmitNs = 0;
        cpuWaitNs = 0;
    }

    private void finish() {
        log("[PerfCapture] Capture complete. " + samples.size() + " samples collected.");

        String dir = outputDir + "/" + scenarioName;

        try {
            writeSamples(dir);
            writeSummary(dir);
            writeRunLog(dir);
            log("[PerfCapture] Results written to: " + dir);
        } catch (IOException e) {
            log("[PerfCapture] ERROR: " + e.getMessage());
        }
    }

    private void writeSamples(String dir) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(new File(dir, "perf_samples.csv")))) {
            pw.println("timestamp_ms,cpu_frame_ms,cpu_world_ms,cpu_gen_ms,cpu_mesh_ms,cpu_upload_ms,cpu_render_submit_ms,cpu_wait_ms,chunks_loaded,chunks_visible,chunks_meshed,mesh_jobs_pending,gen_jobs_pending,io_jobs_pending,io_jobs_dropped,bytes_uploaded,draw_calls,triangles,player_x,player_y,player_z");
            for (Sample s : samples) {
                pw.printf("%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.1f,%.1f,%.1f%n",
                    s.timestampMs, s.cpuFrameMs, s.cpuWorldMs, s.cpuGenMs, s.cpuMeshMs,
                    s.cpuUploadMs, s.cpuRenderSubmitMs, s.cpuWaitMs,
                    s.chunksLoaded, s.chunksVisible, s.chunksMeshed,
                    s.meshJobsPending, s.genJobsPending, s.ioJobsPending, s.ioJobsDropped,
                    s.bytesUploaded, s.drawCalls, s.triangles,
                    s.playerX, s.playerY, s.playerZ);
            }
        }
        log("[PerfCapture] Saved: " + dir + "/perf_samples.csv");
    }

    private void writeSummary(String dir) throws IOException {
        if (samples.isEmpty()) return;

        // Calculate statistics
        List<Float> frameTimes = new ArrayList<>();
        float totalFrameMs = 0, totalWorldMs = 0, totalGenMs = 0, totalMeshMs = 0;
        float totalUploadMs = 0, totalRenderMs = 0, totalWaitMs = 0;
        int maxChunksLoaded = 0, maxMeshPending = 0, maxGenPending = 0, maxIoPending = 0;
        long totalBytesUploaded = 0;
        int totalDrawCalls = 0;
        long totalTriangles = 0;

        for (Sample s : samples) {
            frameTimes.add(s.cpuFrameMs);
            totalFrameMs += s.cpuFrameMs;
            totalWorldMs += s.cpuWorldMs;
            totalGenMs += s.cpuGenMs;
            totalMeshMs += s.cpuMeshMs;
            totalUploadMs += s.cpuUploadMs;
            totalRenderMs += s.cpuRenderSubmitMs;
            totalWaitMs += s.cpuWaitMs;

            if (s.chunksLoaded > maxChunksLoaded) maxChunksLoaded = s.chunksLoaded;
            if (s.meshJobsPending > maxMeshPending) maxMeshPending = s.meshJobsPending;
            if (s.genJobsPending > maxGenPending) maxGenPending = s.genJobsPending;
            if (s.ioJobsPending > maxIoPending) maxIoPending = s.ioJobsPending;

            totalBytesUploaded += s.bytesUploaded;
            totalDrawCalls += s.drawCalls;
            totalTriangles += s.triangles;
        }

        int n = samples.size();
        Collections.sort(frameTimes);
        float p50 = percentile(frameTimes, 0.50f);
        float p95 = percentile(frameTimes, 0.95f);
        float p99 = percentile(frameTimes, 0.99f);
        float fpsAvg = 1000.0f / (totalFrameMs / n);

        // Determine dominant limiter
        float avgWorld = totalWorldMs / n;
        float avgGen = totalGenMs / n;
        float avgMesh = totalMeshMs / n;
        float avgUpload = totalUploadMs / n;
        float avgRender = totalRenderMs / n;
        float avgWait = totalWaitMs / n;

        String dominantLimiter = "unknown";
        float maxAvg = Math.max(avgWorld, Math.max(avgGen, Math.max(avgMesh, Math.max(avgUpload, Math.max(avgRender, avgWait)))));
        if (maxAvg == avgGen) dominantLimiter = "CPU_GEN";
        else if (maxAvg == avgMesh) dominantLimiter = "CPU_MESH";
        else if (maxAvg == avgUpload) dominantLimiter = "CPU_UPLOAD";
        else if (maxAvg == avgRender) dominantLimiter = "CPU_RENDER";
        else if (maxAvg == avgWait) dominantLimiter = "CPU_WAIT";
        else if (maxAvg == avgWorld) dominantLimiter = "CPU_WORLD";

        try (PrintWriter pw = new PrintWriter(new FileWriter(new File(dir, "perf_summary.json")))) {
            pw.println("{");
            pw.println("  \"git_head_hash\": \"" + gitHeadHash + "\",");
            pw.println("  \"scenario\": \"" + scenarioName + "\",");
            pw.println("  \"duration_seconds\": " + DURATION_SECONDS + ",");
            pw.println("  \"sample_count\": " + n + ",");
            pw.println("  \"flight_speed\": " + FLIGHT_SPEED + ",");
            pw.println("  \"flight_altitude\": " + FLIGHT_Y + ",");
            pw.println();
            pw.println("  \"frame_time_ms\": {");
            pw.printf("    \"avg\": %.3f,%n", totalFrameMs / n);
            pw.printf("    \"p50\": %.3f,%n", p50);
            pw.printf("    \"p95\": %.3f,%n", p95);
            pw.printf("    \"p99\": %.3f%n", p99);
            pw.println("  },");
            pw.printf("  \"fps_avg\": %.2f,%n", fpsAvg);
            pw.println();
            pw.println("  \"cpu_breakdown_avg_ms\": {");
            pw.printf("    \"world\": %.3f,%n", avgWorld);
            pw.printf("    \"gen\": %.3f,%n", avgGen);
            pw.printf("    \"mesh\": %.3f,%n", avgMesh);
            pw.printf("    \"upload\": %.3f,%n", avgUpload);
            pw.printf("    \"render_submit\": %.3f,%n", avgRender);
            pw.printf("    \"wait\": %.3f%n", avgWait);
            pw.println("  },");
            pw.println("  \"dominant_limiter\": \"" + dominantLimiter + "\",");
            pw.println();
            pw.println("  \"chunk_stats\": {");
            pw.printf("    \"max_loaded\": %d,%n", maxChunksLoaded);
            pw.printf("    \"max_mesh_pending\": %d,%n", maxMeshPending);
            pw.printf("    \"max_gen_pending\": %d,%n", maxGenPending);
            pw.printf("    \"max_io_pending\": %d%n", maxIoPending);
            pw.println("  },");
            pw.println();
            pw.println("  \"render_stats\": {");
            pw.printf("    \"total_bytes_uploaded\": %d,%n", totalBytesUploaded);
            pw.printf("    \"avg_draw_calls\": %d,%n", totalDrawCalls / n);
            pw.printf("    \"avg_triangles\": %d%n", totalTriangles / n);
            pw.println("  },");
            pw.println();
            pw.println("  \"capture_timestamp\": \"" + Instant.now().toString() + "\"");
            pw.println("}");
        }
        log("[PerfCapture] Saved: " + dir + "/perf_summary.json");
    }

    private void writeRunLog(String dir) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(new File(dir, "run.log")))) {
            pw.print(runLog.toString());
        }
        log("[PerfCapture] Saved: " + dir + "/run.log");
    }

    private void log(String msg) {
        String line = "[" + Instant.now() + "] " + msg;
        System.out.println(line);
        runLog.append(line).append("\n");
    }

    private static float percentile(List<Float> sorted, float p) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) (sorted.size() * p);
        return sorted.get(Math.min(idx, sorted.size() - 1));
    }
}
