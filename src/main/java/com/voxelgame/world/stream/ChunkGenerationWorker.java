package com.voxelgame.world.stream;

import com.voxelgame.world.Chunk;
import com.voxelgame.world.gen.GenPipeline;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * Background worker thread for chunk generation. Pulls tasks from the
 * queue and generates terrain using the GenPipeline.
 */
public class ChunkGenerationWorker implements Runnable {

    private static final long WORLD_SEED = 12345L;

    private final BlockingQueue<ChunkTask> taskQueue;
    private final ConcurrentLinkedQueue<ChunkTask> completedQueue;
    private final GenPipeline pipeline;
    private volatile boolean running = true;

    public ChunkGenerationWorker(BlockingQueue<ChunkTask> taskQueue,
                                  ConcurrentLinkedQueue<ChunkTask> completedQueue) {
        this.taskQueue = taskQueue;
        this.completedQueue = completedQueue;
        this.pipeline = GenPipeline.createDefault(WORLD_SEED);
    }

    /** Get the pipeline (for spawn point finding). */
    public GenPipeline getPipeline() {
        return pipeline;
    }

    @Override
    public void run() {
        while (running) {
            try {
                ChunkTask task = taskQueue.poll(100, TimeUnit.MILLISECONDS);
                if (task == null) continue;

                // Generate the chunk using the full pipeline
                Chunk chunk = new Chunk(task.getPos());
                pipeline.generate(chunk);
                task.setResult(chunk);
                completedQueue.add(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void stop() {
        running = false;
    }
}
