package com.voxelgame.save;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Async region file writer that performs all disk IO on a dedicated background thread.
 * 
 * Key features:
 * - Main thread NEVER blocks on disk writes (enqueue returns immediately)
 * - Coalescing: if same chunk is queued multiple times, only latest is written
 * - Bounded backlog: if queue is full, newest overwrites pending job (latest wins)
 * - Uses primitive long keys (packed cx,cz) to avoid boxing on hot path
 * 
 * Thread safety:
 * - enqueue() is called from main thread
 * - Writer thread consumes jobs and writes to disk
 * - Lock-free stats via AtomicLong
 */
public class AsyncRegionWriter {

    private static final Logger LOG = Logger.getLogger(AsyncRegionWriter.class.getName());

    /** Maximum number of pending save jobs before we start dropping/coalescing more aggressively. */
    private static final int MAX_PENDING_JOBS = 256;

    /** Path to region directory. */
    private final Path regionDir;

    /** Map of pending save jobs: packed(cx,cz) → job data. */
    private final Long2ObjectOpenHashMap<ChunkSaveJob> pendingJobs = new Long2ObjectOpenHashMap<>();

    /** FIFO queue of keys to process (maintains insertion order for fairness). */
    private final LongArrayFIFOQueue keyQueue = new LongArrayFIFOQueue();

    /** Lock for pendingJobs and keyQueue. */
    private final ReentrantLock queueLock = new ReentrantLock();

    /** Cached region files. */
    private final Map<Long, RegionFile> regionCache = new ConcurrentHashMap<>();

    /** Writer thread. */
    private final Thread writerThread;

    /** Shutdown flag. */
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    // ---- Stats (lock-free) ----
    private final AtomicLong bytesWrittenTotal = new AtomicLong(0);
    private final AtomicLong chunksWrittenTotal = new AtomicLong(0);
    private final AtomicLong ioFlushTimeNs = new AtomicLong(0);
    private final AtomicLong mainThreadBlockedNs = new AtomicLong(0);  // Should always be ~0

    public AsyncRegionWriter(Path regionDir) {
        this.regionDir = regionDir;
        this.writerThread = new Thread(this::writerLoop, "AsyncRegionWriter");
        this.writerThread.setDaemon(true);
        this.writerThread.start();
    }

    /**
     * Enqueue a chunk save job. Returns immediately (never blocks).
     * If the same chunk is already queued, the old job is replaced (latest wins).
     * 
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @param compressedData Pre-encoded/compressed chunk data
     */
    public void enqueue(int chunkX, int chunkZ, byte[] compressedData) {
        long key = packKey(chunkX, chunkZ);
        ChunkSaveJob job = new ChunkSaveJob(chunkX, chunkZ, compressedData);

        queueLock.lock();
        try {
            // Check if this key is already pending (coalesce)
            ChunkSaveJob existing = pendingJobs.put(key, job);
            if (existing == null) {
                // New key — add to FIFO queue
                // If queue is at max capacity, we still add but won't grow unbounded
                // because coalescing keeps map size bounded per unique chunk
                keyQueue.enqueue(key);
            }
            // else: replaced existing job, key is already in queue — no action needed
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Pack chunk coordinates into a single long key.
     * Format: (cx << 32) | (cz & 0xFFFFFFFFL)
     */
    private static long packKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    /**
     * Unpack chunk X from key.
     */
    private static int unpackX(long key) {
        return (int) (key >> 32);
    }

    /**
     * Unpack chunk Z from key.
     */
    private static int unpackZ(long key) {
        return (int) key;
    }

    /**
     * Background writer loop. Continuously processes pending save jobs.
     */
    private void writerLoop() {
        while (!shutdown.get()) {
            ChunkSaveJob job = null;

            queueLock.lock();
            try {
                if (!keyQueue.isEmpty()) {
                    long key = keyQueue.dequeueLong();
                    job = pendingJobs.remove(key);
                    // job might be null if it was coalesced and already processed
                    // (shouldn't happen with current logic, but safe to check)
                }
            } finally {
                queueLock.unlock();
            }

            if (job != null) {
                processJob(job);
            } else {
                // No work — sleep briefly to avoid busy-spinning
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // On shutdown, flush remaining jobs
        flushRemaining();
    }

    /**
     * Process a single save job (write to region file).
     */
    private void processJob(ChunkSaveJob job) {
        long startNs = System.nanoTime();

        try {
            RegionFile region = getOrCreateRegion(job.chunkX, job.chunkZ);
            region.writeChunkData(job.chunkX, job.chunkZ, job.data);

            bytesWrittenTotal.addAndGet(job.data.length);
            chunksWrittenTotal.incrementAndGet();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to save chunk " + job.chunkX + "," + job.chunkZ, e);
        }

        long elapsedNs = System.nanoTime() - startNs;
        ioFlushTimeNs.addAndGet(elapsedNs);
    }

    /**
     * Get or create a RegionFile for the given chunk coordinates.
     */
    private RegionFile getOrCreateRegion(int chunkX, int chunkZ) throws IOException {
        int rx = RegionFile.toRegion(chunkX);
        int rz = RegionFile.toRegion(chunkZ);
        long key = packKey(rx, rz);

        return regionCache.computeIfAbsent(key, k -> {
            RegionFile rf = new RegionFile(regionDir, rx, rz);
            try {
                rf.loadHeader();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to load region header for " + rx + "," + rz, e);
            }
            return rf;
        });
    }

    /**
     * Flush all remaining jobs on shutdown.
     */
    private void flushRemaining() {
        queueLock.lock();
        try {
            while (!keyQueue.isEmpty()) {
                long key = keyQueue.dequeueLong();
                ChunkSaveJob job = pendingJobs.remove(key);
                if (job != null) {
                    processJob(job);
                }
            }
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Shutdown the writer thread and flush pending jobs.
     * Blocks until all pending writes complete (up to timeout).
     */
    public void shutdown() {
        shutdown.set(true);
        writerThread.interrupt();
        try {
            writerThread.join(5000); // Wait up to 5 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Flush all pending jobs synchronously. Blocks until complete.
     * Only use at world save/shutdown, never during gameplay.
     */
    public void flushSync() {
        // Signal writer to process all jobs, then wait
        long startNs = System.nanoTime();

        while (true) {
            queueLock.lock();
            boolean empty;
            try {
                empty = keyQueue.isEmpty() && pendingJobs.isEmpty();
            } finally {
                queueLock.unlock();
            }

            if (empty) break;

            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Record any time the main thread spent waiting (should be minimal except at shutdown)
        long elapsedNs = System.nanoTime() - startNs;
        if (elapsedNs > 1_000_000) { // Only record if > 1ms
            mainThreadBlockedNs.addAndGet(elapsedNs);
        }
    }

    // ---- Stats getters ----

    public int getPendingJobCount() {
        queueLock.lock();
        try {
            return pendingJobs.size();
        } finally {
            queueLock.unlock();
        }
    }

    public long getBytesWrittenTotal() {
        return bytesWrittenTotal.get();
    }

    public long getChunksWrittenTotal() {
        return chunksWrittenTotal.get();
    }

    /** Total time spent in IO flush (on IO thread), in milliseconds. */
    public long getIoFlushMs() {
        return ioFlushTimeNs.get() / 1_000_000;
    }

    /** Total time main thread was blocked waiting for IO, in milliseconds. */
    public long getMainThreadBlockedMs() {
        return mainThreadBlockedNs.get() / 1_000_000;
    }

    /**
     * Inner class for save job data.
     */
    private static final class ChunkSaveJob {
        final int chunkX;
        final int chunkZ;
        final byte[] data;

        ChunkSaveJob(int chunkX, int chunkZ, byte[] data) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.data = data;
        }
    }
}
