package com.voxelgame.save;

import java.io.*;
import java.nio.file.Path;

/**
 * Locks the worldgen seed for an existing save. Prevents changing
 * the seed after world creation to avoid chunk seams.
 * Simple implementation: stores the seed in a lock file.
 */
public class GeneratorLock {

    private static final String LOCK_FILE = "generator.lock";

    /**
     * Write the seed to the lock file in the save directory.
     */
    public static void writeLock(Path saveDir, long seed) throws IOException {
        saveDir.toFile().mkdirs();
        Path lockFile = saveDir.resolve(LOCK_FILE);
        try (PrintWriter pw = new PrintWriter(new FileWriter(lockFile.toFile()))) {
            pw.println(seed);
        }
    }

    /**
     * Read the seed from the lock file. Returns null if not present.
     */
    public static Long readLock(Path saveDir) {
        Path lockFile = saveDir.resolve(LOCK_FILE);
        if (!lockFile.toFile().exists()) return null;
        try (BufferedReader br = new BufferedReader(new FileReader(lockFile.toFile()))) {
            return Long.parseLong(br.readLine().trim());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Validate that the given seed matches the locked seed.
     * Returns true if no lock exists or if seeds match.
     */
    public static boolean validate(Path saveDir, long seed) {
        Long locked = readLock(saveDir);
        return locked == null || locked == seed;
    }
}
