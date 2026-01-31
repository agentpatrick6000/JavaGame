package com.voxelgame.save;

import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Region-based chunk storage. Each region file stores up to 32×32 = 1024 chunks.
 *
 * File format:
 *   Header: 1024 entries × 8 bytes each = 8192 bytes
 *     Each entry: [4 bytes offset from start of file] [4 bytes data length]
 *     Offset 0 + length 0 = chunk not present.
 *   Data section: concatenated compressed chunk blobs, appended after header.
 *
 * File naming: r.{regionX}.{regionZ}.dat
 * Region coordinates: regionX = floorDiv(chunkX, 32), regionZ = floorDiv(chunkZ, 32)
 */
public class RegionFile {

    /** Chunks per region axis. */
    public static final int REGION_SIZE = 32;

    /** Number of header entries. */
    private static final int ENTRY_COUNT = REGION_SIZE * REGION_SIZE;

    /** Header size in bytes: 1024 entries × 8 bytes (offset + length). */
    private static final int HEADER_SIZE = ENTRY_COUNT * 8;

    private final Path filePath;
    private final int regionX, regionZ;

    /** In-memory offset table: index → file offset. */
    private final int[] offsets = new int[ENTRY_COUNT];
    /** In-memory length table: index → data length in bytes. */
    private final int[] lengths = new int[ENTRY_COUNT];

    public RegionFile(Path directory, int regionX, int regionZ) {
        this.regionX = regionX;
        this.regionZ = regionZ;
        this.filePath = directory.resolve("r." + regionX + "." + regionZ + ".dat");
    }

    /** Compute the header index for a chunk within this region. */
    private int entryIndex(int chunkX, int chunkZ) {
        int lx = Math.floorMod(chunkX, REGION_SIZE);
        int lz = Math.floorMod(chunkZ, REGION_SIZE);
        return lz * REGION_SIZE + lx;
    }

    /**
     * Read the header from an existing region file.
     * If the file doesn't exist, all offsets/lengths remain zero.
     */
    public void loadHeader() throws IOException {
        File file = filePath.toFile();
        if (!file.exists()) return;

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (raf.length() < HEADER_SIZE) return; // corrupt/empty file
            byte[] headerBytes = new byte[HEADER_SIZE];
            raf.readFully(headerBytes);
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(headerBytes));
            for (int i = 0; i < ENTRY_COUNT; i++) {
                offsets[i] = dis.readInt();
                lengths[i] = dis.readInt();
            }
        }
    }

    /**
     * Check if a chunk is stored in this region file.
     */
    public boolean hasChunk(int chunkX, int chunkZ) {
        int idx = entryIndex(chunkX, chunkZ);
        return offsets[idx] != 0 && lengths[idx] > 0;
    }

    /**
     * Read a chunk's compressed data from the region file.
     * Returns null if the chunk is not stored.
     */
    public byte[] readChunkData(int chunkX, int chunkZ) throws IOException {
        int idx = entryIndex(chunkX, chunkZ);
        if (offsets[idx] == 0 || lengths[idx] == 0) return null;

        File file = filePath.toFile();
        if (!file.exists()) return null;

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(offsets[idx]);
            byte[] data = new byte[lengths[idx]];
            raf.readFully(data);
            return data;
        }
    }

    /**
     * Write a chunk's compressed data to the region file.
     * Uses a simple append-and-rewrite strategy: rewrites the entire file
     * to avoid fragmentation.
     */
    public void writeChunkData(int chunkX, int chunkZ, byte[] data) throws IOException {
        int idx = entryIndex(chunkX, chunkZ);

        // Read all existing chunk data
        Map<Integer, byte[]> allChunks = readAllChunks();
        allChunks.put(idx, data);

        // Rewrite the entire file
        writeAllChunks(allChunks);
    }

    /**
     * Write multiple chunks at once (batch save). More efficient than individual writes.
     *
     * @param chunkData map of (chunkX, chunkZ) → compressed data
     */
    public void writeChunks(Map<long[], byte[]> chunkData) throws IOException {
        Map<Integer, byte[]> allChunks = readAllChunks();
        for (var entry : chunkData.entrySet()) {
            int cx = (int) entry.getKey()[0];
            int cz = (int) entry.getKey()[1];
            int idx = entryIndex(cx, cz);
            allChunks.put(idx, entry.getValue());
        }
        writeAllChunks(allChunks);
    }

    /**
     * Read all chunks currently stored in this region file.
     * Returns map of entry index → compressed data.
     */
    private Map<Integer, byte[]> readAllChunks() throws IOException {
        Map<Integer, byte[]> result = new HashMap<>();
        File file = filePath.toFile();
        if (!file.exists()) return result;

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (raf.length() < HEADER_SIZE) return result;

            for (int i = 0; i < ENTRY_COUNT; i++) {
                if (offsets[i] != 0 && lengths[i] > 0) {
                    raf.seek(offsets[i]);
                    byte[] data = new byte[lengths[i]];
                    raf.readFully(data);
                    result.put(i, data);
                }
            }
        }
        return result;
    }

    /**
     * Rewrite the entire region file with the given chunk data.
     * Compacts storage by removing gaps from deleted/overwritten chunks.
     */
    private void writeAllChunks(Map<Integer, byte[]> allChunks) throws IOException {
        // Reset tables
        for (int i = 0; i < ENTRY_COUNT; i++) {
            offsets[i] = 0;
            lengths[i] = 0;
        }

        // Ensure parent directory exists
        filePath.getParent().toFile().mkdirs();

        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "rw")) {
            // Skip header, write data first
            int dataOffset = HEADER_SIZE;
            raf.seek(dataOffset);

            for (var entry : allChunks.entrySet()) {
                int idx = entry.getKey();
                byte[] data = entry.getValue();
                offsets[idx] = (int) raf.getFilePointer();
                lengths[idx] = data.length;
                raf.write(data);
            }

            // Truncate file to current size (remove any leftover data from previous larger writes)
            raf.setLength(raf.getFilePointer());

            // Write header
            raf.seek(0);
            ByteArrayOutputStream baos = new ByteArrayOutputStream(HEADER_SIZE);
            DataOutputStream dos = new DataOutputStream(baos);
            for (int i = 0; i < ENTRY_COUNT; i++) {
                dos.writeInt(offsets[i]);
                dos.writeInt(lengths[i]);
            }
            dos.flush();
            raf.write(baos.toByteArray());
        }
    }

    public Path getFilePath() { return filePath; }
    public int getRegionX() { return regionX; }
    public int getRegionZ() { return regionZ; }

    /** Compute region coordinate from chunk coordinate. */
    public static int toRegion(int chunkCoord) {
        return Math.floorDiv(chunkCoord, REGION_SIZE);
    }
}
