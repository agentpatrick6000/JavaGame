package com.voxelgame.save;

import java.io.*;
import java.nio.file.Path;
import java.util.Properties;

/**
 * World metadata stored in world.dat as a simple key=value properties file.
 * Contains: seed, player position/rotation, game time, creation time.
 */
public class WorldMeta {

    private long seed;
    private float playerX, playerY, playerZ;
    private float playerYaw, playerPitch;
    private long createdAt;
    private long lastPlayedAt;

    public WorldMeta() {
        this.createdAt = System.currentTimeMillis();
        this.lastPlayedAt = this.createdAt;
    }

    public WorldMeta(long seed) {
        this();
        this.seed = seed;
    }

    // --- Getters / Setters ---

    public long getSeed() { return seed; }
    public void setSeed(long seed) { this.seed = seed; }

    public float getPlayerX() { return playerX; }
    public float getPlayerY() { return playerY; }
    public float getPlayerZ() { return playerZ; }
    public void setPlayerPosition(float x, float y, float z) {
        this.playerX = x;
        this.playerY = y;
        this.playerZ = z;
    }

    public float getPlayerYaw() { return playerYaw; }
    public float getPlayerPitch() { return playerPitch; }
    public void setPlayerRotation(float yaw, float pitch) {
        this.playerYaw = yaw;
        this.playerPitch = pitch;
    }

    public long getCreatedAt() { return createdAt; }
    public long getLastPlayedAt() { return lastPlayedAt; }
    public void setLastPlayedAt(long time) { this.lastPlayedAt = time; }

    // --- Serialization ---

    /**
     * Save metadata to a world.dat file in the given directory.
     */
    public void save(Path directory) throws IOException {
        directory.toFile().mkdirs();
        Path file = directory.resolve("world.dat");

        Properties props = new Properties();
        props.setProperty("seed", Long.toString(seed));
        props.setProperty("playerX", Float.toString(playerX));
        props.setProperty("playerY", Float.toString(playerY));
        props.setProperty("playerZ", Float.toString(playerZ));
        props.setProperty("playerYaw", Float.toString(playerYaw));
        props.setProperty("playerPitch", Float.toString(playerPitch));
        props.setProperty("createdAt", Long.toString(createdAt));
        props.setProperty("lastPlayedAt", Long.toString(System.currentTimeMillis()));

        try (OutputStream os = new FileOutputStream(file.toFile())) {
            props.store(os, "VoxelGame World Metadata");
        }
    }

    /**
     * Load metadata from a world.dat file in the given directory.
     * Returns null if the file doesn't exist.
     */
    public static WorldMeta load(Path directory) throws IOException {
        Path file = directory.resolve("world.dat");
        if (!file.toFile().exists()) return null;

        Properties props = new Properties();
        try (InputStream is = new FileInputStream(file.toFile())) {
            props.load(is);
        }

        WorldMeta meta = new WorldMeta();
        meta.seed = Long.parseLong(props.getProperty("seed", "0"));
        meta.playerX = Float.parseFloat(props.getProperty("playerX", "0"));
        meta.playerY = Float.parseFloat(props.getProperty("playerY", "80"));
        meta.playerZ = Float.parseFloat(props.getProperty("playerZ", "0"));
        meta.playerYaw = Float.parseFloat(props.getProperty("playerYaw", "0"));
        meta.playerPitch = Float.parseFloat(props.getProperty("playerPitch", "0"));
        meta.createdAt = Long.parseLong(props.getProperty("createdAt", "0"));
        meta.lastPlayedAt = Long.parseLong(props.getProperty("lastPlayedAt", "0"));

        return meta;
    }

    /**
     * Check if a world.dat file exists in the given directory.
     */
    public static boolean exists(Path directory) {
        return directory.resolve("world.dat").toFile().exists();
    }
}
